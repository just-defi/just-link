package com.tron.common;

public class Constant {

  public static final long ONE_HOUR = 60 * 60 * 1000L;
  public static final long ONE_MINUTE = 60 * 1000L;

  public static final String READONLY_ACCOUNT = "TXmVpin5vq5gdZsciyyjdZgKRUju4st1wM";  // sun

  public static final String TRIGGET_CONSTANT_CONTRACT = "/wallet/triggerconstantcontract";

  public static final String TRX_DECIMAL_STR = "1000000";

  public static String HTTP_EVENT_HOST = "api.trongrid.io";
  public static String FULLNODE_HOST = "api.trongrid.io";

  public static void initEnv(String env) {
    if ("dev".equals(env)) {
      HTTP_EVENT_HOST = "event.nileex.io";
      FULLNODE_HOST = "api.nileex.io";
    }
  }

  public static final int HTTP_MAX_RETRY_TIME = 3;

  public static final String FULFIL_METHOD_SIGN =
          "fulfillOracleRequest(bytes32,uint256,address,bytes4,uint256,bytes32)";
  public static final String VRF_FULFIL_METHOD_SIGN =
          "fulfillRandomnessRequest(bytes32,uint256,address,bytes4,uint256,bytes32)";

  // task type
  public static final String TASK_TYPE_HTTP_GET = "httpget";
  public static final String TASK_TYPE_HTTP_POST = "httppost";
  public static final String TASK_TYPE_TRON_TX = "trontx";
  public static final String TASK_TYPE_MULTIPLY = "multiply";
  public static final String TASK_TYPE_CONVERT_USD = "convertusd";
  public static final String TASK_TYPE_TRX_TO_USDT = "trx2usdt";
  public static final String TASK_TYPE_RECIPROCAL = "reciprocal";
  public static final String TASK_TYPE_JUST_SWAP = "justswap";
  public static final String TASK_TYPE_CACHE = "cache";
  public static final String TASK_TYPE_CONVERT_TRX = "converttrx";
  public static final String TASK_TYPE_RANDOM = "random";

  // initiator type
  public static final String INITIATOR_TYPE_RUN_LOG = "runlog";

  // pairs
  public static final String PAIR_TYPE_JUST_TRX = "jst-trx";
  public static final String PAIR_TYPE_SUN_TRX = "sun-trx";
  public static final String PAIR_TYPE_WIN_TRX = "win-trx";
  public static final String PAIR_TYPE_DICE_TRX = "dice-trx";
  public static final String PAIR_TYPE_BTC_TRX = "btc-trx";
  public static final String PAIR_TYPE_USDJ_TRX = "usdj-trx";
  public static final String PAIR_TYPE_USDT_TRX = "usdt-trx";
}
