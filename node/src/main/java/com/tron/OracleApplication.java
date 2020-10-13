package com.tron;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.tron.client.OracleClient;
import com.tron.job.JobSubscriber;
import com.tron.keystore.KeyStore;
import java.io.FileNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
		SpringApplication.run(OracleApplication.class, args);
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
						names = "--help",
						help = true,
						order = 2)
		private boolean help;
	}
}


