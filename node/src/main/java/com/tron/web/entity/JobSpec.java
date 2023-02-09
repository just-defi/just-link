package com.tron.web.entity;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class JobSpec implements Serializable {
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

  public boolean archived() {
    return deletedAt != null;
  }
}



