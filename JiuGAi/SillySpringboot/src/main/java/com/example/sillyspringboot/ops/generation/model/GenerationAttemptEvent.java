package com.example.sillyspringboot.ops.generation.model;

import java.time.LocalDateTime;

public record GenerationAttemptEvent(
        Long conversationId,
        String clientMessageId,
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
        Integer promptTokens,
        boolean promptTokensEstimated,
        Integer completionTokens,
        boolean completionTokensEstimated
) {
}
