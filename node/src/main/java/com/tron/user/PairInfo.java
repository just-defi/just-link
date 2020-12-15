package com.tron.user;

import lombok.Data;

@Data
public class PairInfo {
  public PairInfo() {}

  private String name;
  private String contract;
  private long deviation;     // 1:1000
  private long updateInterval;  // ms
}
