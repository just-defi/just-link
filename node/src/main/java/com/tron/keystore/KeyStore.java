package com.tron.keystore;

import java.io.File;
import java.io.FileNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.PropUtil;
import org.tron.common.utils.StringUtil;

/**
 * Store the private and address info of the node.
 */
@Slf4j
@Component
public class KeyStore {

  private static ECKey ecKey;

  public static void initKeyStore(String filePath) throws FileNotFoundException {
    if (Strings.isEmpty(filePath)) {
      filePath = "classpath:key.store";
    }
    String privatekey = "";
    if (!filePath.startsWith("classpath")) {
      log.info("init ECKey from {}", filePath);
      privatekey = PropUtil.readProperty(filePath, "privatekey");
    } else {
      log.info("init ECKey from classpath");
      File file =  ResourceUtils.getFile(filePath);
      privatekey = PropUtil.readProperty(file.getPath(), "privatekey");
    }
    ecKey = ECKey.fromPrivate(ByteArray.fromHexString(privatekey));
  }

  public static ECKey getKey() {
    return ecKey;
  }

  public static String getAddr() {
    return StringUtil.encode58Check(ecKey.getAddress());
  }
}
