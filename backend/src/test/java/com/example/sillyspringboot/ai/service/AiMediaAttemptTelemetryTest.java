package com.example.sillyspringboot.ai.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ops.generation.model.GenerationAttemptEvent;
import com.example.sillyspringboot.ops.generation.service.GenerationTelemetryService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiMediaAttemptTelemetryTest {

    @Test
    void recordsCapabilityFallbackHttpStatusAndByok() {
        GenerationTelemetryService telemetryService = mock(GenerationTelemetryService.class);
        AiMediaAttemptTelemetry telemetry = new AiMediaAttemptTelemetry(telemetryService);
        String requestId = telemetry.newRequestId(AiCapability.STT);
        AiMediaAttemptTelemetry.Attempt attempt = telemetry.start(
                requestId, AiCapability.STT, 2, "provider-2", "openai", "whisper-1", true);

        telemetry.failure(attempt, AiProviderCallException.http(503, "upstream unavailable", null));

        ArgumentCaptor<GenerationAttemptEvent> captor = ArgumentCaptor.forClass(GenerationAttemptEvent.class);
        verify(telemetryService).recordAsync(captor.capture());
        GenerationAttemptEvent event = captor.getValue();
        assertThat(event.clientMessageId()).isEqualTo(requestId);
        assertThat(event.routeKey()).isEqualTo(AiCapability.STT.defaultRouteKey());
        assertThat(event.attemptNo()).isEqualTo(2);
        assertThat(event.fallback()).isTrue();
        assertThat(event.byok()).isTrue();
        assertThat(event.httpStatus()).isEqualTo(503);
        assertThat(event.status()).isEqualTo("failed");
        assertThat(event.providerKey()).isEqualTo("provider-2");
        assertThat(event.model()).isEqualTo("whisper-1");
    }

    @Test
    void fallbackAttemptsFromOneMediaRequestShareCorrelationId() {
        GenerationTelemetryService telemetryService = mock(GenerationTelemetryService.class);
        AiMediaAttemptTelemetry telemetry = new AiMediaAttemptTelemetry(telemetryService);
        String requestId = telemetry.newRequestId(AiCapability.IMAGE);

        AiMediaAttemptTelemetry.Attempt primary = telemetry.start(
                requestId, AiCapability.IMAGE, 1, "primary", "openai", "image-a", false);
        AiMediaAttemptTelemetry.Attempt fallback = telemetry.start(
                requestId, AiCapability.IMAGE, 2, "fallback", "openai", "image-b", false);

        assertThat(primary.requestId()).isEqualTo(requestId);
        assertThat(fallback.requestId()).isEqualTo(requestId);
        assertThat(primary.attemptNo()).isEqualTo(1);
        assertThat(fallback.attemptNo()).isEqualTo(2);
    }

    @Test
    void recordsBusinessErrorCodeWithoutInventingHttpStatus() {
        GenerationTelemetryService telemetryService = mock(GenerationTelemetryService.class);
        AiMediaAttemptTelemetry telemetry = new AiMediaAttemptTelemetry(telemetryService);
        AiMediaAttemptTelemetry.Attempt attempt = telemetry.start(
                AiCapability.TTS, 1, "provider-1", "openai", "tts-1", false);

        telemetry.failure(attempt, new BusinessException(ErrorCode.VALIDATION_FAILED, "invalid voice"));

        ArgumentCaptor<GenerationAttemptEvent> captor = ArgumentCaptor.forClass(GenerationAttemptEvent.class);
        verify(telemetryService).recordAsync(captor.capture());
        assertThat(captor.getValue().httpStatus()).isNull();
        assertThat(captor.getValue().errorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED.name());
        assertThat(captor.getValue().fallback()).isFalse();
    }

    @Test
    void telemetryFailureNeverChangesMediaResult() {
        GenerationTelemetryService telemetryService = mock(GenerationTelemetryService.class);
        doThrow(new IllegalStateException("telemetry unavailable"))
                .when(telemetryService).recordAsync(any(GenerationAttemptEvent.class));
        AiMediaAttemptTelemetry telemetry = new AiMediaAttemptTelemetry(telemetryService);
        AiMediaAttemptTelemetry.Attempt attempt = telemetry.start(
                AiCapability.IMAGE, 1, "provider-1", "openai", "image-1", false);

        assertThatCode(() -> telemetry.success(attempt)).doesNotThrowAnyException();
        assertThatCode(() -> telemetry.failure(attempt, new RuntimeException("provider failed")))
                .doesNotThrowAnyException();
    }
}
