package com.tron.client;

import static com.tron.common.Constant.FULFIL_METHOD_SIGN;
import static com.tron.common.Constant.FULLNODE_HOST;
import static com.tron.common.Constant.HTTP_EVENT_HOST;
import static com.tron.common.Constant.HTTP_MAX_RETRY_TIME;
import static com.tron.common.Constant.ONE_HOUR;
import static com.tron.common.Constant.ONE_MINUTE;
import static com.tron.common.Constant.SUBMIT_METHOD_SIGN;

import com.beust.jcommander.internal.Sets;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import com.tron.client.message.BroadCastResponse;
import com.tron.client.message.EventData;
import com.tron.client.message.EventResponse;
import com.tron.client.message.TriggerResponse;
import com.tron.common.AbiUtil;
import com.tron.common.util.HttpUtil;
import com.tron.common.util.Tool;
import com.tron.job.JobSubscriber;
import com.tron.job.adapters.ContractAdapter;
import com.tron.keystore.KeyStore;
import com.tron.web.entity.TronTx;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.spongycastle.util.encoders.Hex;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.JsonUtil;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.exception.BadItemException;
import org.tron.protos.Protocol;

/**
 * Subscribe the events of the oracle contracts and reply.
 */
@Slf4j
public class OracleClient {

  private static final String EVENT_NAME = "OracleRequest";
  private static final String EVENT_NEW_ROUND = "NewRound";
  private static final long MIN_FEE_LIMIT = 10_000_000L;   // 10 trx

  private static Cache<String, String> requestIdsCache = CacheBuilder.newBuilder().maximumSize(10000)
          .expireAfterWrite(12, TimeUnit.HOURS).recordStats().build();

  private static HashMap<String, Set<String>> listeningAddrs = Maps.newHashMap();
  private HashMap<String, Long> consumeIndexMap = Maps.newHashMap();

  private ScheduledExecutorService listenExecutor = Executors.newSingleThreadScheduledExecutor();

  public void run() {
    listenExecutor.scheduleWithFixedDelay(() -> {
      try {
        listen();
      } catch (Throwable t) {
        log.error("Exception in listener ", t);
      }}, 0, 3000, TimeUnit.MILLISECONDS);
  }

  public static void registerJob(String address, String jobId) {
    Set<String> set = listeningAddrs.get(address);
    if (set == null) {
      set = Sets.newHashSet();
    }
    set.add(jobId);
    listeningAddrs.put(address, set);
  }

  /**
   *
   * @param request
   * @return transactionid
   */
  public static TronTx fulfil(FulfillRequest request) throws IOException, URISyntaxException {
    Map<String, Object> params = Maps.newHashMap();
    params.put("owner_address", KeyStore.getAddr());
    params.put("contract_address",request.getContractAddr());
    params.put("function_selector",FULFIL_METHOD_SIGN);
    params.put("parameter", AbiUtil.parseParameters(FULFIL_METHOD_SIGN, request.toList()));
    params.put("fee_limit", calculateFeeLimit(MIN_FEE_LIMIT));
    params.put("call_value",0);
    params.put("visible",true);
    String response = HttpUtil.post("https", FULLNODE_HOST,
            "/wallet/triggersmartcontract", params);
    TriggerResponse triggerResponse = null;
    triggerResponse = JsonUtil.json2Obj(response, TriggerResponse.class);

    // sign
    ECKey key = KeyStore.getKey();
    String rawDataHex = triggerResponse.getTransaction().getRawDataHex();
    Protocol.Transaction.raw raw = Protocol.Transaction.raw.parseFrom(ByteArray.fromHexString(rawDataHex));
    byte[] hash = Sha256Hash.hash(true, raw.toByteArray());
    ECKey.ECDSASignature signature = key.sign(hash);
    ByteString bsSign = ByteString.copyFrom(signature.toByteArray());
    TransactionCapsule transactionCapsule = new TransactionCapsule(raw, Arrays.asList(bsSign));

    // broadcast
    params.clear();
    params.put("transaction", Hex.toHexString(transactionCapsule.getInstance().toByteArray()));
    response = HttpUtil.post("https", FULLNODE_HOST,
            "/wallet/broadcasthex", params);
    BroadCastResponse broadCastResponse =
            JsonUtil.json2Obj(response, BroadCastResponse.class);

    TronTx tx = new TronTx();
    tx.setFrom(KeyStore.getAddr());
    tx.setTo(request.getContractAddr());
    tx.setSurrogateId(broadCastResponse.getTxid());
    tx.setSignedRawTx(bsSign.toString());
    tx.setHash(ByteArray.toHexString(hash));
    tx.setData(AbiUtil.parseParameters(FULFIL_METHOD_SIGN, request.toList()));
    return tx;
  }

