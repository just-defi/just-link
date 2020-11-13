package com.tron.job.adapters;

import com.tron.common.Constant;
import com.tron.web.common.util.R;

public class CacheAdapter extends BaseAdapter {
  @Override
  public String taskType() {
    return Constant.TASK_TYPE_CACHE;
  }

  @Override
  public R perform(R input) {
    return new R();
  }
}
