package com.tron.web.entity;

import java.util.Date;
import lombok.Data;

@Data
public class TronTx {
  private Long id;
  private String surrogateId;
  private String from;
  private String to;
  private String data;
  private Long value;
  private String hash;
  private Long sentAt;
  private String signedRawTx;
  private Date createdAt;
  private Date updatedAt;
}
