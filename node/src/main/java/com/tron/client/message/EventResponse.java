package com.tron.client.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventResponse {

  private boolean success;

  private EventMeta meta;

  private List<EventData> data;
}


