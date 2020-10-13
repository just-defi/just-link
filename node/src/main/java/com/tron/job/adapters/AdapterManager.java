package com.tron.job.adapters;

import com.tron.common.Constant;
import com.tron.web.common.util.JsonUtil;
import com.tron.web.entity.TaskParams;
import com.tron.web.entity.TaskRun;
import com.tron.web.entity.TaskSpec;

public class AdapterManager {

  public static BaseAdapter getAdapter(TaskSpec taskSpec) {
    BaseAdapter adapter = null;
    TaskParams params = JsonUtil.json2Obj(taskSpec.getParams(), TaskParams.class);
    switch (taskSpec.getType()){
      case Constant.TASK_TYPE_HTTP_GET:
        adapter = new HttpGetAdapter(params.getGet(), params.getPath());
        break;
      case Constant.TASK_TYPE_HTTP_POST:
        adapter = null;
        break;
      case Constant.TASK_TYPE_MULTIPLY:
        adapter = new MultiplyAdapter(params.getTimes());
        break;
      case Constant.TASK_TYPE_CONVERT_USD:
        adapter = new ConvertUsdAdapter();
        break;
      case Constant.TASK_TYPE_TRON_TX:
        adapter = new TronTxAdapter();
        break;
      case Constant.TASK_TYPE_RECIPROCAL:
        adapter = new ReciprocalAdapter();
        break;
      case Constant.TASK_TYPE_JUST_SWAP:
        adapter = new JustSwapAdapter(params.getPair(), params.getPool(), params.getTrc20());
        break;
      case Constant.TASK_TYPE_TRX_TO_USDT:
        adapter = new ConvertUsdtAdapter();
        break;
      default:
        break;
    }

    return adapter;
  }
}
