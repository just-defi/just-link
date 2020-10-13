package com.tron.web.service;

import com.tron.web.entity.Demo;

public interface DemoService {

    int create(Demo demo);

    Demo queryByKey(String key);
}
