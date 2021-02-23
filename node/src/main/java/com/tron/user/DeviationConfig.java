package com.tron.user;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.yaml.snakeyaml.Yaml;

@Data
public class DeviationConfig {

  private String aggType;
  private long sleep;
  private String fullnodeSchema;
  private String fullnode;
  private String privateKey;
  private BigInteger feelimit;
  private List<PairInfo> pairs;
  private List<Map<String, Object>> nodeServer;
  private Map<String, List<String>> jobs;

  private static DeviationConfig config = null;

  public static DeviationConfig loadConfig(String confPath) throws FileNotFoundException {
    Yaml yaml = new Yaml();
    InputStream inputStream = new FileInputStream(confPath);
    config = yaml.loadAs(inputStream, DeviationConfig.class);
    System.out.println(config);
    return config;
  }

  public static DeviationConfig getConfig() {
    if (config == null) {
      throw new RuntimeException("config can not be null");
    }
    return config;
  }
}