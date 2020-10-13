package com.tron.web.entity;

import java.io.Serializable;
import java.util.Date;
import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class Demo implements Serializable {

  private Long id;

  @NotBlank(message="key cannot be null")
  private String key;

  private String value;

  private String create_time;
  private String update_time;
}
