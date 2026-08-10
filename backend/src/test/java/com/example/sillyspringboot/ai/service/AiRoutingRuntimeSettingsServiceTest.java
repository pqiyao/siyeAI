package com.example.sillyspringboot.ai.service;

import com.example.sillyspringboot.ai.config.AiRoutingProperties;
import com.example.sillyspringboot.ops.entity.AppRuntimeSetting;
import com.example.sillyspringboot.ops.mapper.AppRuntimeSettingMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRoutingRuntimeSettingsServiceTest {

    @Test
    void legacyJsonUsesSafeVisionDefaultAndExplicitSaveCanEnableIt() {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        AppRuntimeSetting row = new AppRuntimeSetting();
        row.setSettingValue("{\"enabled\":true,\"chatCanaryPercent\":10,\"imageEnabled\":false}");
        when(mapper.findByKey("ai_routing_runtime_settings")).thenReturn(row);
        AiRoutingProperties defaults = new AiRoutingProperties();
        defaults.setVisionEnabled(false);
        AiRoutingRuntimeSettingsService service =
                new AiRoutingRuntimeSettingsService(mapper, defaults, new ObjectMapper());

        assertThat(service.current().visionEnabled()).isFalse();

        AiRoutingRuntimeSettingsService.Settings saved = service.save(Map.of(
                "confirmed", true,
                "visionEnabled", true
        ));
        assertThat(saved.visionEnabled()).isTrue();
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsert(eq("ai_routing_runtime_settings"), json.capture());
        assertThat(json.getValue()).contains("\"visionEnabled\":true");
    }
}
