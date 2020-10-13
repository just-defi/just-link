package com.tron.client.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RawData {

  private Contract[] contract;
  @JsonProperty("ref_block_bytes")
  private String refBlockBytes;
  @JsonProperty("ref_block_hash")
  private String refBlockHash;
  private long expiration;
  @JsonProperty("fee_limit")
  private long feeLimit;
  private long timestamp;
}
