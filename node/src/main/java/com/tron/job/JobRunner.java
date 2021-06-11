package com.tron.job;

import com.google.common.collect.Maps;
import com.tron.common.Constant;
import com.tron.job.adapters.AdapterManager;
import com.tron.job.adapters.BaseAdapter;
import com.tron.web.common.util.R;
import com.tron.web.entity.Initiator;
import com.tron.web.entity.JobRun;
import com.tron.web.entity.JobSpec;
import com.tron.web.entity.TaskRun;
import com.tron.web.entity.TaskSpec;
import com.tron.web.entity.TronTx;
import com.tron.web.service.HeadService;
import com.tron.web.service.JobRunsService;
import com.tron.web.service.JobSpecsService;
import com.tron.client.EventRequest;
import com.tron.web.service.TronTxService;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobRunner {
  @Autowired
  JobSpecsService jobSpecsService;
  @Autowired
  public JobRunsService jobRunsService;
  @Autowired
  public TronTxService tronTxService;
  @Autowired
  private JobCache jobCache;
  @Autowired
  public HeadService headService;

  @Value("${node.minPayment:#{'100000'}}")
  private String nodeMinPayment;

  public List<Initiator> getAllJobInitiatorList() {
    List<Initiator> initiators = new ArrayList<>();
    List<JobSpec> jobSpecs = jobSpecsService.getAllJob();
    for (JobSpec jobSpec : jobSpecs) {
      if (jobSpec.getDeletedAt() != null) {
        continue;
      }
      List<Initiator> jobInitiators = jobSpecsService.getInitiatorsByJobId(jobSpec.getId());
      initiators.add(jobInitiators.get(0));
    }
    return initiators;
  }

  public void addJobRun(String eventParams) {

    try {
      Map<String, Object> eventMap = com.tron.web.common.util.JsonUtil.json2Map(eventParams);
      JobSpec job = jobSpecsService.getById(eventMap.get("jobId").toString());

      // check run
      boolean checkResult = validateRun(job, eventParams);

      if (checkResult) {
        JobRun jobRun = new JobRun();
        String jobRunId = UUID.randomUUID().toString();
        jobRunId = jobRunId.replaceAll("-", "");
        jobRun.setId(jobRunId);
        jobRun.setJobSpecID(eventMap.get("jobId").toString());
        jobRun.setRequestId(eventMap.get("requestId").toString());
        jobRun.setStatus(1);
        jobRun.setCreationHeight(new Long(eventMap.get("blockNum").toString()));
        jobRun.setPayment(0L);  // todo
        jobRun.setInitiatorId(job.getInitiators().get(0).getId());
        jobRun.setParams(eventParams);

        jobRunsService.insert(jobRun);

        insertTaskRuns(jobRunId, job.getTaskSpecs());

        run(jobRun, eventParams);
      }
    } catch (Exception e) {
      log.error("add job run failed, error msg:" + e.getMessage());
      e.printStackTrace();
    }
  }

  private void insertTaskRuns(String jobRunId, List<TaskSpec> taskSpecs) {
    for (TaskSpec task : taskSpecs) {
      TaskRun taskRun = new TaskRun();
      String taskRunId = UUID.randomUUID().toString();
      taskRunId = taskRunId.replaceAll("-", "");
      taskRun.setId(taskRunId);
      taskRun.setJobRunID(jobRunId);
      taskRun.setTaskSpecId(task.getId());
      taskRun.setLevel(task.getLevel());
      jobRunsService.insertTaskRun(taskRun);
    }
  }

  public void addJobRunV2(String addr, long roundId, String startBy, long startAt, BigInteger payment) {

    try {
      Initiator initiator = jobSpecsService.getInitiatorByAddress(addr);
      if (initiator == null) {
        log.warn("initiator is not exist, address:{}", addr);
        return;
      }
      JobSpec job = jobSpecsService.getById(initiator.getJobSpecID());

      // check run
      boolean checkResult = validateRunV2(job, initiator.getJobSpecID(), payment);

      if (checkResult) {
        JobRun jobRun = new JobRun();
        String jobRunId = UUID.randomUUID().toString();
        jobRunId = jobRunId.replaceAll("-", "");
        jobRun.setId(jobRunId);
        jobRun.setJobSpecID(job.getId());
        jobRun.setRequestId("-");
        jobRun.setStatus(1);
        jobRun.setCreationHeight(0L);
        jobRun.setPayment(0L);  // todo
        jobRun.setInitiatorId(job.getInitiators().get(0).getId());

        Map<String, Object> params = Maps.newHashMap();
        params.put("roundId", roundId);
        params.put("startBy", startBy);
        params.put("startAt", startAt);
        params.put("address", addr);
        String paramsStr = com.tron.web.common.util.JsonUtil.obj2String(params);
        jobRun.setParams(paramsStr);

        jobRunsService.insert(jobRun);

        insertTaskRuns(jobRunId, job.getTaskSpecs());

        run(jobRun, paramsStr);
      }
    } catch (Exception e) {
      log.error("add job run failed, error msg:" + e.getMessage());
      e.printStackTrace();
    }
  }

  private void run(JobRun jobRun, String params) {
    new Thread(() -> {
      try {
        execute(jobRun.getId(), params);
      } catch (Exception e) {
        //TODO
        e.printStackTrace();
      }
    }, "ExecuteJobRun").start();
  }

  private void execute(String runId, String params) {
    try {
      JobRun jobRun = jobRunsService.getById(runId);
      List<TaskRun> taskRuns = jobRunsService.getTaskRunsByJobRunId(runId);
      jobRun.setTaskRuns(taskRuns);

      R preTaskResult = new R();
      preTaskResult.put("params", params);
      preTaskResult.put("result", null);
      preTaskResult.put("jobRunId", runId);
      preTaskResult.put("taskRunId", "");
      for (TaskRun taskRun : taskRuns) {
        preTaskResult.replace("taskRunId", taskRun.getId());
        TaskSpec taskSpec = jobSpecsService.getTasksById(taskRun.getTaskSpecId());
        R result = null;
        if (jobCache.isCacheEnable() && taskSpec.getType().equals(Constant.TASK_TYPE_CACHE)) {
          result = new R();
          jobCache.cachePut(jobRun.getJobSpecID(), (long)preTaskResult.get("result"));
          long value = jobCache.cacheGet(jobRun.getJobSpecID());
          result.put("result", value);
        } else {
          result = executeTask(taskRun, taskSpec, preTaskResult);
        }


        if (result.get("code").equals(0)) {
          preTaskResult.replace("result", result.get("result"));
        } else {
          log.error(taskSpec.getType() + " run failed");
          preTaskResult.replace("code", result.get("code"));
          preTaskResult.replace("msg", result.get("msg"));
          break;
        }
      }

      // update job run
      if (preTaskResult.get("code").equals(0)) {
        jobRunsService.updateJobResult(runId, 2, null, null);
      } else {
        jobRunsService.updateJobResult(runId, 3, null, String.valueOf(preTaskResult.get("msg")));
      }
    } catch (Exception e) {
      log.error("execute job run error, msg:" + e.getMessage());
      e.printStackTrace();
    }
  }

  private R executeTask(TaskRun taskRun, TaskSpec taskSpec, R input) {
    BaseAdapter adapter = AdapterManager.getAdapter(taskSpec);
    R result = adapter.perform(input);

    // update task run
    if (result.get("code").equals(0)) {
      String resultStr = String.valueOf(result.get("result"));
      jobRunsService.updateTaskResult(taskRun.getId(), 2, resultStr, null);

      if (taskSpec.getType().equals(Constant.TASK_TYPE_TRON_TX)) {
        tronTxService.insert((TronTx)result.get("tx"));
      }
    } else {
      jobRunsService.updateTaskResult(taskRun.getId(), 3, null, String.valueOf(result.get("msg")));
      //
      if (taskSpec.getType().equals(Constant.TASK_TYPE_TRON_TX)) {
        if (result.get("tx") != null) { // for VRF resend
          tronTxService.insert((TronTx) result.get("tx"));
        }
      }
    }

    return result;
  }

  private boolean validateRun(JobSpec jobSpec, String eventParams) {
    Map<String, Object> eventMap = com.tron.web.common.util.JsonUtil.json2Map(eventParams);
    String jobId = eventMap.get("jobId").toString();
    if (jobSpec == null) {
      log.warn("failed to find job spec, ID: " + jobId);
      return false;
    }

    if (jobSpec.archived()) {
      log.warn("Trying to run archived job " + jobSpec.getId());
      return false;
    }
    String contractAddr = eventMap.get("contractAddr").toString();
    if (!contractAddr.equals(jobSpec.getInitiators().get(0).getAddress())) {
      log.error("Contract address({}) in event do not match the log subscriber address({})",
              contractAddr, jobSpec.getInitiators().get(0).getAddress());
      return false;
    }

    BigInteger minPayment;
//    if (jobSpec.getMinPayment() == null) {
//      minPayment = nodeMinPayment;
//    } else {
//      minPayment = jobSpec.getMinPayment();
//    }
    minPayment = new BigInteger(nodeMinPayment);

    BigInteger fee = new BigInteger(eventMap.get("payment").toString());

    if (fee.compareTo(new BigInteger("0")) > 0 && minPayment.compareTo(fee) > 0) {
      log.warn("rejecting job {} with payment {} below minimum threshold ({})", jobId, fee, minPayment);
      return false;
    }

    String requestId = eventMap.get("requestId").toString();
    // repeated requestId check
    String runId = jobRunsService.getByRequestId(requestId);
    if (runId != null) {
      log.warn("event repeated request id {}", requestId);
      return false;
    }

    return true;
  }

  private boolean validateRunV2(JobSpec jobSpec, String jobId, BigInteger payment) {
    if (jobSpec == null) {
      log.warn("failed to find job spec, ID: " + jobId);
      return false;
    }

    if (jobSpec.archived()) {
      log.warn("Trying to run archived job " + jobSpec.getId());
      return false;
    }

    BigInteger minPayment;
    minPayment = new BigInteger(nodeMinPayment);

    if (payment != null && payment.compareTo(new BigInteger("0")) > 0 && minPayment.compareTo(payment) > 0) {
      log.warn("rejecting job {} with payment {} below minimum threshold ({})", jobId, payment, minPayment);
      return false;
    }

    return true;
  }


  public R getJobResultById(String jobId) {
    return jobCache.getJobResultById(jobId);
  }
}
