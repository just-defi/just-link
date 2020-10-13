package com.tron.web.service.impl;

import com.tron.web.entity.TronTx;
import com.tron.web.mapper.TxesMapper;
import com.tron.web.service.TronTxService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Lazy
@Service
@AllArgsConstructor
public class TronTxServiceImpl implements TronTxService {
  private TxesMapper txesMapper;

  public int insert(TronTx tronTx) {
    return txesMapper.insert(tronTx);
  }
}
