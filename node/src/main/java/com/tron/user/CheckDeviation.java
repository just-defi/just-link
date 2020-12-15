package com.tron.user;

import static com.tron.common.Constant.FULLNODE_HOST;
import static com.tron.common.Constant.READONLY_ACCOUNT;
import static com.tron.common.Constant.TRIGGET_CONSTANT_CONTRACT;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.internal.Lists;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.StringUtil;

@Slf4j
public class CheckDeviation {

  private static DeviationConfig config = null;
  private static ECKey key;
  private static long DEVIATION = 10;
  private static Map<String, Long> deviationMap = new HashMap<>();
  private static Map<String, Long> forceRequestTime = new HashMap<>();

  public static void main(String[] args) {
    Args argv = new Args();
    JCommander jct = JCommander.newBuilder()
            .addObject(argv)
            .build();
    jct.setProgramName("Deviation check");
    jct.setAcceptUnknownOptions(true);
    jct.parse(args);
    if (Strings.isNullOrEmpty(argv.config)) {
      throw new RuntimeException("config file cannot be null");
    }
    try {
      config = DeviationConfig.loadConfig(argv.config);
      key = ECKey.fromPrivate(ByteArray.fromHexString(config.getPrivateKey()));
      run();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      return;
    }
  }

  private static void run() {
    setDeviation();
    setForceRequestTime();
    AtomicLongMap forceMap = AtomicLongMap.create();
    while (true) {
      for (PairInfo pairInfo: config.getPairs()) {
        String contract = pairInfo.getContract();
        long price = getAggPrice(pairInfo.getName());
        if (price == 0) {
          forceMap.addAndGet(contract, config.getSleep());
          continue;
        }
        if (!compare(contract, price) && !mustForce(forceMap, contract)) {
          forceMap.addAndGet(contract, config.getSleep());
        } else {
          sendRequest(contract);
          log.info("send request");
          forceMap.remove(contract);
        }
        sleep(100);  // for slow down the http qps
      }
      sleep(config.getSleep());
    }
  }

  private static long getAggPrice(String pair) {
    List<String> jobs = config.getJobs().get(pair);
    if (jobs == null) {
      log.error("can not get jobUrls, pair: {}", pair);
      throw new RuntimeException("can get jobUrls");
    }
    List<Long> priceList = Lists.newArrayList();
    for (int i = 0; i < jobs.size(); i++) {
      Map<String, Object> server = config.getNodeServer().get(i);
      priceList.add(getPrice(
              String.format("http://%s:%d/job/result/%s",
                      server.get("host"), server.get("port"), jobs.get(i))
      ));
    }
    Long[] priceArr = priceList.toArray(new Long[priceList.size()]);
    Arrays.sort(priceArr);
    log.info("pair: {}, price: {}", pair, priceArr);
    return priceArr[priceArr.length/2];
  }

  private static long getPrice(String jobUrl) {
    long price = 0;
    try {
      HttpResponse response = HttpUtil.getByUri(jobUrl);
      if (response != null && response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
        String result = EntityUtils.toString(response.getEntity());
        JsonObject data = (JsonObject) JsonParser.parseString(result);
        return data.getAsJsonPrimitive("data").getAsLong();
      } else {
        log.error("[alarm]getPrice fail. job="+jobUrl);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return price;
  }

  private static void sendRequest(String contract) {
    try {
      Map<String, Object> params = Maps.newHashMap();
      params.put("owner_address", StringUtil.encode58Check(key.getAddress()));
      params.put("contract_address", contract);
      params.put("function_selector", "requestRateUpdate()");
      params.put("parameter", "");
      params.put("fee_limit", config.getFeelimit().toString());
      params.put("call_value", 0);
      params.put("visible", true);
      BroadCastResponse rps = Tool.triggerContract(key, params, config.getFullnode());
      if (rps != null) {
        log.info("trigger " + contract + " Contract result is: " + rps.isResult()
                + ", msg is: " + rps.getMessage());
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }
  }

  private static long getPriceFromContract(String contract) throws IOException {
    String param = AbiUtil.parseParameters("latestAnswer()", "");
    Map<String, Object> params = Maps.newHashMap();
    params.put("owner_address", READONLY_ACCOUNT);
    params.put("contract_address", contract);
    params.put("function_selector", "latestAnswer()");
    params.put("parameter", param);
    params.put("visible", true);
    HttpResponse response = HttpUtil.post(
            "https", FULLNODE_HOST, TRIGGET_CONSTANT_CONTRACT, params);
    ObjectMapper mapper = new ObjectMapper();
    assert response != null;
    Map<String, Object> result = mapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
    return Optional.ofNullable((List<String>)result.get("constant_result"))
            .map(constantResult -> constantResult.get(0))
            .map(str-> str.replaceAll("^0[x|X]", ""))
            .map(str -> Long.parseLong(str, 16))
            .orElseThrow(() -> new IllegalArgumentException("can not get the price, contract:" + contract));
  }

  private static boolean compare(String contract, long nowPrice) {
    try {
      long prePrice = getPriceFromContract(contract);
      log.info(contract +": " + prePrice + "  " + nowPrice);
      long sub = Math.abs(Math.subtractExact(prePrice, nowPrice));
      Long deviation = deviationMap.get(contract);
      deviation = deviation == null ? DEVIATION : deviation;
      return Math.multiplyExact(sub, 1000) / prePrice >= deviation;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return true;
  }

  private static void setDeviation() {
    for (PairInfo pairInfo: config.getPairs()) {
      deviationMap.put(pairInfo.getContract(), pairInfo.getDeviation());
    }
  }

  private static void setForceRequestTime() {
    for (PairInfo pairInfo: config.getPairs()) {
      forceRequestTime.put(pairInfo.getContract(), pairInfo.getUpdateInterval());
    }
  }

  private static boolean mustForce(AtomicLongMap forceMap, String contract) {
    Long forceTime = forceRequestTime.get(contract);
    if (forceTime == null) {
      return true;
    }
    return forceMap.get(contract) >= forceTime;
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  static class Args {
    @Parameter(
            names = {"--config", "-c"},
            help = true,
            description = "specify the config file",
            order = 1)
    private String config;
    @Parameter(
            names = "--help",
            help = true,
            order = 2)
    private boolean help;
  }
}



