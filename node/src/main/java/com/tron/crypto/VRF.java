package com.tron.crypto;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.BigIntegers;
import org.tron.common.crypto.ECKey;
import org.tron.common.crypto.Hash;
import org.tron.common.utils.ByteArray;

@Slf4j
public class VRF {

  // Secp256k1 curve：y²=x³+7
  private static ECKey ecKey;
  public static final ECPoint generator = ECKey.CURVE_SPEC.getG();
  // p = FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F
  // field num on Fp
  public static final BigInteger fieldSize =
      ECKey.CURVE_SPEC.getCurve().getField().getCharacteristic();
  // N = FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
  // point num on Eclipse Curve
  public static final BigInteger groupOrder = ECKey.CURVE_SPEC.getN();

  //some predefine constant
  public final static int HashLength = 32;
  private final BigInteger zero, one, two, three, four, seven, eulerCriterionPower, sqrtPower;
  // some prefix, byte[32]
  private final byte[] hashToCurveHashPrefix; //1
  private final byte[] scalarFromCurveHashPrefix; //2
  public final byte[] vrfRandomOutputHashPrefix; //3

  //SolidityProof = 416
  public static final int ProofLength = 64 + // PublicKey
      64 + // Gamma
      32 + // C
      32 + // S
      32 + // Seed
      32 + // uWitness (gets padded to 256 bits, even though it's only 160)
      64 + // cGammaWitness
      64 + // sHashWitness
      32; // zInv  (Leave Output out, because that can be efficiently calculated)


 @AllArgsConstructor
  class MarshaledProof {

    // byte array length is ProofLength
    public byte[] value;
  }

  /**
   * construct VRF node with privateKey
   */
  public VRF(String privateKey) {
    ecKey = ECKey.fromPrivate(ByteArray.fromHexString(privateKey));

    zero = BigInteger.valueOf(0);
    one = BigInteger.valueOf(1);
    two = BigInteger.valueOf(2);
    three = BigInteger.valueOf(3);
    four = BigInteger.valueOf(4);
    seven = BigInteger.valueOf(7);

    eulerCriterionPower = fieldSize.subtract(one).divide(two); //  (p-1)/2
    sqrtPower = fieldSize.add(one).divide(four); // (p+1)/4

    hashToCurveHashPrefix = bytesToHash(one.toByteArray());
    scalarFromCurveHashPrefix = bytesToHash(two.toByteArray());
    vrfRandomOutputHashPrefix = bytesToHash(three.toByteArray());
  }

  /**
   * get last HashLength byte of b; if len(b) < HashLength, padding with 0 before.
   * equal to function BigToHash in go module.
   */
  public byte[] bytesToHash(byte[] b) {
    byte[] hash = new byte[HashLength];
    if (b.length > HashLength) {
      hash = Arrays.copyOfRange(b, b.length - HashLength, b.length);
    } else {
      System.arraycopy(b, 0, hash, HashLength - b.length, b.length);
    }
    return hash;
  }

  /**
   * linearCombination of scalar and EcPoint, [c]·p1 + [s]·p2
   */
  public ECPoint linearCombination(BigInteger c, ECPoint p1, BigInteger s, ECPoint p2) {
    ECPoint p11 = p1.multiply(c.mod(groupOrder)).normalize();
    ECPoint p22 = p2.multiply(s.mod(groupOrder)).normalize();
    return p11.add(p22).normalize();
  }

  /**
   * represent one ECPoint with byte array. ECPoint must be normalized already.
   */
  public static byte[] longMarshal(ECPoint p1) {
    byte[] x = BigIntegers.asUnsignedByteArray(32, p1.getRawXCoord().toBigInteger());
    byte[] y = BigIntegers.asUnsignedByteArray(32, p1.getRawYCoord().toBigInteger());
    byte[] merged = new byte[x.length + y.length];
    System.arraycopy(x, 0, merged, 0, x.length);
    System.arraycopy(y, 0, merged, x.length, y.length);
    return merged;
  }

