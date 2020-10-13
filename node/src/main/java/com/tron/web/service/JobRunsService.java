package com.tron.web.service;

import com.tron.web.entity.JobRun;
import com.tron.web.entity.TaskRun;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface JobRunsService {
  int insert(JobRun jobRun);

  int insertTaskRun(TaskRun taskRun);

  JobRun getById(String id);

  List<JobRun> getRunList(int page, int size);

  List<JobRun> getRunListByJobId(String jobId, int page, int size);

  long getRunsCount(String jobId);

  int updateTaskResult(String taskRunId, int status, String result, String errorStr);

  int updateJobResult(String runId, int status, String result, String errorStr);

  List<TaskRun> getTaskRunsByJobRunId(String id);
}
