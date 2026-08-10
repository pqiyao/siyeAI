package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserAiProvider;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserAiProviderMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.ops.dto.AppFeatureSettings;
import com.example.sillyspringboot.ops.dto.EntitlementPolicy;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.EntitlementPolicyService;
import com.example.sillyspringboot.ops.service.ImageGenerationReadinessService;
import com.example.sillyspringboot.ops.service.TtsVoiceTemplateService;
import com.example.sillyspringboot.shared.crypto.SensitiveTextCrypto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class H5UserAiProviderPersistenceTest {

    @Test
    void emptyApiKeyKeepsTheEncryptedKeyAlreadyStoredForTheSameProvider() {
        Fixture fixture = fixture("cipher-old", "stored-secret");

        H5UserAiProviderService.UserAiProviderView view = fixture.service.save("client", Map.of(
                "mode", "custom",
                "providerSource", "openai",
                "modelName", "gpt-4o-mini",
                "apiKey", "",
                "clearStoredKey", false
        ));

        assertThat(fixture.row.getApiKeyCipher()).isEqualTo("cipher-old");
        assertThat(view.apiKeyConfigured()).isTrue();
        assertThat(view.apiKeyMask()).isEqualTo("****cret");
        verify(fixture.mapper).upsert(fixture.row);
    }

    @Test
    void newApiKeyIsEncryptedAndOnlyItsMaskReturnsToTheClient() {
        Fixture fixture = fixture("cipher-old", "stored-secret");
        when(fixture.crypto.encrypt("replacement-secret")).thenReturn("cipher-new");
        when(fixture.crypto.decrypt("cipher-new")).thenReturn("replacement-secret");

        H5UserAiProviderService.UserAiProviderView view = fixture.service.save("client", Map.of(
                "mode", "custom",
                "providerSource", "openai",
                "modelName", "gpt-4o-mini",
                "apiKey", "replacement-secret"
        ));

        assertThat(fixture.row.getApiKeyCipher()).isEqualTo("cipher-new");
        assertThat(fixture.row.getApiKeyCipher()).doesNotContain("replacement-secret");
        assertThat(view.apiKeyConfigured()).isTrue();
        assertThat(view.apiKeyMask()).isEqualTo("****cret");
    }

    @Test
    void systemModeReportsImageUnavailableWhenRuntimeReadinessFails() {
        Fixture fixture = fixture("cipher-old", "stored-secret");
        fixture.row.setProviderMode("system");
        fixture.settings.setImageGenerationEnabled(true);
        when(fixture.readiness.systemSnapshot()).thenReturn(new ImageGenerationReadinessService.Snapshot(
                false,
                "REDIS_UNAVAILABLE",
                "系统生图 Redis 当前不可用",
                "novelai",
                true,
                true,
                false,
                true,
                false
        ));

        H5UserAiProviderService.UserAiProviderView view = fixture.service.getView("client");

        assertThat(view.imageCanUse()).isFalse();
        assertThat(view.imageDenyReason()).contains("Redis");
    }

    private static Fixture fixture(String cipherText, String clearText) {
        H5ClientUidAuthService h5Auth = mock(H5ClientUidAuthService.class);
        AppTokenService tokenService = mock(AppTokenService.class);
        AppH5UserAiProviderMapper mapper = mock(AppH5UserAiProviderMapper.class);
        AppH5UserProfileExtMapper profileExtMapper = mock(AppH5UserProfileExtMapper.class);
        AppFeatureSettingsService featureSettingsService = mock(AppFeatureSettingsService.class);
        EntitlementPolicyService entitlementPolicyService = mock(EntitlementPolicyService.class);
        AiRoutingService aiRoutingService = mock(AiRoutingService.class);
        TtsVoiceTemplateService ttsVoiceTemplateService = mock(TtsVoiceTemplateService.class);
        ImageGenerationReadinessService imageGenerationReadinessService = mock(ImageGenerationReadinessService.class);
        SensitiveTextCrypto crypto = mock(SensitiveTextCrypto.class);

        AppUser user = new AppUser();
        user.setId(7L);
        AppFeatureSettings settings = new AppFeatureSettings();
        settings.setUserByokEnabled(true);
        settings.setUserByokVipMinLevel(0);
        settings.setImageGenerationEnabled(false);
        settings.setVoiceFeatureEnabled(false);
        EntitlementPolicy policy = mock(EntitlementPolicy.class);

        AppH5UserAiProvider row = new AppH5UserAiProvider();
        row.setUserId(7L);
        row.setProviderMode("custom");
        row.setProviderSource("openai");
        row.setModelName("gpt-4o-mini");
        row.setApiKeyCipher(cipherText);

        when(h5Auth.requireAuthenticatedTokenForClientUid("client")).thenReturn("token");
        when(tokenService.validateAndLoadUser("token")).thenReturn(user);
        when(mapper.findByUserId(7L)).thenReturn(row);
        when(featureSettingsService.getSettings()).thenReturn(settings);
        when(entitlementPolicyService.effectiveVipLevel(null)).thenReturn(0);
        when(entitlementPolicyService.getPolicy()).thenReturn(policy);
        when(aiRoutingService.capabilitySummary(AiCapability.VISION)).thenReturn(Map.of());
        when(aiRoutingService.capabilitySummary(AiCapability.TTS)).thenReturn(Map.of());
        when(crypto.decrypt(cipherText)).thenReturn(clearText);

        H5UserAiProviderService service = new H5UserAiProviderService(
                h5Auth,
                tokenService,
                mapper,
                profileExtMapper,
                featureSettingsService,
                entitlementPolicyService,
                aiRoutingService,
                ttsVoiceTemplateService,
                imageGenerationReadinessService,
                crypto,
                new ObjectMapper()
        );
        return new Fixture(service, mapper, row, settings, imageGenerationReadinessService, crypto);
    }

    private record Fixture(
            H5UserAiProviderService service,
            AppH5UserAiProviderMapper mapper,
            AppH5UserAiProvider row,
            AppFeatureSettings settings,
            ImageGenerationReadinessService readiness,
            SensitiveTextCrypto crypto
    ) {
    }
}
