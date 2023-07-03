package com.tron.user;

import static com.tron.common.Constant.FULLNODE_HOST;
import static com.tron.common.Constant.READONLY_ACCOUNT;
import static com.tron.common.Constant.TRIGGET_CONSTANT_CONTRACT;

import com.alibaba.fastjson.JSONObject;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.internal.Lists;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tron.client.message.BroadCastResponse;
import com.tron.common.util.AbiUtil;
import com.tron.common.util.HttpUtil;
import com.tron.common.util.Tool;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;

import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.StringUtil;

@Slf4j
public class CheckDeviation {

  private static DeviationConfig config = null;
  private static ECKey key;
  private static final long DEVIATION = 10;
  private static final Map<String, Long> deviationMap = new HashMap<>();
  private static String schema = "https";
  private static String fullnode = FULLNODE_HOST;

  private static final ScheduledExecutorService comparePriceExecutor = Executors.newSingleThreadScheduledExecutor();
  private static final ScheduledExecutorService intervalUpdateExecutor = Executors.newSingleThreadScheduledExecutor();
  private static final ScheduledExecutorService sendRequestExecutor = Executors.newSingleThreadScheduledExecutor();
  private static final BlockingQueue<String> sendRequestQueue = new LinkedBlockingQueue<>();
  private static boolean updateFlag = false;
  private static HashMap<String, Long> updateTimeMap;

  private static HashMap<String, PairInfo> pairInfoMap;

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
      if (!Strings.isNullOrEmpty(config.getFullnodeSchema())) {
        schema = config.getFullnodeSchema();
      }
      if (!Strings.isNullOrEmpty(config.getFullnode())) {
        fullnode = config.getFullnode();
      }
      if (!config.getPairs().isEmpty()) {
        pairInfoMap = config.getPairs().stream().collect(Collectors.toMap(PairInfo::getName, Function.identity(), (prev, next) -> next, HashMap::new));
      }
      start();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

  private static void start() {
    setDeviation();
    updateTimeMap = new HashMap<>(config.getPairs().size());

    //start comparePriceExecutor schedule config.getSleep()
    comparePriceExecutor.scheduleWithFixedDelay(() -> {
      try {
        if (!updateFlag) {
          updateFlag = true;
          comparePrice();
        }
      } catch (Exception e) {
        log.error("comparePriceExecutor execute error.", e);
      } finally {
        updateFlag = false;
      }
    }, 1, config.getSleep(), TimeUnit.MILLISECONDS);

    //start intervalUpdateExecutor schedule 120s
    intervalUpdateExecutor.scheduleWithFixedDelay(() -> {
      try {
        if (!updateFlag) {
          updateFlag = true;
          intervalUpdate();
        }
      } catch (Exception e) {
        log.error("intervalUpdateExecutor execute error.", e);
      } finally {
        updateFlag = false;
      }
    }, 5, 120, TimeUnit.SECONDS);

    //start sendRequestExecutor schedule 1s
    sendRequestExecutor.scheduleWithFixedDelay(() -> {
      try {
        takeContractFromQueue();
      } catch (Exception e) {
        log.error("sendRequestExecutor execute error.", e);
      } finally {
        updateFlag = false;
      }
    }, 1, 1, TimeUnit.SECONDS);

    log.info("CheckDeviation start success!");
  }

  private static void comparePrice() {
    for (PairInfo pairInfo : config.getPairs()) {
      String contract = pairInfo.getContract();
      long price = getAggPrice(pairInfo.getName());
      if (price == 0) {
        continue;
      }
      if (compare(contract, price)) {
        putContractIntoQueue(contract);
        updateTimeMap.put(pairInfo.getName(), System.currentTimeMillis());
        log.info("comparePrice put pairInfo into sendRequestQueue : {}", JSONObject.toJSONString(pairInfo));
      }
    }
  }

  private static void intervalUpdate() {
    for (PairInfo pairInfo : config.getPairs()) {
      String contract = pairInfo.getContract();
      Long lastUpdateTime = updateTimeMap.get(pairInfo.getName());
      if (null == lastUpdateTime) {
        updateTimeMap.put(pairInfo.getName(), System.currentTimeMillis());
        log.info("intervalUpdate init update time for pairInfo: {}", JSONObject.toJSONString(pairInfo));
        continue;
      }

      if (lastUpdateTime + pairInfo.getUpdateInterval() > System.currentTimeMillis()) {
        continue;
      }

      putContractIntoQueue(contract);
      updateTimeMap.put(pairInfo.getName(), System.currentTimeMillis());
      log.info("intervalUpdate put pairInfo into sendRequestQueue : {}", JSONObject.toJSONString(pairInfo));
    }
  }

