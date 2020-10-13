package com.tron.web.controller;

import com.tron.common.TronException;
import com.tron.job.JobSubscriber;
import com.tron.web.common.ResultStatus;
import com.tron.web.common.util.R;
import com.tron.web.entity.JobSpec;
import com.tron.web.entity.JobSpecRequest;
import com.tron.web.service.JobSpecsService;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.core.runtime.jobs.Job;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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

  @PostMapping("/specs")
  public R create(@RequestBody JobSpecRequest jobSpecRequest) {
    try {
      JobSpec result = jobSpecsService.insert(jobSpecRequest);

      if (result != null){
        jobSubscriber.addJob(result);
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

  @RequestMapping(value = "/specs/{jobId}", method = RequestMethod.GET)
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
      Long value = JobSubscriber.getJobResultById(jobId);

      return R.ok().put("data", value);
    } catch (Exception e) {
      log.error("get job result failed, error : " + e.getMessage());
      return R.error(ResultStatus.GET_JOB_DETAIL_FAILED);
    }
  }
}
