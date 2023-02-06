package com.tron.web.service;

import com.tron.common.TronException;
import com.tron.web.entity.DetailActiveJob;
import com.tron.web.entity.Initiator;
import com.tron.web.entity.JobSpec;
import com.tron.web.entity.JobSpecRequest;
import com.tron.web.entity.TaskSpec;
import java.util.List;

public interface JobSpecsService {

  JobSpec insert(JobSpecRequest jsr) throws TronException;

  List<JobSpec> getJobList(int page, int size);

  List<DetailActiveJob> getActiveJobListWithResults(String type, int page, int size);

  long getActiveJobCount(String type);

  JobSpec getById(String id);

  List<Initiator> getInitiatorsByJobId(String id);

  List<TaskSpec> getTasksByJobId(String id);

  TaskSpec getTasksById(Long id);

  List<JobSpec> getAllJob();

  int deleteJob(String jobId);

  long getJobCount();

  Initiator getInitiatorByAddress(String addr);
}
