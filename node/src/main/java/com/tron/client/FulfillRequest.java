package com.tron.client;

import com.google.common.collect.Lists;
import java.math.BigInteger;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FulfillRequest {
  private String contractAddr;
  private String requestId;
  private BigInteger payment;
  private String callbackAddress;
  private String callbackFunctionId;
  private long expiration;
  private String data;

  public List<Object> toList() {
    List<Object> list = Lists.newArrayList();
    list.add(requestId);
    list.add(payment.toString());
    list.add(callbackAddress);
    list.add(callbackFunctionId);
    list.add(expiration);
    list.add(data);
    return list;
  }
}
