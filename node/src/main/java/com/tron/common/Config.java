package com.tron.common;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Config {

  @Getter
  private static long minFeeLimit;

  @Value("${node.minFeeLimit:#{10000000}}")
  public void setMinFeeLimit(long minFeeLimit) {
    Config.minFeeLimit = minFeeLimit;
  }
}
