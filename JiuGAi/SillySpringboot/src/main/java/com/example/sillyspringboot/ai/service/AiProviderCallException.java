package com.example.sillyspringboot.ai.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;

/**
 * Provider-call failure with an explicit retry decision. Request validation and
 * content-policy failures must not be turned into provider fallback attempts.
 */
public final class AiProviderCallException extends BusinessException {

    private final Integer httpStatus;
    private final boolean retryable;

    public AiProviderCallException(
            ErrorCode errorCode,
            String message,
            Integer httpStatus,
            boolean retryable,
            Throwable cause
    ) {
        super(errorCode, message, cause);
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public static AiProviderCallException http(int status, String message, Throwable cause) {
        boolean retryable = status == 408 || status == 425 || status == 429 || status >= 500;
        ErrorCode code;
        if (status == 401) {
            code = ErrorCode.UNAUTHORIZED;
        } else if (status == 403) {
            code = ErrorCode.FORBIDDEN;
        } else if (status == 404) {
            code = ErrorCode.NOT_FOUND;
        } else if (status == 429) {
            code = ErrorCode.RATE_LIMITED;
        } else if (status >= 500 || status == 408 || status == 425) {
            code = ErrorCode.UPSTREAM_ERROR;
        } else {
            code = ErrorCode.VALIDATION_FAILED;
        }
        return new AiProviderCallException(code, message, status, retryable, cause);
    }

    public static AiProviderCallException transientFailure(String message, Throwable cause) {
        return new AiProviderCallException(ErrorCode.UPSTREAM_ERROR, message, null, true, cause);
    }
}
