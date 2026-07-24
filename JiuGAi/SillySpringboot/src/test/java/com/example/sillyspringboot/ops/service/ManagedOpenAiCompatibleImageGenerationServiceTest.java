package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.model.AiProtocol;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.ai.service.AiProviderCallException;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagedOpenAiCompatibleImageGenerationServiceTest {

    @Test
    void officialImagePoolFallsBackAndRecordsHealthWithoutChangingBillingLayer() {
        OpenAiCompatibleImageGenerationService delegate = mock(OpenAiCompatibleImageGenerationService.class);
        AiRoutingService routing = mock(AiRoutingService.class);
        when(routing.isCapabilityEnabled(AiCapability.IMAGE)).thenReturn(true);
        when(routing.resolve(AiCapability.IMAGE)).thenReturn(List.of(
                provider(11L, "primary", "image-a"),
                provider(12L, "fallback", "image-b")
        ));
        when(delegate.generateManaged(anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(new BusinessException(ErrorCode.INTERNAL_ERROR, "primary failed"))
                .thenReturn(Map.of("images", List.of(Map.of("url", "data:image/png;base64,AA=="))));
        ManagedOpenAiCompatibleImageGenerationService service =
                new ManagedOpenAiCompatibleImageGenerationService(delegate, routing);

        Map<String, Object> result = service.generate("client", Map.of("prompt", "test"));

        assertThat(result).containsEntry("providerSource", "fallback").containsEntry("modelName", "image-b");
        verify(routing).recordFailure(11L, "primary failed");
        verify(routing).recordSuccess(12L);
    }

    @Test
    void disabledOfficialRouteDoesNotUseLegacyManagedConfiguration() {
        OpenAiCompatibleImageGenerationService delegate = mock(OpenAiCompatibleImageGenerationService.class);
        AiRoutingService routing = mock(AiRoutingService.class);
        when(routing.isCapabilityEnabled(AiCapability.IMAGE)).thenReturn(false);
        ManagedOpenAiCompatibleImageGenerationService service =
                new ManagedOpenAiCompatibleImageGenerationService(delegate, routing);

        assertThatThrownBy(() -> service.generate("client", Map.of("prompt", "test")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("模型路由");
        verify(routing, never()).resolve(any());
        verify(delegate, never()).generateManaged(anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void terminalProviderErrorDoesNotFallbackOrCountCircuitFailure() {
        OpenAiCompatibleImageGenerationService delegate = mock(OpenAiCompatibleImageGenerationService.class);
        AiRoutingService routing = mock(AiRoutingService.class);
        when(routing.isCapabilityEnabled(AiCapability.IMAGE)).thenReturn(true);
        when(routing.resolve(AiCapability.IMAGE)).thenReturn(List.of(
                provider(11L, "primary", "image-a"),
                provider(12L, "fallback", "image-b")
        ));
        when(delegate.generateManaged(anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt()))
                .thenThrow(AiProviderCallException.http(400, "content rejected", null));
        ManagedOpenAiCompatibleImageGenerationService service =
                new ManagedOpenAiCompatibleImageGenerationService(delegate, routing);

        assertThatThrownBy(() -> service.generate("client", Map.of("prompt", "test")))
                .isInstanceOf(AiProviderCallException.class)
                .hasMessage("content rejected");

        verify(delegate).generateManaged(anyString(), any(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt());
        verify(routing).recordConfigurationError(11L, "content rejected");
        verify(routing, never()).recordFailure(anyLong(), anyString());
    }

    private static AiRoutingService.ResolvedProvider provider(long id, String key, String model) {
        return new AiRoutingService.ResolvedProvider(
                id, key, key, "custom", "https://example.com/v1", "key",
                AiCapability.IMAGE, AiProtocol.OPENAI_IMAGE, model, "", 1);
    }
}