  /**
   * construct one ECPoint from byte array.
   */
  public ECPoint longUnmarshal(byte[] m) throws VRFException {
    if (m == null || m.length != 64) {
      throw new VRFException(String.format(
          "0x%s does not represent an uncompressed secp256k1Point. Should be length 64, but is length %d",
          ByteArray.toHexString(m), m.length));
    }
    byte[] xByte = new byte[32], yByte = new byte[32];
    System.arraycopy(m, 0, xByte, 0, 32);
    System.arraycopy(m, 32, yByte, 0, 32);
    BigInteger x = new BigInteger(1, xByte);
    BigInteger y = new BigInteger(1, yByte);
    ECPoint rv = ECKey.CURVE_SPEC.getCurve().createPoint(x, y);
    if (!rv.isValid()) {
      throw new VRFException("point requested from invalid coordinates");
    }
    return rv;
  }

  /**
   * MustHash returns the keccak256 hash, or panics on failure, 32 byte
   */
  public static byte[] mustHash(byte[] in) {
    return Hash.sha3(in);
  }

  /**
   * concat the 1,2,3,5th point which has the form of p.x||p.y, join uWitness, at last sha3 the result
   */
  public BigInteger scalarFromCurvePoints(ECPoint hash, ECPoint pk, ECPoint gamma, byte[] uWitness,
      ECPoint v) throws VRFException {
    if (!(hash.isValid() && pk.isValid() && gamma.isValid() && v.isValid())) {
      throw new VRFException("bad arguments to vrf.ScalarFromCurvePoints");
    }

//    System.out.println();
//    System.out.println("ScalarFromCurvePoints\nhash:" + ByteArray.toHexString(LongMarshal(hash)));
//    System.out.println("pk:" + ByteArray.toHexString(LongMarshal(pk)));
//    System.out.println("gamma:" + ByteArray.toHexString(LongMarshal(gamma)));
//    System.out.println("v:" + ByteArray.toHexString(LongMarshal(v)));
//    System.out.println();

    byte[] merged = new byte[32 + 64 + 64 + 64 + 64 + 20];
    System.arraycopy(scalarFromCurveHashPrefix, 0, merged, 0, 32);
    System.arraycopy(longMarshal(hash), 0, merged, 32, 64);
    System.arraycopy(longMarshal(pk), 0, merged, 96, 64);
    System.arraycopy(longMarshal(gamma), 0, merged, 160, 64);
    System.arraycopy(longMarshal(v), 0, merged, 224, 64);
    System.arraycopy(uWitness, 0, merged, 288, 20);
    byte[] mustHash = mustHash(merged);

    return new BigInteger(1, mustHash);
  }

  /**
   * convert sha3(message) to the field element on Fp
   */
  public BigInteger fieldHash(byte[] message) {
//    System.out.println("message:" + ByteArray.toHexString(message));
    byte[] hashResult = mustHash(message);
//    System.out.println("hashResult:" + ByteArray.toHexString(hashResult));
    BigInteger rv = new BigInteger(1, bytesToHash(hashResult));
//    System.out.println("FieldHash1:" + rv);

    while (rv.compareTo(fieldSize) >= 0) {
      byte[] shortRV = bytesToHash(BigIntegers.asUnsignedByteArray(rv));
      rv = new BigInteger(1, mustHash(shortRV));
    }
    return rv;
  }

  /**
   * left pad byte 0 of slice to length l
   */
  public byte[] leftPadBytes(byte[] slice, int l) {
    if (slice.length >= l) {
      return slice;
    }

    byte[] newSlice = new byte[l];
    System.arraycopy(slice, 0, newSlice, l - slice.length, slice.length);
    return newSlice;
  }

  /**
   * convert uint256 to byte array, without sign byte
   */
  public byte[] uint256ToBytes32(BigInteger uint256) throws VRFException {
    if (BigIntegers.asUnsignedByteArray(uint256).length > HashLength) { //256=HashLength*8
      throw new VRFException("vrf.uint256ToBytes32: too big to marshal to uint256");
    }
    return leftPadBytes(BigIntegers.asUnsignedByteArray(uint256), HashLength);
  }

  /**
   * x => x^3 + 7，
   */
  public BigInteger ySquare(BigInteger x) {
    return x.modPow(three, fieldSize).add(seven).mod(fieldSize);
  }

  /**
   * check whether a BigInteger is the square of some element on Fp.
   */
  public boolean isSquare(BigInteger x) {
    return x.modPow(eulerCriterionPower, fieldSize).compareTo(one) == 0;
  }

