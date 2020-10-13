package com.tron.web.mapper;

import com.tron.web.entity.JobRun;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JobRunsMapper {

  int insert(JobRun jobRun);

  JobRun getById(@Param("id") String id);

  List<JobRun> getList(@Param("offset") int offset, @Param("limit") int limit);

  List<JobRun> getListByJobId(@Param("id") String id, @Param("offset") int offset, @Param("limit") int limit);

  int updateResult(@Param("id") String id, @Param("status") int status, @Param("result") String result, @Param("error") String error);

  long getCount(@Param("jobId") String jobId);
}
