package com.tron.web.entity;

import java.util.Date;
import java.util.List;
import lombok.Data;

@Data
public class JobRun {
  private String id;
  private String jobSpecID;
  private String requestId;
  private String params;
  private String result;
  private String error;
  private int status;
  private Long initiatorId;
  private Long creationHeight;
  private Long observedHeight;
  private Long payment;
  private List<TaskRun> taskRuns;
  private Date createdAt;
  private Date updatedAt;
  private Date finishedAt;
  private Date deletedAt;
}
