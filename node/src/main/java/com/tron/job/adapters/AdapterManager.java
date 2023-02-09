package com.tron.job.adapters;

import com.tron.common.Constant;
import com.tron.web.common.util.JsonUtil;
import com.tron.web.entity.TaskParams;
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
      case Constant.TASK_TYPE_CONVERT_TRX:
        adapter = new ConvertTrxAdapter(params.getGet(), params.getPath());
        break;
      case Constant.TASK_TYPE_MULTIPLY:
        adapter = new MultiplyAdapter(params.getTimes());
        break;
      case Constant.TASK_TYPE_CONVERT_USD:
        adapter = ConvertUsdAdapter.getInstance();
        break;
      case Constant.TASK_TYPE_TRON_TX:
        String tronTxType = "";
        try {
          tronTxType = params.getType();
        } catch (Exception e) {
          tronTxType = "";
        }
        if (params != null) {
          adapter = new TronTxAdapter(params.getVersion(), tronTxType);
        } else {
          adapter = new TronTxAdapter(null, tronTxType);
        }
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
      case Constant.TASK_TYPE_CACHE:
        adapter = new CacheAdapter();
        break;
      case Constant.TASK_TYPE_RANDOM:
        adapter = new RandomAdapter(params.getPublicKey());
        break;
      default:
        break;
    }

    return adapter;
  }
}
