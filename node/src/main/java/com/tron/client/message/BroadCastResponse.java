package com.tron.client.message;

import lombok.Data;

@Data
public class BroadCastResponse {
  private boolean result;
  private String code;
  private String txid;
  private String message;
  private String transaction;
}
