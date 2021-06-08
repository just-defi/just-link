package com.tron.client;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import com.tron.client.message.BroadCastResponse;
import com.tron.client.message.TriggerResponse;
import com.tron.common.AbiUtil;
import com.tron.common.util.HttpUtil;
import com.tron.keystore.KeyStore;
import com.tron.web.entity.TronTx;
import com.tron.web.service.TronTxService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.JsonUtil;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.exception.BadItemException;
import org.tron.protos.Protocol;

import java.io.IOException;
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

  public ReSender() {
  }

  private static final String EVENT_NAME = "OracleRequest";
  private static final String VRF_EVENT_NAME = "VRFRequest";
  private static final long MIN_FEE_LIMIT = 100_000_000L; // 100 trx
  private static final long RESEND_AFTER_THRESHOLD = 30_000L ; // 30 Second

  private static final HashMap<String, String> initiatorEventMap =
      new HashMap<String, String>() {
        {
          put(INITIATOR_TYPE_RUN_LOG, EVENT_NAME);
          put(INITIATOR_TYPE_RANDOMNESS_LOG, VRF_EVENT_NAME);
        }
      };

  private static Cache<String, String> requestIdsCache =
      CacheBuilder.newBuilder()
          .maximumSize(10000)
          .expireAfterWrite(12, TimeUnit.HOURS)
          .recordStats()
          .build();

  private static HashMap<String, HashMap<String, String>> listeningAddrs = Maps.newHashMap();
  private HashMap<String, Long> consumeIndexMap = Maps.newHashMap();

  private ScheduledExecutorService listenExecutor = Executors.newSingleThreadScheduledExecutor();

  public void run() {
    listenExecutor.scheduleWithFixedDelay(
        () -> {
          try {
            listen();
          } catch (Throwable t) {
            log.error("Exception in listener ", t);
          }
        },
        0,
        5000, // poll every 5 seconds
        TimeUnit.MILLISECONDS);
  }

  public static void registerJob(String address, String jobId, String initiatorType) {
    HashMap<String, String> map = listeningAddrs.get(address);
    if (map == null) {
      map = new HashMap<String, String>();
    }
    map.put(jobId, initiatorEventMap.get(initiatorType));
    listeningAddrs.put(address, map);
  }

  /**
   * @param request
   * @return transactionid
   */
  public static TronTx fulfil(FulfillRequest request) throws IOException, BadItemException {
    Map<String, Object> params = Maps.newHashMap();
    params.put("owner_address", KeyStore.getAddr());
    params.put("contract_address", request.getContractAddr());
    params.put("function_selector", FULFIL_METHOD_SIGN);
    params.put("parameter", AbiUtil.parseParameters(FULFIL_METHOD_SIGN, request.toList()));
    params.put("fee_limit", calculateFeeLimit(MIN_FEE_LIMIT));
    params.put("call_value", 0);
    params.put("visible", true);

    return triggerSignAndResponse(params);
  }

  /**
   * @param request
   * @return transactionid
   */
  public static TronTx vrfFulfil(FulfillRequest request) throws IOException, BadItemException {
    List<Object> parameters = Arrays.asList(request.getData());
    Map<String, Object> params = Maps.newHashMap();
    params.put("owner_address", KeyStore.getAddr());
    params.put("contract_address", request.getContractAddr());
    params.put("function_selector", VRF_FULFIL_METHOD_SIGN);
    params.put("parameter", AbiUtil.parseParameters(VRF_FULFIL_METHOD_SIGN, parameters));
    params.put("fee_limit", calculateFeeLimit(MIN_FEE_LIMIT));
    params.put("call_value", 0);
    params.put("visible", true);

    return triggerSignAndResponse(params);
  }

  private static TronTx triggerSignAndResponse(Map<String, Object> params)
      throws IOException {
    HttpResponse response =
        HttpUtil.post("https", FULLNODE_HOST, "/wallet/triggersmartcontract", params);
    HttpEntity responseEntity = response.getEntity();
    TriggerResponse triggerResponse = null;
    String responsrStr = EntityUtils.toString(responseEntity);
    triggerResponse = JsonUtil.json2Obj(responsrStr, TriggerResponse.class);

    // sign
    ECKey key = KeyStore.getKey();
    String rawDataHex = triggerResponse.getTransaction().getRawDataHex();
    Protocol.Transaction.raw raw =
        Protocol.Transaction.raw.parseFrom(ByteArray.fromHexString(rawDataHex));
    byte[] hash = Sha256Hash.hash(true, raw.toByteArray());
    ECKey.ECDSASignature signature = key.sign(hash);
    ByteString bsSign = ByteString.copyFrom(signature.toByteArray());
    TransactionCapsule transactionCapsule = new TransactionCapsule(raw, Arrays.asList(bsSign));

    String contractAddress = params.get("contract_address").toString();
    String data = params.get("parameter").toString();

    // broadcast
    params.clear();
    params.put("transaction", Hex.toHexString(transactionCapsule.getInstance().toByteArray()));
    response = HttpUtil.post("https", FULLNODE_HOST, "/wallet/broadcasthex", params);
    BroadCastResponse broadCastResponse =
        JsonUtil.json2Obj(EntityUtils.toString(response.getEntity()), BroadCastResponse.class);

    TronTx tx = new TronTx();
    tx.setFrom(KeyStore.getAddr());
    tx.setTo(contractAddress);
    tx.setSurrogateId(broadCastResponse.getTxid());
    tx.setSignedRawTx(bsSign.toString());
    tx.setHash(ByteArray.toHexString(hash));
    tx.setData(data);
    return tx;
  }

  private void listen() {
    // 1. for inprogress tx, confirmed=TronTxInProgress
    List<TronTx> inProgressTxes = tronTxService.getByConfirmedAndDate(TronTxInProgress,
        System.currentTimeMillis() - RESEND_AFTER_THRESHOLD);
    for (TronTx tx : inProgressTxes) {
      String txId = tx.getSurrogateId();
      String responseStr = getConfirmedTransationInfoById(txId);
      if("{}\n".equals(responseStr)) { // cannot find the tx
        System.out.println("zyd test resend: " + tx.getId());
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
        tx.setConfirmed(TronTxOutOfEnergy);
        tx.setUpdatedAt(new Date());
        tronTxService.update(tx);
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
      HttpResponse response =
          HttpUtil.post("https", FULLNODE_HOST, "/walletsolidity/gettransactioninfobyid", params);
      HttpEntity responseEntity = response.getEntity();
      TriggerResponse triggerResponse = null;
      String responseStr = EntityUtils.toString(responseEntity);

      return responseStr;
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
      TronTx resendTx = OracleClient.resendTriggerSignAndResponse(params);
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

  private static long calculateFeeLimit(long payment) {
    /*double trxBalance = 0;
    try {
      trxBalance = ContractAdapter.getTradePriceWithTRX(ContractAdapter.TradePair.JUST_TRX) * payment;
    } catch (IOException e) {
      return MIN_FEE_LIMIT;
    }
    if (Math.round(trxBalance) < MIN_FEE_LIMIT) {
      log.warn("the payment maybe even can't afford the energy cost, payment: {}", payment);
    }
    return Math.max(MIN_FEE_LIMIT, Math.round(trxBalance * 20 / 100));*/
    return MIN_FEE_LIMIT;
  }
}
