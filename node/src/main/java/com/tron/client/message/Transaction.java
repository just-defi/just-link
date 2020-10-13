package com.tron.client.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Transaction {
  private boolean visible;
  private String txID;
  @JsonProperty("raw_data")
  private RawData rawData;
  @JsonProperty("raw_data_hex")
  private String rawDataHex;
}
