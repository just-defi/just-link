package com.tron.web.service.impl;

import com.tron.web.entity.Head;
import com.tron.web.mapper.HeadMapper;
import com.tron.web.service.HeadService;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import java.util.List;

@Lazy
@Service
@AllArgsConstructor
public class HeadServiceImpl implements HeadService {
  private HeadMapper headMapper;

  public int insert(Head head) {
    return headMapper.insert(head);
  }
  public int update(Head head) { return headMapper.update(head); }

  @Override
  public List<Head> getByAddress(String address) {
    return headMapper.getByAddress(address);
  }
}
