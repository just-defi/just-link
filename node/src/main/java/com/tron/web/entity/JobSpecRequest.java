package com.tron.web.entity;

import java.util.Date;
import java.util.List;
import lombok.Data;

@Data
public class JobSpecRequest {
    private List<InitiatorRequest> initiators;
    private List<TaskSpecRequest> tasks;
    private Long minPayment;
    private Date startAt;
    private Date endAt;

}