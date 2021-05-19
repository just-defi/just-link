package com.tron.crypto;

import com.tron.keystore.KeyStore;
import java.io.FileNotFoundException;
import org.junit.Before;
import org.junit.Test;
import org.tron.common.utils.ByteArray;

public class CryptoTest {

//  @Before
//  public void init() throws FileNotFoundException {
//    KeyStore.initKeyStore("classpath:key.store");
//  }

  @Test
  public void testIsSquare() {
    VRF vrf = new VRF("41a23d61356e7e92531baaf8273a47d6058fb44a4f155a101c6513412e7ffa2d");
    Proof proof = vrf.GenerateProof(ByteArray.fromHexString(
        "91b10c5c56c46097870d96c7f5d8fddb0c1fea25f6d176e43700dd8b11af7d19"));
    System.out.println(proof.toString());
  }
}
