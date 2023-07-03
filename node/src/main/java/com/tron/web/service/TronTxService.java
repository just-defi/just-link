package com.tron.web.service;

import com.tron.web.entity.TronTx;

import java.util.Date;
import java.util.List;

public interface TronTxService {
  int insert(TronTx tronTx);
  int update(TronTx tronTx);
  TronTx getById(Long id);
  TronTx getByTxId(String txId);
  List<TronTx> getByConfirmedAndDate(Long confirmed, Long sentAt);
}
