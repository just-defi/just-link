package com.tron.job.adapters;

import static com.tron.common.Constant.HTTP_MAX_RETRY_TIME;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tron.common.Constant;
import com.tron.common.util.HttpUtil;
import com.tron.web.common.util.R;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;

@Slf4j
public class ConvertUsdAdapter extends BaseAdapter {

  @Override
  public String taskType() {
    return Constant.TASK_TYPE_CONVERT_USD;
  }

  @Override
  public R perform(R input) {
    R result  = new R();
    String url = "https://api-pub.bitfinex.com/v2/ticker/tUSTUSD";
    HttpResponse response = requestWithRetry(url);

    if (response != null) {
      HttpEntity responseEntity = response.getEntity();

      try {
        JsonElement data = JsonParser.parseString(EntityUtils.toString(responseEntity));

        double value = 1;
        if (data.isJsonArray() && data.getAsJsonArray().size() > 6) {
          data = data.getAsJsonArray().get(6);
          value = data.getAsDouble();
        }

        value = (long)input.get("result") * value;

        result.put("result", Math.round(value));
      } catch (Exception e) {
        result.put("result", input.get("result"));
        log.info("parse response failed, url:" + url);
      }
    } else {
      result.put("result", input.get("result"));
    }

    return result;
  }

  private HttpResponse requestWithRetry(String url) {
    try {
      HttpResponse response = HttpUtil.getByUri(url);
      if (response == null) {
        return null;
      }

      int status = response.getStatusLine().getStatusCode();
      if (status == HttpStatus.SC_SERVICE_UNAVAILABLE) {
        int retry = 1;
        while(true) {
          if(retry > HTTP_MAX_RETRY_TIME) {
            log.warn("request failed, url:" + url);
            break;
          }
          try {
            Thread.sleep(100 * retry);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          response = HttpUtil.getByUri(url);
          if (response == null) {
            break;
          }
          retry++;
          status = response.getStatusLine().getStatusCode();
          if (status != HttpStatus.SC_SERVICE_UNAVAILABLE) {
            break;
          }
        }
      }

      return response;
    } catch (Exception e) {
      log.error("request failed, url : " + url);
    }

    return null;
  }
}
