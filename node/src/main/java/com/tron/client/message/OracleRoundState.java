package com.tron.client.message;

import java.math.BigInteger;
import lombok.Data;

@Data
public class OracleRoundState {
  private Boolean eligibleToSubmit;
  private long roundId;
  private BigInteger latestSubmission;
  private long startedAt;
  private long timeout;
  private BigInteger availableFunds;
  private int oracleCount;
  private BigInteger paymentAmount;
}
