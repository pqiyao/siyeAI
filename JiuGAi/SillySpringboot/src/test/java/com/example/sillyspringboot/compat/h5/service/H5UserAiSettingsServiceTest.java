package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.ai.service.AiChatModelService;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

class H5UserAiSettingsServiceTest {

    private final H5UserAiProviderService providerService = mock(H5UserAiProviderService.class);
    private final AiChatModelService chatModelService = mock(AiChatModelService.class);
    private final H5EntitlementService entitlementService = mock(H5EntitlementService.class);
    private final H5UserAiSettingsService service = new H5UserAiSettingsService(
            providerService, chatModelService, entitlementService);

    @Test
    void savesProviderAndModelLibraryThroughOneServiceBoundary() {
        AppUser user = new AppUser();
        user.setId(7L);
        when(entitlementService.resolveUser("client")).thenReturn(user);
        Map<String, Object> provider = new LinkedHashMap<>(Map.of(
                "mode", "custom",
                "modelName", "chat-model"
        ));
        Map<String, Object> models = new LinkedHashMap<>(Map.of(
                "models", List.of(Map.of("modelName", "chat-model", "enabled", true)),
                "defaultModelName", "other-model"
        ));

        service.save("client", Map.of("provider", provider, "chatModels", models));

        verify(providerService).save("client", provider);
        verify(chatModelService).saveUserModels(eq(7L), argThat(saved ->
                "chat-model".equals(saved.get("defaultModelName"))
                        && saved.containsKey("defaultModelId")
                        && saved.get("defaultModelId") == null));
    }

    @Test
    void rejectsRemovingTheActiveCustomModelBeforeAnyWrite() {
        Map<String, Object> provider = Map.of(
                "mode", "custom",
                "modelName", "chat-model"
        );
        Map<String, Object> models = Map.of(
                "models", List.of(Map.of("modelName", "other-model", "enabled", true))
        );

        assertThatThrownBy(() -> service.save(
                "client", Map.of("provider", provider, "chatModels", models)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须保留");

        verify(providerService, never()).save("client", provider);
        verify(chatModelService, never()).saveUserModels(7L, models);
    }
}
