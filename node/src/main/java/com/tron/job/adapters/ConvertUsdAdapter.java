package com.tron.job.adapters;

import com.tron.common.Config;
import com.tron.common.Constant;
import com.tron.job.JobSubscriber;
import com.tron.web.common.util.R;

import com.tron.web.entity.Initiator;
import com.tron.web.mapper.InitiatorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConvertUsdAdapter extends BaseAdapter {

  public static ConvertUsdAdapter instance;

  @Autowired
  private InitiatorMapper initiatorMapper;

  public static ConvertUsdAdapter getInstance() {
    return instance;
  }

  @Autowired
  public void setInstance(ConvertUsdAdapter adapter) {
    instance = adapter;
  }

  @Override
  public String taskType() {
    return Constant.TASK_TYPE_CONVERT_USD;
  }

  @Override
  public R perform(R input) {
    R result  = new R();
    double value = usdtUsdRate();
    value = (long)input.get("result") * value;
    result.put("result", Math.round(value));

    return result;
  }

  public Double usdtUsdRate() {
    Initiator initiator = initiatorMapper.getByAddress(Config.getUsdtUsdAggregator());
    if (initiator == null) {
      log.warn("USDT/USD job doesn't exist");
      return 1.0;
    }
    Long value = JobSubscriber.getJobResultById(initiator.getJobSpecID());
    return value / 1000000.0;
  }
}
