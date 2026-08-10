package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.entity.AppTtsVoiceTemplate;
import com.example.sillyspringboot.ops.mapper.AppUserTtsVoiceInstanceMapper;
import com.example.sillyspringboot.compat.h5.web.H5UploadService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TtsVoiceProvisionServiceIsolationTest {

    @Test
    void officialAndCustomTemplateInstancesHaveDifferentOwners() {
        TtsVoiceProvisionService.TtsRuntimeContext official = context(false, "siliconflow", "model-a");
        TtsVoiceProvisionService.TtsRuntimeContext custom = context(true, "siliconflow", "model-a");

        assertThat(TtsVoiceProvisionService.instanceOwnerIdForRuntime(42L, official)).isZero();
        assertThat(TtsVoiceProvisionService.instanceOwnerIdForRuntime(42L, custom)).isEqualTo(42L);
    }

    @Test
    void officialTemplateMustMatchBothProviderAndDeploymentModel() {
        TtsVoiceTemplateService templates = mock(TtsVoiceTemplateService.class);
        AppTtsVoiceTemplate template = new AppTtsVoiceTemplate();
        template.setTemplateCode("voice-a");
        template.setProviderSource("siliconflow");
        template.setTtsModelName("model-a");
        when(templates.findEnabledTemplate("voice-a")).thenReturn(template);
        TtsVoiceProvisionService service = new TtsVoiceProvisionService(
                templates,
                mock(AppUserTtsVoiceInstanceMapper.class),
                mock(H5UploadService.class),
                new ObjectMapper());

        assertThat(service.isTemplateCompatible("voice-a", context(false, "siliconflow", "model-a"))).isTrue();
        assertThat(service.isTemplateCompatible("voice-a", context(false, "openai", "model-a"))).isFalse();
        assertThat(service.isTemplateCompatible("voice-a", context(false, "siliconflow", "model-b"))).isFalse();
        assertThat(service.isTemplateCompatible("voice-a", context(true, "siliconflow", "model-b"))).isTrue();
    }

    @Test
    void provisioningDeadlineIsBoundedAndRoundsUpPartialSeconds() {
        long second = 1_000_000_000L;

        assertThat(TtsVoiceProvisionService.remainingSeconds(200 * second, 0)).isEqualTo(90);
        assertThat(TtsVoiceProvisionService.remainingSeconds(15 * second, 0)).isEqualTo(15);
        assertThat(TtsVoiceProvisionService.remainingSeconds(second + 1, second)).isEqualTo(1);
        assertThat(TtsVoiceProvisionService.remainingSeconds(second, second)).isZero();
    }

    @Test
    void expiredSpeechDeadlineStopsBeforeTemplateLookupOrProvisioning() {
        TtsVoiceTemplateService templates = mock(TtsVoiceTemplateService.class);
        AppUserTtsVoiceInstanceMapper instances = mock(AppUserTtsVoiceInstanceMapper.class);
        H5UploadService uploads = mock(H5UploadService.class);
        TtsVoiceProvisionService service = new TtsVoiceProvisionService(
                templates, instances, uploads, new ObjectMapper());

        assertThatThrownBy(() -> service.resolveVoiceForUser(
                42L, "voice-a", context(false, "siliconflow", "model-a"), System.nanoTime() - 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("语音合成等待超时");
        verifyNoInteractions(templates, instances, uploads);
    }

    private static TtsVoiceProvisionService.TtsRuntimeContext context(
            boolean customMode,
            String provider,
            String model
    ) {
        return new TtsVoiceProvisionService.TtsRuntimeContext(
                customMode, provider, "https://example.com/v1", "key", model);
    }
}
