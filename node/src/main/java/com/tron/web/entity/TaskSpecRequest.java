package com.tron.web.entity;

import com.fasterxml.jackson.databind.util.JSONPObject;
import java.util.Date;
import lombok.Data;

@Data
public class TaskSpecRequest {
  private Long confirmations;
  private String type;
  private TaskParams params;

}