package com.tron.client;
import com.google.common.collect.Maps;
import com.tron.OracleApplication;
import com.tron.common.AbiUtil;
import com.tron.common.Config;
import com.tron.job.JobCache;
import com.tron.job.JobSubscriber;
import com.tron.keystore.KeyStore;
import com.tron.web.entity.TronTx;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.base.Strings;
import com.tron.client.OracleClient;
import com.tron.client.ReSender;
import com.tron.common.Constant;
import com.tron.job.JobCache;
import com.tron.job.JobSubscriber;
import com.tron.keystore.KeyStore;
import com.tron.keystore.VrfKeyStore;
import java.io.FileNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.tron.common.parameter.CommonParameter;

import java.io.FileNotFoundException;
import java.util.Map;

import static com.tron.common.Constant.FULFIL_METHOD_SIGN;
import java.util.List;

import static com.tron.common.Constant.TronTxInProgress;


public class ReSenderTest {

  @BeforeClass
  public static void init() throws FileNotFoundException {
    KeyStore.initKeyStore("classpath:key.store");

    ConfigurableApplicationContext context = SpringApplication.run(OracleApplication.class, new String[]{});
    JobCache jobCache = context.getBean(JobCache.class);
    jobCache.run();
    JobSubscriber.setup();
  }

  @Test
  //pre: Create at least one ongoing transaction
  public void reSenderTest() {
    Constant.HTTP_EVENT_HOST = "nile.trongrid.io";
    Constant.FULLNODE_HOST = "api.nileex.io";
    ReSender reSender = new ReSender(JobSubscriber.jobRunner.tronTxService);
    reSender.run();
  }

  @Test
  public void stringMapTest() {
    Map<String, Object> params = Maps.newHashMap();
    params.put("owner_address", KeyStore.getAddr());
    params.put("contract_address", "TM63JqzAhc3oAgSjAzhD5i3ohFp1f3YY4k");
    params.put("function_selector", FULFIL_METHOD_SIGN);
    params.put("parameter", "test");
    params.put("fee_limit", Config.getMinFeeLimit());
    params.put("call_value", 0);
    params.put("visible", true);

    String strFromMap = OracleClient.convertWithIteration(params);
    Map<String, Object> mapFromStr = ReSender.convertWithStream(strFromMap);
    for (Map.Entry<String, Object> param : params.entrySet())  {
      Assert.assertEquals(param.getValue().toString(), mapFromStr.get(param.getKey()));
    }
  }
}

