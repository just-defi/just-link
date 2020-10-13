package com.tron.web.controller;

import com.tron.web.common.ResultStatus;
import com.tron.web.common.util.R;
import com.tron.web.entity.JobRun;
import com.tron.web.service.JobRunsService;
import java.util.List;
import javax.servlet.http.HttpSession;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/job")
@AllArgsConstructor
@CrossOrigin
public class JobRunsController {
  JobRunsService jobRunsService;

  @GetMapping("/runs")
  public R index(
      @RequestParam(required = false, defaultValue = "") String id,
      @RequestParam(required = false, defaultValue = "1") int page,
      @RequestParam(required = false, defaultValue = "10") int size
      ) {
    try {
      List<JobRun> jobRuns;
      long totalCount;
      if (id.isEmpty()) {
        jobRuns = jobRunsService.getRunList(page, size);
        totalCount = jobRunsService.getRunsCount(null);
      } else {
        jobRuns = jobRunsService.getRunListByJobId(id, page, size);
        totalCount = jobRunsService.getRunsCount(id);
      }

      return R.ok().put("data", jobRuns).put("count", totalCount);
    } catch (Exception e) {
      log.error("get job runs failed, error : " + e.getMessage());
      return R.error(ResultStatus.GET_JOB_RUNS_FAILED);
    }
  }
}
