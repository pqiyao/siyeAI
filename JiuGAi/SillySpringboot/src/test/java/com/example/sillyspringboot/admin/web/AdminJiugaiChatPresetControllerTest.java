package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.ops.service.ChatPresetService;
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
        AdminJiugaiChatPresetController controller = new AdminJiugaiChatPresetController(service);

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
        AdminJiugaiChatPresetController controller = new AdminJiugaiChatPresetController(service);

        controller.status(12L, Map.of("enabled", true));

        verify(service).updateStatus(12L, true);
    }
}
