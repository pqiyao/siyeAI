package com.example.sillyspringboot.shared.net;

import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaNetworkSafetyTest {

    @Test
    void boundedReaderRejectsOversizedBody() {
        byte[] body = "123456".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> BoundedHttpBodyHandlers.readBytes(new ByteArrayInputStream(body), 5))
                .isInstanceOf(BoundedHttpBodyHandlers.BodyTooLargeException.class);
        assertThat(BoundedHttpBodyHandlers.readBytes(new ByteArrayInputStream(body), 6)).isEqualTo(body);
    }

    @Test
    void outboundGuardBlocksLoopbackAndPrivateNetworks() {
        assertThatThrownBy(() -> OutboundUrlGuard.requirePublicHttpUrl("http://127.0.0.1/a", "blocked"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("blocked");
        assertThatThrownBy(() -> OutboundUrlGuard.requirePublicHttpUrl("http://10.0.0.8/a", "blocked"))
                .isInstanceOf(BusinessException.class);
        assertThat(OutboundUrlGuard.requirePublicHttpUrl("https://8.8.8.8/a", "blocked").getHost())
                .isEqualTo("8.8.8.8");
    }

    @Test
    void mediaValidatorUsesMagicBytes() {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        byte[] mp3 = new byte[] {'I', 'D', '3', 4, 0, 0};
        assertThat(MediaPayloadValidator.requireImage(png, "application/octet-stream")).isEqualTo("image/png");
        assertThat(MediaPayloadValidator.requireAudio(mp3, "audio/mpeg")).isEqualTo("audio/mpeg");
        assertThatThrownBy(() -> MediaPayloadValidator.requireImage("html".getBytes(StandardCharsets.UTF_8), "image/png"))
                .isInstanceOf(BusinessException.class);
    }
}
