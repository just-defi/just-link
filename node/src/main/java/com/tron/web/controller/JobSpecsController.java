package com.tron.web.controller;

import com.tron.common.Constant;
import com.tron.common.TronException;
import com.tron.job.JobCache;
import com.tron.job.JobSubscriber;
import com.tron.web.common.ResultStatus;
import com.tron.web.common.util.R;
import com.tron.web.entity.DetailActiveJob;
import com.tron.web.entity.Initiator;
import com.tron.web.entity.JobSpec;
import com.tron.web.entity.JobSpecRequest;
import com.tron.web.entity.TaskSpec;
import com.tron.web.mapper.InitiatorMapper;
import com.tron.web.service.JobSpecsService;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/job")
@AllArgsConstructor
@CrossOrigin
public class JobSpecsController {
  private JobSpecsService jobSpecsService;
  private JobSubscriber jobSubscriber;
  private JobCache jobCache;
  private InitiatorMapper initiatorMapper;

  @GetMapping("/specs")
  public R index(
      @RequestParam(required = false, defaultValue = "1") int page,
      @RequestParam(required = false, defaultValue = "10") int size
      ) {
    try {
      List<JobSpec> result = jobSpecsService.getJobList(page, size);
      long count = jobSpecsService.getJobCount();
      return R.ok().put("data", result).put("count", count);
    } catch (Exception e) {
      log.error("get job list failed, error : " + e.getMessage());
      return R.error(ResultStatus.GET_JOB_LIST_FAILED);
    }
  }

  @GetMapping(value="/specs/active")
  public R getDetailActiveJobs(
      @RequestParam(required = false, defaultValue = "runlog") String type,
      @RequestParam(required = false, defaultValue = "1") int page,
      @RequestParam(required = false, defaultValue = "10") int size) {
    try {
      List<DetailActiveJob> jobs = jobSpecsService.getActiveJobListWithResults( type, page, size);
      long count = jobSpecsService.getActiveJobCount(type);
      return R.ok().put("data", jobs).put("count", count);
    } catch (Exception e) {
      log.error("get job list failed, error: " + e.getMessage());
      return R.error(ResultStatus.GET_JOB_DETAIL_FAILED);

    }
  }

  @PostMapping("/specs")
  public R create(@RequestBody JobSpecRequest jobSpecRequest) {
    try {
      JobSpec result = jobSpecsService.insert(jobSpecRequest);

      if (result != null){
        jobSubscriber.addJob(result);

        if (jobCache.isCacheEnable()) {
          for (TaskSpec taskSpec : result.getTaskSpecs()) {
            if (taskSpec.getType().equals(Constant.TASK_TYPE_CACHE)) {
              jobCache.addToCacheList(result.getId());
            }
          }
        }
        return R.ok().put("data", "");
      } else {
        return R.error(ResultStatus.CREATE_JOB_FAILED);
      }
    } catch (TronException te) {
      return R.error(11000, te.getMessage());
    } catch (Exception e) {
      log.error("create job failed, error : " + e.getMessage());
      return R.error(ResultStatus.CREATE_JOB_FAILED);
    }
  }

  @GetMapping(value = "/specs/{jobId}")
  public R getJobById(@PathVariable("jobId") String jobId) {
    try {
      JobSpec jobSpec = jobSpecsService.getById(jobId);

      return R.ok().put("data", jobSpec);
    } catch (Exception e) {
      log.error("get job detail failed, error : " + e.getMessage());
      return R.error(ResultStatus.GET_JOB_DETAIL_FAILED);
    }
  }

  @DeleteMapping(value = "/specs/{jobId}")
  public R delete(@PathVariable("jobId") String jobId) {
    try {
      jobSpecsService.deleteJob(jobId);
      return R.ok();
    } catch (Exception e) {
      log.error("delete job failed, error : " + e.getMessage());
      return R.error(ResultStatus.ARCHIVE_JOB_FAILED);
    }
  }

  @GetMapping(value = "/result/{jobId}")
  public R getJobResult(@PathVariable("jobId") String jobId) {
    try {
      log.info("Get result by job id: {}", jobId);
      Long value = JobSubscriber.getJobResultById(jobId);

      return R.ok().put("data", value);
    } catch (Exception e) {
      log.error("get job result failed, jobId:" + jobId + ", error : " + e.getMessage());
      return R.error(ResultStatus.GET_JOB_DETAIL_FAILED).put("data", 0);
    }
  }

  @GetMapping(value = "/cache/{jobId}")
  public R getJobCache(@PathVariable("jobId") String jobId) {
    try {
      Queue<Long> valueList = jobCache.getValueListByJobId(jobId);
      if (valueList != null) {
        return R.ok().put("data", valueList.toArray());
      } else {
        JobSpec jobSpec = jobSpecsService.getById(jobId);
        if (jobCache.isCacheEnable()) {
          for (TaskSpec taskSpec : jobSpec.getTaskSpecs()) {
            if (taskSpec.getType().equals(Constant.TASK_TYPE_CACHE)) {
              jobCache.addToCacheList(jobId);
            }
          }
        }
        return R.ok().put("data", new ArrayList<>());
      }
    } catch (Exception e) {
      log.error("get job cache failed, jobId:" + jobId + ", error : " + e.getMessage());
      return R.error(ResultStatus.GET_JOB_DETAIL_FAILED).put("data", 0);
    }
  }

  @GetMapping(value = "/active/{address}")
  public R getActiveJob(@PathVariable("address") String address) {
    Initiator initiator = initiatorMapper.getByAddress(address);
    if (initiator == null) {
      return R.error("initiator not exists");
    }

    return R.ok().put("data", initiator.getJobSpecID());
  }

  @GetMapping(value = "/active")
  public R getAllActiveJobs() {
    List<Initiator> initiators = JobSubscriber.jobRunner.getAllJobInitiatorList();
    Set<String> addresses = initiators.stream().map(Initiator::getAddress).collect(Collectors.toSet());

    List<String> jobIds = new ArrayList<>(addresses.size());
    addresses.forEach(each -> {
      Initiator initiator = initiatorMapper.getByAddress(each);
      if (initiator != null) {
        jobIds.add(initiator.getJobSpecID());
      }
    });

    return R.ok().put("data", jobIds);
  }


}
