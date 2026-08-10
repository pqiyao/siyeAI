package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.ops.dto.AppFeatureSettings;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiV1AppRuntimeConfigTest {

    @Test
    void runtimeConfigExposesRechargeEntryVisibility() {
        AppFeatureSettingsService featureSettingsService = mock(AppFeatureSettingsService.class);
        AppFeatureSettings settings = new AppFeatureSettings();
        settings.setRechargeEntryVisible(false);
        when(featureSettingsService.getSettings()).thenReturn(settings);
        when(featureSettingsService.toMap(settings)).thenReturn(Map.of("rechargeEntryVisible", false));
        ApiV1AppController controller = new ApiV1AppController(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                featureSettingsService
        );

        ApiV1Result<Map<String, Object>> result = controller.runtimeConfig();

        assertThat(result.code()).isEqualTo(1);
        assertThat(result.data()).containsEntry("rechargeEntryVisible", false);
    }

    @Test
    void runtimeConfigExposesBothChatPresetEntrySwitches() {
        AppFeatureSettingsService featureSettingsService = mock(AppFeatureSettingsService.class);
        AppFeatureSettings settings = new AppFeatureSettings();
        settings.setSystemChatPresetEntryVisible(false);
        settings.setUserChatPresetEntryVisible(false);
        Map<String, Object> data = Map.of(
                "systemChatPresetEntryVisible", false,
                "userChatPresetEntryVisible", false
        );
        when(featureSettingsService.getSettings()).thenReturn(settings);
        when(featureSettingsService.toMap(settings)).thenReturn(data);
        ApiV1AppController controller = new ApiV1AppController(
                null, null, null, null, null, null, null, null, null, null, featureSettingsService
        );

        ApiV1Result<Map<String, Object>> result = controller.runtimeConfig();

        assertThat(result.data())
                .containsEntry("systemChatPresetEntryVisible", false)
                .containsEntry("userChatPresetEntryVisible", false);
    }

    @Test
    void runtimeConfigExposesLongTermMemoryAsDisabledByDefault() {
        AppFeatureSettingsService featureSettingsService = mock(AppFeatureSettingsService.class);
        AppFeatureSettings settings = new AppFeatureSettings();
        when(featureSettingsService.getSettings()).thenReturn(settings);
        when(featureSettingsService.toMap(settings)).thenReturn(Map.of("longTermMemoryEnabled", false));
        ApiV1AppController controller = new ApiV1AppController(
                null, null, null, null, null, null, null, null, null, null, featureSettingsService
        );

        ApiV1Result<Map<String, Object>> result = controller.runtimeConfig();

        assertThat(result.data()).containsEntry("longTermMemoryEnabled", false);
    }
}
