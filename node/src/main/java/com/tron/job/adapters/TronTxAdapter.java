package com.tron.job.adapters;

import com.googlecode.cqengine.query.simple.In;
import com.tron.client.EventRequest;
import com.tron.client.FulfillRequest;
import com.tron.client.OracleClient;
import com.tron.web.common.util.JsonUtil;
import com.tron.web.common.util.R;
import com.tron.web.entity.TronTx;
import java.util.Map;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TronTxAdapter extends BaseAdapter {

  @Getter
  private Long ver;

  public TronTxAdapter(Long version) {
    ver = version;
  }

  @Override
  public String taskType() {
    return "trontx";
  }

  @Override
  public R perform(R input) {
    // send tx
    try {
      TronTx tx;
      if (ver == null || ver == 1) {
        EventRequest event = JsonUtil.fromJson((String)input.get("params"), EventRequest.class);
        FulfillRequest fulfillRequest = new FulfillRequest(
            event.getContractAddr(),
            event.getRequestId(),
            event.getPayment(),
            event.getCallbackAddr(),
            event.getCallbackFunctionId(),
            event.getCancelExpiration(),
            codecData((long)input.get("result")));
        //Long.toString((long)input.get("result")));
        //(Long) input.get("result"));

        tx = OracleClient.fulfil(fulfillRequest);
      } else {
        Map<String, Object> params = JsonUtil.json2Map((String)input.get("params"));
        long roundId = Long.parseLong(params.get("roundId").toString());
        String addr = String.valueOf(params.get("address"));

        tx = OracleClient.submit(addr, roundId, (long)input.get("result"));
      }

      tx.setValue((long)input.get("result"));
      tx.setSentAt(1L);
      log.info("tx id : " + tx.getSurrogateId());

      return R.ok().put("result", tx.getSurrogateId()).put("tx", tx);
    } catch (Exception e) {
      log.error("fulfil failed :" + e.getMessage());
      return R.error(1, "fulfillRequest failed");
    }

  }

  private String codecData(long data) {
    String base = "0000000000000000000000000000000000000000000000000000000000000000";
    String dataHexStr = Long.toHexString(data);
    int sub = base.length()-dataHexStr.length();
    if (sub < 0) {
      log.error("data is too large");
      return "";
    }
    return base.substring(0, sub) + dataHexStr;
  }
}
