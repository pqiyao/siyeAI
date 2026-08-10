package com.example.sillyspringboot.shared.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 与旧 H5 约定对齐：code == 1 表示成功，便于后续 Mini App 迁移期对照。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ApiResult<T> {

    private final int code;
    private final String msg;
    private final T data;

    private ApiResult(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(1, "ok", data);
    }

    public static ApiResult<Void> okEmpty() {
        return new ApiResult<>(1, "ok", null);
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public T getData() {
        return data;
    }
}
