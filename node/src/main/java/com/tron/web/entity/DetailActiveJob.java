package com.tron.web.entity;

import java.io.Serializable;

import lombok.*;


@ToString
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
public class DetailActiveJob extends JobSpec implements Serializable {
  private Long result;

  public DetailActiveJob(JobSpec jobSpec, Long result) {
    super(
      jobSpec.getId(),
      jobSpec.getCreatedAt(),
      jobSpec.getInitiators(),
      jobSpec.getTaskSpecs(),
      jobSpec.getMinPayment(),
      jobSpec.getStartAt(),
      jobSpec.getEndAt(),
      jobSpec.getUpdatedAt(),
      jobSpec.getDeletedAt(),
      jobSpec.getParams()
    );

    this.result = result;
  }
}
