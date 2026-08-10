package com.example.sillyspringboot.integration.sillytavern;

import com.example.sillyspringboot.ai.service.AiProviderCallException;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;

/** ST 不可达或返回错误时包装，对用户文案由 {@link BusinessException} 统一映射。 */
public class StUnavailableException extends BusinessException {

    private final AiProviderCallException providerFailure;

    public StUnavailableException(Throwable cause) {
        this(cause, null);
    }

    private StUnavailableException(Throwable cause, AiProviderCallException providerFailure) {
        // 商用品：对用户只暴露统一友好提示；细节通过 traceId + server logs 排查。
        super(ErrorCode.UPSTREAM_ERROR, "服务暂时不可用，请稍后重试", cause);
        this.providerFailure = providerFailure;
    }

    public static StUnavailableException providerFailure(AiProviderCallException failure) {
        if (failure == null) {
            throw new IllegalArgumentException("failure must not be null");
        }
        return new StUnavailableException(failure, failure);
    }

    public AiProviderCallException getProviderFailure() {
        return providerFailure;
    }
}
