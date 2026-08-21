package com.example.sillyspringboot.ops.generation.model;

import java.time.LocalDateTime;

public record GenerationAttemptEvent(
        Long conversationId,
        String clientMessageId,
        String traceId,
        int attemptNo,
        String providerKey,
        String routeKey,
        String providerSource,
        String model,
        boolean byok,
        Long effectivePresetId,
        Integer effectiveMaxContext,
        Integer effectiveMaxTokens,
        String effectiveProvider,
        String effectiveApiSource,
        boolean fallback,
        LocalDateTime startedAt,
        LocalDateTime firstTokenAt,
        LocalDateTime finishedAt,
        Integer httpStatus,
        String status,
        String errorCode,
        String errorMessage,
        Integer promptTokens,
        boolean promptTokensEstimated,
        Integer completionTokens,
        boolean completionTokensEstimated
) {
    public GenerationAttemptEvent(
            Long conversationId,
            String clientMessageId,
            String traceId,
            int attemptNo,
            String providerKey,
            String routeKey,
            String providerSource,
            String model,
            boolean byok,
            boolean fallback,
            LocalDateTime startedAt,
            LocalDateTime firstTokenAt,
            LocalDateTime finishedAt,
            Integer httpStatus,
            String status,
            String errorCode,
            String errorMessage,
            Integer promptTokens,
            boolean promptTokensEstimated,
            Integer completionTokens,
            boolean completionTokensEstimated
    ) {
        this(conversationId, clientMessageId, traceId, attemptNo, providerKey, routeKey, providerSource, model,
                byok, null, null, null, null, null, fallback, startedAt, firstTokenAt, finishedAt, httpStatus,
                status, errorCode, errorMessage, promptTokens, promptTokensEstimated, completionTokens,
                completionTokensEstimated);
    }
}
