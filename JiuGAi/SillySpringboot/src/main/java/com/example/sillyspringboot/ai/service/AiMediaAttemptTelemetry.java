package com.example.sillyspringboot.ai.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ops.generation.model.GenerationAttemptEvent;
import com.example.sillyspringboot.ops.generation.service.GenerationTelemetryService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AiMediaAttemptTelemetry {

    public record Attempt(
            String requestId,
            AiCapability capability,
            int attemptNo,
            String providerKey,
            String providerSource,
            String modelName,
            boolean byok,
            LocalDateTime startedAt
    ) {}

    private final GenerationTelemetryService telemetryService;

    public AiMediaAttemptTelemetry(GenerationTelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    public String newRequestId(AiCapability capability) {
        return capability.name().toLowerCase() + "-" + UUID.randomUUID();
    }

    public Attempt start(
            AiCapability capability,
            int attemptNo,
            String providerKey,
            String providerSource,
            String modelName,
            boolean byok
    ) {
        return start(newRequestId(capability), capability, attemptNo, providerKey, providerSource, modelName, byok);
    }

    public Attempt start(
            String requestId,
            AiCapability capability,
            int attemptNo,
            String providerKey,
            String providerSource,
            String modelName,
            boolean byok
    ) {
        return new Attempt(
                safe(requestId).isBlank() ? newRequestId(capability) : safe(requestId),
                capability,
                Math.max(1, attemptNo),
                safe(providerKey),
                safe(providerSource),
                safe(modelName),
                byok,
                LocalDateTime.now());
    }

    public void success(Attempt attempt) {
        record(attempt, "success", null);
    }

    public void failure(Attempt attempt, Throwable error) {
        record(attempt, "failed", error);
    }

    private void record(Attempt attempt, String status, Throwable error) {
        if (attempt == null) return;
        try {
            Integer httpStatus = error instanceof AiProviderCallException provider ? provider.getHttpStatus() : null;
            String errorCode = error instanceof BusinessException business && business.getErrorCode() != null
                    ? business.getErrorCode().name() : "";
            telemetryService.recordAsync(new GenerationAttemptEvent(
                    null,
                    attempt.requestId(),
                    attempt.attemptNo(),
                    attempt.providerKey(),
                    attempt.capability().defaultRouteKey(),
                    attempt.providerSource(),
                    attempt.modelName(),
                    attempt.byok(),
                    attempt.attemptNo() > 1,
                    attempt.startedAt(),
                    null,
                    LocalDateTime.now(),
                    httpStatus,
                    status,
                    errorCode,
                    null,
                    false,
                    null,
                    false
            ));
        } catch (RuntimeException ignored) {
            // Observability must never change the media result.
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
