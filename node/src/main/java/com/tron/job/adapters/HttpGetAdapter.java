package com.tron.job.adapters;

import static com.tron.common.Constant.HTTP_MAX_RETRY_TIME;

import com.google.common.base.Strings;
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
    String response = null;
    try {
      response = HttpUtil.requestWithRetry(url);
    } catch (IOException e) {
      log.info("parse response failed, err:" + e.getMessage());
    }

    if (!Strings.isNullOrEmpty(response)) {
      try {
        JsonElement data = JsonParser.parseString(response);

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
      } catch (Exception e) {
        result.replace("code", 1);
        result.replace("msg", "parse response failed, url:" + url);
        log.info("parse response failed, url:" + url);
      }
    } else {
      result.replace("code", 1);
      result.replace("msg", "request failed, url:" + url);
      log.error("request failed, url:" + url);
    }

    return result;
  }
}