  /**
   *
   * @param
   * @return transactionid
   */
  public static TronTx submit(String addr, long roundId, long result) throws IOException, URISyntaxException {
    Map<String, Object> params = Maps.newHashMap();
    params.put("owner_address", KeyStore.getAddr());
    params.put("contract_address", addr);
    params.put("function_selector", SUBMIT_METHOD_SIGN);
    List<Object> list = Lists.newArrayList();
    list.add(roundId);
    list.add(result);
    params.put("parameter", AbiUtil.parseParameters(SUBMIT_METHOD_SIGN, list));
    params.put("fee_limit", calculateFeeLimit(MIN_FEE_LIMIT));
    params.put("call_value",0);
    params.put("visible",true);
    String response = HttpUtil.post("https", FULLNODE_HOST,
        "/wallet/triggersmartcontract", params);
    TriggerResponse triggerResponse = null;
    triggerResponse = JsonUtil.json2Obj(response, TriggerResponse.class);

    // sign
    ECKey key = KeyStore.getKey();
    String rawDataHex = triggerResponse.getTransaction().getRawDataHex();
    Protocol.Transaction.raw raw = Protocol.Transaction.raw.parseFrom(ByteArray.fromHexString(rawDataHex));
    byte[] hash = Sha256Hash.hash(true, raw.toByteArray());
    ECKey.ECDSASignature signature = key.sign(hash);
    ByteString bsSign = ByteString.copyFrom(signature.toByteArray());
    TransactionCapsule transactionCapsule = new TransactionCapsule(raw, Arrays.asList(bsSign));

    // broadcast
    params.clear();
    params.put("transaction", Hex.toHexString(transactionCapsule.getInstance().toByteArray()));
    response = HttpUtil.post("https", FULLNODE_HOST,
        "/wallet/broadcasthex", params);
    BroadCastResponse broadCastResponse =
        JsonUtil.json2Obj(response, BroadCastResponse.class);
    TronTx tx = new TronTx();
    tx.setFrom(KeyStore.getAddr());
    tx.setTo(addr);
    tx.setSurrogateId(broadCastResponse.getTxid());
    tx.setSignedRawTx(bsSign.toString());
    tx.setHash(ByteArray.toHexString(hash));
    tx.setData(AbiUtil.parseParameters(SUBMIT_METHOD_SIGN, list));
    return tx;
  }

  private void listen() {
    listeningAddrs.keySet().forEach(
            addr -> {
              List<EventData> events = getEventData(addr);
              if (events == null) {
                return;
              }
              // handle events
              for (EventData eventData: events) {
                // update consumeIndexMap
                updateConsumeMap(addr, eventData.getBlockTimestamp());
                // filter the events
                if (EVENT_NAME.equals(eventData.getEventName())) {
                  processOracleRequestEvent(addr, eventData);
                } else if (EVENT_NEW_ROUND.equals(eventData.getEventName())) {
                  processNewRoundEvent(addr, eventData);
                } else {
                  log.warn("this node does not support this event, event name: {}",
                      eventData.getEventName());
                }
              }
            }
    );
  }

  private void processOracleRequestEvent(String addr, EventData eventData) {
    String jobId = null;
    try {
      jobId = new String(
          org.apache.commons.codec.binary.Hex.decodeHex(
              ((String)eventData.getResult().get("specId"))));
    } catch (DecoderException e) {
      log.warn("parse job failed, jobid: {}", jobId);
      return;
    }
    // filter events
    if (!listeningAddrs.get(addr).contains(jobId)) {
      log.warn("this node does not support this job, jobid: {}", jobId);
      return;
    }
    long blockNum = eventData.getBlockNumber();
    String requester = Tool.convertHexToTronAddr((String)eventData.getResult().get("requester"));
    String callbackAddr = Tool.convertHexToTronAddr((String)eventData.getResult().get("callbackAddr"));
    String callbackFuncId = (String)eventData.getResult().get("callbackFunctionId");
    long cancelExpiration = Long.parseLong((String)eventData.getResult().get("cancelExpiration"));
    String data = (String)eventData.getResult().get("data");
    long dataVersion = Long.parseLong((String)eventData.getResult().get("dataVersion"));
    String requestId = (String)eventData.getResult().get("requestId");
    BigInteger payment = new BigInteger((String)eventData.getResult().get("payment"));
    if (requestIdsCache.getIfPresent(requestId) != null) {
      log.info("this event has been handled, requestid:{}", requestId);
      return;
    }
    JobSubscriber.receiveLogRequest(
        new EventRequest(blockNum, jobId, requester, callbackAddr, callbackFuncId,
            cancelExpiration, data, dataVersion,requestId, payment, addr));
    requestIdsCache.put(requestId, "");
  }