  /**
   * check whether one BigInteger can be the x coordinate of Curve
   */
  public boolean isCurveXOrdinate(BigInteger x) {
    return isSquare(ySquare(x));
  }

  /**
   * SquareRoot returns a s.t. a^2=x, as long as x is a square
   */
  public BigInteger squareRoot(BigInteger x) {
    return x.modPow(sqrtPower, fieldSize);
  }

  public BigInteger neg(BigInteger f) {
    return fieldSize.subtract(f);
  }

  // projectiveSub(x1, z1, x2, z2) is the projective coordinates of x1/z1 - x2/z2
  public BigInteger[] projectiveSub(BigInteger x1, BigInteger z1, BigInteger x2, BigInteger z2) {
    BigInteger num1 = z2.multiply(x1);
    BigInteger num2 = neg(z1.multiply(x2));
    return new BigInteger[] {num1.add(num2).mod(fieldSize), z1.multiply(z2).mod(fieldSize)};
  }

  // projectiveMul(x1, z1, x2, z2) is projective coordinates of (x1/z1)×(x2/z2)
  public BigInteger[] projectiveMul(BigInteger x1, BigInteger z1, BigInteger x2, BigInteger z2) {
    return new BigInteger[] {x1.multiply(x2), z1.multiply(z2)};
  }

  /**
   * add two affine point, get the projective point
   */
  public BigInteger[] ProjectiveECAdd(ECPoint p, ECPoint q) {
    BigInteger px = p.normalize().getRawXCoord().toBigInteger();
    BigInteger py = p.normalize().getRawYCoord().toBigInteger();
    BigInteger qx = q.normalize().getRawXCoord().toBigInteger();
    BigInteger qy = q.normalize().getRawYCoord().toBigInteger();
    BigInteger pz = BigInteger.valueOf(1);
    BigInteger qz = BigInteger.valueOf(1);

    BigInteger lx = qy.subtract(py);
    BigInteger lz = qx.subtract(px);

    BigInteger[] array1 = projectiveMul(lx, lz, lx, lz);
    BigInteger sx = array1[0];
    BigInteger dx = array1[1];
    BigInteger[] array2 = projectiveSub(sx, dx, px, pz);
    sx = array2[0];
    dx = array2[1];
    BigInteger[] array3 = projectiveSub(sx, dx, qx, qz);
    sx = array3[0];
    dx = array3[1];

    BigInteger[] array4 = projectiveSub(px, pz, sx, dx);
    BigInteger sy = array4[0];
    BigInteger dy = array4[1];
    BigInteger[] array5 = projectiveMul(sy, dy, lx, lz);
    sy = array5[0];
    dy = array5[1];
    BigInteger[] array6 = projectiveSub(sy, dy, py, pz);
    sy = array6[0];
    dy = array6[1];

    BigInteger sz;
    if (dx.compareTo(dy) != 0) {
      sx = sx.multiply(dy);
      sy = sy.multiply(dx);
      sz = dx.multiply(dy);
    } else {
      sz = dx;
    }

    return new BigInteger[] {sx.mod(fieldSize), sy.mod(fieldSize), sz.mod(fieldSize)};
  }

  /**
   * create an uncompressed ECPoint with coordinate x,y on Curve
   */
  public ECPoint setCoordinates(BigInteger x, BigInteger y) throws VRFException {
    ECPoint rv = ECKey.CURVE_SPEC.getCurve().createPoint(x, y);
    if (!rv.isValid()) {
      throw new VRFException("point requested from invalid coordinates");
    }
    return rv;
  }

  /**
   * get a ECPoint whose coordinate x = hash(p.x||p.y||seed)
   */
  public ECPoint hashToCurve(ECPoint p, BigInteger seed) throws VRFException {
    if (!(p.isValid() && seed.toByteArray().length <= 256 && seed.compareTo(zero) >= 0)) {
      throw new VRFException("bad input to vrf.HashToCurve");
    }
    byte[] inputTo32Byte = uint256ToBytes32(seed);

    byte[] merged = new byte[32 + 64 + 32];
    System.arraycopy(hashToCurveHashPrefix, 0, merged, 0, 32);
    System.arraycopy(longMarshal(p), 0, merged, 32, 64);
    System.arraycopy(inputTo32Byte, 0, merged, 96, 32);

    BigInteger x = fieldHash(merged);

    while (!isCurveXOrdinate(x)) { // Hash recursively until x^3+7 is a square
      x = fieldHash(bytesToHash(BigIntegers.asUnsignedByteArray(x)));
    }
    BigInteger y_2 = ySquare(x);
    BigInteger y = squareRoot(y_2);
    ECPoint rv = setCoordinates(x, y);

    // Negate response if y odd
    if (y.mod(two).compareTo(one) == 0) {
      rv = rv.negate();
    }
    return rv;
  }

