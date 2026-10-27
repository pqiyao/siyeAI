package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.ops.service.ChatPresetService;
import com.example.sillyspringboot.ops.service.ChatRuntimeDefaultSettingsService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminJiugaiChatPresetControllerTest {

    @Test
    void statusRequiresJsonBoolean() {
        ChatPresetService service = mock(ChatPresetService.class);
        AdminJiugaiChatPresetController controller = new AdminJiugaiChatPresetController(
                service, mock(ChatRuntimeDefaultSettingsService.class));

        assertThatThrownBy(() -> controller.status(12L, Map.of("enabled", "1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("enabled must be a boolean");
        assertThatThrownBy(() -> controller.status(12L, Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("enabled must be a boolean");

        verify(service, never()).updateStatus(12L, false);
    }

    @Test
    void statusAcceptsJsonBoolean() {
        ChatPresetService service = mock(ChatPresetService.class);
        when(service.updateStatus(12L, true)).thenReturn(true);
        AdminJiugaiChatPresetController controller = new AdminJiugaiChatPresetController(
                service, mock(ChatRuntimeDefaultSettingsService.class));

        controller.status(12L, Map.of("enabled", true));

        verify(service).updateStatus(12L, true);
    }

    @Test
    void globalDefaultParametersAreSavedThroughDedicatedSettingsService() {
        ChatRuntimeDefaultSettingsService settings = mock(ChatRuntimeDefaultSettingsService.class);
        when(settings.save(Map.of("maxTokens", 2536, "maxContext", 300000)))
                .thenReturn(new ChatRuntimeDefaultSettingsService.Settings(2536, 300000, "database"));
        when(settings.toMap(new ChatRuntimeDefaultSettingsService.Settings(2536, 300000, "database")))
                .thenReturn(Map.of("maxTokens", 2536, "maxContext", 300000, "source", "database"));
        AdminJiugaiChatPresetController controller = new AdminJiugaiChatPresetController(
                mock(ChatPresetService.class), settings);

        controller.updateGlobalDefault(Map.of("maxTokens", 2536, "maxContext", 300000));

        verify(settings).save(Map.of("maxTokens", 2536, "maxContext", 300000));
    }

    @Test
    void presetConfigurationAndRestoreDelegateToThePresetService() {
        ChatPresetService service = mock(ChatPresetService.class);
        when(service.updateAdminConfiguration(12L, "{\"generation\":{}}" )).thenReturn(true);
        when(service.restoreStConfiguration(12L)).thenReturn(true);
        AdminJiugaiChatPresetController controller = new AdminJiugaiChatPresetController(
                service, mock(ChatRuntimeDefaultSettingsService.class));

        controller.config(12L, Map.of("bundleJson", "{\"generation\":{}}"));
        controller.restoreConfig(12L);

        verify(service).updateAdminConfiguration(12L, "{\"generation\":{}}" );
        verify(service).restoreStConfiguration(12L);
    }
}
