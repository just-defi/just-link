package com.tron.client.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.Data;

@Data
public class Parameter {

  @JsonProperty("type_url")
  private String typeUrl;
  private Map<String, String> value;
}
