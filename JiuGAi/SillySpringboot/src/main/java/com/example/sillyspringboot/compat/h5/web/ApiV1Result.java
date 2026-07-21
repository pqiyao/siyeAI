package com.example.sillyspringboot.compat.h5.web;

/**
 * 兼容 H5 旧后端返回结构：{code:1,data,msg}
 */
public record ApiV1Result<T>(int code, T data, String msg) {
    public static <T> ApiV1Result<T> ok(T data) {
        return new ApiV1Result<>(1, data, "ok");
    }

    public static <T> ApiV1Result<T> fail(String msg) {
        return new ApiV1Result<>(0, null, msg == null ? "fail" : msg);
    }
}