  /**
   * check if [c]·gamma ≠ [s]·hash as required by solidity verifier
   *
   * @return false if [c]·gamma ≠ [s]·hash else true
   */
  public boolean checkCGammaNotEqualToSHash(BigInteger c, ECPoint gamma, BigInteger s,
      ECPoint hash) {
    ECPoint p1 = gamma.multiply(c.mod(groupOrder)).normalize();
    ECPoint p2 = hash.multiply(s.mod(groupOrder)).normalize();
    return !p1.equals(p2);
  }

  /**
   * get last 160 bit of sha3(p.x||p.y)，equal to function EthereumAddress in go module
   */
  public byte[] getLast160BitOfPoint(ECPoint point) {
//    System.out.println("getLast160BitOfPoint:" + ByteArray.toHexString(LongMarshal(point)));
    byte[] sha3Result = mustHash(longMarshal(point));
//    System.out.println("sha3Result:" + ByteArray.toHexString(sha3Result));

    byte[] cv = new byte[20];
    System.arraycopy(sha3Result, 12, cv, 0, 20);
    return cv;
  }

  /**
   * VerifyVRFProof is true iff gamma was generated in the mandated way from the
   * given publicKey and seed, and no error was encountered
   */
  public boolean verifyVRFProof(Proof proof) throws ErrCGammaEqualsSHash, VRFException {
    if (!proof.wellFormed()) {
      throw new VRFException("badly-formatted proof");
    }
    ECPoint h = hashToCurve(proof.PublicKey, proof.Seed).normalize();
//    System.out.println("verify_h_x:" +
//        ByteArray.toHexString(h.getRawXCoord().toBigInteger().toByteArray()));
//    System.out.println("verify_h_y:" +
//        ByteArray.toHexString(h.getRawYCoord().toBigInteger().toByteArray()));

    boolean notEqual = checkCGammaNotEqualToSHash(proof.C, proof.getGamma(), proof.S, h);
    if (!notEqual) {
      throw new ErrCGammaEqualsSHash("c*γ = s*hash (disallowed in solidity verifier)");
    }

    // u = [c]·Q + [s]·G
//    System.out.println(String.format("\tproof.C:%s\n\tproof.PublicKey.x:%s\n\tproof.PublicKey.y:%s"
//            + "\n\tproof.S:%s\n\tgenerator.x:%s\n\tgenerator.y:%s",
//        proof.C,
//        ByteArray.toHexString(proof.PublicKey.getRawXCoord().toBigInteger().toByteArray()),
//        ByteArray.toHexString(proof.PublicKey.getRawYCoord().toBigInteger().toByteArray()),
//        proof.S,
//        ByteArray.toHexString(generator.getRawXCoord().toBigInteger().toByteArray()),
//        ByteArray.toHexString(generator.getRawYCoord().toBigInteger().toByteArray())
//    ));
    ECPoint uPrime = linearCombination(proof.C, proof.PublicKey, proof.S, generator);
//    System.out.println("verify_u_x:" +
//        ByteArray.toHexString(uPrime.getRawXCoord().toBigInteger().toByteArray()));
//    System.out.println("verify_u_y:" +
//        ByteArray.toHexString(uPrime.getRawYCoord().toBigInteger().toByteArray()));

    // v = [c]·β + [s]·h
    ECPoint vPrime = linearCombination(proof.C, proof.getGamma(), proof.S, h);
//    System.out.println("verify_v_x:" +
//        ByteArray.toHexString(vPrime.getRawXCoord().toBigInteger().toByteArray()));
//    System.out.println("verify_v_y:" +
//        ByteArray.toHexString(vPrime.getRawYCoord().toBigInteger().toByteArray()));

    //点u转换为以太坊地址
    byte[] uWitness = getLast160BitOfPoint(uPrime);
    //把1、2、3、5的x、y串一起，链接uWitness，得到点c
    BigInteger cPrime = scalarFromCurvePoints(h, proof.PublicKey, proof.getGamma(), uWitness,
        vPrime);
    byte[] gammaRepresent = longMarshal(proof.getGamma());

    //随机数β的x、y坐标连在一起，加前缀
    byte[] prefixAndGamma = new byte[vrfRandomOutputHashPrefix.length + gammaRepresent.length];
    System.arraycopy(vrfRandomOutputHashPrefix, 0, prefixAndGamma, 0,
        vrfRandomOutputHashPrefix.length);
    System.arraycopy(gammaRepresent, 0, prefixAndGamma, vrfRandomOutputHashPrefix.length,
        gammaRepresent.length);
    byte[] output = mustHash(prefixAndGamma);

    // check if point proof.β == point cPrime
    if (!(proof.C.compareTo(cPrime) == 0)) {
      return false;
    }
    // check if proof.Output == output
    if (!(proof.Output.compareTo(new BigInteger(1, output)) == 0)) {
      return false;
    }
    return true;
  }

