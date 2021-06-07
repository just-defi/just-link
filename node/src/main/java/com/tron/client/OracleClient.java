package com.tron.client;

import com.alibaba.fastjson.JSONObject;
import com.beust.jcommander.internal.Sets;
import com.google.common.base.Strings;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import com.tron.client.message.BroadCastResponse;
import com.tron.client.message.EventData;
import com.tron.client.message.EventResponse;
import com.tron.client.message.TriggerResponse;
import com.tron.common.AbiUtil;
import com.tron.common.Constant;
import com.tron.common.util.HttpUtil;
import com.tron.common.util.Tool;
import com.tron.job.JobSubscriber;
import com.tron.keystore.KeyStore;
import com.tron.web.entity.Head;
import com.tron.web.entity.TronTx;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.tron.web.service.HeadService;
import com.tron.web.service.JobRunsService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.JsonUtil;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.exception.BadItemException;
import org.tron.protos.Protocol;

import static com.tron.common.Constant.*;

/** Subscribe the events of the oracle contracts and reply. */
@Slf4j
public class OracleClient {

  @Autowired
  private HeadService headService;
  @Autowired
  private JobRunsService jobRunsService;

  public OracleClient(HeadService _headService, JobRunsService _jobRunsService) {
    headService = _headService;
    jobRunsService = _jobRunsService;
  }

  public OracleClient() {
  }

  private static final String EVENT_NAME = "OracleRequest";
  private static final String VRF_EVENT_NAME = "VRFRequest";
  private static final long MIN_FEE_LIMIT = 100_000_000L; // 100 trx

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
        3000,
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

    return triggerSignAndResponse(params, FULFIL_METHOD_SIGN);
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

