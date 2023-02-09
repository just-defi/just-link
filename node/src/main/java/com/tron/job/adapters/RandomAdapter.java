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
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.encoders.Hex;

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
import java.util.HashMap;


@Slf4j
public class RandomAdapter extends BaseAdapter {
  @Getter
  private String strPublicKey;

  final BigInteger groupOrder = ECKey.CURVE_SPEC.getN();


  public RandomAdapter(String _strPublicKey) {
    strPublicKey = _strPublicKey.replaceFirst("0x", "");
  }

  @Override
  public String taskType() {
    return Constant.TASK_TYPE_RANDOM;
  }

  @Override
  public R perform(R input) {
    R result = new R();
    try {
      VrfEventRequest event = JsonUtil.fromJson((String)input.get("params"), VrfEventRequest.class);
      String coordinatorAddress = event.getContractAddr();

      // 1. checkFulfillment
      boolean shouldFulfill = checkFulfillment(coordinatorAddress, event.getRequestId());
      if (!shouldFulfill) {
        log.error("randomness request already fulfilled");
        throw new RuntimeException("randomness request already fulfilled");
      }

      // 2. getInputs and checkKeyHash
      String inputKeyHash = event.getKeyHash();
      ECKey ecKey = ECKey.fromPublicOnly(ByteArray.fromHexString(strPublicKey));
      ECPoint taskPublicKey = ecKey.getPubKeyPoint();
      if (!taskPublicKey.isValid()) {
        log.error("invalid public key from task key: {}", strPublicKey);
        throw new RuntimeException("invalid public key from task key: " + strPublicKey);
      }
      String taskKeyHash = ByteArray.toHexString(VRF.mustHash(VRF.longMarshal(taskPublicKey)));
      if (Strings.isNullOrEmpty(inputKeyHash) || !inputKeyHash.equals(taskKeyHash)) {
        log.error("this task's keyHas:{} does not match the input hash:{}", taskKeyHash, inputKeyHash);
        throw new RuntimeException("this task's keyHas:" + taskKeyHash + " does not match the input hash:" + inputKeyHash);
      }
      String preSeed = event.getSeed();
      long blockNum = event.getBlockNum();
      String blockHash = event.getBlockHash();
      // ChainLink save the public key & private both in memory and db.
      HashMap<String, String> vrkKeyMap = VrfKeyStore.getVrfKeyMap();
      String privateKey = vrkKeyMap.get(strPublicKey);
      if(Strings.isNullOrEmpty(privateKey)) {
        log.error("cannot find the private key for:{}", strPublicKey);
        throw new RuntimeException("cannot find the private key for " + strPublicKey);
      }

      // 3. generateProof
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
  private boolean checkFulfillment(String contractAddr, String requestId) throws Exception {
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
    String response = null;
    int i = 0;
    while (i <= HTTP_MAX_RETRY_TIME && Strings.isNullOrEmpty(response)) {
      try {
        i++;
        response = HttpUtil.post("https", FULLNODE_HOST, TRIGGET_CONSTANT_CONTRACT, params);
      } catch (Exception ex) {
        log.error("checkFulfillment failed:" + ex.getMessage() + ", num:" + i);
        ex.printStackTrace();
      }
    }
    ObjectMapper mapper = new ObjectMapper();
    assert response != null;
    Map<String, Object> result = mapper.readValue(response, Map.class);
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
    // 1. FinalSeed
    byte[] finalSeed = vrf.mustHash(ByteUtil.merge(ByteArray.fromHexString(preSeed), ByteArray.fromHexString(blockHash)));
    // 2. GenerateProof
    Proof proof = vrf.generateProof(finalSeed);
    // 3. Precalculation
    SolidityProof solidityProof = vrf.solidityPrecalculations(proof);

    // Overwrite seed input to the VRF proof generator with the seed the
    // VRFCoordinator originally requested, so that it can identify the request
    // corresponding to this response, and compute the final seed itself using the
    // blockhash it infers from the block number.
    // 4.1 Marshal
    byte[] marshaledProof;
    try {
      marshaledProof = vrf.marshalForSolidityVerifier(solidityProof);
    } catch (VRFException vrfException) {
      vrfException.printStackTrace();
      return null;
    }

    //4.2 add prefix and append blocknum, and replace finalSeed with preSeed
    byte[] beforeSeed = new byte[192];
    byte[] afterSeed = new byte[192];
    byte[] preSeedBytes = ByteArray.fromHexString(preSeed);

    System.arraycopy(marshaledProof, 0, beforeSeed, 0, 192);
    System.arraycopy(marshaledProof, 224, afterSeed, 0, 192);
    byte[] solidityProofResponse = ByteUtil.merge(
            beforeSeed, preSeedBytes, afterSeed, ByteUtil.longTo32Bytes(blockNum));

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
