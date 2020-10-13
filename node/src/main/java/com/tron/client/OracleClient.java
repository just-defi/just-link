package com.tron.client;

import static com.tron.common.Constant.FULFIL_METHOD_SIGN;
import static com.tron.common.Constant.FULLNODE_HOST;
import static com.tron.common.Constant.HTTP_EVENT_HOST;
import static com.tron.common.Constant.HTTP_MAX_RETRY_TIME;
import static com.tron.common.Constant.ONE_HOUR;

import com.beust.jcommander.internal.Sets;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;
import com.google.protobuf.ByteString;
import com.tron.client.message.BroadCastResponse;
import com.tron.client.message.EventData;
import com.tron.client.message.EventResponse;
import com.tron.client.message.TriggerResponse;
import com.tron.common.util.AbiUtil;
import com.tron.common.util.HttpUtil;
import com.tron.common.util.Tool;
import com.tron.job.JobSubscriber;
import com.tron.keystore.KeyStore;
import com.tron.web.entity.TronTx;
import java.io.IOException;
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
  public static TronTx fulfil(FulfillRequest request) throws IOException, BadItemException {
    Map<String, Object> params = Maps.newHashMap();
    params.put("owner_address", KeyStore.getAddr());
    params.put("contract_address",request.getContractAddr());
    params.put("function_selector",FULFIL_METHOD_SIGN);
    params.put("parameter", AbiUtil.parseParameters(FULFIL_METHOD_SIGN, request.toList()));
    params.put("fee_limit", 100000000);
    params.put("call_value",0);
    params.put("visible",true);
    HttpResponse response = HttpUtil.post("https", FULLNODE_HOST,
            "/wallet/triggersmartcontract", params);
    HttpEntity responseEntity = response.getEntity();
    TriggerResponse triggerResponse = null;
    String responsrStr = EntityUtils.toString(responseEntity);
    triggerResponse = JsonUtil.json2Obj(responsrStr, TriggerResponse.class);

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
            JsonUtil.json2Obj(EntityUtils.toString(response.getEntity()), BroadCastResponse.class);

    TronTx tx = new TronTx();
    tx.setFrom(KeyStore.getAddr());
    tx.setTo(request.getContractAddr());
    tx.setSurrogateId(broadCastResponse.getTxid());
    tx.setSignedRawTx(bsSign.toString());
    tx.setHash(ByteArray.toHexString(hash));
    tx.setData(AbiUtil.parseParameters(FULFIL_METHOD_SIGN, request.toList()));
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
                if (!EVENT_NAME.equals(eventData.getEventName())) {
                  log.warn("this node does not support this event, event name: {}",
                          eventData.getEventName());
                  continue;
                }
                String jobId = null;
                try {
                  jobId = new String(
                          org.apache.commons.codec.binary.Hex.decodeHex(
                                  ((String)eventData.getResult().get("specId"))));
                } catch (DecoderException e) {
                  log.warn("parse job failed, jobid: {}", jobId);
                  continue;
                }
                // filter events
                if (!listeningAddrs.get(addr).contains(jobId)) {
                  log.warn("this node does not support this job, jobid: {}", jobId);
                  continue;
                }
                long blockNum = eventData.getBlockNumber();
                String requester = Tool.convertHexToTronAddr((String)eventData.getResult().get("requester"));
                String callbackAddr = Tool.convertHexToTronAddr((String)eventData.getResult().get("callbackAddr"));
                String callbackFuncId = (String)eventData.getResult().get("callbackFunctionId");
                long cancelExpiration = Long.parseLong((String)eventData.getResult().get("cancelExpiration"));
                String data = (String)eventData.getResult().get("data");
                long dataVersion = Long.parseLong((String)eventData.getResult().get("dataVersion"));
                String requestId = (String)eventData.getResult().get("requestId");
                long payment = Long.parseLong((String)eventData.getResult().get("payment"));
                JobSubscriber.receiveLogRequest(
                        new EventRequest(blockNum, jobId, requester, callbackAddr, callbackFuncId,
                                cancelExpiration, data, dataVersion,requestId, payment, addr));
              }
            }
    );
  }

  public HttpResponse requestEvent(String urlPath, Map<String, String> params) {
    HttpResponse response = HttpUtil.get("https", HTTP_EVENT_HOST,
            urlPath, params);
    int status = response.getStatusLine().getStatusCode();
    if (status == HttpStatus.SC_SERVICE_UNAVAILABLE) {
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
        status = response.getStatusLine().getStatusCode();
        if (status != HttpStatus.SC_SERVICE_UNAVAILABLE) {
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
        params.put("since", "1600234218000");
        //params.put("since", Long.toString(System.currentTimeMillis() - ONE_HOUR));
      }
      String urlPath = String.format("/event/contract/%s", addr);
      HttpResponse httpResponse = requestEvent(urlPath, params);
      HttpEntity responseEntity = httpResponse.getEntity();
      try {
        String responseStr = EntityUtils.toString(responseEntity);
        ObjectMapper om = new ObjectMapper();
        return om.readValue(responseStr, new TypeReference<List<EventData>>() {});
      } catch (IOException e) {
        log.error("parse response failed, err: {}", e.getMessage());
      }
    } else {  // for production
      Map<String, String> params = Maps.newHashMap();
      params.put("order_by", "block_timestamp,asc");
      params.put("only_confirmed", "true");
      if (consumeIndexMap.containsKey(addr)) {
        params.put("min_block_timestamp", Long.toString(consumeIndexMap.get(addr)));
      } else {
        params.put("min_block_timestamp", Long.toString(System.currentTimeMillis() - ONE_HOUR));
      }
      String urlPath = String.format("/v1/contracts/%s/events", addr);
      HttpResponse httpResponse = requestEvent(urlPath, params);
      HttpEntity responseEntity = httpResponse.getEntity();
      EventResponse response = null;
      try {
        String responseStr = EntityUtils.toString(responseEntity);
        response = JsonUtil.json2Obj(responseStr, EventResponse.class);
      } catch (IOException e) {
        log.error("parse response failed, err: {}", e.getMessage());
      }
      return response.getData();
    }
    return null;
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
}
