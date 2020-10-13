package com.tron.user;

import com.google.common.collect.Maps;
import com.tron.client.message.BroadCastResponse;
import com.tron.common.util.Tool;
import java.util.Map;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.StringUtil;

public class SendRequest {

  private static long mills = 100;
  private static String contracts;
  private static String pk;
  private static String fullnode;
  private static ECKey key;

  private static void sendRequest(String contract) {
    try {
      Map<String, Object> params = Maps.newHashMap();
      params.put("owner_address", StringUtil.encode58Check(key.getAddress()));
      params.put("contract_address", contract);
      params.put("function_selector", "requestRateUpdate()");
      params.put("parameter", "");
      params.put("fee_limit", 100000000);
      params.put("call_value", 0);
      params.put("visible", true);
      BroadCastResponse rps = Tool.triggerContract(key, params, fullnode);
      if (rps != null) {
        System.out.println("trigger " + contract + " Contract result is: " + rps.isResult()
            + ", msg is: " + rps.getMessage());
      }
    } catch (Exception e) {
      e.printStackTrace();
    } catch (Throwable t) {
      t.printStackTrace();
    }
  }

  public static void main(String[] args) {
    if (args.length < 4) {
      throw new RuntimeException(
          "args length must eq 4, request interval, contract address, private key, http url");
    }
    mills = Long.valueOf(args[0]);
    contracts = args[1];
    pk = args[2];
    fullnode = args[3];
    System.out.println("mills:" + mills);
    System.out.println("contracts:" + contracts);
    System.out.println("pk length:" + pk.length());
    System.out.println("fullnode:" + fullnode);
    key = ECKey.fromPrivate(ByteArray.fromHexString(pk));
    String[] contractArray = contracts.split(",");
    while (true) {
      for (String contract : contractArray) {
        sendRequest(contract);
      }
      sleep(mills);
    }
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

}
