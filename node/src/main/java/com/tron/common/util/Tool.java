package com.tron.common.util;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.tron.client.message.BroadCastResponse;
import com.tron.client.message.Transaction;
import com.tron.client.message.TriggerResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.spongycastle.util.encoders.Hex;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.JsonUtil;
import org.tron.common.utils.Sha256Hash;
import org.tron.common.utils.StringUtil;
import org.tron.protos.Protocol;

public class Tool {

  public static String convertHexToTronAddr(String addr) {
    if (addr.startsWith("0x")) {
      addr = addr.replaceFirst("0x", "41");
    }
    return StringUtil.encode58Check(ByteArray.fromHexString(addr));
  }

  public static BroadCastResponse triggerContract(ECKey key, Map<String, Object> params, String api)
      throws IOException {
    HttpResponse response = HttpUtil.post("https", api, "/wallet/triggersmartcontract", params);
    HttpEntity responseEntity = response.getEntity();
    String responsrStr = EntityUtils.toString(responseEntity);
    TriggerResponse triggerResponse = JsonUtil.json2Obj(responsrStr, TriggerResponse.class);
    //
    return broadcastHex(api, signTransaction(triggerResponse.getTransaction(), key));
  }

  public static org.tron.protos.Protocol.Transaction signTransaction(Transaction transaction,
      ECKey key) throws InvalidProtocolBufferException {
    String rawDataHex = transaction.getRawDataHex();
    Protocol.Transaction.raw raw = Protocol.Transaction.raw
        .parseFrom(ByteArray.fromHexString(rawDataHex));
    byte[] hash = Sha256Hash.hash(true, raw.toByteArray());
    ECKey.ECDSASignature signature = key.sign(hash);
    ByteString bsSign = ByteString.copyFrom(signature.toByteArray());
    return Protocol.Transaction.newBuilder().setRawData(raw).addSignature(bsSign).build();
  }

  public static BroadCastResponse broadcastHex(String api,
      org.tron.protos.Protocol.Transaction transaction) throws IOException {
    Map<String, Object> params = new HashMap<>();
    params.put("transaction", Hex.toHexString(transaction.toByteArray()));
    HttpResponse response = HttpUtil.post("https", api, "/wallet/broadcasthex", params);
    return JsonUtil.json2Obj(EntityUtils.toString(response.getEntity()), BroadCastResponse.class);
  }

}
