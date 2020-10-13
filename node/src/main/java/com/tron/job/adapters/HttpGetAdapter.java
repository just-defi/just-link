package com.tron.job.adapters;

import static com.tron.common.Constant.HTTP_MAX_RETRY_TIME;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.tron.common.Constant;
import com.tron.common.util.HttpUtil;
import com.tron.web.common.util.R;
import java.io.IOException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;

@Slf4j
public class HttpGetAdapter extends BaseAdapter {

  @Getter
  private String url;
  @Getter
  private String path;

  public HttpGetAdapter(String urlStr, String pathStr) {
    url = urlStr;
    path = pathStr;
  }

  @Override
  public String taskType() {
    return Constant.TASK_TYPE_HTTP_GET;
  }

  @Override
  public R perform(R input) {
    R result  = new R();
    HttpResponse response = requestWithRetry(url);
    HttpEntity responseEntity = response.getEntity();

    try {
      JsonElement data = JsonParser.parseString(EntityUtils.toString(responseEntity));

      String[] paths = path.split("\\.");
      for (String key : paths) {
        if (data.isJsonArray()) {
          data = data.getAsJsonArray().get(Integer.parseInt(key));
        } else {
          data = data.getAsJsonObject().get(key);
        }
      }
      double value = data.getAsDouble();
      result.put("result", value);
    } catch (IOException e) {
      result.replace("code", 1);
      result.replace("msg", "parse response failed, url:" + url);
      log.info("parse response failed");
    }

    return result;
  }

  private HttpResponse requestWithRetry(String url) {
    try {
      HttpResponse response = HttpUtil.getByUri(url);
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