  /**
   * generateProofWithNonce allows external nonce generation for testing purposes
   *
   * As with signatures, using nonces which are in any way predictable to an
   * adversary will leak your secret key! Most people should use GenerateProof instead.
   *
   * @param secretKey private key of node, same as k in pseudocode，
   * @param seed seed provided by use, same as message in pseudocode，
   * @param nonce one scalar between 0 and p, same as t in pseudocode, not equal with Output Point of VRF
   * @return proof
   */
  public Proof generateProofWithNonce(BigInteger secretKey, BigInteger seed, BigInteger nonce)
      throws VRFException, ErrCGammaEqualsSHash {
//    System.out.println("seed:" + ByteArray.toHexString(BigIntegers.asUnsignedByteArray(seed)));
    if (!(secretKey.compareTo(groupOrder) < 0
        && BigIntegers.asUnsignedByteArray(nonce).length <= HashLength)) {
      throw new VRFException("badly-formatted key or seed");
    }

    BigInteger skAsScalar = secretKey.mod(groupOrder);
    //伪代码的公钥Q = [secretKey]·G
    ECPoint publicKey = generator.multiply(skAsScalar).normalize();

    // 点 h = HashToCurve(Q,message)
    ECPoint h = hashToCurve(publicKey, seed).normalize();

    // 点 β = [secretKey]·h
    ECPoint gamma = h.multiply(skAsScalar).normalize();

    BigInteger sm = nonce.mod(groupOrder); //t
//    System.out.println(sm);
    ECPoint u = generator.multiply(sm).normalize();//normalize操作使 z=1

    byte[] uWitness = getLast160BitOfPoint(u);
//    System.out.println("uWitness:" + ByteArray.toHexString(uWitness));
    ECPoint v = h.multiply(sm).normalize();

    //把1、2、3、5的x、y串一起，链接uWitness，得到c
    BigInteger c = scalarFromCurvePoints(h, publicKey, gamma, uWitness, v);
//    System.out.println("c:" + c);

    //s = (nonce - c·k) mod q
    BigInteger s = nonce.subtract(c.multiply(skAsScalar)).mod(groupOrder);
//    System.out.println("s:" + s);
    if (!checkCGammaNotEqualToSHash(c, gamma, s, h)) {
      System.out.println("cccccc");
      return null;
    }

    byte[] gammaRepresent = longMarshal(gamma);
//    System.out.println("gammaRepresent:" + ByteArray.toHexString(gammaRepresent));

    //prefix||β.x||β.y
    byte[] prefixAndGamma = new byte[vrfRandomOutputHashPrefix.length + gammaRepresent.length];
    System.arraycopy(vrfRandomOutputHashPrefix, 0, prefixAndGamma, 0,
        vrfRandomOutputHashPrefix.length);
    System.arraycopy(gammaRepresent, 0, prefixAndGamma, vrfRandomOutputHashPrefix.length,
        gammaRepresent.length);

    //sha3(prefix||β.x||β.y)
    byte[] output = mustHash(prefixAndGamma);

    Proof rv = new Proof(
        publicKey,
        gamma,
        c,
        s,
        seed,
        new BigInteger(1, output));

//    System.out.println("\nVerifyVRFProof...");
    if (!verifyVRFProof(rv)) {
      throw new VRFException("constructed invalid proof");
    }
    return rv;
  }

