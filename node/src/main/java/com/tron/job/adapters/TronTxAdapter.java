package com.tron.job.adapters;

import com.googlecode.cqengine.query.simple.In;
import com.tron.client.EventRequest;
import com.tron.client.FluxAggregator;
import com.tron.client.VrfEventRequest;
import com.tron.client.FulfillRequest;
import com.tron.client.OracleClient;
import com.tron.common.Constant;
import com.tron.web.common.util.JsonUtil;
import com.tron.web.common.util.R;
import com.tron.web.entity.TronTx;
import java.util.Map;
import lombok.Getter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;

import java.math.BigInteger;

@Slf4j
public class TronTxAdapter extends BaseAdapter {

  @Getter
  private Long ver;
  @Getter
  private String linkType;

  public TronTxAdapter(Long version, String _linkType) {
    ver = version;
    linkType = _linkType;
  }

  @Override
  public String taskType() {
    return "trontx";
  }

  @Override
  public R perform(R input) {
    // send tx
    try {
      int iLinkType = 0; // oracle:0, vrf:1,
      try {
        if ("TronVRF".equals(linkType)){
          iLinkType = 1;
        }
      } catch (Exception ex){
        log.error("no type info for trontx :" + ex.getMessage());
      }
      switch (iLinkType) {
        case 0:
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

            tx = FluxAggregator.submit(addr, roundId, (long)input.get("result"));
          }

          tx.setValue((long)input.get("result"));
          tx.setSentAt(System.currentTimeMillis());
          tx.setTaskRunId((String)input.get("taskRunId"));
          //tx.setConfirmed(Constant.TronTxInProgress); // do not resend for oracle request
          log.info("tx id : " + tx.getSurrogateId());

          return R.ok().put("result", tx.getSurrogateId()).put("tx", tx);
        case 1:
          String proof = (String)input.get("result");
          VrfEventRequest vrfEvent = JsonUtil.fromJson((String)input.get("params"), VrfEventRequest.class);
          FulfillRequest vrfFulfillRequest = new FulfillRequest(
              vrfEvent.getContractAddr(),
              vrfEvent.getRequestId(),
              new BigInteger("0"),
              "",
              "",
              0,
              proof);
          TronTx vrfTx = OracleClient.vrfFulfil(vrfFulfillRequest);
          vrfTx.setValue(0L);
          vrfTx.setSentAt(System.currentTimeMillis());
          vrfTx.setTaskRunId((String)input.get("taskRunId"));
          vrfTx.setConfirmed(Constant.TronTxInProgress);
          log.info("vrfTx id : " + vrfTx.getSurrogateId());

          return R.ok().put("result", vrfTx.getSurrogateId()).put("tx", vrfTx);
        default:
          log.error("unsupported linkType neither oracle nor vrf: " + linkType);
          return R.error(1, "fulfillRequest failed");
      }

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
