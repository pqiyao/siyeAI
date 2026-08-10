package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.chat.config.AppMediaProperties;
import com.example.sillyspringboot.ops.dto.AppMediaRuntimeSettings;
import com.example.sillyspringboot.ops.service.AppMediaRuntimeSettingsService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaConcurrencyGateTest {

    @Test
    void oneTtsTaskCountsOnceAcrossSegments() {
        AppMediaProperties properties = new AppMediaProperties();
        properties.getTts().setPerUserRequestsPerWindow(2);
        MediaConcurrencyGate gate = localGate(properties);

        for (int index = 0; index < 8; index++) {
            try (MediaConcurrencyGate.Lease ignored =
                         gate.acquire(MediaConcurrencyGate.Capability.TTS, 7L, "tts_db_1")) {
                // sequential segments of one task
            }
        }
        try (MediaConcurrencyGate.Lease ignored =
                     gate.acquire(MediaConcurrencyGate.Capability.TTS, 7L, "tts_db_2")) {
            // second task is still allowed
        }
        assertThatThrownBy(() -> gate.acquire(MediaConcurrencyGate.Capability.TTS, 7L, "tts_db_3"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过于频繁");
    }

    @Test
    void perUserConcurrencyIsIndependentForTtsAndStt() {
        MediaConcurrencyGate gate = localGate(new AppMediaProperties());
        try (MediaConcurrencyGate.Lease tts = gate.acquire(MediaConcurrencyGate.Capability.TTS, 9L, "tts_task");
             MediaConcurrencyGate.Lease stt = gate.acquire(MediaConcurrencyGate.Capability.STT, 9L, "stt_task")) {
            assertThatThrownBy(() -> gate.acquire(MediaConcurrencyGate.Capability.TTS, 9L, "tts_other"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已有语音生成任务");
        }
    }

    @SuppressWarnings("unchecked")
    private static MediaConcurrencyGate localGate(AppMediaProperties properties) {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        AppMediaRuntimeSettings settings = new AppMediaRuntimeSettings();
        settings.setCounterTtlSeconds(properties.getCounterTtlSeconds());
        settings.setRateWindowSeconds(properties.getRateWindowSeconds());
        settings.setTts(new AppMediaRuntimeSettings.Limits(
                properties.getTts().getGlobalConcurrentLimit(),
                properties.getTts().getPerUserConcurrentLimit(),
                properties.getTts().getPerUserRequestsPerWindow()));
        settings.setStt(new AppMediaRuntimeSettings.Limits(
                properties.getStt().getGlobalConcurrentLimit(),
                properties.getStt().getPerUserConcurrentLimit(),
                properties.getStt().getPerUserRequestsPerWindow()));
        AppMediaRuntimeSettingsService settingsService = mock(AppMediaRuntimeSettingsService.class);
        when(settingsService.getSettings()).thenReturn(settings);
        return new MediaConcurrencyGate(settingsService, provider);
    }
}
