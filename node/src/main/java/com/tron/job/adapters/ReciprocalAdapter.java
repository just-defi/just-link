package com.tron.job.adapters;

import com.tron.common.Constant;
import com.tron.web.common.util.R;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReciprocalAdapter extends BaseAdapter {
  @Override
  public String taskType() {
    return Constant.TASK_TYPE_RECIPROCAL;
  }

  @Override
  public R perform(R input) {
    R result = new R();
    try{
      result.put("result", (double)1/(double)input.get("result"));
    } catch (Exception e) {
      result.replace("code", 1);
      result.replace("msg", "reciprocal failed");
      log.warn("reciprocal failed, error msg: {}", e.getMessage());
    }

    return result;
  }
}
