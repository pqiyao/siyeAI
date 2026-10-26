package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.entity.AppRuntimeSetting;
import com.example.sillyspringboot.ops.mapper.AppRuntimeSettingMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRuntimeDefaultSettingsServiceTest {

    @Test
    void absentSettingKeepsLegacyRuntimeValues() {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        ChatRuntimeDefaultSettingsService service = new ChatRuntimeDefaultSettingsService(mapper, new ObjectMapper());

        assertThat(service.current()).isEqualTo(
                new ChatRuntimeDefaultSettingsService.Settings(1000, 65536, "legacy-default"));
    }

    @Test
    void currentReadsTheIndependentRuntimeSetting() {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        AppRuntimeSetting row = new AppRuntimeSetting();
        row.setSettingValue("{\"maxTokens\":2536,\"maxContext\":300000}");
        when(mapper.findByKey("chat_runtime_default")).thenReturn(row);
        ChatRuntimeDefaultSettingsService service = new ChatRuntimeDefaultSettingsService(mapper, new ObjectMapper());

        assertThat(service.current()).isEqualTo(
                new ChatRuntimeDefaultSettingsService.Settings(2536, 300000, "database"));
    }

    @Test
    void savePersistsOnlyValidatedRuntimeParameters() throws Exception {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        ChatRuntimeDefaultSettingsService service = new ChatRuntimeDefaultSettingsService(mapper, new ObjectMapper());

        assertThat(service.save(Map.of("maxTokens", 2536, "maxContext", 300000)))
                .isEqualTo(new ChatRuntimeDefaultSettingsService.Settings(2536, 300000, "database"));

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(mapper).upsert(org.mockito.ArgumentMatchers.eq("chat_runtime_default"), value.capture());
        assertThat(new ObjectMapper().readTree(value.getValue()).path("maxTokens").asInt()).isEqualTo(2536);
        assertThat(new ObjectMapper().readTree(value.getValue()).path("maxContext").asInt()).isEqualTo(300000);
    }

    @Test
    void saveRejectsContextThatCannotFitOutputBudget() {
        AppRuntimeSettingMapper mapper = mock(AppRuntimeSettingMapper.class);
        ChatRuntimeDefaultSettingsService service = new ChatRuntimeDefaultSettingsService(mapper, new ObjectMapper());

        assertThatThrownBy(() -> service.save(Map.of("maxTokens", 2536, "maxContext", 3000)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("maxContext");
    }
}
