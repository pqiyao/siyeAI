package com.example.sillyspringboot.chat;

import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.chat.service.AppChatCompatibilityService;
import com.example.sillyspringboot.integration.sillytavern.StClient;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppChatCompatibilityServiceTest {

    @Test
    void decideForGeneration_shouldUseRuntimeWithoutFrontendInspectionByDefault() {
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

        assertThat(decision.frontendBridgeRequired()).isFalse();
        assertThat(decision.recommendedMode()).isEqualTo("runtime");
        assertThat(decision.effectiveMode()).isEqualTo("runtime");
        assertThat(decision.reasons()).isEmpty();
        assertThat(decision.markers()).isEmpty();
    }

    @Test
    void decideForGeneration_shouldOnlyWarnAboutFrontendMarkersInAutoMode() {
        AppChatProperties properties = new AppChatProperties();
        properties.getCompatibility().setMode("auto");
        StClient stClient = mock(StClient.class);
        when(stClient.readWorldbookRaw("complex_world")).thenReturn("""
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
                106L, null, null, null, List.of("complex_world"), "");

        assertThat(decision.frontendBridgeRequired()).isFalse();
        assertThat(decision.recommendedMode()).isEqualTo("frontend_bridge");
        assertThat(decision.effectiveMode()).isEqualTo("runtime");
        assertThat(decision.reasons()).contains("worldbook:complex_world");
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

    @Test
    void decideForGeneration_shouldRouteAdvancedCharacterMacrosToRealFrontend() {
        AppChatProperties properties = new AppChatProperties();
        properties.getCompatibility().setMode("frontend_bridge");
        properties.getCompatibility().setFrontendBridgeEnabled(true);
        StClient stClient = mock(StClient.class);
        AppChatCompatibilityService service = new AppChatCompatibilityService(properties, stClient);
        StCharacterDetail detail = mock(StCharacterDetail.class);
        when(detail.rawJson()).thenReturn("{\"description\":\"{{persona}} {{getvar::trust}}\"}");

        AppChatCompatibilityService.Decision decision = service.decideForGeneration(
                102L,
                null,
                detail,
                null,
                List.of(),
                ""
        );

        assertThat(decision.frontendBridgeRequired()).isTrue();
        assertThat(decision.effectiveMode()).isEqualTo("frontend_bridge");
        assertThat(decision.markers()).contains(
                "character:advanced_macro:persona",
                "character:advanced_macro:getvar"
        );
    }

    @Test
    void decideForGeneration_shouldKeepRuntimeForItsSupportedNameMacros() {
        AppChatProperties properties = new AppChatProperties();
        StClient stClient = mock(StClient.class);
        AppChatCompatibilityService service = new AppChatCompatibilityService(properties, stClient);
        StCharacterDetail detail = mock(StCharacterDetail.class);
        when(detail.rawJson()).thenReturn("{\"description\":\"{{user}} meets {{char}}: {{lastChatMessage}}\"}");

        AppChatCompatibilityService.Decision decision = service.decideForGeneration(
                103L,
                null,
                detail,
                null,
                List.of(),
                ""
        );

        assertThat(decision.frontendBridgeRequired()).isFalse();
        assertThat(decision.effectiveMode()).isEqualTo("runtime");
    }

    @Test
    void decideForGeneration_shouldRouteAdvancedPromptManagerPresetToRealFrontend() {
        AppChatProperties properties = new AppChatProperties();
        properties.getCompatibility().setMode("frontend_bridge");
        properties.getCompatibility().setFrontendBridgeEnabled(true);
        StClient stClient = mock(StClient.class);
        AppChatCompatibilityService service = new AppChatCompatibilityService(properties, stClient);
        String presetBundle = """
                {
                  "generation": {
                    "prompts": [
                      {
                        "identifier": "plot-state",
                        "role": "assistant",
                        "content": "state",
                        "injection_position": 1,
                        "injection_depth": 4,
                        "injection_order": 80,
                        "injection_trigger": ["normal"],
                        "forbid_overrides": true
                      }
                    ],
                    "prompt_order": [
                      {"character_id": "42", "order": [{"identifier": "plot-state", "enabled": true}]}
                    ]
                  }
                }
                """;

        AppChatCompatibilityService.Decision decision = service.decideForGeneration(
                104L,
                null,
                null,
                null,
                List.of(),
                presetBundle
        );

        assertThat(decision.frontendBridgeRequired()).isTrue();
        assertThat(decision.effectiveMode()).isEqualTo("frontend_bridge");
        assertThat(decision.reasons()).contains("preset");
        assertThat(decision.markers()).contains(
                "preset:prompt_manager_role:assistant",
                "preset:prompt_manager_absolute_injection",
                "preset:prompt_manager_forbid_overrides",
                "preset:prompt_manager_generation_trigger",
                "preset:prompt_manager_character_order"
        );
    }

    @Test
    void decideForGeneration_shouldKeepSimpleRelativeSystemPresetOnRuntime() {
        AppChatProperties properties = new AppChatProperties();
        StClient stClient = mock(StClient.class);
        AppChatCompatibilityService service = new AppChatCompatibilityService(properties, stClient);
        String presetBundle = """
                {
                  "prompts": [
                    {
                      "identifier": "style",
                      "role": "system",
                      "content": "Write vividly.",
                      "injection_position": 0,
                      "injection_depth": 4,
                      "injection_order": 100,
                      "injection_trigger": [],
                      "forbid_overrides": false
                    }
                  ],
                  "prompt_order": [
                    {"character_id": 100001, "order": [{"identifier": "style", "enabled": true}]}
                  ]
                }
                """;

        AppChatCompatibilityService.Decision decision = service.decideForGeneration(
                105L,
                null,
                null,
                null,
                List.of(),
                presetBundle
        );

        assertThat(decision.frontendBridgeRequired()).isFalse();
        assertThat(decision.effectiveMode()).isEqualTo("runtime");
    }

    @Test
    void decideForGeneration_shouldRouteUnsupportedNativeWorldInfoFeaturesToFrontend() {
        AppChatProperties properties = new AppChatProperties();
        properties.getCompatibility().setMode("frontend_bridge");
        properties.getCompatibility().setFrontendBridgeEnabled(true);
        StClient stClient = mock(StClient.class);
        when(stClient.readWorldbookRaw("complex_world")).thenReturn("""
                {
                  "entries": {
                    "1": {
                      "content": "faction state",
                      "position": 6,
                      "group": "factions",
                      "useGroupScoring": true,
                      "sticky": 3
                    }
                  }
                }
                """);
        AppChatCompatibilityService service = new AppChatCompatibilityService(properties, stClient);

        AppChatCompatibilityService.Decision decision = service.decideForGeneration(
                104L,
                null,
                null,
                null,
                List.of("complex_world"),
                ""
        );

        assertThat(decision.frontendBridgeRequired()).isTrue();
        assertThat(decision.effectiveMode()).isEqualTo("frontend_bridge");
        assertThat(decision.markers()).contains(
                "worldbook:complex_world:world_info_special_position",
                "worldbook:complex_world:world_info_group",
                "worldbook:complex_world:world_info_group_scoring",
                "worldbook:complex_world:world_info_sticky"
        );
    }
}