    return triggerSignAndResponse(params, VRF_FULFIL_METHOD_SIGN);
  }

  private static TronTx triggerSignAndResponse(Map<String, Object> params, String method)
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
    for (Map.Entry<String, HashMap<String, String>> addrEntry : listeningAddrs.entrySet()) {
      String addr = addrEntry.getKey();
      Map<String, String> map = addrEntry.getValue();
      Map.Entry<String, String> entry = map.entrySet().iterator().next();
      String destJobId = entry.getKey();
      String filterEvent = entry.getValue();
      List<EventData> events = getEventData(addr, filterEvent);
      if (events == null || events.size() == 0) {
        continue;
      }
      // handle events
      for (EventData eventData : events) {
        // update consumeIndexMap
        updateConsumeMap(addr, eventData.getBlockTimestamp());

        // filter the events
        String eventName = eventData.getEventName();
        if (!EVENT_NAME.equals(eventName) && !VRF_EVENT_NAME.equals(eventName)) {
          log.warn(
              "this node does not support this event, event name: {}", eventData.getEventName());
          continue;
        }
        String jobId = null;
        try {
          switch (eventName) {
            case EVENT_NAME:
              jobId =
                  new String(
                      org.apache.commons.codec.binary.Hex.decodeHex(
                          ((String) eventData.getResult().get("specId"))));
              break;
            case VRF_EVENT_NAME:
              jobId =
                  new String(
                      org.apache.commons.codec.binary.Hex.decodeHex(
                          ((String) eventData.getResult().get("jobID"))));
              break;
            default:
              break;
          }

        } catch (DecoderException e) {
          log.warn("parse job failed, jobid: {}", jobId);
          continue;
        }
        // filter events
        if (Strings.isNullOrEmpty(destJobId) || !destJobId.equals(jobId)) {
          log.warn("this node does not support this job, jobid: {}", jobId);
          continue;
        }
        // Number/height of the block in which this request appeared
        long blockNum = eventData.getBlockNumber();
        String requestId;
        switch (eventName) {
          case EVENT_NAME:
            String requester =
                Tool.convertHexToTronAddr((String) eventData.getResult().get("requester"));
            String callbackAddr =
                Tool.convertHexToTronAddr((String) eventData.getResult().get("callbackAddr"));
            String callbackFuncId = (String) eventData.getResult().get("callbackFunctionId");
            long cancelExpiration =
                Long.parseLong((String) eventData.getResult().get("cancelExpiration"));
            String data = (String) eventData.getResult().get("data");
            long dataVersion = Long.parseLong((String) eventData.getResult().get("dataVersion"));
            requestId = (String) eventData.getResult().get("requestId");
            BigInteger payment = new BigInteger((String) eventData.getResult().get("payment"));
            if (requestIdsCache.getIfPresent(requestId) != null) {
              log.info("this event has been handled, requestid:{}", requestId);
              continue;
            }
            JobSubscriber.receiveLogRequest(
                new EventRequest(
                    blockNum,
                    jobId,
                    requester,
                    callbackAddr,
                    callbackFuncId,
                    cancelExpiration,
                    data,
                    dataVersion,
                    requestId,
                    payment,
                    addr));
            requestIdsCache.put(requestId, "");
            break;
          case VRF_EVENT_NAME:
            requestId = (String) eventData.getResult().get("requestID");
            if (requestIdsCache.getIfPresent(requestId) != null) {
              log.info("this vrf event has been handled, requestid:{}", requestId);
              continue;
            }
            if(!Strings.isNullOrEmpty(jobRunsService.getByRequestId(requestId))) { // for reboot
              log.info("from DB, this vrf event has been handled, requestid:{}", requestId);
              continue;
            }
            // Hash of the block in which this request appeared
            String responsrStr = getBlockByNum(blockNum);
            JSONObject responseContent = JSONObject.parseObject(responsrStr);
            String blockHash = responseContent.getString("blockID");
            JSONObject rawHead = JSONObject.parseObject(JSONObject.parseObject(responseContent.getString("block_header"))
                .getString("raw_data"));
            String parentHash = rawHead.getString("parentHash");
            Long blockTimestamp = Long.valueOf(rawHead.getString("timestamp"));
            List<Head> hisHead = headService.getByAddress(addr);
            Head head = new Head();
            head.setAddress(addr);
            head.setNumber(blockNum);
            head.setHash(blockHash);
            head.setParentHash(parentHash);
            head.setBlockTimestamp(blockTimestamp);
            if (hisHead == null || hisHead.size() == 0) {
              headService.insert(head);
            } else if (!hisHead.get(0).getNumber().equals(blockNum)) { //Only update unequal blockNum.
              head.setId(hisHead.get(0).getId());
              head.setUpdatedAt(new Date());
              headService.update(head);
            } else {

            }

            String sender = Tool.convertHexToTronAddr((String) eventData.getResult().get("sender"));
            String keyHash = (String) eventData.getResult().get("keyHash");
            String seed = (String) eventData.getResult().get("seed");
            BigInteger fee = new BigInteger((String) eventData.getResult().get("fee"));
            JobSubscriber.receiveVrfRequest(
                new VrfEventRequest(
                    blockNum, blockHash, jobId, keyHash, seed, sender, requestId, fee, addr));
            requestIdsCache.put(requestId, "");
            break;
          default:
            log.warn("unexpected event:{}", eventName);
            break;
        }
      }
    }
  }

  public HttpResponse requestEvent(String urlPath, Map<String, String> params) {
    HttpResponse response = HttpUtil.get("https", HTTP_EVENT_HOST, urlPath, params);
    if (response == null) {
      return null;
    }
    int status = response.getStatusLine().getStatusCode();
    if (status == HttpStatus.SC_SERVICE_UNAVAILABLE) {
      int retry = 1;
      for (; ; ) {
        if (retry > HTTP_MAX_RETRY_TIME) {
          break;
        }
        try {
          Thread.sleep(100 * retry);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        response = HttpUtil.get("https", HTTP_EVENT_HOST, urlPath, params);
        retry++;
        status = response.getStatusLine().getStatusCode();
        if (status != HttpStatus.SC_SERVICE_UNAVAILABLE) {
          break;
        }
      }
    }
    return response;
  }

  public HttpResponse requestNextPage(String urlNext) {
    HttpResponse response = HttpUtil.getByUri(urlNext);
    if (response == null) {
      return null;
    }
    int status = response.getStatusLine().getStatusCode();
    if (status == HttpStatus.SC_SERVICE_UNAVAILABLE) {
      int retry = 1;
      for (; ; ) {
        if (retry > HTTP_MAX_RETRY_TIME) {
          break;
        }
        try {
          Thread.sleep(100 * retry);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        response = HttpUtil.getByUri(urlNext);
        retry++;
        status = response.getStatusLine().getStatusCode();
        if (status != HttpStatus.SC_SERVICE_UNAVAILABLE) {
          break;
        }
      }
    }
    return response;
  }

  /** constructor. */
  public static String getBlockByNum(long blockNum) {
    try {
      Map<String, Object> params = Maps.newHashMap();
      params.put("num", blockNum);
      params.put("visible", true);
      HttpResponse response =
          HttpUtil.post("https", FULLNODE_HOST, "/wallet/getblockbynum", params);
      HttpEntity responseEntity = response.getEntity();
      TriggerResponse triggerResponse = null;
      String responsrStr = EntityUtils.toString(responseEntity);

      return responsrStr;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  private List<EventData> getEventData(String addr, String filterEvent) {
    List<EventData> data = new ArrayList<>();
    HttpResponse httpResponse = null;
    String urlPath;
    Map<String, String> params = Maps.newHashMap();
    if ("nile.trongrid.io".equals(HTTP_EVENT_HOST)) { // for test
      params.put("event_name", filterEvent);
      params.put("order_by", "block_timestamp,asc");
      if (consumeIndexMap.containsKey(addr)) {
        params.put("min_block_timestamp", Long.toString(consumeIndexMap.get(addr)));
      } else {
        params.put("min_block_timestamp", Long.toString(System.currentTimeMillis() - ONE_MINUTE));
      }
      urlPath = String.format("/v1/contracts/%s/events", addr);
    } else { // for production
      params.put("event_name", filterEvent);
      params.put("order_by", "block_timestamp,asc");
      // params.put("only_confirmed", "true");
      if (consumeIndexMap.containsKey(addr)) {
        params.put("min_block_timestamp", Long.toString(consumeIndexMap.get(addr)));
      } else {
        params.put("min_block_timestamp", Long.toString(System.currentTimeMillis() - ONE_MINUTE));
      }
      urlPath = String.format("/v1/contracts/%s/events", addr);
    }
    httpResponse = requestEvent(urlPath, params);
    if (httpResponse == null) {
      return null;
    }
    HttpEntity responseEntity = httpResponse.getEntity();
    EventResponse response = null;
    try {
      String responseStr = EntityUtils.toString(responseEntity);
      response = JsonUtil.json2Obj(responseStr, EventResponse.class);
    } catch (IOException e) {
      log.error("parse response failed, err: {}", e.getMessage());
    }
    data.addAll(response.getData());

    boolean isNext = false;
    Map<String, String> links = response.getMeta().getLinks();
    if (links == null) {
      return data;
    }
    String urlNext = links.get("next");
    if (!Strings.isNullOrEmpty(urlNext)) {
      isNext = true;
    }
    while (isNext) {
      isNext = false;
      HttpResponse responseNext = requestNextPage(urlNext);
      if (responseNext == null) {
        return data;
      }
      responseEntity = responseNext.getEntity();
      response = null;
      try {
        String responseStr = EntityUtils.toString(responseEntity);
        response = JsonUtil.json2Obj(responseStr, EventResponse.class);
      } catch (IOException e) {
        log.error("parse response failed, err: {}", e.getMessage());
      }
      data.addAll(response.getData());

      links = response.getMeta().getLinks();
      if (links == null) {
        return data;
      }
      urlNext = links.get("next");
      if (!Strings.isNullOrEmpty(urlNext)) {
        isNext = true;
      }
    }

    return data;
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
