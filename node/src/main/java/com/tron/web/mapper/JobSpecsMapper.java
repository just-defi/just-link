package com.tron.web.mapper;

import com.tron.web.entity.JobSpec;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface JobSpecsMapper {

  int insert(JobSpec jobSpec);

  JobSpec getById(@Param("id") String id);

  List<JobSpec> getAll();

  List<JobSpec> getList(@Param("offset") int offset, @Param("limit") int limit);

  int deleteJob(@Param("id") String id, @Param("deletedAt") Date deletedAt);

  long getCount();
}
