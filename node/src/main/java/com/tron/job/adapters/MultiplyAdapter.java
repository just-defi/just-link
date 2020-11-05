package com.tron.job.adapters;

import com.tron.common.Constant;
import com.tron.web.common.util.R;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MultiplyAdapter extends BaseAdapter {
  @Getter
  private Long times;

  public MultiplyAdapter(Long t) {
    times = t;
  }

  @Override
  public String taskType() {
    return Constant.TASK_TYPE_MULTIPLY;
  }

  @Override
  public R perform(R input) {
    R result = new R();
    try {
      result.put("result", Math.round((double)input.get("result") * times));
    } catch (Exception e) {
      result.replace("code", 1);
      result.replace("msg", "multiply failed");
      log.warn("multiply failed, error msg: {}", e.getMessage());
    }

    return result;
  }
}
