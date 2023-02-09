package com.tron.web.entity;

import java.io.Serializable;

import java.util.Date;
import lombok.*;


@ToString
@EqualsAndHashCode
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DetailActiveJob implements Serializable {
  private Long  initiatorId;
  private String address;
  private String jobSpecsId;
  private String type;
  private Date createdAt;
  private Date updatedAt;
  private Date deletedAt;
  private String params;
  private String result;
}
