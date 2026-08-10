package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.dto.AppFeatureSettings;
import com.example.sillyspringboot.ops.entity.AppRuntimeSetting;
import com.example.sillyspringboot.ops.mapper.AppRuntimeSettingMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppFeatureSettingsServiceTest {

    @Test
    void illustrationEntryDefaultsToEnabled() {
        AppFeatureSettingsService service = new AppFeatureSettingsService(mock(AppRuntimeSettingMapper.class));

        assertTrue(service.getSettings().isIllustrationEntryEnabled());
        assertTrue((Boolean) service.toMap(service.getSettings()).get("illustrationEntryEnabled"));
        assertTrue(service.getSettings().isRechargeEntryVisible());
        assertTrue((Boolean) service.toMap(service.getSettings()).get("rechargeEntryVisible"));
        assertTrue(service.getSettings().isCheckinEntryVisible());
        assertTrue((Boolean) service.toMap(service.getSettings()).get("checkinEntryVisible"));
        assertTrue(service.getSettings().isSystemChatPresetEntryVisible());
        assertTrue((Boolean) service.toMap(service.getSettings()).get("systemChatPresetEntryVisible"));
        assertTrue(service.getSettings().isUserChatPresetEntryVisible());
        assertTrue((Boolean) service.toMap(service.getSettings()).get("userChatPresetEntryVisible"));
        assertFalse(service.getSettings().isLongTermMemoryEnabled());
        assertFalse((Boolean) service.toMap(service.getSettings()).get("longTermMemoryEnabled"));
        assertFalse(service.getSettings().isUserCharacterPromotionEnabled());
        assertFalse((Boolean) service.toMap(service.getSettings()).get("userCharacterPromotionEnabled"));
        assertFalse(service.getSettings().isSemanticAnnotationEnabled());
        assertFalse((Boolean) service.toMap(service.getSettings()).get("semanticAnnotationEnabled"));
        assertTrue(service.getSettings().getSemanticAnnotationRouteKey().isEmpty());
    }

    @Test
    void longTermMemorySwitchCanBeDisabledAndIsSerialized() {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        AppFeatureSettingsService service = new AppFeatureSettingsService(mapper);

        AppFeatureSettings saved = service.saveSettings(Map.of("longTermMemoryEnabled", false));

        assertFalse(saved.isLongTermMemoryEnabled());
        assertFalse((Boolean) service.toMap(saved).get("longTermMemoryEnabled"));
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsert(eq("app_feature_settings"), json.capture());
        assertTrue(json.getValue().contains("\"longTermMemoryEnabled\":false"));
    }

    @Test
    void semanticAnnotationRequiresExplicitSwitchAndDedicatedRoute() {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        AppFeatureSettingsService service = new AppFeatureSettingsService(mapper);

        AppFeatureSettings saved = service.saveSettings(Map.of(
                "semanticAnnotationEnabled", true,
                "semanticAnnotationRouteKey", "CHAT.SEMANTIC-CHEAP"
        ));

        assertTrue(saved.isSemanticAnnotationEnabled());
        assertTrue(saved.getSemanticAnnotationRouteKey().equals("chat.semantic-cheap"));
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsert(eq("app_feature_settings"), json.capture());
        assertTrue(json.getValue().contains("\"semanticAnnotationEnabled\":true"));
        assertTrue(json.getValue().contains("\"semanticAnnotationRouteKey\":\"chat.semantic-cheap\""));
    }

    @Test
    void userCharacterPromotionMustBeExplicitlyEnabled() {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        AppFeatureSettingsService service = new AppFeatureSettingsService(mapper);

        AppFeatureSettings saved = service.saveSettings(Map.of("userCharacterPromotionEnabled", true));

        assertTrue(saved.isUserCharacterPromotionEnabled());
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsert(eq("app_feature_settings"), json.capture());
        assertTrue(json.getValue().contains("\"userCharacterPromotionEnabled\":true"));
    }

    @Test
    void illustrationEntrySwitchIsPersisted() {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        AppFeatureSettingsService service = new AppFeatureSettingsService(mapper);

        AppFeatureSettings saved = service.saveSettings(Map.of("illustrationEntryEnabled", false));

        assertFalse(saved.isIllustrationEntryEnabled());
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsert(eq("app_feature_settings"), json.capture());
        assertTrue(json.getValue().contains("\"illustrationEntryEnabled\":false"));
    }

    @Test
    void legacyJsonWithoutRechargeFieldKeepsEntryVisible() {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        AppRuntimeSetting legacy = new AppRuntimeSetting();
        legacy.setSettingValue("{\"loginEnabled\":false,\"illustrationEntryEnabled\":true}");
        when(mapper.findByKey("app_feature_settings")).thenReturn(legacy);
        AppFeatureSettingsService service = new AppFeatureSettingsService(mapper);

        AppFeatureSettings settings = service.getSettings();

        assertFalse(settings.isLoginEnabled());
        assertTrue(settings.isRechargeEntryVisible());
        assertTrue((Boolean) service.toMap(settings).get("rechargeEntryVisible"));
        assertTrue(settings.isCheckinEntryVisible());
        assertTrue((Boolean) service.toMap(settings).get("checkinEntryVisible"));
    }

    @Test
    void rechargeEntryCanBeExplicitlyHiddenAndIsSerialized() {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        AppFeatureSettingsService service = new AppFeatureSettingsService(mapper);

        AppFeatureSettings saved = service.saveSettings(Map.of("rechargeEntryVisible", false));

        assertFalse(saved.isRechargeEntryVisible());
        assertFalse((Boolean) service.toMap(saved).get("rechargeEntryVisible"));
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsert(eq("app_feature_settings"), json.capture());
        assertTrue(json.getValue().contains("\"rechargeEntryVisible\":false"));
    }

    @Test
    void unrelatedPartialUpdatePreservesExplicitRechargeVisibility() {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        AppRuntimeSetting current = new AppRuntimeSetting();
        current.setSettingValue("{\"loginEnabled\":true,\"rechargeEntryVisible\":false}");
        when(mapper.findByKey("app_feature_settings")).thenReturn(current);
        AppFeatureSettingsService service = new AppFeatureSettingsService(mapper);

        AppFeatureSettings saved = service.saveSettings(Map.of("loginEnabled", false));

        assertFalse(saved.isLoginEnabled());
        assertFalse(saved.isRechargeEntryVisible());
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsert(eq("app_feature_settings"), json.capture());
        assertTrue(json.getValue().contains("\"rechargeEntryVisible\":false"));
    }

    @Test
    void checkinEntryCanBeExplicitlyHiddenAndIsSerialized() {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        AppFeatureSettingsService service = new AppFeatureSettingsService(mapper);

        AppFeatureSettings saved = service.saveSettings(Map.of("checkinEntryVisible", false));

        assertFalse(saved.isCheckinEntryVisible());
        assertFalse((Boolean) service.toMap(saved).get("checkinEntryVisible"));
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsert(eq("app_feature_settings"), json.capture());
        assertTrue(json.getValue().contains("\"checkinEntryVisible\":false"));
    }

    @Test
    void chatPresetEntrySwitchesCanBeExplicitlyHiddenAndAreSerialized() {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        AppFeatureSettingsService service = new AppFeatureSettingsService(mapper);

        AppFeatureSettings saved = service.saveSettings(Map.of(
                "systemChatPresetEntryVisible", false,
                "userChatPresetEntryVisible", false
        ));

        assertFalse(saved.isSystemChatPresetEntryVisible());
        assertFalse(saved.isUserChatPresetEntryVisible());
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsert(eq("app_feature_settings"), json.capture());
        assertTrue(json.getValue().contains("\"systemChatPresetEntryVisible\":false"));
        assertTrue(json.getValue().contains("\"userChatPresetEntryVisible\":false"));
    }

    @Test
    void unrelatedPartialUpdatePreservesExplicitCheckinVisibility() {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        AppRuntimeSetting current = new AppRuntimeSetting();
        current.setSettingValue("{\"loginEnabled\":true,\"checkinEntryVisible\":false}");
        when(mapper.findByKey("app_feature_settings")).thenReturn(current);
        AppFeatureSettingsService service = new AppFeatureSettingsService(mapper);

        AppFeatureSettings saved = service.saveSettings(Map.of("loginEnabled", false));

        assertFalse(saved.isLoginEnabled());
        assertFalse(saved.isCheckinEntryVisible());
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsert(eq("app_feature_settings"), json.capture());
        assertTrue(json.getValue().contains("\"checkinEntryVisible\":false"));
    }
}
