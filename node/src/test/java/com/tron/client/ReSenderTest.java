package com.tron.client;
import com.tron.OracleApplication;
import com.tron.job.JobCache;
import com.tron.job.JobSubscriber;
import com.tron.keystore.KeyStore;
import com.tron.web.entity.TronTx;
import org.junit.Before;
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

public class ReSenderTest {

  @Before
  public void init() throws FileNotFoundException {
    KeyStore.initKeyStore("classpath:key.store");

    ConfigurableApplicationContext context = SpringApplication.run(OracleApplication.class, new String[]{});
    JobCache jobCache = context.getBean(JobCache.class);
    jobCache.run();
    JobSubscriber.setup();
  }

  @Test
  //pre: Create at least one ongoing transaction
  public void ReSenderTest() {
    Constant.HTTP_EVENT_HOST = "nile.trongrid.io";
    Constant.FULLNODE_HOST = "api.nileex.io";
    ReSender reSender = new ReSender(JobSubscriber.jobRunner.tronTxService);
    reSender.run();
  }
}
