package com.tron.web.common.util;

import com.tron.web.common.ResultStatus;
import java.util.HashMap;
import java.util.Map;

public class R extends HashMap<String, Object> {
	private static final long serialVersionUID = 1L;

	public R() {
		put("code", 0);
		put("msg", "success");
	}

	public static R error() {
		return error(org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR, "unknown err");
	}

	public static R error(String msg) {
		return error(org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR, msg);
	}

	public static R error(int code, String msg) {
		R r = new R();
		r.put("code", code);
		r.put("msg", msg);
		return r;
	}

	public static R error(ResultStatus httpCode) {
		R r = new R();
		r.put("code", httpCode.getCode());
		r.put("msg", httpCode.getMsg());
		return r;
	}

	public static R ok(String msg) {
		R r = new R();
		r.put("msg", msg);
		return r;
	}

	public static R ok(Map<String, Object> map) {
		R r = new R();
		r.putAll(map);
		return r;
	}

	public static R ok() {
		return new R();
	}

	public R put(String key, Object value) {
		super.put(key, value);
		return this;
	}
}
