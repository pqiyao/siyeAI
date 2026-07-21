package com.example.sillyspringboot.chat;

import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.chat.service.AppChatCompatibilityService;
import com.example.sillyspringboot.integration.sillytavern.StClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppChatCompatibilityServiceTest {

    @Test
    void decideForGeneration_shouldFlagPromptTemplateWorldbookAndFallbackToRuntimeByDefault() {
        AppChatProperties properties = new AppChatProperties();
        StClient stClient = mock(StClient.class);
        when(stClient.readWorldbookRaw("创世回廊1.5")).thenReturn("""
                {
                  "entries": {
                    "1": {
                      "content": "state table",
                      "comment": "@@generate_after @INJECT",
                      "is_ejs_processed": [true]
                    }
                  }
                }
                """);

        AppChatCompatibilityService service = new AppChatCompatibilityService(properties, stClient);

        AppChatCompatibilityService.Decision decision = service.decideForGeneration(
                100L,
                null,
                null,
                null,
                List.of("创世回廊1.5"),
                ""
        );

        assertThat(decision.frontendBridgeRequired()).isTrue();
        assertThat(decision.recommendedMode()).isEqualTo("frontend_bridge");
        assertThat(decision.effectiveMode()).isEqualTo("runtime_fallback");
        assertThat(decision.reasons()).contains("worldbook:创世回廊1.5");
        assertThat(decision.markers()).anyMatch(x -> x.contains("prompt_template_generate_after"));
    }

    @Test
    void decideForGeneration_shouldKeepPlainRuntimeWhenNoExtensionMarkersExist() {
        AppChatProperties properties = new AppChatProperties();
        StClient stClient = mock(StClient.class);
        when(stClient.readWorldbookRaw("plain_world")).thenReturn("{\"entries\":{\"1\":{\"content\":\"ordinary lore\"}}}");

        AppChatCompatibilityService service = new AppChatCompatibilityService(properties, stClient);

        AppChatCompatibilityService.Decision decision = service.decideForGeneration(
                101L,
                null,
                null,
                null,
                List.of("plain_world"),
                ""
        );

        assertThat(decision.frontendBridgeRequired()).isFalse();
        assertThat(decision.recommendedMode()).isEqualTo("runtime");
        assertThat(decision.effectiveMode()).isEqualTo("runtime");
    }
}
