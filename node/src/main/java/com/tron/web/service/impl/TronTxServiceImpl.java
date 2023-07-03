package com.tron.web.service.impl;

import com.tron.web.entity.TronTx;
import com.tron.web.mapper.TxesMapper;
import com.tron.web.service.TronTxService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Lazy
@Service
@AllArgsConstructor
public class TronTxServiceImpl implements TronTxService {
  private TxesMapper txesMapper;

  public int insert(TronTx tronTx) {
    return txesMapper.insert(tronTx);
  }

  public int update(TronTx tronTx) {
    return txesMapper.update(tronTx);
  }

  public TronTx getById(Long id) {
    return txesMapper.getById(id);
  }

  public TronTx getByTxId(String txId) {
    return txesMapper.getByTxId(txId);
  }

  @Override
  public List<TronTx> getByConfirmedAndDate(Long confirmed, Long sentAt) {
    return txesMapper.getByConfirmedAndDate(confirmed, sentAt);
  }
}
