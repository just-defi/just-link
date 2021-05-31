package com.tron.keystore;

import com.google.common.collect.Maps;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.PropUtil;
import org.tron.common.utils.StringUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;

/**
 * Store the private and address info of the node.
 */
@Slf4j
@Component
public class VrfKeyStore {

  private static ECKey ecKey;
  @Getter
  private static String privateKey;

  private static HashMap<String, String> vrfKeyMap = Maps.newHashMap();

  public static void initKeyStore(String filePath) throws FileNotFoundException {
    if (Strings.isEmpty(filePath)) {
      filePath = "classpath:vrf.key.store";
    }
    if (!filePath.startsWith("classpath")) {
      log.info("init VRF ECKey from {}", filePath);
      privateKey = PropUtil.readProperty(filePath, "privatekey");
    } else {
      log.info("init VRF ECKey from classpath");
      File file =  ResourceUtils.getFile(filePath);
      privateKey = PropUtil.readProperty(file.getPath(), "privatekey");
    }
    ecKey = ECKey.fromPrivate(ByteArray.fromHexString(privateKey));

      vrfKeyMap.put(ByteArray.toHexString(ecKey.getPubKeyPoint().getEncoded(true)), privateKey);

      System.out.println("zyd ecKey x:" + ecKey.getPubKeyPoint().normalize().getAffineXCoord()
              + ", ecKey y:" + ecKey.getPubKeyPoint().normalize().getAffineYCoord()
      + ",\n ecKey compressed:" + ByteArray.toHexString(ecKey.getPubKeyPoint().getEncoded(true))
      + ", ecKey uncompressed:" + ECKey.fromPublicOnly(ByteArray.fromHexString(ByteArray.toHexString(ecKey.getPubKeyPoint().getEncoded(true)))).getPubKeyPoint().getXCoord());
 }

  public static ECKey getKey() {
    return ecKey;
  }

  public static String getAddr() {
    return StringUtil.encode58Check(ecKey.getAddress());
  }

  public static HashMap<String, String> getVrfKeyMap() {
    return vrfKeyMap;
  }
}
