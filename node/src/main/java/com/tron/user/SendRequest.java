package com.tron.user;

import static com.tron.common.Constant.FULLNODE_HOST;
import static com.tron.common.Constant.READONLY_ACCOUNT;
import static com.tron.common.Constant.TRIGGET_CONSTANT_CONTRACT;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AtomicLongMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tron.client.message.BroadCastResponse;
import com.tron.common.util.AbiUtil;
import com.tron.common.util.HttpUtil;
import com.tron.common.util.Tool;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.StringUtil;

/**
 * will be replaced by class {CheckDeviation}
 */
@Deprecated
public class SendRequest {

  private static String SPLIT = ",";
  private static long mills = 100;
  private static String contracts;
  private static String pk;
  private static String fullnode;
  private static ECKey key;
  private static long DEVIATION = 10;
  private static String priceUrl;
  private static Map<String, Long> deviationMap = new HashMap<>();
  private static Map<String, Long> forceRequestTime = new HashMap<>();
  private static String[] contractArray;

  private static void sendRequest(String contract) {
    try {
      Map<String, Object> params = Maps.newHashMap();
      params.put("owner_address", StringUtil.encode58Check(key.getAddress()));
      params.put("contract_address", contract);
      params.put("function_selector", "requestRateUpdate()");
      params.put("parameter", "");
      params.put("fee_limit", 40000000);
      params.put("call_value", 0);
      params.put("visible", true);
      BroadCastResponse rps = Tool.triggerContract(key, params, fullnode);
      if (rps != null) {
        System.out.println(new Date() + ": trigger " + contract + " Contract result is: " + rps.isResult()
            + ", msg is: " + rps.getMessage());
      }
    } catch (Exception e) {
      e.printStackTrace();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  private static long getPrice(String jobId) {
    long price = 0;
    try {
      String response = HttpUtil.getByUri(priceUrl + jobId);
      if (Strings.isNullOrEmpty(response)) {
        JsonObject data = (JsonObject) JsonParser.parseString(response);
        return data.getAsJsonPrimitive("data").getAsLong();
      }else {
        System.out.println("[alarm]getPrice fail. jobId="+jobId);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return price;
  }

  private static boolean compare(String contract, long nowPrice) {
    try {
      long prePrice = getPriceFromContract(contract);
      System.out.println(contract +": " + prePrice + "  " + nowPrice);
      long sub = Math.abs(Math.subtractExact(prePrice, nowPrice));
      Long deviation = deviationMap.get(contract);
      deviation = deviation == null ? DEVIATION : deviation;
      return Math.multiplyExact(sub, 1000) / prePrice >= deviation;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return true;
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private static void setDeviation(String deviations) {
    if (StringUtils.isBlank(deviations)) {
      return;
    }
    String[] deviationArray = deviations.split(SPLIT);
    for (int i = 0; i < deviationArray.length; i++) {
      deviationMap.put(contractArray[i], Long.valueOf(deviationArray[i]));
    }
  }

  private static void setForceRequestTime(String forces) {
    if (StringUtils.isBlank(forces)) {
      return;
    }
    String[] forceArray = forces.split(SPLIT);
    for (int i = 0; i < forceArray.length; i++) {
      forceRequestTime.put(contractArray[i], Long.valueOf(forceArray[i]));
    }
  }

  private static boolean mustForce(AtomicLongMap forceMap, String contract) {
    Long forceTime = forceRequestTime.get(contract);
    if (forceTime == null) {
      return true;
    }
    return forceMap.get(contract) >= forceTime;
  }

  private static long getPriceFromContract(String contract) throws IOException, URISyntaxException {
    String param = AbiUtil.parseParameters("latestAnswer()", "");
    Map<String, Object> params = Maps.newHashMap();
    params.put("owner_address", READONLY_ACCOUNT);
    params.put("contract_address", contract);
    params.put("function_selector", "latestAnswer()");
    params.put("parameter", param);
    params.put("visible", true);
    String response = HttpUtil.post(
            "https", FULLNODE_HOST, TRIGGET_CONSTANT_CONTRACT, params);
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> result = mapper.readValue(response, Map.class);
    return Optional.ofNullable((List<String>)result.get("constant_result"))
            .map(constantResult -> constantResult.get(0))
            .map(str-> str.replaceAll("^0[x|X]", ""))
            .map(str -> Long.parseLong(str, 16))
            .orElseThrow(() -> new IllegalArgumentException("can not get the price, contract:" + contract));
  }

  public static void main(String[] args) throws InterruptedException {
    if (args.length < 8) {
      throw new RuntimeException(
          "args length must eq 8, request interval, contract address, private key, http url, jobId");
    }
    mills = Long.valueOf(args[0]);
    contracts = args[1];
    pk = args[2];
    fullnode = args[3];
    System.out.println("mills:" + mills);
    System.out.println("contracts:" + contracts);
    System.out.println("pk length:" + pk.length());
    System.out.println("fullnode:" + fullnode);
    System.out.println("jobIds:" + args[4]);
    System.out.println("priceUrl:" + args[5]);
    System.out.println("deviations:" + args[6]);
    System.out.println("forceTime:" + args[7]);
    key = ECKey.fromPrivate(ByteArray.fromHexString(pk));
    contractArray = contracts.split(SPLIT);
    String[] jobIdArray = args[4].split(SPLIT);
    priceUrl = args[5];
    setDeviation(args[6]);
    setForceRequestTime(args[7]);
    AtomicLongMap forceMap = AtomicLongMap.create();
    while (true) {
      for (int i = 0; i < contractArray.length; i++) {
        String contract = contractArray[i];
        long price = getPrice(jobIdArray[i]);
        if (price == 0) {
          forceMap.addAndGet(contract, mills);
          continue;
        }
        if (!compare(contract, price) && !mustForce(forceMap, contract)) {
          forceMap.addAndGet(contract, mills);
        } else {
          sendRequest(contract);
          forceMap.remove(contract);
        }
        Thread.sleep(100);
      }
      sleep(mills);
    }
  }
}
