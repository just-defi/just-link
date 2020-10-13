package com.tron.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

public class HttpUtil {

  private static HttpClient client = HttpClients.createDefault();

  public static HttpResponse get(String scheme, String host, String path, Map<String, String> paramMap) {
    List<NameValuePair> params = new ArrayList<>();
    paramMap.keySet().forEach(
            k -> {
              params.add(new BasicNameValuePair(k, paramMap.get(k)));
            }
    );
    URI uri = null;
    try {
      uri = new URIBuilder().setScheme(scheme).setHost(host).setPath(path)
              .setParameters(params)
              .build();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }

    HttpGet httpGet = new HttpGet(uri);
    try {
      return client.execute(httpGet);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public static HttpResponse post(String scheme, String host, String path, Map<String, Object> paramMap) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    String jsonString = mapper.writeValueAsString(paramMap);
    StringEntity entity = new StringEntity(jsonString, "UTF-8");
    URI uri = null;
    try {
      uri = new URIBuilder()
              .setScheme(scheme)
              .setHost(host)
              .setPath(path)
              .build();
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    HttpPost httpPost = new HttpPost(uri);
    httpPost.setEntity(entity);
    httpPost.setHeader("Content-Type", "application/json;charset=utf8");
    try {
      return client.execute(httpPost);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public static HttpResponse getByUri(String uriStr) {
    URI uri = null;
    try {
      uri = new URI(uriStr);
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }

    HttpGet httpGet = new HttpGet(uri);
    try {
      return client.execute(httpGet);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }
}
