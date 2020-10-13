package com.tron.web.service.impl;

import com.tron.web.entity.Demo;
import com.tron.web.mapper.DemoMapper;
import com.tron.web.service.DemoService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Lazy
@Service
@AllArgsConstructor
public class DemoServiceImpl implements DemoService {
    private DemoMapper demoMapper;

    @Override
    public int create(Demo demo) {
        return demoMapper.create(demo);
    }

    @Override
    public Demo queryByKey(String key) {
        return demoMapper.queryByKey(key);
    }

}
