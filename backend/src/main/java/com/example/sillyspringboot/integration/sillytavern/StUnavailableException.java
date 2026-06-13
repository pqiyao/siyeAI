package com.example.sillyspringboot.integration.sillytavern;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;

/** ST 不可达或返回错误时包装，对用户文案由 {@link BusinessException} 统一映射。 */
public class StUnavailableException extends BusinessException {

    public StUnavailableException(Throwable cause) {
        // 商用品：对用户只暴露统一友好提示；细节通过 traceId + server logs 排查。
        super(ErrorCode.UPSTREAM_ERROR, "服务暂时不可用，请稍后重试", cause);
    }
}
