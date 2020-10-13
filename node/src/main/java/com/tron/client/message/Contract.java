package com.tron.client.message;

import lombok.Data;

@Data
public class Contract {
  private String type;
  private Parameter parameter;
}
