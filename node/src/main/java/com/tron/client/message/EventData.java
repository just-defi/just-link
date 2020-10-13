package com.tron.client.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventData {
  @JsonProperty("block_timestamp")
  private long blockTimestamp;

  @JsonProperty("contract_address")
  private String contractAddress;

  @JsonProperty("event_name")
  private String eventName;

  @JsonProperty("block_number")
  private int blockNumber;

  @JsonProperty("event_index")
  private int eventIndex;

  @JsonProperty("transaction_id")
  private String transactionId;

  @JsonProperty("caller_contract_address")
  private String callerContractAddress;

  private Map<String, Object> result;

  @JsonProperty("result_type")
  private Map<String, Object> resultType;

  @JsonProperty("_unconfirmed")
  private boolean unconfirmed;
}