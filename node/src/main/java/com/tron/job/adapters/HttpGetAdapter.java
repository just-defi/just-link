package com.tron.job.adapters;

import static com.tron.common.Constant.HTTP_MAX_RETRY_TIME;

import com.google.common.base.Strings;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.MalformedJsonException;
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
    String response = getByUrl();
    int retry = 1;

    if (!Strings.isNullOrEmpty(response)) {
      try {
        result.put("result", parseResponse(response));
      } catch (Exception e) {
        //Catch exception during response parsing and retry
        log.info("{} when parsing response {} from {}", e.getClass().getSimpleName(), response, url);
        while (true) {
          if (retry > HTTP_MAX_RETRY_TIME) {
            log.error("Max parse response retry reached");
            break;
          }
          try {
            response = getByUrl();
            result.put("result", parseResponse(response));
            log.info("Number {} retry for {}, parsed response = {}", retry, url, result.get("result"));
            break;
          } catch (Exception ex) {
            log.info("{} encountered during retry", ex.getClass().getSimpleName());
            retry++;
          }
        }
      }
    } else {
      result.replace("code", 1);
      result.replace("msg", "request failed, url:" + url);
      log.error("request failed, url:" + url);
    }
    log.info("{} result parsed with {} parsing retry", url, retry-1);
    return result;
  }

  private String getByUrl() {
    try {
      String response = HttpUtil.requestWithRetry(url);
      log.info("HttpGet Request: {} | Response: {}", url, response);
      return response;
    } catch (IOException e) {
      log.error("Http Request failed, err:" + e.getMessage(), e);
      return null;
    }
  }

  private double parseResponse(String response) throws Exception {
    JsonElement data = JsonParser.parseString(response);

    String[] paths = path.split("\\.");
    for (String key : paths) {
      if (data.isJsonArray()) {
        data = data.getAsJsonArray().get(Integer.parseInt(key));
      } else {
        data = data.getAsJsonObject().get(key);
      }
    }
    return data.getAsDouble();
  }
}
