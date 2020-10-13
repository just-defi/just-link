package com.tron.web.mapper;

import com.tron.web.entity.TronTx;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TxesMapper {
  int insert(TronTx tronTx);

  TronTx getById(@Param("id") Long id);

  TronTx getByTxId(@Param("txId") String txId);
}
