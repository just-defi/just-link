package com.tron.common;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Config {

  @Getter
  private static long minFeeLimit;

  @Getter
  private static String apiKey;

  @Getter
  private static String usdtUsdAggregator;

  @Value("${node.minFeeLimit:#{10000000}}")
  public void setMinFeeLimit(long minFeeLimit) {
    Config.minFeeLimit = minFeeLimit;
  }

  @Value("${node.tronApiKey}")
  public void setApiKey(String apiKey) {
    Config.apiKey = apiKey;
  }

  @Value("${node.usdtUsdAggregator:}")
  public void setUsdtUsdAggregator(String address) {
    Config.usdtUsdAggregator = address;
  }
}
