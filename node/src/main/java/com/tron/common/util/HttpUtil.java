package com.tron.common.util;

import static com.tron.common.Constant.HTTP_MAX_RETRY_TIME;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tron.common.Config;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

@Slf4j
public class HttpUtil {

  private static RequestConfig requestConfig = RequestConfig.custom()
    .setSocketTimeout(15000).setConnectTimeout(15000).build();
  private static CloseableHttpClient client =
    HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();

  public static String get(String scheme, String host, String path, Map<String, String> paramMap)
    throws IOException {
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
      return null;
    }

    CloseableHttpClient client =
      HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
    HttpGet httpGet = new HttpGet(uri);
    httpGet.setHeader("TRON_PRO_API_KEY", Config.getApiKey());
    try (CloseableHttpResponse response = client.execute(httpGet)) {
      return EntityUtils.toString(response.getEntity());
    } catch (IOException e) {
      e.printStackTrace();
      throw e;
    } finally {
      client.close();
    }
  }

  public static String post(String scheme, String host, String path, Map<String, Object> paramMap)
    throws IOException, URISyntaxException {
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
      throw e;
    }

    CloseableHttpClient client =
      HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
    HttpPost httpPost = new HttpPost(uri);
    httpPost.setEntity(entity);
    httpPost.setHeader("Content-Type", "application/json;charset=utf8");
    httpPost.setHeader("TRON_PRO_API_KEY", Config.getApiKey());
    try (CloseableHttpResponse response = client.execute(httpPost)) {
      return EntityUtils.toString(response.getEntity());
    } catch (IOException e) {
      e.printStackTrace();
      throw e;
    } finally {
      client.close();
    }
  }

  public static String getByUri(String uriStr) throws IOException {
    URI uri = null;
    try {
      uri = new URI(uriStr);
    } catch (URISyntaxException e) {
      e.printStackTrace();
      return null;
    }

//    CloseableHttpClient client =
//            HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
    HttpGet httpGet = new HttpGet(uri);
    httpGet.setHeader("TRON_PRO_API_KEY", Config.getApiKey());
    try (CloseableHttpResponse response = client.execute(httpGet)) {
      return EntityUtils.toString(response.getEntity());
    } catch (IOException e) {
      e.printStackTrace();
      throw e;
    } finally {
      //client.close();
    }
  }

  public static String requestWithRetry(String url) throws IOException {
    CloseableHttpClient client =
      HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).build();
    try {
      return requestHandleTimeout(client, url);
    } catch (Exception e) {
      log.error("Http Exception: {}", e.getMessage(), e);
      return null;
    } finally {
      client.close();
    }
  }

  private static String requestHandleTimeout(CloseableHttpClient client, String url) throws Exception {
    URI uri = new URI(url);
    HttpGet httpGet = new HttpGet(uri);
    httpGet.setHeader("TRON_PRO_API_KEY", Config.getApiKey());
    String response = null;
    int retry = 0;

    try {
      response = serverUnavailableRetry(client, httpGet, url);
    } catch (SocketTimeoutException | ConnectTimeoutException ex) {
      //Catch read time out and retry
      log.info("{} entering retry {}", ex.getClass().getSimpleName(), url);
      retry++;
      while (true) {
        if (retry > HTTP_MAX_RETRY_TIME) {
          log.error("Max http retry reached");
          break;
        }
        try {
          response = serverUnavailableRetry(client, httpGet, url);
          log.info("Number {} retry for {}, response = {}", retry, url, response);
          break;
        } catch (SocketTimeoutException | ConnectTimeoutException exception) {
          log.error("{} encountered during retry", exception.getClass().getSimpleName());
          retry++;
        }
      }
    }
    log.info("{} returned result with {} socket timeout retry", url, retry);
    return response;
  }

  private static String serverUnavailableRetry(CloseableHttpClient client, HttpGet httpGet, String url) throws Exception {
    HttpResponse response = client.execute(httpGet);
    if (response == null) {
      log.error("Http response is null");
      return null;
    }
    int status = response.getStatusLine().getStatusCode();
    log.info("Call Url={} , status={}, raw response={}", url, status, response);
    if (status == HttpStatus.SC_SERVICE_UNAVAILABLE) {
      int retry = 1;
      while (true) {
        if (retry > HTTP_MAX_RETRY_TIME) {
          break;
        }
        try {
          Thread.sleep(100 * retry);
        } catch (InterruptedException e) {
          log.error("InterruptedException: {}", e.getMessage(), e);
        }
        response = client.execute(httpGet);
        if (response == null) {
          log.error("Http response is null");
          break;
        }
        retry++;
        status = response.getStatusLine().getStatusCode();
        if (status != HttpStatus.SC_SERVICE_UNAVAILABLE) {
          break;
        }
      }
    }
    return EntityUtils.toString(response.getEntity());
  }

}
