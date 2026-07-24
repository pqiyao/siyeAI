package com.example.sillyspringboot.ai.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;

public final class AiProviderFailurePolicy {

    private AiProviderFailurePolicy() {
    }

    public static boolean shouldFallback(BusinessException error) {
        if (error instanceof AiProviderCallException providerError) {
            return providerError.isRetryable();
        }
        ErrorCode code = error == null ? null : error.getErrorCode();
        return code == ErrorCode.RATE_LIMITED
                || code == ErrorCode.SERVICE_BUSY
                || code == ErrorCode.UPSTREAM_ERROR
                || code == ErrorCode.INTERNAL_ERROR;
    }

    public static boolean shouldCountCircuitFailure(BusinessException error) {
        return shouldFallback(error);
    }
}
