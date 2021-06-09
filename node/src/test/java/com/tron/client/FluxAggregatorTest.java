package com.tron.client;

import com.tron.client.message.OracleRoundState;
import com.tron.common.Constant;
import com.tron.keystore.KeyStore;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;

public class FluxAggregatorTest {

  @Before
  public void init() throws FileNotFoundException {
    KeyStore.initKeyStore("classpath:key.store");
  }

  @Test
  public void getOracleRoundState() throws IOException {
    Constant.FULLNODE_HOST = "api.nileex.io";
    OracleRoundState ret = FluxAggregator.getOracleRoundState("TGm9cecRyrHAUziKrmRASPLb8fgZbJJmF9", 13);
    System.out.println(ret);
  }
}
