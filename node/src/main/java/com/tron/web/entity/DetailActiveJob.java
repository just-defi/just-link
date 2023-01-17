package com.tron.web.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import lombok.*;

//@Data
@NoArgsConstructor
@ToString
@EqualsAndHashCode
@Getter
@Setter
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

  public DetailActiveJob(JobSpec jobSpec, Long result) {
    this.id = jobSpec.getId();
    this.createdAt = jobSpec.getCreatedAt();
    this.initiators = jobSpec.getInitiators();
    this.taskSpecs = jobSpec.getTaskSpecs();
    this.minPayment = jobSpec.getMinPayment();
    this.startAt = jobSpec.getStartAt();
    this.endAt = jobSpec.getEndAt();
    this.updatedAt = jobSpec.getUpdatedAt();
    this.deletedAt = jobSpec.getDeletedAt();
    this.params = jobSpec.getParams();
    this.result = result;
  }
}
