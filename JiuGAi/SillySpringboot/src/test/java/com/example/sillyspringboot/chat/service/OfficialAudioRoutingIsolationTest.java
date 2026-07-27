package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.ops.service.TtsVoiceProvisionService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OfficialAudioRoutingIsolationTest {

    @Test
    void officialTtsUsesDeploymentSettingsAndRejectsClientOverrides() {
        ChatAudioSpeechService.SpeechSelection selected = ChatAudioSpeechService.selectSpeechSettings(
                false,
                "official-model",
                "official-voice",
                "",
                "client-model",
                "client-voice",
                "client-template"
        );

        assertThat(selected.modelName()).isEqualTo("official-model");
        assertThat(selected.voiceName()).isEqualTo("official-voice");
        assertThat(selected.voiceTemplateCode()).isEmpty();
        assertThat(ChatAudioSpeechService.privateVoiceIdForRuntime(false, 91L)).isNull();
    }

    @Test
    void customTtsKeepsClientOverrides() {
        ChatAudioSpeechService.SpeechSelection selected = ChatAudioSpeechService.selectSpeechSettings(
                true,
                "byok-model",
                "byok-voice",
                "stored-template",
                "client-model",
                "client-voice",
                "client-template"
        );

        assertThat(selected.modelName()).isEqualTo("client-model");
        assertThat(selected.voiceName()).isEqualTo("client-voice");
        assertThat(selected.voiceTemplateCode()).isEqualTo("client-template");
        assertThat(ChatAudioSpeechService.privateVoiceIdForRuntime(true, 91L)).isEqualTo(91L);
        assertThat(ChatAudioSpeechService.privateVoiceIdForRuntime(true, 0L)).isNull();
    }

    @Test
    void providerScopedOverrideIsIgnoredAfterRuntimeProviderChanges() {
        assertThat(ChatAudioSpeechService.providerScopeMatchesRuntime("siliconflow", "siliconflow"))
                .isTrue();
        assertThat(ChatAudioSpeechService.providerScopeMatchesRuntime("SILICONFLOW", "siliconflow"))
                .isTrue();
        assertThat(ChatAudioSpeechService.providerScopeMatchesRuntime("siliconflow", "openai"))
                .isFalse();
        assertThat(ChatAudioSpeechService.providerScopeMatchesRuntime("", "openai"))
                .isTrue();
    }

    @Test
    void invalidCustomTtsNeverFallsBackToOfficialRoute() {
        H5UserAiProviderService byok = mock(H5UserAiProviderService.class);
        AiRoutingService routing = mock(AiRoutingService.class);
        when(byok.resolveActiveTtsSettingsForUser(7L)).thenReturn(null);
        when(byok.isCustomModeSelectedForUser(7L)).thenReturn(true);
        when(routing.isCapabilityEnabled(AiCapability.TTS)).thenReturn(true);
        ChatAudioSpeechService service = new ChatAudioSpeechService(
                byok, mock(TtsVoiceProvisionService.class), new ObjectMapper(), routing);

        assertThatThrownBy(() -> service.synthesizeForUser(7L, "hello"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户自定义 TTS 配置不可用");
        verify(routing, never()).resolve(any());
    }

    @Test
    void invalidCustomSttNeverFallsBackToOfficialRoute() {
        H5UserAiProviderService byok = mock(H5UserAiProviderService.class);
        AiRoutingService routing = mock(AiRoutingService.class);
        when(byok.resolveActiveOverrideForUser(8L)).thenReturn(null);
        when(byok.isCustomModeSelectedForUser(8L)).thenReturn(true);
        when(routing.isCapabilityEnabled(AiCapability.STT)).thenReturn(true);
        ChatAudioTranscriptionService service = new ChatAudioTranscriptionService(byok, new ObjectMapper(), routing);
        MockMultipartFile audio = new MockMultipartFile("file", "voice.wav", "audio/wav", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.transcribeForUser(8L, audio))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户自定义 STT 配置不可用");
        verify(routing, never()).resolve(any());
    }

    @Test
    void officialSttAuthenticationErrorPointsToAdminRouting() {
        ChatAudioTranscriptionService service = new ChatAudioTranscriptionService(
                mock(H5UserAiProviderService.class), new ObjectMapper(), mock(AiRoutingService.class));

        String message = service.safeProviderErrorMessage(
                401, "{\"error\":{\"message\":\"invalid api key\"}}", false);

        assertThat(message).contains("系统 STT").contains("管理员").doesNotContain("BYOK");
    }

    @Test
    void customSttAuthenticationErrorPointsToByok() {
        ChatAudioTranscriptionService service = new ChatAudioTranscriptionService(
                mock(H5UserAiProviderService.class), new ObjectMapper(), mock(AiRoutingService.class));

        String message = service.safeProviderErrorMessage(403, "", true);

        assertThat(message).contains("API Key").contains("BYOK").doesNotContain("管理员");
    }

    @Test
    void officialAndCustomSttModelErrorsRemainSeparated() {
        assertThat(ChatAudioTranscriptionService.normalizeProviderTranscriptionMessage(
                "model does not exist", false))
                .contains("系统 STT").contains("管理员").doesNotContain("AI 设置");
        assertThat(ChatAudioTranscriptionService.normalizeProviderTranscriptionMessage(
                "model does not exist", true))
                .contains("当前 STT").contains("AI 设置").doesNotContain("管理员");
    }
}
