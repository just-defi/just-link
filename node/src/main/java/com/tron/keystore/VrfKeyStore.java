package com.tron.keystore;

import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import org.yaml.snakeyaml.Yaml;

/**
 * Store the private and address info of the node.
 */
@Slf4j
@Component
public class VrfKeyStore {
    private List<String> privateKeys;

    private static HashMap<String, String> vrfKeyMap = Maps.newHashMap(); // <CompressedPublicKey, PrivateKey>

    public static void initKeyStore(String filePath) throws FileNotFoundException {
        if (Strings.isEmpty(filePath)) {
            filePath = "classpath:vrfKeyStore.yml";
        }
        Yaml yaml = new Yaml();
        HashMap<String, List<String>> vrfConfig = new HashMap<>();
        if (!filePath.startsWith("classpath")) {
            log.info("init VRF ECKey from {}", filePath);
            InputStream inputStream = new FileInputStream(filePath);
            vrfConfig = yaml.load(inputStream);
        } else {
            log.info("init VRF ECKey from classpath");
            File file =  ResourceUtils.getFile(filePath);
            InputStream inputStream = new FileInputStream(file.getPath());
            vrfConfig = yaml.load(inputStream);
        }
        ECKey ecKey;
        for (String privateKey : vrfConfig.get("privateKeys")) {
            ecKey = ECKey.fromPrivate(ByteArray.fromHexString(privateKey));
            vrfKeyMap.put(ByteArray.toHexString(ecKey.getPubKeyPoint().getEncoded(true)), privateKey);

            System.out.println("zyd ecKey x:" + ecKey.getPubKeyPoint().normalize().getAffineXCoord()
                    + ", ecKey y:" + ecKey.getPubKeyPoint().normalize().getAffineYCoord()
                    + ",\n ecKey compressed:" + ByteArray.toHexString(ecKey.getPubKeyPoint().getEncoded(true))
                    + ", ecKey uncompressed:" + ECKey.fromPublicOnly(ByteArray.fromHexString(ByteArray.toHexString(ecKey.getPubKeyPoint().getEncoded(true)))).getPubKeyPoint().getXCoord());
        }
    }

    public static HashMap<String, String> getVrfKeyMap() {
        return vrfKeyMap;
    }
}
