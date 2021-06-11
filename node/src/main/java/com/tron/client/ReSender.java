package com.tron.client;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Maps;
import com.tron.client.message.TriggerResponse;
import com.tron.common.Config;
import com.tron.common.util.HttpUtil;
import com.tron.job.adapters.ContractAdapter;
import com.tron.keystore.KeyStore;
import com.tron.web.entity.TronTx;
import com.tron.web.service.TronTxService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.tron.common.Constant.*;

/** Subscribe the events of the oracle contracts and reply. */
@Slf4j
public class ReSender {

  @Autowired
  private TronTxService tronTxService;
  public ReSender(TronTxService _tronTxService) {
    tronTxService = _tronTxService;
  }

  private static final long RESEND_AFTER_THRESHOLD = 100_000L ; // 100 Second


  private ScheduledExecutorService listenExecutor = Executors.newSingleThreadScheduledExecutor();

  public void run() {
    listenExecutor.scheduleWithFixedDelay(
        () -> {
          try {
            pollResend();
          } catch (Throwable t) {
            log.error("Exception in pollResend ", t);
          }
        },
        0,
        5000, // poll every 5 seconds
        TimeUnit.MILLISECONDS);
  }

  private void pollResend() {
    // 1. for inprogress tx, confirmed=TronTxInProgress
    List<TronTx> inProgressTxes = tronTxService.getByConfirmedAndDate(TronTxInProgress,
        System.currentTimeMillis() - RESEND_AFTER_THRESHOLD);
    if(inProgressTxes == null || inProgressTxes.size() == 0) {
      return;
    }
    for (TronTx tx : inProgressTxes) {
      String txId = tx.getSurrogateId();
      String responseStr = getConfirmedTransationInfoById(txId);
      if("{}\n".equals(responseStr)) { // cannot find the tx
        long nodeBalance = 0L;
        try {
          nodeBalance = ContractAdapter.getTRXBalance(KeyStore.getAddr());
        } catch (Exception ex) {
          log.error("Exception in pollResend ", ex);
          return;
        }
        if (nodeBalance < Config.getMinFeeLimit()) {
          log.error("Insufficient TRX in the node account");
          continue;
        }
        log.info("Resending txes id: " + tx.getId());
        TronTx resendTx = resendUnconfirmed(tx);
        tronTxService.update(resendTx);
        continue;
      }
      JSONObject receipt = JSONObject.parseObject(JSONObject.parseObject(responseStr).getString("receipt"));
      String receiptResult = receipt.getString("result");
      /* contract result type:
        DEFAULT = 0;
        SUCCESS = 1;
        REVERT = 2;
        BAD_JUMP_DESTINATION = 3;
        OUT_OF_MEMORY = 4;
        PRECOMPILED_CONTRACT = 5;
        STACK_TOO_SMALL = 6;
        STACK_TOO_LARGE = 7;
        ILLEGAL_OPERATION = 8;
        STACK_OVERFLOW = 9;
        OUT_OF_ENERGY = 10;
        OUT_OF_TIME = 11;
        JVM_STACK_OVER_FLOW = 12;
        UNKNOWN = 13;
        TRANSFER_FAILED = 14;*/
      if ("OUT_OF_ENERGY".equals(receiptResult)) {
        long nodeBalance = 0L;
        try {
          nodeBalance = ContractAdapter.getTRXBalance(KeyStore.getAddr());
        } catch (Exception ex) {
          log.error("Exception in pollResend for OUT_OF_ENERGY tx", ex);
          return;
        }
        if (nodeBalance < Config.getMinFeeLimit()) {
          log.error("Insufficient TRX in the node account for OUT_OF_ENERGY tx");
          continue;
        }
        log.info("Resending OUT_OF_ENERGY txes id: " + tx.getId());
        TronTx resendTx = resendUnconfirmed(tx);
        tronTxService.update(resendTx);
      } else if ("SUCCESS".equals(receiptResult)) {
        tx.setConfirmed(TronTxConfirmed);
        tx.setUpdatedAt(new Date());
        tronTxService.update(tx);
      } else {
        tx.setConfirmed(TronTxFatalError);
        tx.setUpdatedAt(new Date());
        tronTxService.update(tx);
      }
    }
  }

  /** constructor. */
  public static String getConfirmedTransationInfoById(String txId) {
    try {
      Map<String, Object> params = Maps.newHashMap();
      params.put("value", txId);
      params.put("visible", true);
      String response =
              HttpUtil.post("https", FULLNODE_HOST, "/walletsolidity/gettransactioninfobyid", params);

      return response;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  private static Map<String, Object> convertWithStream(String mapAsString) {
    Map<String, Object> map = Arrays.stream(mapAsString.split(","))
        .map(entry -> entry.split("="))
        .collect(Collectors.toMap(entry -> entry[0], entry -> entry[1]));
    return map;
  }

  private static TronTx resendUnconfirmed(TronTx tx) {
    try {
      String data = tx.getData();
      Map<String, Object> params = convertWithStream(data);
      params.put("call_value", 0); // reset
      params.put("visible", true);
      TronTx resendTx = OracleClient.triggerSignAndResponse(params);
      tx.setSurrogateId(resendTx.getSurrogateId());
      tx.setSignedRawTx(resendTx.getSignedRawTx()); // for resend
      tx.setHash(resendTx.getHash());
      tx.setSentAt(System.currentTimeMillis());
      tx.setUpdatedAt(new Date());
      tx.setConfirmed(TronTxInProgress);
      return tx;
    } catch (Exception ex) {
      return null;
    }
  }

}
