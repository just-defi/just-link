package com.tron.client.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;

@Data
public class EventMeta {
  private long at;
  @JsonProperty("page_size")
  private int pageSize;
  private String fingerprint;
  private Map<String, String> links;
}