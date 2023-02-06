package com.tron.web.service.impl;

import com.tron.common.Constant;
import com.tron.common.TronException;
import com.tron.job.adapters.AdapterManager;
import com.tron.job.adapters.BaseAdapter;
import com.tron.job.adapters.HttpGetAdapter;
import com.tron.job.adapters.JustSwapAdapter;
import com.tron.job.adapters.MultiplyAdapter;
import com.tron.web.common.util.JsonUtil;
import com.tron.web.entity.DetailActiveJob;
import com.tron.web.entity.Initiator;
import com.tron.web.entity.InitiatorRequest;
import com.tron.web.entity.JobSpec;
import com.tron.web.entity.JobSpecRequest;
import com.tron.web.entity.TaskSpec;
import com.tron.web.entity.TaskSpecRequest;
import com.tron.web.mapper.InitiatorMapper;
import com.tron.web.mapper.JobSpecsMapper;
import com.tron.web.mapper.TaskSpecsMapper;
import com.tron.web.service.JobSpecsService;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Lazy
@Service
@AllArgsConstructor
public class JobSpecsServiceImpl implements JobSpecsService {
  private JobSpecsMapper jobSpecsMapper;
  private InitiatorMapper initiatorMapper;
  private TaskSpecsMapper taskSpecsMapper;

  public JobSpec insert(JobSpecRequest jsr) throws TronException {

    JobSpec jobSpec = newJobFromRequest(jsr);

    // check the job
    checkJobSpec(jobSpec);

    int result = jobSpecsMapper.insert(jobSpec);
    if (result > 0) {
      initiatorMapper.insertList(jobSpec.getInitiators());
      taskSpecsMapper.insertList(jobSpec.getTaskSpecs());
    } else {
      return null;
    }

    return jobSpec;
  }

  public List<JobSpec> getJobList(int page, int size) {
    List<JobSpec> jobSpecs = jobSpecsMapper.getList((page - 1) * size, size);
    if (jobSpecs != null) {
      for (JobSpec jobSpec : jobSpecs) {
        List<Initiator> initiators = getInitiatorsByJobId(jobSpec.getId());
        jobSpec.setInitiators(initiators);
      }
    }

    return jobSpecs;
  }

  public List<DetailActiveJob> getActiveJobListWithResults( String type, int page, int size) {
    int offset = ((page - 1 )* size);
    return jobSpecsMapper.getAllActive(type, offset, size);
  }

  public long getJobCount() {
    return jobSpecsMapper.getCount();
  }

  public long getActiveJobCount(String type){
    return jobSpecsMapper.getActiveJobCount(type);
  }

  public JobSpec getById(String id) {
    JobSpec jobSpec = jobSpecsMapper.getById(id);
    List<Initiator> initiators = getInitiatorsByJobId(id);
    jobSpec.setInitiators(initiators);
    List<TaskSpec> taskSpecs = getTasksByJobId(id);
    jobSpec.setTaskSpecs(taskSpecs);

    return jobSpec;
  }

  public int deleteJob(String jobId) {
    Date now = new Date();
    return jobSpecsMapper.deleteJob(jobId, now);
  }

  public List<JobSpec> getAllJob() {
    return jobSpecsMapper.getAll();
  }

  public List<Initiator> getInitiatorsByJobId(String id) {
    return initiatorMapper.getByJobId(id);
  }

  public Initiator getInitiatorByAddress(String addr) {
    return initiatorMapper.getByAddress(addr);
  }

  public List<TaskSpec> getTasksByJobId(String id) {
    return taskSpecsMapper.getByJobId(id);
  }

  public TaskSpec getTasksById(Long id) {
    return taskSpecsMapper.getById(id);
  }

