package com.tron.web.service.impl;

import com.tron.web.entity.JobRun;
import com.tron.web.entity.TaskRun;
import com.tron.web.entity.TaskSpec;
import com.tron.web.mapper.JobRunsMapper;
import com.tron.web.mapper.TaskRunsMapper;
import com.tron.web.mapper.TaskSpecsMapper;
import com.tron.web.service.JobRunsService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Lazy
@Service
@AllArgsConstructor
public class JobRunsServiceImpl implements JobRunsService {
  JobRunsMapper jobRunsMapper;
  TaskRunsMapper taskRunsMapper;
  TaskSpecsMapper taskSpecsMapper;

  public int insert(JobRun jobRun) {
    return jobRunsMapper.insert(jobRun);
  }

  public int insertTaskRun(TaskRun taskRun) {
    return taskRunsMapper.insert(taskRun);
  }

  public JobRun getById(String id) {
    return jobRunsMapper.getById(id);
  }

  public List<JobRun> getRunList(int page, int size) {
    List<JobRun> jobRuns = jobRunsMapper.getList((page - 1) * size, size);
    return jobRuns;
  }

  public List<JobRun> getRunListByJobId(String jobId, int page, int size) {
    List<JobRun> jobRuns = jobRunsMapper.getListByJobId(jobId, (page - 1) * size, size);
    if (jobRuns != null) {
      List<TaskSpec> taskSpecs = taskSpecsMapper.getByJobId(jobId);
      Map<Long, String> typeMap = new HashMap<>();
      for (TaskSpec taskSpec : taskSpecs) {
        typeMap.put(taskSpec.getId(), taskSpec.getType());
      }

      for (JobRun jobRun : jobRuns) {
        List<TaskRun> taskRuns = taskRunsMapper.getByJobRunId(jobRun.getId());
        for (TaskRun taskRun : taskRuns) {
          taskRun.setType(typeMap.get(taskRun.getTaskSpecId()));
        }

        jobRun.setTaskRuns(taskRuns);
      }
    }

    return jobRuns;
  }

  public long getRunsCount(String jobId) {
    return jobRunsMapper.getCount(jobId);
  }

  public int updateTaskResult(String taskRunId, int status, String result, String errorStr) {
    return taskRunsMapper.updateResult(taskRunId, status, result, errorStr);
  }

  public int updateJobResult(String runId, int status, String result, String errorStr) {
    return jobRunsMapper.updateResult(runId, status, result, errorStr);
  }

  public List<TaskRun> getTaskRunsByJobRunId(String id) {
    return taskRunsMapper.getByJobRunId(id);
  }
}
