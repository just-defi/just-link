package com.tron.job.adapter;

import com.tron.job.adapters.JustSwapAdapter;
import com.tron.web.common.util.R;
import org.junit.Test;

public class JustSwapAdapterTest {
  @Test
  public void testJustSwapPrice() {
    JustSwapAdapter justSwapAdapter = new JustSwapAdapter("usdt-trx", null, null);
    R input = new R();
    R output = justSwapAdapter.perform(input);
    System.out.println(output);
  }

  @Test
  public void testJustSwapPriceByAddr() {
    JustSwapAdapter justSwapAdapter = new JustSwapAdapter("", "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t");
    R input = new R();
    R output = justSwapAdapter.perform(input);
    System.out.println(output);
  }
}
