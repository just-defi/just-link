package com.tron.web.mapper;

import com.tron.web.entity.TronTx;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Date;

@Mapper
public interface TxesMapper {
  int insert(TronTx tronTx);
  int update(TronTx tronTx);

  TronTx getById(@Param("id") Long id);

  TronTx getByTxId(@Param("txId") String txId);

  List<TronTx> getByConfirmedAndDate(@Param("confirmed") Long confirmed, @Param("sentAt") Long sentAt);
}