  private static long getAggPrice(String pair) {
    List<Long> priceList = Lists.newArrayList();
    List<String> hostPriceList = Lists.newArrayList();
    for (int i = 0; i < config.getNodeServer().size(); i++) {
      Object host = config.getNodeServer().get(i).get("host");
      Object port = config.getNodeServer().get(i).get("port");
      String alias = String.valueOf(config.getNodeServer().get(i).get("alias"));
      String jobId = getJobId(String.format("http://%s:%d/job/active/%s", host, port, pairInfoMap.get(pair).getContract()), alias);
      if (jobId == null) {
        log.error("unable to get job id from host {}, contract {}, pair: {}", host + String.valueOf(port), pairInfoMap.get(pair).getContract(), pair);
        throw new RuntimeException("unable to get job id");
      }
      Long price = getPrice(String.format("http://%s:%d/job/result/%s", host, port, jobId), alias);
      priceList.add(price);
      hostPriceList.add(getHostWithPrice(alias, price));
    }

    Long[] priceArr = priceList.toArray(new Long[0]);
    Arrays.sort(priceArr);
    log.info("pair: {}, price: {}", pair, hostPriceList.toArray(new String[0]));
    return priceArr[priceArr.length / 2];
  }

  private static void takeContractFromQueue() {
    String contract = "";
    try {
      if (!sendRequestQueue.isEmpty()) {
        contract = sendRequestQueue.peek();
        sendRequest(contract);
        sendRequestQueue.poll();
        log.info("sendRequestQueue take success ! contract : {}", contract);
      }
    } catch (Exception ex) {
      log.error("sendRequestQueue take error. contract : {}", contract, ex);
    }
  }

  private static void putContractIntoQueue(String contract) {
    try {
      if (!sendRequestQueue.contains(contract)) {
        sendRequestQueue.put(contract);
      }
    } catch (Exception ex) {
      log.error("sendRequestQueue put error. contract : {}", contract, ex);
    }
  }

  private static long getPrice(String jobUrl, String alias) {
    long price = 0;
    try {
      String response = HttpUtil.getByUri(jobUrl);
      log.info("Get price from {} | {}, response = {}", alias, jobUrl, response);
      if (!Strings.isNullOrEmpty(response)) {
        JsonObject data = (JsonObject) JsonParser.parseString(response);
        return data.getAsJsonPrimitive("data").getAsLong();
      } else {
        log.error("[alarm]getPrice fail. job=" + jobUrl);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return price;
  }

  private static void sendRequest(String contract) {
    String method;
    if (!Strings.isNullOrEmpty(config.getAggType()) && "flux".equals(config.getAggType())) {
      method = "requestNewRound()";
    } else {
      method = "requestRateUpdate()";
    }

    try {
      Map<String, Object> params = Maps.newHashMap();
      params.put("owner_address", StringUtil.encode58Check(key.getAddress()));
      params.put("contract_address", contract);
      params.put("function_selector", method);
      params.put("parameter", "");
      params.put("fee_limit", config.getFeelimit().toString());
      params.put("call_value", 0);
      params.put("visible", true);
      BroadCastResponse rps = Tool.triggerContract(key, params, schema, config.getFullnode());
      if (rps != null) {
        log.info("trigger " + contract + " Contract result is: " + rps.isResult()
            + ", msg is: " + rps.getMessage());
      }
    } catch (Throwable e) {
      e.printStackTrace();
    }
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
        schema, fullnode, TRIGGET_CONSTANT_CONTRACT, params);
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> result = mapper.readValue(response, Map.class);
    return Optional.ofNullable((List<String>) result.get("constant_result"))
        .map(constantResult -> constantResult.get(0))
        .map(str -> str.replaceAll("^0[x|X]", ""))
        .map(str -> Long.parseLong(str, 16))
        .orElseThrow(() -> new IllegalArgumentException("can not get the price, contract:" + contract));
  }

  private static boolean compare(String contract, long nowPrice) {
    try {
      long prePrice = getPriceFromContract(contract);
      log.info(contract + ": " + prePrice + "  " + nowPrice);
      if (prePrice == 0) {
        return true;
      }
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
    for (PairInfo pairInfo : config.getPairs()) {
      deviationMap.put(pairInfo.getContract(), pairInfo.getDeviation());
    }
  }

  private static String getHostWithPrice(Object host, Long price) {
    return String.format("%s - %d", host, price);
  }

  private static String getJobId(String uri, String alias) {
    String jobId = null;
    try {
      String response = HttpUtil.getByUri(uri);
      log.info("Get jobId from {} | {}, response = {}", alias, uri, response);
      if (!Strings.isNullOrEmpty(response)) {
        JsonObject data = (JsonObject) JsonParser.parseString(response);
        return data.getAsJsonPrimitive("data").getAsString();
      } else {
        log.error("[alarm]getJobId fail. contract=" + uri);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return jobId;
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



