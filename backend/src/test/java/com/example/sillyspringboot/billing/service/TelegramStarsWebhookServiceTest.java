package com.example.sillyspringboot.billing.service;

import com.example.sillyspringboot.billing.config.TelegramStarsPaymentProperties;
import com.example.sillyspringboot.billing.mapper.AppPaymentOrderMapper;
import com.example.sillyspringboot.billing.service.provider.TelegramStarsBotClient;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TelegramStarsWebhookServiceTest {

    @Test
    void rejectsWhenWebhookSecretBlank() {
        TelegramStarsPaymentProperties props = new TelegramStarsPaymentProperties();
        props.setWebhookSecret(" ");
        TelegramStarsWebhookService service = new TelegramStarsWebhookService(
                props,
                mock(TelegramStarsBotClient.class),
                mock(AppPaymentOrderMapper.class),
                mock(StoreService.class)
        );

        assertThatThrownBy(() -> service.handleWebhook("anything", Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("webhook secret");
    }

    @Test
    void rejectsMismatchedSecret() {
        TelegramStarsPaymentProperties props = new TelegramStarsPaymentProperties();
        props.setWebhookSecret("expected-secret");
        TelegramStarsWebhookService service = new TelegramStarsWebhookService(
                props,
                mock(TelegramStarsBotClient.class),
                mock(AppPaymentOrderMapper.class),
                mock(StoreService.class)
        );

        assertThatThrownBy(() -> service.handleWebhook("wrong", Map.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("webhook secret");
    }

    @Test
    void expectedStarsCeilFromCents() {
        assertThat(TelegramStarsWebhookService.expectedStars(100)).isEqualTo(1);
        assertThat(TelegramStarsWebhookService.expectedStars(101)).isEqualTo(2);
        assertThat(TelegramStarsWebhookService.expectedStars(990)).isEqualTo(10);
    }
}
