package com.tron.web.mapper;

import com.tron.web.entity.Demo;
import org.apache.ibatis.annotations.Param;

public interface DemoMapper {

  int create(Demo demo);

  Demo queryByKey(@Param("key") String key);
}