  /**
   * generate a biginteger between 0 and n as seed
   */
  public BigInteger getRandomBigInteger() {
    SecureRandom random = new SecureRandom();
    int numBits = groupOrder.bitLength();
    BigInteger a;
    do {
      //Constructs a randomly generated BigInteger, uniformly distributed over the range 0 to (2^numBits - 1),
      a = new BigInteger(numBits, random);
    } while (a.compareTo(groupOrder) >= 0);
    return a;
  }

  /**
   * 采用节点的私钥、用户提供的种子生成proof。
   * GenerateProof returns gamma, plus proof that gamma was constructed from seed
   * as mandated from the given secretKey, with public key secretKey*Generator
   *
   * secretKey and seed must be less than secp256k1 group order. (Without this
   * constraint on the seed, the samples and the possible public keys would
   * deviate very slightly from uniform distribution.)
   *
   * @param seed byte[HashLength] that user inputs
   */
  public Proof generateProof(byte[] seed) {
    Proof proof = null;
    while (true) {
      BigInteger nonce = getRandomBigInteger();
      try {
        proof = generateProofWithNonce(ecKey.getPrivKey(), new BigInteger(1, seed), nonce);
      } catch (ErrCGammaEqualsSHash errCGammaEqualsSHash) {
        log.error("", errCGammaEqualsSHash);
        continue;
      } catch (VRFException vrfException) {
        log.error("", vrfException);
        break;
      }
      break;
    }
    return proof;
  }

  public void testIsSquare() {
    assert isSquare(four) == true;
    assert isSquare(fieldSize.subtract(one)) == true;
  }

  public void testSquareRoot() {
    assert squareRoot(four).compareTo(two) == 0;
  }

  public void testYSquare() {
    assert two.multiply(two).multiply(two).compareTo(ySquare(two)) == 0;
  }

  public void testIsCurveXOrdinate() {
    assert isCurveXOrdinate(one) == true;
    assert isCurveXOrdinate(BigInteger.valueOf(5)) == true;
  }

  public void testVerifyProof() {
    BigInteger secretKey = BigInteger.valueOf(1);
    BigInteger seed = BigInteger.valueOf(2);
    BigInteger nonce = BigInteger.valueOf(3);

    Proof proof = null;
    try {
      proof = generateProofWithNonce(secretKey, seed, nonce);
    } catch (VRFException vrfException) {
      vrfException.printStackTrace();
    } catch (ErrCGammaEqualsSHash errCGammaEqualsSHash) {
      errCGammaEqualsSHash.printStackTrace();
    }

    //如果seed不一样，proof无法验证通过
    proof.setSeed(seed.add(BigInteger.valueOf(1)));
    try {
      boolean valid = verifyVRFProof(proof);
      assert valid == false;
    } catch (ErrCGammaEqualsSHash errCGammaEqualsSHash) {
      errCGammaEqualsSHash.printStackTrace();
    } catch (VRFException vrfException) {
      vrfException.printStackTrace();
    }
  }

  /**
   * returns the precomputed values needed by the solidity verifier, or an error on failure.
   */
  public SolidityProof solidityPrecalculations(Proof proof) {
    ECPoint uPrime = linearCombination(proof.C, proof.PublicKey, proof.S, generator);
    byte[] uWitness = getLast160BitOfPoint(uPrime); //1
    ECPoint CGammaWitness = proof.Gamma.multiply(proof.C.mod(groupOrder)).normalize(); //2

    ECPoint hash;
    try {
      hash = hashToCurve(proof.PublicKey, proof.Seed);
    } catch (VRFException vrfException) {
      vrfException.printStackTrace();
      return null;
    }
    ECPoint SHashWitness = hash.multiply(proof.S.mod(groupOrder)).normalize();//3

    BigInteger[] pArray = ProjectiveECAdd(CGammaWitness, SHashWitness);
    BigInteger zInv = pArray[2].modInverse(fieldSize);

    SolidityProof solidityProof = new SolidityProof(proof, uWitness, CGammaWitness, SHashWitness,
        zInv);
    return solidityProof;
  }

