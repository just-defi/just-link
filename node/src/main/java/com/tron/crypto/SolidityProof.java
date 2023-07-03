package com.tron.crypto;

import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bouncycastle.math.ec.ECPoint;
import org.tron.common.utils.ByteArray;

@AllArgsConstructor
public class SolidityProof {

  @Getter
  private Proof proof;
  @Getter
  private byte[] UWitness;
  @Getter
  private ECPoint CGammaWitness;
  @Getter
  private ECPoint SHashWitness;
  @Getter
  private BigInteger ZInv;

  @Override
  public String toString() {
    return String.format(
        "SolidityProof:{%s, UWitness:%s, CGammaWitness:{x:%x, y:%x}, SHashWitness:{x:%x, y:%x}, ZInv:%x}",
        proof.toString(),
        ByteArray.toHexString(UWitness),
        CGammaWitness.getRawXCoord().toBigInteger(),
        CGammaWitness.getRawYCoord().toBigInteger(),
        SHashWitness.getRawXCoord().toBigInteger(),
        SHashWitness.getRawYCoord().toBigInteger(),
        ZInv
    );
  }
}