  private JobSpec newJobFromRequest(JobSpecRequest jsr) {
    String jobId = UUID.randomUUID().toString();
    jobId = jobId.replaceAll("-", "");
    JobSpec jobSpec = new JobSpec();
    jobSpec.setId(jobId);
    jobSpec.setEndAt(jsr.getEndAt());
    jobSpec.setStartAt(jsr.getStartAt());
    jobSpec.setMinPayment(jsr.getMinPayment());
    jobSpec.setParams(JsonUtil.toJson(jsr));
    jobSpec.setInitiators(new ArrayList<>());
    jobSpec.setTaskSpecs(new ArrayList<>());

    for (InitiatorRequest ir : jsr.getInitiators()) {
      Initiator i = new Initiator();
      i.setJobSpecID(jobId);
      i.setType(ir.getType());
      i.setParams(JsonUtil.obj2String(ir.getParams()));
      i.setAddress(ir.getParams().getAddress());
      jobSpec.getInitiators().add(i);
    }

    for (TaskSpecRequest tr : jsr.getTasks()) {
      TaskSpec ts = new TaskSpec();
      ts.setJobSpecID(jobId);
      ts.setType(tr.getType().toLowerCase());
      ts.setParams(JsonUtil.obj2String(tr.getParams()));
      jobSpec.getTaskSpecs().add(ts);
    }

    return jobSpec;
  }

  // check the job and its associated Initiators and Tasks for any
  // application logic errors
  private void checkJobSpec(JobSpec job) throws TronException {
    if (job.getInitiators().isEmpty() || job.getTaskSpecs().isEmpty()) {
      throw new TronException("Must have at least one Initiator and one Task");
    }

    for (Initiator initiator : job.getInitiators()) {
      checkInitiator(initiator);
    }

    for (TaskSpec taskSpec : job.getTaskSpecs()) {
      checkTaskSpec(taskSpec);
    }
  }

  private void checkInitiator(Initiator initiator) throws TronException {
    switch (initiator.getType()) {
      case Constant.INITIATOR_TYPE_RUN_LOG:
      case Constant.INITIATOR_TYPE_RANDOMNESS_LOG:
        if (initiator.getAddress() == null || initiator.getAddress().isEmpty()) {
          throw new TronException("Initiator's address parameter is required");
        }
        break;
      default:
        throw new TronException("Initiator type " + initiator.getType() + " dose dot support");
    }
  }

  private void checkTaskSpec(TaskSpec taskSpec) throws TronException {
    BaseAdapter adapter = AdapterManager.getAdapter(taskSpec);
    if (adapter == null) {
      throw new TronException("Type " + taskSpec.getType() + " dose dot support");
    }
    switch (taskSpec.getType()) {
      case Constant.TASK_TYPE_HTTP_GET:
        if (((HttpGetAdapter)adapter).getUrl() == null || ((HttpGetAdapter)adapter).getUrl().isEmpty()) {
          throw new TronException(Constant.TASK_TYPE_HTTP_GET + " task's url parameter is required");
        }
        if (((HttpGetAdapter)adapter).getPath() == null || ((HttpGetAdapter)adapter).getPath().isEmpty()) {
          throw new TronException(Constant.TASK_TYPE_HTTP_GET + " task's path parameter is required");
        }
        break;
      case Constant.TASK_TYPE_MULTIPLY:
        if (((MultiplyAdapter)adapter).getTimes() == null || ((MultiplyAdapter)adapter).getTimes() == 0L) {
          throw new TronException(Constant.TASK_TYPE_MULTIPLY + " task's times parameter is required");
        }
        break;
      case Constant.TASK_TYPE_JUST_SWAP:
        if ((((JustSwapAdapter)adapter).getPair() == null ||
            ((JustSwapAdapter)adapter).getPair().isEmpty()) &&
            (((JustSwapAdapter)adapter).getPool() == null ||
                ((JustSwapAdapter)adapter).getPool().isEmpty() ||
                ((JustSwapAdapter)adapter).getTrc20() == null ||
                ((JustSwapAdapter)adapter).getTrc20().isEmpty())) {
          throw new TronException(Constant.TASK_TYPE_JUST_SWAP + " task's pair or addr parameters are required");
        }
        break;
      case Constant.TASK_TYPE_TRON_TX:
      case Constant.TASK_TYPE_CONVERT_USD:
      case Constant.TASK_TYPE_RECIPROCAL:
      case Constant.TASK_TYPE_TRX_TO_USDT:
      case Constant.TASK_TYPE_CACHE:
      case Constant.TASK_TYPE_CONVERT_TRX:
      case Constant.TASK_TYPE_RANDOM:
        break;
      default:
        throw new TronException("Task type " + taskSpec.getType() + " dose dot support");
    }
  }
}
