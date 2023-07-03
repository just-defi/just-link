package com.tron.job;

import com.tron.OracleApplication;
import com.tron.client.EventRequest;
import com.tron.client.VrfEventRequest;
import com.tron.common.Constant;
import com.tron.keystore.KeyStore;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes=OracleApplication.class)
public class JobSubscriberTest {

  @Before
  public void init() throws FileNotFoundException {
    KeyStore.initKeyStore("classpath:key.store");
  }

  @Test
  public void receiveLogRequestTest() throws InterruptedException {
    EventRequest event = new EventRequest(
        100,
        "f6ee7a6608d6426ea72a9034eff49acc",
        "",
        "TGpAFMZKd7rjjzz2E4WzYVKwBovWaFVcEQ",
        "6a9705b400000000000000000000000000000000000000000000000000000000",
        1600574445,
        "",
        1,
        "1129035f0f96441e7eb4c8ee95b894fb9e01c11d143fd7eb80a59ed33d9d18ed",
        new BigInteger("1000000"),
        "TKGBsz6gX8RDMpkma4wPncafWbZFjNkDXB"
    );

    JobSubscriber.receiveLogRequest(event);
    Thread.sleep(10000);
  }

  @Test
  public void receiveNewRoundTest() throws InterruptedException {

    Constant.FULLNODE_HOST = "api.nileex.io";
    JobSubscriber.receiveNewRoundLog("TGm9cecRyrHAUziKrmRASPLb8fgZbJJmF9",
        "TGm9cecRyrHAUziKrmRASPLb8fgZbJJmF9",
        13,
        1);
    Thread.sleep(10000);
  }

  @Test
  public void test1() throws DecoderException {
    String str = "3062666335386330643331373439653161383138616263643330623337376630";
    System.out.println(new String(Hex.decodeHex(str)));
    long l = 100;
    System.out.println(Long.toHexString(100));

    System.out.println(Long.parseLong("623e", 16));

    String base = "0000000000000000000000000000000000000000000000000000000000000000";
    String dataHexStr = Long.toHexString(100);
    int sub = base.length()-dataHexStr.length() - dataHexStr.length();
    System.out.println(base);
    System.out.println(base.substring(0, sub) + dataHexStr);
  }
}
