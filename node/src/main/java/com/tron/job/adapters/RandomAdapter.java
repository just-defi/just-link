package com.tron.job.adapters;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.tron.client.EventRequest;
import com.tron.client.VrfEventRequest;
import com.tron.common.Constant;
import com.tron.common.util.AbiUtil;
import com.tron.common.util.HttpUtil;
import com.tron.crypto.Proof;
import com.tron.crypto.SolidityProof;
import com.tron.crypto.VRF;
import com.tron.crypto.VRFException;
import com.tron.keystore.VrfKeyStore;
import com.tron.web.common.util.JsonUtil;
import com.tron.web.common.util.R;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.spongycastle.math.ec.ECPoint;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.tron.common.Constant.*;
import org.tron.common.crypto.Hash;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.ByteUtil;
import org.tron.keystore.Wallet;

@Slf4j
public class RandomAdapter extends BaseAdapter {
  @Getter
  private String publicKey;

  final BigInteger groupOrder = ECKey.CURVE_SPEC.getN();


  public RandomAdapter(String publicKey) {
    publicKey = publicKey;
  }

  @Override
  public String taskType() {
    return Constant.TASK_TYPE_RANDOM;
  }

  @Override
  public R perform(R input) {
    R result = new R();
    try {
      //result.put("result", Math.round((double)input.get("result")));
      System.out.println("zyd random");
      VrfEventRequest event = JsonUtil.fromJson((String)input.get("params"), VrfEventRequest.class);
      String coordinatorAddress = event.getContractAddr();
      boolean shouldFulfill = checkFulfillment(coordinatorAddress, event.getRequestId());
      if (!shouldFulfill) {
        log.error("randomness request already fulfilled");
      }
      String publicKey = event.getKeyHash();
      String preSeed = event.getSeed();
      long blockNum = event.getBlockNum();
      String blockHash = event.getBlockHash();

      // ChainLink save the public key & private both in memory and db.
      // TODO
      String privateKey = VrfKeyStore.getPriKey();

      byte[] responseProof = GenerateProofResponse(privateKey, preSeed, blockNum, blockHash);

      if (responseProof == null) {
        throw new RuntimeException("generate vrf proof error!");
      }
      result.put("result", ByteArray.toHexString(responseProof));

    } catch (Exception e) {
      result.replace("code", 1);
      result.replace("msg", "generate VRF failed");
      log.warn("generate VRF failed, error msg: {}", e.getMessage());
    }

    return result;
  }

  // checkFulfillment checks to see if the randomness request has already been fulfilled or not
  private boolean checkFulfillment(String contractAddr, String requestId) throws IOException {
    if (Strings.isNullOrEmpty(contractAddr)) {
      return true;
    }

    List<Object> parameters = Arrays.asList(requestId);
    String param = com.tron.common.util.AbiUtil.parseParameters("callbacks(bytes32)", parameters);
    Map<String, Object> params = Maps.newHashMap();
    params.put("owner_address", READONLY_ACCOUNT);
    params.put("contract_address", contractAddr);
    params.put("function_selector", "callbacks(bytes32)");
    params.put("parameter", param);
    params.put("visible", true);
    HttpResponse response = HttpUtil.post("https", FULLNODE_HOST, TRIGGET_CONSTANT_CONTRACT, params);
    ObjectMapper mapper = new ObjectMapper();
    assert response != null;
    Map<String, Object> result = mapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
    boolean flag = Optional.ofNullable((List<String>)result.get("constant_result"))
            .map(constantResult -> constantResult.get(0))
            .map(str -> str.substring(128)) //seedAndBlockNum is the third element in the `Callback` struct.
            .map(Hex::decode)
            .map(Hex::toHexString)
            .map(str -> new BigInteger(str, 16))
            .map(value -> (value != null && !value.equals(BigInteger.ZERO)))
            .orElseThrow(() -> new IllegalArgumentException("can not get the callbacks, contract:" + contractAddr));

    return flag;
  }

