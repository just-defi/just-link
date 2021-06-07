package com.tron.web.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
public class Head implements Serializable {
  private Long id;
  private String address;
  private String hash;
  private Long number;
  private String parentHash;
  private Date createdAt;
  private Date updatedAt;
  private Long blockTimestamp;

}
