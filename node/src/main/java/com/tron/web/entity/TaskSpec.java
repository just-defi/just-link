package com.tron.web.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;
import lombok.Data;

@Data
public class TaskSpec implements Serializable {
  private Long id;
  private String jobSpecID;
  private Long confirmations;
  private String type;
  private String params;
  private Date createdAt;
  private Date updatedAt;
  private Date deletedAt;
}
