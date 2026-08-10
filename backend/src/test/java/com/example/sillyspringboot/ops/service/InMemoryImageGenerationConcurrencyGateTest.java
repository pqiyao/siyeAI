package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.dto.AppImageGenerationSettings;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InMemoryImageGenerationConcurrencyGateTest {

    @Test
    void requestClaimReleasesFailureAndRejectsCompletedReplay() {
        AppImageGenerationSettingsService settingsService = mock(AppImageGenerationSettingsService.class);
        AppImageGenerationSettings settings = new AppImageGenerationSettings();
        when(settingsService.getSettings()).thenReturn(settings);
        InMemoryImageGenerationConcurrencyGate gate = new InMemoryImageGenerationConcurrencyGate(settingsService);

        try (ImageGenerationConcurrencyGate.RequestLease first = gate.claimRequest(7L, "image_request_1")) {
            assertThatThrownBy(() -> gate.claimRequest(7L, "image_request_1"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("正在处理");
        }

        try (ImageGenerationConcurrencyGate.RequestLease retry = gate.claimRequest(7L, "image_request_1")) {
            retry.markSucceeded();
        }
        assertThatThrownBy(() -> gate.claimRequest(7L, "image_request_1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已经完成");
    }
}
