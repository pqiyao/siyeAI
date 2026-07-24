package com.example.sillyspringboot.ai.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AiProviderProbeServiceSecurityTest {

    @Test
    void rejectsLoopbackPrivateAndNonHttpTargets() {
        assertThatThrownBy(() -> AiProviderProbeService.validatePublicTarget(URI.create("http://127.0.0.1/v1/models")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内网或保留网络");
        assertThatThrownBy(() -> AiProviderProbeService.validatePublicTarget(URI.create("http://10.20.30.40/v1/models")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内网或保留网络");
        assertThatThrownBy(() -> AiProviderProbeService.validatePublicTarget(URI.create("file:///etc/passwd")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HTTP/HTTPS");
    }

    @Test
    void boundedReaderAcceptsLimitAndRejectsLimitPlusOne() throws Exception {
        assertThat(AiProviderProbeService.readBounded(
                new ByteArrayInputStream("1234".getBytes(StandardCharsets.UTF_8)),
                4
        )).containsExactly("1234".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> AiProviderProbeService.readBounded(
                new ByteArrayInputStream("12345".getBytes(StandardCharsets.UTF_8)),
                4
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("安全限制");
    }

    @Test
    void capabilityValidationRejectsEmptyChatAndFakeTtsSuccess() {
        AiProviderProbeService service = new AiProviderProbeService(mock(AiRoutingService.class), new ObjectMapper());
        AiRoutingService.DraftCredential chat = draft(AiCapability.CHAT);
        AiRoutingService.DraftCredential tts = draft(AiCapability.TTS);

        assertThatThrownBy(() -> service.validateCapabilityResponse(chat, response(
                "application/json", "{\"choices\":[{\"message\":{\"content\":\"\"}}]}".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无有效内容");
        assertThatThrownBy(() -> service.validateCapabilityResponse(tts, response(
                "text/html", "<html>upstream error</html>".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是有效音频");
    }

    @Test
    void capabilityValidationRequiresActualImageAndNonBlankTranscript() {
        AiProviderProbeService service = new AiProviderProbeService(mock(AiRoutingService.class), new ObjectMapper());

        assertThatThrownBy(() -> service.validateCapabilityResponse(draft(AiCapability.IMAGE), response(
                "application/json", "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无有效内容");
        assertThatThrownBy(() -> service.validateCapabilityResponse(draft(AiCapability.STT), response(
                "application/json", "{\"text\":\"  \"}".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无有效内容");
    }

    @Test
    void sttProbeUsesARealSpokenSampleInsteadOfSilence() {
        byte[] wav = AiProviderProbeService.sttProbeWav();

        assertThat(wav).hasSizeGreaterThan(8_000);
        assertThat(new String(wav, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        boolean hasAudiblePcm = false;
        for (int i = 44; i < wav.length; i++) {
            if (wav[i] != 0) {
                hasAudiblePcm = true;
                break;
            }
        }
        assertThat(hasAudiblePcm).isTrue();
    }

    private static AiRoutingService.DraftCredential draft(AiCapability capability) {
        return new AiRoutingService.DraftCredential(
                null, null, "custom", "https://example.com/v1", "key", capability,
                "model", "", 10, 90);
    }

    private static AiProviderProbeService.ProbeResponse response(String contentType, byte[] body) {
        HttpHeaders headers = HttpHeaders.of(Map.of("Content-Type", List.of(contentType)), (name, value) -> true);
        return new AiProviderProbeService.ProbeResponse(200, headers, body);
    }
}
