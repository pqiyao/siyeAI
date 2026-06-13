package com.example.sillyspringboot.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 错误响应体：不包含堆栈与上游原始报文。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class AppErrorResponse {

    private final String code;
    private final String message;
    private final String traceId;
    private final Map<String, String> fieldErrors;

    public AppErrorResponse(String code, String message, String traceId, Map<String, String> fieldErrors) {
        this.code = code;
        this.message = message;
        this.traceId = traceId;
        this.fieldErrors = fieldErrors;
    }

    public static AppErrorResponse of(ErrorCode errorCode, String message, String traceId) {
        return new AppErrorResponse(errorCode.name(), message, traceId, null);
    }

    public static AppErrorResponse withFieldErrors(
            ErrorCode errorCode,
            String message,
            String traceId,
            Map<String, String> fieldErrors) {
        return new AppErrorResponse(errorCode.name(), message, traceId, fieldErrors);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getTraceId() {
        return traceId;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
