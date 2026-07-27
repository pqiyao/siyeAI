package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.entity.AppTtsVoiceTemplate;
import com.example.sillyspringboot.ops.mapper.AppUserTtsVoiceInstanceMapper;
import com.example.sillyspringboot.compat.h5.web.H5UploadService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

    private static TtsVoiceProvisionService.TtsRuntimeContext context(
            boolean customMode,
            String provider,
            String model
    ) {
        return new TtsVoiceProvisionService.TtsRuntimeContext(
                customMode, provider, "https://example.com/v1", "key", model);
    }
}
