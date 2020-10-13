package com.tron.job.adapters;

import com.tron.common.Constant;
import com.tron.web.common.util.R;

public class ReciprocalAdapter extends BaseAdapter {
  @Override
  public String taskType() {
    return Constant.TASK_TYPE_RECIPROCAL;
  }

  @Override
  public R perform(R input) {
    R result = new R();
    result.put("result", (double)1/(double)input.get("result"));
    return result;
  }
}
