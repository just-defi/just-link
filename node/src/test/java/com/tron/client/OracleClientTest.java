package com.tron.client;

import static com.tron.common.Constant.ONE_HOUR;

import com.google.common.collect.Maps;
import com.tron.keystore.KeyStore;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.tron.common.parameter.CommonParameter;
import org.tron.core.exception.BadItemException;

public class OracleClientTest {

  @Before
  public void init() throws FileNotFoundException {
    KeyStore.initKeyStore("classpath:key.store");
  }

  @Test
  public void requestEventTest() {
    String addr = "TCFLL5dx5ZJdKnWuesXxi1VPwjLVmWZZy9";
    Map<String, String> params = Maps.newHashMap();
    params.put("min_block_timestamp", Long.toString(System.currentTimeMillis() - ONE_HOUR));
    OracleClient oracleClient = new OracleClient();
    System.out.println(oracleClient.requestEvent(addr, params));
  }

  @Test
  public void fulfilTest() throws IOException, BadItemException {
    System.out.println(CommonParameter.getInstance()
            .getValidContractProtoThreadNum());
    CommonParameter.getInstance().setValidContractProtoThreadNum(1);
    FulfillRequest request = new FulfillRequest(
            "TNweaBP3y96ui2aR3yGPCTDAscoJ2hKR5E",
            "bf6263dad699d6ef3f80a204a92488776c55a703bbe08f118708179f1345a86d",
            100,
            "TGpAFMZKd7rjjzz2E4WzYVKwBovWaFVcEQ",
            "6a9705b400000000000000000000000000000000000000000000000000000000",
            1600670994,
            "456");

    OracleClient.fulfil(request);
  }


}
