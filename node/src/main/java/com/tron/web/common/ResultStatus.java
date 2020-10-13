package com.tron.web.common;


public enum ResultStatus {

    Failed(10001, "request failed"),
    CREATE_JOB_FAILED(10002, "create job failed"),
    GET_JOB_DETAIL_FAILED(10003, "get job detail failed"),
    ARCHIVE_JOB_FAILED(10004, "archive job failed"),
    GET_JOB_LIST_FAILED(10005, "get job list failed"),
    GET_JOB_RUNS_FAILED(10010, "get job runs failed");

    private int code;

    private String msg;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    ResultStatus(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
