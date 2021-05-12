package com.tron.job;

import com.tron.client.EventRequest;
import com.tron.client.VrfEventRequest;
import com.tron.client.OracleClient;
import com.tron.web.common.util.JsonUtil;
import com.tron.web.common.util.R;
import com.tron.web.entity.Initiator;
import com.tron.web.entity.JobSpec;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Slf4j
@Component
public class JobSubscriber {
  private static JobRunner jobRunner;
  private static VrfJobRunner vrfJobRunner;

  private List<String> jobSubscriberList = new ArrayList<>();

  @Autowired
  public JobSubscriber(JobRunner jobRunner, VrfJobRunner vrfJobRunner) {
    JobSubscriber.jobRunner = jobRunner;
    JobSubscriber.vrfJobRunner = vrfJobRunner;
  }

  /*@Autowired
  public JobSubscriber(VrfJobRunner vrfJobRunner) {
    JobSubscriber.vrfJobRunner = vrfJobRunner;
  }*/

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

  public static void receiveVrfRequest(VrfEventRequest event) {
    // validate request
    if (event.getJobId() == null || event.getJobId().isEmpty()) {
      log.error("Job id in VRF event request is empty");
      return;
    }

    if (event.getSeed() == null || event.getSeed().isEmpty() ||
            event.getKeyHash() == null || event.getKeyHash().isEmpty() ||
            event.getRequestId() == null || event.getRequestId().isEmpty() ||
            event.getContractAddr() == null || event.getContractAddr().isEmpty()) {
      log.error("Necessary parameters in  VRF event request is empty");
      return;
    }

    log.info("VRF event: " + event);
    vrfJobRunner.addJobRun(event);
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
