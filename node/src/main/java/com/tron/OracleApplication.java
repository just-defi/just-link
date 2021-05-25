package com.tron;

import static com.tron.common.Constant.FULLNODE_HOST;
import static com.tron.common.Constant.HTTP_EVENT_HOST;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.tron.client.OracleClient;
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

@Slf4j
@SpringBootApplication
@MapperScan("com.tron.web.mapper")
public class OracleApplication {

	public static void main(String[] args) {

		CommonParameter.getInstance().setValidContractProtoThreadNum(2);
		Args argv = new Args();
		JCommander jct = JCommander.newBuilder()
						.addObject(argv)
						.build();
		jct.setProgramName("just-link");
		jct.setAcceptUnknownOptions(true);
		jct.parse(args);
		try {
			KeyStore.initKeyStore(argv.key);
		} catch (FileNotFoundException e) {
			log.error("init ECKey failed, err: {}", e.getMessage());
			System.exit(-1);
		}
		try {
			VrfKeyStore.initKeyStore(argv.vrfKey);
		} catch (FileNotFoundException e) {
			log.error("init VRF ECKey failed, err: {}", e.getMessage());
			System.exit(-1);
		}

		Constant.initEnv(argv.env);
		ConfigurableApplicationContext context = SpringApplication.run(OracleApplication.class, args);
		JobCache jobCache = context.getBean(JobCache.class);
		jobCache.run();
		OracleClient oracleClient = new OracleClient();
		oracleClient.run();
		JobSubscriber.setup();
		log.info("==================Just Link start success================");
	}

	static class Args {
		@Parameter(
						names = {"--key", "-k"},
						help = true,
						description = "specify the privatekey",
						order = 1)
		private String key;
		@Parameter(
						names = {"--env", "-e"},
						help = true,
						description = "specify the env",
						order = 2)
		private String env;
		@Parameter(
						names = "--help",
						help = true,
						order = 3)
		private boolean help;
		@Parameter(
				names = {"--vrfKey", "-vrfK"},
				help = true,
				description = "specify the VRF privatekey",
				order = 4)
		private String vrfKey;
	}
}