  // GenerateProof is marshaled randomness proof given k and VRF input seed
  // computed from the SeedData
  //
  // Key must have already been unlocked in ks, as constructing the VRF proof
  // requires the secret key.
  private byte[] GenerateProof(String priKey, String preSeed, long blockNum, String blockHash) {
    return MarshaledProof(priKey, preSeed, blockNum, blockHash);
  }

  // MarshaledProof is a VRF proof of randomness using i.Key and seed, in the form
  // required by VRFCoordinator.sol's fulfillRandomnessRequest
  private byte[] MarshaledProof(String priKey, String preSeed, long blockNum, String blockHash) {
    return GenerateProofResponse(priKey, preSeed, blockNum, blockHash);
  }

  // GenerateProofResponse returns the marshaled proof of the VRF output given the
  // secretKey and the seed computed from the s.PreSeed and the s.BlockHash
  private byte[] GenerateProofResponse(String priKey, String preSeed, long blockNum, String blockHash) {
    VRF vrf = new VRF(priKey);
    byte[] finalSeed = vrf.mustHash(ByteUtil.merge(ByteArray.fromHexString(preSeed), ByteArray.fromHexString(blockHash)));
    Proof proof = vrf.generateProof(finalSeed);
    System.out.println("preSeed:" + preSeed + ", finalSeed:" + ByteArray.toHexString(finalSeed) + ", zyd 1:" + proof.toString());

    //2
    SolidityProof solidityProof = vrf.solidityPrecalculations(proof);
    System.out.println("zyd 3:" + solidityProof.toString());

    // Overwrite seed input to the VRF proof generator with the seed the
    // VRFCoordinator originally requested, so that it can identify the request
    // corresponding to this response, and compute the final seed itself using the
    // blockhash it infers from the block number.

    //3
    byte[] marshaledProof;
    try {
      marshaledProof = vrf.marshalForSolidityVerifier(solidityProof);
    } catch (VRFException vrfException) {
      vrfException.printStackTrace();
      return null;
    }
    System.out.println("marshaledProof 2:" + ByteArray.toHexString(marshaledProof));

    //4 add prefix and append blocknum, and replace finalSeed with preSeed
    byte[] beforeSeed = new byte[192];
    byte[] afterSeed = new byte[192];
    byte[] preSeedBytes = ByteArray.fromHexString(preSeed);

    System.arraycopy(marshaledProof, 0, beforeSeed, 0, 192);
    System.arraycopy(marshaledProof, 224, afterSeed, 0, 192);
    byte[] solidityProofResponse = ByteUtil.merge(
            beforeSeed, preSeedBytes, afterSeed, ByteUtil.longTo32Bytes(blockNum));

    System.out.println("zyd 4:" + ByteArray.toHexString(solidityProofResponse));

    if (solidityProofResponse.length != vrf.ProofLength+32) {
      return null;
    }

    return solidityProofResponse;
  }

  // GenerateProof returns gamma, plus proof that gamma was constructed from seed
  // as mandated from the given secretKey, with public key secretKey*Generator
  //
  // secretKey and seed must be less than secp256k1 group order. (Without this
  // constraint on the seed, the samples and the possible public keys would
  // deviate very slightly from uniform distribution.)
  private String GenerateProof(String priKey, byte[] seed) {
    //group order: "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141"

    BigInteger nonce = new BigInteger(Hex.toHexString(Wallet.generateRandomBytes(32)), 16).mod(groupOrder);
    String proof = generateProofWithNonce(priKey, seed, nonce);
    return "";
  }

  // generateProofWithNonce allows external nonce generation for testing purposes
  //
  // As with signatures, using nonces which are in any way predictable to an
  // adversary will leak your secret key! Most people should use GenerateProof
  // instead.
  private String generateProofWithNonce(String priKey, byte[] seed, BigInteger nonce) {
    BigInteger priVal = new BigInteger(priKey, 16);
    BigInteger seedVal = new BigInteger(Hex.toHexString(seed),16);
    if(!(priVal.compareTo(groupOrder) == -1 && seedVal.compareTo(groupOrder) == -1)){
      log.error("badly-formatted key or seed");
    }
    ECPoint pubKey = ECKey.CURVE_SPEC.getG().multiply(priVal);
    pubKey.isValid();
    return "";
  }
}
