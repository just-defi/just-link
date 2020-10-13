package com.tron.job.adapters;

import com.tron.common.Constant;
import com.tron.web.common.util.R;
import lombok.Getter;

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
    result.put("result", Math.round((double)input.get("result") * times));
    return result;
  }
}
