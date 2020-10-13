package com.tron.job;

import com.tron.job.adapters.ConvertUsdAdapter;
import com.tron.web.common.util.R;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
public class ConvertUsdAdapterTest {

  @Test
  public void convertUsdTest() {
    ConvertUsdAdapter convertUsdAdapter = new ConvertUsdAdapter();
    R input = new R();
    Long value = 2L;
    input.put("result", 20000L);
    R output = convertUsdAdapter.perform(input);
    System.out.println(output);
  }
}
