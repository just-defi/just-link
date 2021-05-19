package com.tron.crypto;

import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.spongycastle.math.ec.ECPoint;
import org.spongycastle.util.BigIntegers;
import org.tron.common.crypto.ECKey;

@Data
@AllArgsConstructor
public class Proof {

  ECPoint PublicKey;
  ECPoint Gamma;
  BigInteger C;
  BigInteger S;
  BigInteger Seed;
  BigInteger Output;

  @Override
  public String toString() {
    return String.format(
        "vrf.Proof{PublicKey: {x:%x, y:%x}, Gamma: {x:%x, y:%x}, C:%x, S:%x, Seed:%x, Output:%x}",
        PublicKey.getRawXCoord().toBigInteger(),
        PublicKey.getRawYCoord().toBigInteger(),
        Gamma.getRawXCoord().toBigInteger(),
        Gamma.getRawXCoord().toBigInteger(),
        C,
        S,
        Seed,
        Output);
  }

  public boolean WellFormed() {
    BigInteger groupOrder = ECKey.CURVE_SPEC.getCurve().getField().getCharacteristic();
    if (!PublicKey.isValid()) {
      return false;
    }
    if (!Gamma.isValid()) {
      return false;
    }
    if (!(C.compareTo(groupOrder) < 0)) {
      return false;
    }
    if (!(S.compareTo(groupOrder) < 0)) {
      return false;
    }
    if (!(BigIntegers.asUnsignedByteArray(Output).length <= VRF.HashLength)) {
      return false;
    }
    return true;
  }
}
