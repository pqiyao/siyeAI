package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.service.AiProviderFailurePolicy;
import com.example.sillyspringboot.ai.service.AiMediaAttemptTelemetry;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ManagedOpenAiCompatibleImageGenerationService implements ImageGenerationEngine {

    private final OpenAiCompatibleImageGenerationService delegate;
    private final AiRoutingService routingService;
    private final AiMediaAttemptTelemetry attemptTelemetry;

    public ManagedOpenAiCompatibleImageGenerationService(
            OpenAiCompatibleImageGenerationService delegate,
            AiRoutingService routingService
    ) {
        this(delegate, routingService, null);
    }

    @Autowired
    public ManagedOpenAiCompatibleImageGenerationService(
            OpenAiCompatibleImageGenerationService delegate,
            AiRoutingService routingService,
            AiMediaAttemptTelemetry attemptTelemetry
    ) {
        this.delegate = delegate;
        this.routingService = routingService;
        this.attemptTelemetry = attemptTelemetry;
    }

    @Override
    public String engineName() {
        return "managed_openai_compatible";
    }

    @Override
    public Map<String, Object> generate(String clientUid, Map<String, Object> payload) {
        if (!routingService.isCapabilityEnabled(AiCapability.IMAGE)) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "官方生图路由未启用，请在模型路由中配置 IMAGE 能力");
        }
        List<AiRoutingService.ResolvedProvider> providers = routingService.resolve(AiCapability.IMAGE);
        if (providers.isEmpty()) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "系统生图供应商池暂无可用节点");
        }

        BusinessException last = null;
        int attemptNo = 0;
        String telemetryRequestId = attemptTelemetry == null
                ? "" : attemptTelemetry.newRequestId(AiCapability.IMAGE);
        for (AiRoutingService.ResolvedProvider provider : providers) {
            attemptNo++;
            AiMediaAttemptTelemetry.Attempt attempt = startTelemetry(telemetryRequestId, provider, attemptNo);
            try {
                Map<String, Object> generated = delegate.generateManaged(
                        clientUid,
                        payload,
                        provider.vendor(),
                        provider.modelName(),
                        provider.apiKey(),
                        "custom".equalsIgnoreCase(provider.vendor()) ? provider.baseUrl() : "",
                        provider.connectTimeoutSeconds(),
                        provider.requestTimeoutSeconds()
                );
                recordSuccessQuietly(provider.deploymentId());
                successTelemetry(attempt);
                Map<String, Object> result = new LinkedHashMap<>(generated);
                result.put("providerSource", provider.displayName());
                result.put("modelName", provider.modelName());
                return result;
            } catch (BusinessException ex) {
                failureTelemetry(attempt, ex);
                last = ex;
                if (!AiProviderFailurePolicy.shouldFallback(ex)) {
                    recordConfigurationErrorQuietly(provider.deploymentId(), ex.getMessage());
                    throw ex;
                }
                if (AiProviderFailurePolicy.shouldCountCircuitFailure(ex)) {
                    recordFailureQuietly(provider.deploymentId(), ex.getMessage());
                }
            }
        }
        throw last == null
                ? new BusinessException(ErrorCode.SERVICE_BUSY, "系统生图供应商池暂无可用节点")
                : last;
    }

    private void recordFailureQuietly(long deploymentId, String message) {
        try {
            routingService.recordFailure(deploymentId, message);
        } catch (RuntimeException ignored) {
            // Routing telemetry must not replace the provider's real error.
        }
    }

    private void recordSuccessQuietly(long deploymentId) {
        try {
            routingService.recordSuccess(deploymentId);
        } catch (RuntimeException ignored) {
            // Routing telemetry must not turn a successful image into a failed request.
        }
    }

    private void recordConfigurationErrorQuietly(long deploymentId, String message) {
        try {
            routingService.recordConfigurationError(deploymentId, message);
        } catch (RuntimeException ignored) {
            // Routing telemetry must not replace the provider's real error.
        }
    }

    private AiMediaAttemptTelemetry.Attempt startTelemetry(
            String requestId,
            AiRoutingService.ResolvedProvider provider,
            int attemptNo
    ) {
        if (attemptTelemetry == null) return null;
        return attemptTelemetry.start(
                requestId, AiCapability.IMAGE, attemptNo, provider.providerKey(), provider.vendor(),
                provider.modelName(), false);
    }

    private void successTelemetry(AiMediaAttemptTelemetry.Attempt attempt) {
        if (attemptTelemetry != null) attemptTelemetry.success(attempt);
    }

    private void failureTelemetry(AiMediaAttemptTelemetry.Attempt attempt, Throwable error) {
        if (attemptTelemetry != null) attemptTelemetry.failure(attempt, error);
    }
}
