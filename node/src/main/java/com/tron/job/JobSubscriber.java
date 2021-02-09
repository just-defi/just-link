package com.tron.job;

import com.tron.client.EventRequest;
import com.tron.client.FluxAggregator;
import com.tron.client.OracleClient;
import com.tron.client.message.OracleRoundState;
import com.tron.keystore.KeyStore;
import com.tron.web.common.util.R;
import com.tron.web.entity.Initiator;
import com.tron.web.entity.JobSpec;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobSubscriber {
  private static JobRunner jobRunner;

  private List<String> jobSubscriberList = new ArrayList<>();

  @Autowired
  public JobSubscriber(JobRunner jobRunner) {
    JobSubscriber.jobRunner = jobRunner;
  }


  public boolean addJob(JobSpec jobSpec) {

    for (Initiator initiator : jobSpec.getInitiators()) {
      // register job subscription
      OracleClient.registerJob(initiator.getAddress(), jobSpec.getId());
    }
    jobSubscriberList.add(jobSpec.getId());
    return true;
  }

  public static void receiveLogRequest(EventRequest event) {
    // validate request
    if (event.getJobId() == null || event.getJobId().isEmpty()) {
      log.error("Job id in event request is empty");
      return;
    }

    if (event.getCallbackAddr() == null || event.getCallbackAddr().isEmpty() ||
        event.getCallbackFunctionId() == null || event.getCallbackFunctionId().isEmpty() ||
        event.getRequestId() == null || event.getRequestId().isEmpty() ||
        event.getContractAddr() == null || event.getContractAddr().isEmpty()) {
      log.error("Necessary parameters in event request is empty");
      return;
    }

    log.info("event: " + event);
    jobRunner.addJobRun(event);
  }

  public static void receiveNewRoundLog(String addr, String startBy, long roundId, long startAt) {
    log.info("receive event: roundId:{}, startBy:{}, startAt:{}", roundId, startBy, startAt);

    // validate request
    if (startBy == null || startBy.isEmpty()) {
      log.error("startBy in event request is empty");
      return;
    }

    // Ignore rounds we started
    if (KeyStore.getAddr().equals(startBy)) {
      log.info("Ignoring new round request: we started this round, contract:{}, roundId:{}", addr, roundId);
      return;
    }

    OracleRoundState roundState = FluxAggregator.getOracleRoundState(addr, roundId);
    boolean checkResult = FluxAggregator.checkOracleRoundState(roundState);
    if (checkResult) {
      jobRunner.addJobRunV2(addr, roundId, startBy, startAt, roundState.getPaymentAmount());
    }
  }

  public static void setup() {
    List<Initiator> initiators = jobRunner.getAllJobInitiatorList();
    for (Initiator initiator : initiators) {
      OracleClient.registerJob(initiator.getAddress(), initiator.getJobSpecID());
    }
  }

  public static Long getJobResultById(String jobId) {
    R result = jobRunner.getJobResultById(jobId);
    if (result.get("code").equals(0)) {
      return (Long) result.get("result");
    } else {
      return 0L;
    }
  }
}
