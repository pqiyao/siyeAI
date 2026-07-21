package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatFailureDetailsTest {

    @Test
    void fromBusinessException_shouldPreferUpstreamCauseAndRedactSecrets() {
        BusinessException exception = new BusinessException(
                ErrorCode.UPSTREAM_ERROR,
                "服务暂时不可用，请稍后重试",
                new IllegalStateException("st runtime generate http 503: {\"error\":\"model not found\",\"authorization\":\"Bearer secret-token\"}")
        );

        ChatFailureDetails.TaskFailure failure = ChatFailureDetails.fromBusinessException(exception, "trace-upstream-1");

        assertThat(failure.errorCode()).isEqualTo("UPSTREAM_ERROR");
        assertThat(failure.httpStatus()).isEqualTo(503);
        assertThat(failure.errorMessage()).contains("UPSTREAM_ERROR");
        assertThat(failure.errorMessage()).contains("st runtime generate http 503");
        assertThat(failure.errorMessage()).contains("traceId=trace-upstream-1");
        assertThat(failure.errorMessage()).doesNotContain("secret-token");
    }

    @Test
    void fromCode_shouldKeepSpecificValidationReasonAndMapHttpStatus() {
        ChatFailureDetails.TaskFailure failure = ChatFailureDetails.fromCode(
                ErrorCode.VALIDATION_FAILED,
                "重新生成结果为空",
                "trace-validate-1"
        );

        assertThat(failure.errorCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(failure.httpStatus()).isEqualTo(400);
        assertThat(failure.errorMessage()).contains("VALIDATION_FAILED");
        assertThat(failure.errorMessage()).contains("重新生成结果为空");
        assertThat(failure.errorMessage()).contains("traceId=trace-validate-1");
    }
}
