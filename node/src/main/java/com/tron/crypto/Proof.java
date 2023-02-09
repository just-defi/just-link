package com.tron.crypto;

import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.BigIntegers;

/**
 * Proof represents a proof that Gamma was constructed from the Seed
 * according to the process mandated by the PublicKey.
 */
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
        Gamma.getRawYCoord().toBigInteger(),
        C,
        S,
        Seed,
        Output);
  }

  //WellFormed is true if proof's attributes satisfy basic domain checks
  public boolean wellFormed() {
    BigInteger groupOrder = VRF.groupOrder;
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