  /**
   * serialize SolidityProof as binary marshaled proof
   */
  public byte[] marshalForSolidityVerifier(SolidityProof solidityProof)
      throws VRFException {
    byte[] cursor = new byte[ProofLength];
    System.arraycopy(longMarshal(solidityProof.getProof().PublicKey), 0, cursor, 0, 64);
    System.arraycopy(longMarshal(solidityProof.getProof().getGamma()), 0, cursor, 64, 64);
    System.arraycopy(uint256ToBytes32(solidityProof.getProof().C), 0, cursor, 128, 32);
    System.arraycopy(uint256ToBytes32(solidityProof.getProof().S), 0, cursor, 160, 32);
    System.arraycopy(uint256ToBytes32(solidityProof.getProof().Seed), 0, cursor, 192, 32);
    byte[] padding = new byte[12];
    System.arraycopy(padding, 0, cursor, 224, 12);
    System.arraycopy(solidityProof.getUWitness(), 0, cursor, 236, 20);
    System.arraycopy(longMarshal(solidityProof.getCGammaWitness()), 0, cursor, 256, 64);
    System.arraycopy(longMarshal(solidityProof.getSHashWitness()), 0, cursor, 320, 64);
    System.arraycopy(uint256ToBytes32(solidityProof.getZInv()), 0, cursor, 384, 32);

    return cursor;
  }

  /**
   * deserialize binary marshaled proof to Proof
   */
  public Proof unmarshalSolidityProof(byte[] marshaledProof) throws VRFException {
    if (marshaledProof == null || marshaledProof.length != ProofLength) {
      throw new VRFException(String.format("VRF proof is %d bytes long, should be %d: %s",
          marshaledProof.length, ProofLength, ByteArray.toHexString(marshaledProof)));
    }
    byte[] byte1 = new byte[64];
    System.arraycopy(marshaledProof, 0, byte1, 0, 64);
    ECPoint PublicKey = longUnmarshal(byte1);

    byte[] rawGamma = new byte[64];
    System.arraycopy(marshaledProof, 64, rawGamma, 0, 64);
    ECPoint Gamma = longUnmarshal(rawGamma);

    byte[] byte3 = new byte[32];
    System.arraycopy(marshaledProof, 128, byte3, 0, 32);
    BigInteger c = new BigInteger(1, byte3);

    byte[] byte4 = new byte[32];
    System.arraycopy(marshaledProof, 160, byte4, 0, 32);
    BigInteger s = new BigInteger(1, byte4);

    byte[] byte5 = new byte[32];
    System.arraycopy(marshaledProof, 192, byte5, 0, 32);
    BigInteger seed = new BigInteger(1, byte5);

    byte[] merged = new byte[32 + 64];
    System.arraycopy(vrfRandomOutputHashPrefix, 0, merged, 0, 32);
    System.arraycopy(rawGamma, 0, merged, 32, 64);
    BigInteger output = new BigInteger(1, mustHash(merged));

    Proof proof = new Proof(PublicKey, Gamma, c, s, seed, output);
    return proof;
  }

  public static void main(String[] args) {
    VRF vrf = new VRF("41a23d61356e7e92531baaf8273a47d6058fb44a4f155a101c6513412e7ffa2d");
//    vrf.testIsSquare();
//    vrf.testSquareRoot();
//    vrf.testYSquare();
//    vrf.testIsCurveXOrdinate();
//    vrf.testVerifyProof();

    //secretKey and seed must be less than secp256k1 group order
    Proof proof = vrf.generateProof(ByteArray.fromHexString(
        "91b10c5c56c46097870d96c7f5d8fddb0c1fea25f6d176e43700dd8b11af7d19"));
    System.out.println(proof.toString());

    //2
    SolidityProof solidityProof = vrf.solidityPrecalculations(proof);
    System.out.println(solidityProof.toString());

    //3
    byte[] marshaledProof;
    try {
      marshaledProof = vrf.marshalForSolidityVerifier(solidityProof);
    } catch (VRFException vrfException) {
      vrfException.printStackTrace();
      return;
    }
    System.out.println("marshaledProof:" + ByteArray.toHexString(marshaledProof));

    //4
    Proof unmarshalProof;
    try {
      unmarshalProof = vrf.unmarshalSolidityProof(marshaledProof);
    } catch (VRFException vrfException) {
      vrfException.printStackTrace();
      return;
    }
    System.out.println(unmarshalProof.toString());
  }
}