  private void processNewRoundEvent(String addr, EventData eventData) {
    long roundId = 0;
    try {
      roundId = Long.parseLong((String)eventData.getResult().get("roundId"));
    } catch (NumberFormatException e) {
      log.warn("parse job failed, roundId: {}", roundId);
      return;
    }

    String startedBy = Tool.convertHexToTronAddr((String)eventData.getResult().get("startedBy"));
    long startedAt = Long.parseLong((String)eventData.getResult().get("startedAt"));
    if (requestIdsCache.getIfPresent(addr + roundId) != null) {
      log.info("this event has been handled, address:{}, roundId:{}", addr, roundId);
      return;
    }

    JobSubscriber.receiveNewRoundLog(addr, startedBy, roundId, startedAt);

    requestIdsCache.put(addr + roundId, "");
  }

  public String requestEvent(String urlPath, Map<String, String> params) throws IOException {
    String response = HttpUtil.get("https", HTTP_EVENT_HOST,
            urlPath, params);
    if (Strings.isNullOrEmpty(response)) {
      int retry = 1;
      for (;;) {
        if(retry > HTTP_MAX_RETRY_TIME) {
          break;
        }
        try {
          Thread.sleep(100 * retry);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        response = HttpUtil.get("https", HTTP_EVENT_HOST,
                urlPath, params);
        retry++;
        if (!Strings.isNullOrEmpty(response)) {
          break;
        }
      }
    }
    return response;
  }

  private List<EventData> getEventData(String addr) {
    if ("event.nileex.io".equals(HTTP_EVENT_HOST)) {  // for test
      Map<String, String> params = Maps.newHashMap();
      if (consumeIndexMap.containsKey(addr)) {
        params.put("since", Long.toString(consumeIndexMap.get(addr) +1));
      } else {
        //params.put("since", "1611300134000");
        params.put("since", Long.toString(System.currentTimeMillis() - ONE_HOUR));
      }
      String urlPath = String.format("/event/contract/%s", addr);
      try {
        String httpResponse = requestEvent(urlPath, params);
        ObjectMapper om = new ObjectMapper();
        return om.readValue(httpResponse, new TypeReference<List<EventData>>() {});
      } catch (IOException e) {
        log.error("parse response failed, err: {}", e.getMessage());
        return null;
      }
    } else {  // for production
      Map<String, String> params = Maps.newHashMap();
      params.put("order_by", "block_timestamp,asc");
      //params.put("only_confirmed", "true");
      if (consumeIndexMap.containsKey(addr)) {
        params.put("min_block_timestamp", Long.toString(consumeIndexMap.get(addr)));
      } else {
        params.put("min_block_timestamp", Long.toString(System.currentTimeMillis() - ONE_MINUTE));
      }
      String urlPath = String.format("/v1/contracts/%s/events", addr);
      String httpResponse = null;
      try {
        httpResponse = requestEvent(urlPath, params);
      } catch (IOException e) {
        log.error("parse response failed, err: {}", e.getMessage());
        return null;
      }
      EventResponse response = JsonUtil.json2Obj(httpResponse, EventResponse.class);
      return response.getData();
    }
  }

  private void updateConsumeMap(String addr, long timestamp) {
    if (consumeIndexMap.containsKey(addr)) {
      if (timestamp > consumeIndexMap.get(addr)) {
        consumeIndexMap.put(addr, timestamp);
      }
    } else {
      consumeIndexMap.put(addr, timestamp);
    }
  }

  public static boolean checkTransactionStatus(String transactionId) {
    return true;
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
