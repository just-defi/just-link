package com.tron.job;

import com.tron.job.adapters.ConvertUsdAdapter;
import com.tron.job.adapters.HttpGetAdapter;
import com.tron.web.common.util.R;
import org.junit.Test;

public class HttpGetAdapterTest {
  @Test
  public void requestTest() {
    String url = "https://poloniex.com/public?command=returnTicker";
    HttpGetAdapter httpGetAdapter = new HttpGetAdapter(url, "USDT_TRX.last");
    R input = new R();
    R output = httpGetAdapter.perform(input);
    System.out.println(output);
  }
}
