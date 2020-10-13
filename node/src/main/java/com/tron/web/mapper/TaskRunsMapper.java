package com.tron.web.mapper;

import com.tron.web.entity.TaskRun;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface TaskRunsMapper {

  int insert(TaskRun taskRun);

  TaskRun getById(@Param("id") String id);

  List<TaskRun> getByJobRunId(@Param("id") String id);

  int updateResult(@Param("id") String id, @Param("status") int status, @Param("result") String result, @Param("error") String error);
}
