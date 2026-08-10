package com.example.sillyspringboot.shared.error;

/**
 * 稳定错误码，供日志检索与前端展示映射；不暴露内部实现细节。
 */
public enum ErrorCode {

    VALIDATION_FAILED,
    NOT_FOUND,
    CONFLICT,
    UNAUTHORIZED,
    FORBIDDEN,
    RATE_LIMITED,
    SERVICE_BUSY,
    UPSTREAM_ERROR,
    /** 能力未实现或当前版本不可用（如尚未接通的 StAdapter 方法） */
    UNSUPPORTED_OPERATION,
    INTERNAL_ERROR
}
