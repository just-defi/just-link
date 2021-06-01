package com.tron.client;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigInteger;

@Data
@AllArgsConstructor
public class VrfEventRequest {
  private long blockNum;
  private String blockHash;
  private String jobId;
  private String keyHash;
  private String seed;
  private String sender;
  private String requestId;
  private BigInteger payment; // VRFRequest: fee；OracleRequest：payment
  private String contractAddr;
}
