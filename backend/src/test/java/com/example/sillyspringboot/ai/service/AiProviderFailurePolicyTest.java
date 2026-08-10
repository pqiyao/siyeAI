package com.example.sillyspringboot.ai.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderFailurePolicyTest {

    @Test
    void retriesTransientHttpFailuresOnly() {
        assertThat(AiProviderFailurePolicy.shouldFallback(AiProviderCallException.http(408, "timeout", null))).isTrue();
        assertThat(AiProviderFailurePolicy.shouldFallback(AiProviderCallException.http(429, "limited", null))).isTrue();
        assertThat(AiProviderFailurePolicy.shouldFallback(AiProviderCallException.http(503, "busy", null))).isTrue();
        assertThat(AiProviderFailurePolicy.shouldFallback(AiProviderCallException.http(400, "bad request", null))).isFalse();
        assertThat(AiProviderFailurePolicy.shouldFallback(AiProviderCallException.http(401, "bad key", null))).isFalse();
        assertThat(AiProviderFailurePolicy.shouldFallback(AiProviderCallException.http(404, "model missing", null))).isFalse();
    }

    @Test
    void validationAndContentStyleFailuresAreTerminal() {
        assertThat(AiProviderFailurePolicy.shouldFallback(
                new BusinessException(ErrorCode.VALIDATION_FAILED, "content rejected"))).isFalse();
        assertThat(AiProviderFailurePolicy.shouldFallback(
                new BusinessException(ErrorCode.FORBIDDEN, "forbidden"))).isFalse();
        assertThat(AiProviderFailurePolicy.shouldFallback(
                new BusinessException(ErrorCode.UPSTREAM_ERROR, "transport"))).isTrue();
    }
}
