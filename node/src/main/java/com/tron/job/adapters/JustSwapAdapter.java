package com.tron.job.adapters;

import com.tron.common.Constant;
import com.tron.job.adapters.ContractAdapter.TradePair;
import com.tron.web.common.util.R;
import java.io.IOException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JustSwapAdapter extends BaseAdapter {
  @Getter
  private String pair;
  @Getter
  private String pool;
  @Getter
  private String trc20;

  public JustSwapAdapter(String pair, String pool, String trc20) {
    this.pair = pair;
    this.pool = pool;
    this.trc20 = trc20;
  }

  @Override
  public String taskType() {
    return Constant.TASK_TYPE_JUST_SWAP;
  }

  @Override
  public R perform(R input) {
    R result = new R();
    double value = getPairPrice();
    if (Math.abs(value) > 0.000000001) {
      result.put("result", value);
    } else {
      log.error("get price from justswap failed, pair:" + pair);
      return R.error(1, "get price from justswap failed, pair:" + pair);
    }

    return result;
  }

  private double getPairPrice() {
    double result = 0;

    try {
      if (pair == null || pair.isEmpty()) {
        result = ContractAdapter.getTradePriceWithTRX(pool, trc20);
      } else {
        switch (pair) {
          case Constant.PAIR_TYPE_JUST_TRX:
            result = ContractAdapter.getTradePriceWithTRX(TradePair.JUST_TRX);
            break;
          case Constant.PAIR_TYPE_BTC_TRX:
            result = ContractAdapter.getTradePriceWithTRX(TradePair.BTC_TRX);
            break;
          case Constant.PAIR_TYPE_SUN_TRX:
            result = ContractAdapter.getTradePriceWithTRX(TradePair.SUN_TRX);
            break;
          case Constant.PAIR_TYPE_WIN_TRX:
            result = ContractAdapter.getTradePriceWithTRX(TradePair.WIN_TRX);
            break;
          case Constant.PAIR_TYPE_DICE_TRX:
            result = ContractAdapter.getTradePriceWithTRX(TradePair.DICE_TRX);
            break;
          case Constant.PAIR_TYPE_USDJ_TRX:
            result = ContractAdapter.getTradePriceWithTRX(TradePair.USDJ_TRX);
            break;
          case Constant.PAIR_TYPE_USDT_TRX:
            result = ContractAdapter.getTradePriceWithTRX(TradePair.USDT_TRX);
            break;
          default:
            break;
        }
      }
    } catch (Exception e) {
      log.error("get pair price failed! msg:" + e.getMessage());
    }

    return result;
  }
}
