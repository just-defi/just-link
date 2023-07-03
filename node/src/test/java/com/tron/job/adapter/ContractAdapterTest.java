package com.tron.job.adapter;

import static com.tron.job.adapters.ContractAdapter.TradePair.BTC_TRX;
import static com.tron.job.adapters.ContractAdapter.TradePair.DICE_TRX;
import static com.tron.job.adapters.ContractAdapter.TradePair.JUST_TRX;
import static com.tron.job.adapters.ContractAdapter.TradePair.SUN_TRX;
import static com.tron.job.adapters.ContractAdapter.TradePair.USDJ_TRX;
import static com.tron.job.adapters.ContractAdapter.TradePair.WIN_TRX;

import com.tron.job.adapters.ContractAdapter;
import java.io.IOException;
import java.math.BigInteger;
import org.junit.Test;

public class ContractAdapterTest {

  @Test
  public void testGetTRXBalance() throws Exception {
    System.out.println(ContractAdapter.getTRXBalance(
            "TYukBQZ2XXCcRCReAUguyXncCWNY9CEiDQ", true, false));
    System.out.println(ContractAdapter.getTRXBalance(
            "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", true, false));
  }

  @Test
  public void testGetBalance() throws Exception {
    BigInteger balance = ContractAdapter.balanceOf(
            "TQcia2H2TU3WrFk9sKtdK9qCfkW8XirfPQ",
            "TMwFHYXLJaRUPeW6421aqXL4ZEzPRFGkGT");
    System.out.println(balance.toString());
  }

  @Test
  public void testGetDecimal() throws Exception {
    System.out.println(ContractAdapter.getDecimal(JUST_TRX.getTrc20Addr()));
    System.out.println(ContractAdapter.getDecimal(BTC_TRX.getTrc20Addr()));
    System.out.println(ContractAdapter.getDecimal(DICE_TRX.getTrc20Addr()));
    System.out.println(ContractAdapter.getDecimal(SUN_TRX.getTrc20Addr()));
    System.out.println(ContractAdapter.getDecimal(WIN_TRX.getTrc20Addr()));
  }

  @Test
  public void testGetTradePriceWithTRX() throws Exception {
    System.out.println(ContractAdapter.getTradePriceWithTRX(USDJ_TRX));
  }
}
