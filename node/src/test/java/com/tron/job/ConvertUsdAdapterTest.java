package com.tron.job;

import com.tron.job.adapters.ConvertUsdAdapter;
import com.tron.web.common.util.R;
import com.tron.web.mapper.InitiatorMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
public class ConvertUsdAdapterTest {

  @InjectMocks
  ConvertUsdAdapter convertUsdAdapter;

  @Mock
  InitiatorMapper initiatorMapper;

  @Test
  public void convertUsdTest() {
    R input = new R();
    input.put("result", 20000L);
    R output = convertUsdAdapter.perform(input);
    System.out.println(output);
    assert 0 == (Integer) output.get("code");
    assert 20000L == (Long) output.get("result");
  }
}
