package com.tron.job;

import com.tron.OracleApplication;
import com.tron.client.EventRequest;
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
  public void init() {
//    String[] args = {""};
//    SpringApplication.run(OracleApplication.class, args);
  }

  @Test
  public void receiveLogRequestTest() throws InterruptedException {
    EventRequest event = new EventRequest(
        100,
        "228e943babdf4db78402666ca26ed5ed",
        "",
        "TGpAFMZKd7rjjzz2E4WzYVKwBovWaFVcEQ",
        "6a9705b400000000000000000000000000000000000000000000000000000000",
        1600574445,
        "",
        1,
        "1129035f0f96441e7eb4c8ee95b894fb9e01c11d143fd7eb80a59ed33d9d18eb",
        100,
        "TNweaBP3y96ui2aR3yGPCTDAscoJ2hKR5E"
    );

    JobSubscriber.receiveLogRequest(event);
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
