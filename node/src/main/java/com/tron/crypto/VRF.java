package com.tron.crypto;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spongycastle.math.ec.ECPoint;
import org.spongycastle.util.BigIntegers;
import org.tron.common.crypto.ECKey;
import org.tron.common.crypto.Hash;
import org.tron.common.utils.ByteArray;

@Slf4j
public class VRF {

  // Secp256k1 用到的椭圆曲线：y²=x³+7
  // p = FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F
  // N = FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
  private static ECKey ecKey;
  public static final ECPoint generator = ECKey.CURVE_SPEC.getG();
  public static final BigInteger fieldSize =
      ECKey.CURVE_SPEC.getCurve().getField().getCharacteristic(); //基域上点的个数p
  public static final BigInteger groupOrder = ECKey.CURVE_SPEC.getN(); //椭圆曲线上点的个数N

  //一些预定义的标量
  public final static int HashLength = 32;
  private final BigInteger zero, one, two, three, four, seven, eulerCriterionPower, sqrtPower;
  private final byte[] hashToCurveHashPrefix; //1
  private final byte[] scalarFromCurveHashPrefix; //2
  private final byte[] vrfRandomOutputHashPrefix; //3

  //SolidityProof的长度
  private static final int ProofLength = 64 + // PublicKey
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

    //长度为 ProofLength 的 byte 数组
    public byte[] value;
  }

  /**
   * 节点的私钥初始化一个 VRF node
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

    hashToCurveHashPrefix = BytesToHash(one.toByteArray());
    scalarFromCurveHashPrefix = BytesToHash(two.toByteArray());
    vrfRandomOutputHashPrefix = BytesToHash(three.toByteArray());
  }

  /**
   * 截取 b 的后 HashLength 个byte；不足前补0；结果长度为 HashLength。BytesToHash 等价于go中的 BigToHash
   */
  public byte[] BytesToHash(byte[] b) {
    byte[] hash = new byte[HashLength];
    if (b.length > HashLength) {
      hash = Arrays.copyOfRange(b, b.length - HashLength, b.length);
    } else {
      System.arraycopy(b, 0, hash, HashLength - b.length, b.length);
    }
    return hash;
  }

  /**
   * 标量与点的线性组合
   */
  public ECPoint linearCombination(BigInteger c, ECPoint p1, BigInteger s, ECPoint p2) {
    ECPoint p11 = p1.multiply(c.mod(groupOrder)).normalize();
    ECPoint p22 = p2.multiply(s.mod(groupOrder)).normalize();
//    System.out.println(String.format("add_1_x:%s\nadd_1_y:%s\nadd_2_x:%s\nadd_2_y:%s\n",
//        ByteArray.toHexString(p11.getRawXCoord().toBigInteger().toByteArray()),
//        ByteArray.toHexString(p11.getRawYCoord().toBigInteger().toByteArray()),
//        ByteArray.toHexString(p22.getRawXCoord().toBigInteger().toByteArray()),
//        ByteArray.toHexString(p22.getRawYCoord().toBigInteger().toByteArray())
//        ));
    return p11.add(p22).normalize();
  }

  /**
   * 点表示为 byte 数组。p1已经经过normalize
   */
  public static byte[] LongMarshal(ECPoint p1) {
    byte[] x = BigIntegers.asUnsignedByteArray(32, p1.getRawXCoord().toBigInteger()); //固定长度，前面补0
    byte[] y = BigIntegers.asUnsignedByteArray(32, p1.getRawYCoord().toBigInteger()); //固定长度，前面补0
    byte[] merged = new byte[x.length + y.length];
    System.arraycopy(x, 0, merged, 0, x.length);
    System.arraycopy(y, 0, merged, x.length, y.length);
    return merged;
  }

  /**
   * 从合并的 byte 数组中反解出点
   */
  public ECPoint LongUnmarshal(byte[] m) throws VRFException {
    if (m == null || m.length != 64) {
      throw new VRFException(String.format(
          "0x%x does not represent an uncompressed secp256k1Point. Should be length 64, but is length %d",
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
   * sha3操作，结果长度为256bit，32byte。MustHash returns the keccak256 hash, or panics on failure.
   */
  private static byte[] MustHash(byte[] in) {
    return Hash.sha3(in);
  }

  /**
   * 把第1、2、3、5个点参数的 p.x||p.y 串一起，链接uWitness，sha3得到点c
   */
  public BigInteger ScalarFromCurvePoints(ECPoint hash, ECPoint pk, ECPoint gamma, byte[] uWitness,
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
    System.arraycopy(LongMarshal(hash), 0, merged, 32, 64);
    System.arraycopy(LongMarshal(pk), 0, merged, 96, 64);
    System.arraycopy(LongMarshal(gamma), 0, merged, 160, 64);
    System.arraycopy(LongMarshal(v), 0, merged, 224, 64);
    System.arraycopy(uWitness, 0, merged, 288, 20);
    byte[] mustHash = MustHash(merged);

    return new BigInteger(1, mustHash);
  }

  /**
   * 把 message 做 sha3 操作，转换为曲线上的标量域，作为x坐标
   */
  public BigInteger FieldHash(byte[] message) {
//    System.out.println("message:" + ByteArray.toHexString(message));
    byte[] hashResult = MustHash(message);
//    System.out.println("hashResult:" + ByteArray.toHexString(hashResult));
    BigInteger rv = new BigInteger(1, BytesToHash(hashResult));
//    System.out.println("FieldHash1:" + rv);

    if (rv.compareTo(fieldSize) >= 0) {
      byte[] shortRV = BytesToHash(BigIntegers.asUnsignedByteArray(rv));
      rv = new BigInteger(1, MustHash(shortRV));
    }
    return rv;
  }

  /**
   * 若slice长度小于l,左填充0为长度l；否则直接返回
   */
  public byte[] LeftPadBytes(byte[] slice, int l) {
    if (slice.length >= l) {
      return slice;
    }

    byte[] newSlice = new byte[l];
    System.arraycopy(slice, 0, newSlice, l - slice.length, slice.length);
    return newSlice;
  }

  /**
   * 把整数转化为 32 字节的 byte 数组
   */
  public byte[] uint256ToBytes32(BigInteger uint256) throws VRFException {
    if (BigIntegers.asUnsignedByteArray(uint256).length > HashLength) { //256=HashLength*8
      throw new VRFException("vrf.uint256ToBytes32: too big to marshal to uint256");
    }
    return LeftPadBytes(BigIntegers.asUnsignedByteArray(uint256), HashLength);
  }

  /**
   * 得到 y^2 = x^3 + 7
   */
  public BigInteger ySquare(BigInteger x) {
    return x.modPow(three, fieldSize).add(seven).mod(fieldSize);
  }

  /**
   * 判断一个数 x 是不是域上一个数的平方
   */
  public boolean isSquare(BigInteger x) {
    return x.modPow(eulerCriterionPower, fieldSize).compareTo(one) == 0;
  }

  /**
   * 判断 x 是否可以作为曲线上的横坐标
   */
  public boolean IsCurveXOrdinate(BigInteger x) {
    return isSquare(ySquare(x));
  }

  /**
   * SquareRoot returns a s.t. a^2=x, as long as x is a square
   */
  public BigInteger SquareRoot(BigInteger x) {
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
   * 构造一个 CURVE 上的点
   */
  public ECPoint SetCoordinates(BigInteger x, BigInteger y) throws VRFException {
    ECPoint rv = ECKey.CURVE_SPEC.getCurve().createPoint(x, y);
    if (!rv.isValid()) {
      throw new VRFException("point requested from invalid coordinates");
    }
    return rv;
  }

  /**
   * 吧hash值映射为曲线上的一个点
   */
  public ECPoint HashToCurve(ECPoint p, BigInteger seed) throws VRFException {
    if (!(p.isValid() && seed.toByteArray().length <= 256 && seed.compareTo(zero) >= 0)) {
      throw new VRFException("bad input to vrf.HashToCurve");
    }
    byte[] inputTo32Byte = uint256ToBytes32(seed);

    byte[] merged = new byte[32 + 64 + 32];
    System.arraycopy(hashToCurveHashPrefix, 0, merged, 0, 32);
    System.arraycopy(LongMarshal(p), 0, merged, 32, 64);
    System.arraycopy(inputTo32Byte, 0, merged, 96, 32);
//    System.out.println(ByteArray.toHexString(merged));
    BigInteger x = FieldHash(merged);
//    System.out.println("x_1:" + x);

    while (!IsCurveXOrdinate(x)) { // Hash recursively until x^3+7 is a square
      x = FieldHash(BytesToHash(BigIntegers.asUnsignedByteArray(x)));
    }
    BigInteger y_2 = ySquare(x);
//    System.out.println("y_2:" + y_2);
    BigInteger y = SquareRoot(y_2);
//    System.out.println("y:" + y);
    ECPoint rv = SetCoordinates(x, y);
//    System.out.println("rv_x:" +
//        ByteArray.toHexString(rv.getRawXCoord().toBigInteger().toByteArray()));
//    System.out.println("rv_y:" +
//        ByteArray.toHexString(rv.getRawYCoord().toBigInteger().toByteArray()));

    // Negate response if y odd. 如果y为奇数，取负点
    if (y.mod(two).compareTo(one) == 0) {
      rv = rv.negate();
    }
    return rv;
  }

  /**
   * 验证组合是否不相等。不等返回true，相等返回false
   */
  public boolean checkCGammaNotEqualToSHash(BigInteger c, ECPoint gamma, BigInteger s,
      ECPoint hash) {
    ECPoint p1 = gamma.multiply(c.mod(groupOrder)).normalize();
    ECPoint p2 = hash.multiply(s.mod(groupOrder)).normalize();
    return !p1.equals(p2);
  }

  /**
   * sha3(p.x||p.y)，取最低160个bit。等价于函数 EthereumAddress
   */
  public byte[] getLast160BitOfPoint(ECPoint point) {
//    System.out.println("getLast160BitOfPoint:" + ByteArray.toHexString(LongMarshal(point)));
    byte[] sha3Result = MustHash(LongMarshal(point)); //32字节
//    System.out.println("sha3Result:" + ByteArray.toHexString(sha3Result));

    byte[] cv = new byte[20];
    System.arraycopy(sha3Result, 12, cv, 0, 20);
    return cv;
  }

  /**
   * 通过公钥、用户提供的种子验证刚才生成的 proof 是否成立。
   */
  public boolean VerifyVRFProof(Proof proof) throws ErrCGammaEqualsSHash, VRFException {
    if (!proof.WellFormed()) {
      throw new VRFException("badly-formatted proof");
    }
    ECPoint h = HashToCurve(proof.PublicKey, proof.Seed).normalize();
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
    BigInteger cPrime = ScalarFromCurvePoints(h, proof.PublicKey, proof.getGamma(), uWitness,
        vPrime);
    byte[] gammaRepresent = LongMarshal(proof.getGamma());

    //随机数β的x、y坐标连在一起，加前缀
    byte[] prefixAndGamma = new byte[vrfRandomOutputHashPrefix.length + gammaRepresent.length];
    System.arraycopy(vrfRandomOutputHashPrefix, 0, prefixAndGamma, 0,
        vrfRandomOutputHashPrefix.length);
    System.arraycopy(gammaRepresent, 0, prefixAndGamma, vrfRandomOutputHashPrefix.length,
        gammaRepresent.length);
    byte[] output = MustHash(prefixAndGamma);

    // 验证是否满足 点β = 点c 且随机数 output 一致
    if (!(proof.C.compareTo(cPrime) == 0)) {
//      System.out.println("ddddddd");
      return false;
    }
    if (!(proof.Output.compareTo(new BigInteger(1, output)) == 0)) {
//      System.out.println("eeeeeeee");
      return false;
    }
    return true;
  }

  /**
   * @param secretKey 节点的私钥，相当于k，
   * @param seed 用户给的种子，相当于message，
   * @param nonce 是一个0~q之间的随机数，标量，相当于t。不是vrf的随机数，vrf输出的随机数是一个点
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
//    System.out.println("base_x1:" +
//        ByteArray.toHexString(generator.getRawXCoord().toBigInteger().toByteArray()));
//    System.out.println("base_y1:" +
//        ByteArray.toHexString(generator.getRawYCoord().toBigInteger().toByteArray()));
//
//    System.out.println("publicKey_x:" +
//        ByteArray.toHexString(publicKey.getRawXCoord().toBigInteger().toByteArray()));
//    System.out.println("publicKey_y:" +
//        ByteArray.toHexString(publicKey.getRawYCoord().toBigInteger().toByteArray()));

    ECPoint h = HashToCurve(publicKey, seed).normalize(); // 点 h = HashToCurve(Q,message)
//    System.out.println("h_x1:" +
//        ByteArray.toHexString(h.getRawXCoord().toBigInteger().toByteArray()));
//    System.out.println("h_y1:" +
//        ByteArray.toHexString(h.getRawYCoord().toBigInteger().toByteArray()));

    ECPoint gamma = h.multiply(skAsScalar).normalize(); // 点 β = [secretKey]·h
//    System.out.println("gamma_x1:" +
//        ByteArray.toHexString(gamma.getRawXCoord().toBigInteger().toByteArray()));
//    System.out.println("gamma_y1:" +
//        ByteArray.toHexString(gamma.getRawYCoord().toBigInteger().toByteArray()));

    BigInteger sm = nonce.mod(groupOrder); //t
//    System.out.println(sm);
    ECPoint u = generator.multiply(sm).normalize();//normalize操作使 z=1
//    System.out.println("u_x1:" +
//        ByteArray.toHexString(u.getRawXCoord().toBigInteger().toByteArray()));
//    System.out.println("u_y1:" +
//        ByteArray.toHexString(u.getRawYCoord().toBigInteger().toByteArray()));

    byte[] uWitness = getLast160BitOfPoint(u);
//    System.out.println("uWitness:" + ByteArray.toHexString(uWitness));
    ECPoint v = h.multiply(sm).normalize();
//    System.out.println("v_x1:" +
//        ByteArray.toHexString(v.getRawXCoord().toBigInteger().toByteArray()));
//    System.out.println("v_y1:" +
//        ByteArray.toHexString(v.getRawYCoord().toBigInteger().toByteArray()));

    //把1、2、3、5的x、y串一起，链接uWitness，得到c
    BigInteger c = ScalarFromCurvePoints(h, publicKey, gamma, uWitness, v);
//    System.out.println("c:" + c);

    //s = (nonce - c·k) mod q
//    System.out.println("c.multiply(skAsScalar):" + c.multiply(skAsScalar));
//    System.out.println(
//        "nonce.subtract(c.multiply(skAsScalar)):" + nonce.subtract(c.multiply(skAsScalar)));
//    System.out.println("groupOrder:" + groupOrder);
    BigInteger s = nonce.subtract(c.multiply(skAsScalar)).mod(groupOrder);
//    System.out.println("s:" + s);
    if (!checkCGammaNotEqualToSHash(c, gamma, s, h)) {
      System.out.println("cccccc");
      return null;
    }

    byte[] gammaRepresent = LongMarshal(gamma);
//    System.out.println("gammaRepresent:" + ByteArray.toHexString(gammaRepresent));

    //随机数β的x、y坐标连在一起，加前缀
    byte[] prefixAndGamma = new byte[vrfRandomOutputHashPrefix.length + gammaRepresent.length];
    System.arraycopy(vrfRandomOutputHashPrefix, 0, prefixAndGamma, 0,
        vrfRandomOutputHashPrefix.length);
    System.arraycopy(gammaRepresent, 0, prefixAndGamma, vrfRandomOutputHashPrefix.length,
        gammaRepresent.length);
//    System.out.println("prefixAndGamma:" + ByteArray.toHexString(prefixAndGamma));

    //输出随机数，是点β的x、y坐标的hash
    byte[] output = MustHash(prefixAndGamma);
//    System.out.println("output:" + ByteArray.toHexString(output));

    Proof rv = new Proof(
        publicKey,
        gamma,
        c,
        s,
        seed,
        new BigInteger(1, output));

//    System.out.println("\nVerifyVRFProof...");
    if (!VerifyVRFProof(rv)) {
      throw new VRFException("constructed invalid proof");
    }
    return rv;
  }

  /**
   * 生成一个小于 n 的随机大正数
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
   * @param seed HashLength长度的byte数组
   */
  public Proof GenerateProof(byte[] seed) {
    Proof proof = null;
    while (true) {
      BigInteger nonce = getRandomBigInteger();
      try {
        proof = generateProofWithNonce(ecKey.getPrivKey(), new BigInteger(1, seed), nonce);
      } catch (ErrCGammaEqualsSHash errCGammaEqualsSHash) {
//        System.out.println("aaaaaa");
        log.error("", errCGammaEqualsSHash);
        continue;
      } catch (VRFException vrfException) {
//        System.out.println("bbbbbb");
        vrfException.printStackTrace();
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
    assert SquareRoot(four).compareTo(two) == 0;
  }

  public void testYSquare() {
    assert two.multiply(two).multiply(two).compareTo(ySquare(two)) == 0;
  }

  public void testIsCurveXOrdinate() {
    assert IsCurveXOrdinate(one) == true;
    assert IsCurveXOrdinate(BigInteger.valueOf(5)) == true;
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
      boolean valid = VerifyVRFProof(proof);
      assert valid == false;
    } catch (ErrCGammaEqualsSHash errCGammaEqualsSHash) {
      errCGammaEqualsSHash.printStackTrace();
    } catch (VRFException vrfException) {
      vrfException.printStackTrace();
    }
  }

  /**
   * 根据 Proof 预计算生成一个 SolidityProof
   * returns the precomputed values needed by the solidity verifier, or an error on failure.
   */
  public SolidityProof SolidityPrecalculations(Proof proof) {
    ECPoint uPrime = linearCombination(proof.C, proof.PublicKey, proof.S, generator);
    byte[] uWitness = getLast160BitOfPoint(uPrime); //1
    ECPoint CGammaWitness = proof.Gamma.multiply(proof.C.mod(groupOrder)).normalize(); //2

    ECPoint hash;
    try {
      hash = HashToCurve(proof.PublicKey, proof.Seed);
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
   * 把 SolidityProof 序列化为二进制的 marshaled proof
   */
  public byte[] MarshalForSolidityVerifier(SolidityProof solidityProof)
      throws VRFException {
    byte[] cursor = new byte[ProofLength];
    System.arraycopy(LongMarshal(solidityProof.getProof().PublicKey), 0, cursor, 0, 64);
    System.arraycopy(LongMarshal(solidityProof.getProof().getGamma()), 0, cursor, 64, 64);
    System.arraycopy(uint256ToBytes32(solidityProof.getProof().C), 0, cursor, 128, 32);
    System.arraycopy(uint256ToBytes32(solidityProof.getProof().S), 0, cursor, 160, 32);
    System.arraycopy(uint256ToBytes32(solidityProof.getProof().Seed), 0, cursor, 192, 32);
    byte[] padding = new byte[12];
    System.arraycopy(padding, 0, cursor, 224, 12);
    System.arraycopy(solidityProof.getUWitness(), 0, cursor, 236, 20);
    System.arraycopy(LongMarshal(solidityProof.getCGammaWitness()), 0, cursor, 256, 64);
    System.arraycopy(LongMarshal(solidityProof.getSHashWitness()), 0, cursor, 320, 64);
    System.arraycopy(uint256ToBytes32(solidityProof.getZInv()), 0, cursor, 384, 32);

    return cursor;
  }

  /**
   * 把二进制的 marshaled proof 反序列化为 Proof
   */
  public Proof UnmarshalSolidityProof(byte[] marshaledProof) throws VRFException {
    if (marshaledProof == null || marshaledProof.length != ProofLength) {
      throw new VRFException(String.format("VRF proof is %d bytes long, should be %d: %s",
          marshaledProof.length, ProofLength, ByteArray.toHexString(marshaledProof)));
    }
    byte[] byte1 = new byte[64];
    System.arraycopy(marshaledProof, 0, byte1, 0, 64);
    ECPoint PublicKey = LongUnmarshal(byte1);

    byte[] rawGamma = new byte[64];
    System.arraycopy(marshaledProof, 64, rawGamma, 0, 64);
    ECPoint Gamma = LongUnmarshal(rawGamma);

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
    BigInteger output = new BigInteger(1, MustHash(merged));

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

    //seed不能超过uint256范围，即32字节
    Proof proof = vrf.GenerateProof(ByteArray.fromHexString(
        "91b10c5c56c46097870d96c7f5d8fddb0c1fea25f6d176e43700dd8b11af7d19"));
    System.out.println(proof.toString());

    //2
    SolidityProof solidityProof = vrf.SolidityPrecalculations(proof);
    System.out.println(solidityProof.toString());

    //3
    byte[] marshaledProof;
    try {
      marshaledProof = vrf.MarshalForSolidityVerifier(solidityProof);
    } catch (VRFException vrfException) {
      vrfException.printStackTrace();
      return;
    }
    System.out.println("marshaledProof:" + ByteArray.toHexString(marshaledProof));

    //4
    Proof unmarshalProof;
    try {
      unmarshalProof = vrf.UnmarshalSolidityProof(marshaledProof);
    } catch (VRFException vrfException) {
      vrfException.printStackTrace();
      return;
    }
    System.out.println(unmarshalProof.toString());
  }
}
