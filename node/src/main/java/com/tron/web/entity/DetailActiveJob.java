package com.tron.web.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import lombok.Data;

@Data
public class DetailActiveJob extends JobSpec implements Serializable {
  private String id;
  private Date createdAt;
  private List<Initiator> initiators;
  private List<TaskSpec> taskSpecs;
  private Long minPayment;
  private Date startAt;
  private Date endAt;
  private Date updatedAt;
  private Date deletedAt;
  private String params;
  private Long result;

  @Override
  public boolean archived() {
    return deletedAt != null;
  }
}
