package com.tron.web.mapper;

import com.tron.web.entity.TaskSpec;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TaskSpecsMapper {

  int insert(TaskSpec taskSpec);

  int insertList(List<TaskSpec> taskSpecs);

  List<TaskSpec> getByJobId(@Param("jobId") String jobId);

  TaskSpec getById(@Param("id") Long id);
}
