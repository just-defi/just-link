package com.tron.job.adapters;

import com.tron.web.common.util.R;

public abstract class BaseAdapter {
  abstract public String taskType();

  abstract public R perform(R input);
}
