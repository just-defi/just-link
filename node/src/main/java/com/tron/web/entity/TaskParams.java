package com.tron.web.entity;

import lombok.Data;

@Data
public class TaskParams {
  private String get;
  private String path;
  private Long times;
  private String pair;
  private String pool;
  private String trc20;
  private Long version;
}
