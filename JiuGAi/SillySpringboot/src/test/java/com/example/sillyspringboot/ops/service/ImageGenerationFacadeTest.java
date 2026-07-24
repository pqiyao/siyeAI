package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.ops.dto.AppImageGenerationSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageGenerationFacadeTest {

    @Test
    void systemModeUsesOfficialPoolAndIgnoresClientEngine() {
        Fixture fixture = fixture();
        when(fixture.userProviders.isCustomModeSelectedForClientUid("client")).thenReturn(false);
        when(fixture.routing.isCapabilityEnabled(AiCapability.IMAGE)).thenReturn(true);
        when(fixture.managed.generate(eq("client"), any())).thenReturn(Map.of("source", "managed"));

        assertThat(fixture.facade.generate("client", Map.of("engine", "openai_compatible", "prompt", "x")))
                .containsEntry("source", "managed");
        verify(fixture.byok, never()).generate(any(), any());
    }

    @Test
    void customModeUsesByokAndIgnoresClientEngine() {
        Fixture fixture = fixture();
        when(fixture.userProviders.isCustomModeSelectedForClientUid("client")).thenReturn(true);
        when(fixture.byok.generate(eq("client"), any())).thenReturn(Map.of("source", "byok"));

        assertThat(fixture.facade.generate("client", Map.of("engine", "managed_openai_compatible", "prompt", "x")))
                .containsEntry("source", "byok");
        verify(fixture.routing, never()).isCapabilityEnabled(AiCapability.IMAGE);
    }

    @Test
    void systemModeUsesComfyOnlyWhenOfficialRouteIsDisabledAndCompatibilityIsEnabled() {
        Fixture fixture = fixture();
        when(fixture.userProviders.isCustomModeSelectedForClientUid("client")).thenReturn(false);
        when(fixture.routing.isCapabilityEnabled(AiCapability.IMAGE)).thenReturn(false);
        when(fixture.comfy.generate(eq("client"), any())).thenReturn(Map.of("source", "comfy"));

        assertThat(fixture.facade.generate("client", Map.of("prompt", "x")))
                .containsEntry("source", "comfy");
        verify(fixture.byok, never()).generate(any(), any());
        verify(fixture.managed, never()).generate(any(), any());
    }

    @Test
    void systemModeWithoutOfficialRouteOrComfyFailsClearly() {
        Fixture fixture = fixture();
        AppImageGenerationSettings disabled = new AppImageGenerationSettings();
        disabled.setEngine("user_openai_compatible");
        when(fixture.settings.getSettings()).thenReturn(disabled);
        when(fixture.userProviders.isCustomModeSelectedForClientUid("client")).thenReturn(false);
        when(fixture.routing.isCapabilityEnabled(AiCapability.IMAGE)).thenReturn(false);

        assertThatThrownBy(() -> fixture.facade.generate("client", Map.of("prompt", "x")))
                .isInstanceOf(com.example.sillyspringboot.shared.error.BusinessException.class)
                .hasMessageContaining("模型路由");
        verify(fixture.byok, never()).generate(any(), any());
        verify(fixture.managed, never()).generate(any(), any());
        verify(fixture.comfy, never()).generate(any(), any());
    }

    private static Fixture fixture() {
        AppImageGenerationSettingsService settings = mock(AppImageGenerationSettingsService.class);
        H5UserAiProviderService userProviders = mock(H5UserAiProviderService.class);
        AiRoutingService routing = mock(AiRoutingService.class);
        H5EntitlementService entitlement = mock(H5EntitlementService.class);
        ImageGenerationConcurrencyGate concurrencyGate = mock(ImageGenerationConcurrencyGate.class);
        ImageGenerationPolicyService policyService = mock(ImageGenerationPolicyService.class);
        ImageGenerationConcurrencyGate.Lease lease = mock(ImageGenerationConcurrencyGate.Lease.class);
        ImageGenerationConcurrencyGate.RequestLease requestLease = mock(ImageGenerationConcurrencyGate.RequestLease.class);
        AppUser user = mock(AppUser.class);
        ImageGenerationEngine byok = engine("openai_compatible");
        ImageGenerationEngine managed = engine("managed_openai_compatible");
        ImageGenerationEngine comfy = engine("st_comfy");
        AppImageGenerationSettings legacySettings = new AppImageGenerationSettings();
        legacySettings.setEngine("st_comfy");
        when(settings.getSettings()).thenReturn(legacySettings);
        when(user.getId()).thenReturn(7L);
        when(entitlement.resolveUser("client")).thenReturn(user);
        when(entitlement.guardImage(eq("client"), eq(1), eq(0L), any())).thenReturn(
                new H5EntitlementService.AccessTicket(
                        7L, "client", true, 1, H5EntitlementService.QuotaBucket.IMAGE,
                        null, "GENERATE_IMAGE", false, 0, 0, ""
                )
        );
        when(concurrencyGate.acquire(7L)).thenReturn(lease);
        when(concurrencyGate.claimRequest(eq(7L), any())).thenReturn(requestLease);
        when(policyService.resolve(anyLong(), any(), any())).thenAnswer(invocation -> {
            Map<String, Object> payload = invocation.getArgument(2);
            return new ImageGenerationPolicyService.Resolution(
                    payload, "free", "latest_generated_first", "prompt_first", List.of());
        });
        return new Fixture(
                new ImageGenerationFacade(
                        settings, userProviders, routing, entitlement, concurrencyGate,
                        policyService, List.of(byok, managed, comfy)),
                settings,
                userProviders,
                routing,
                byok,
                managed,
                comfy
        );
    }

    private static ImageGenerationEngine engine(String name) {
        ImageGenerationEngine engine = mock(ImageGenerationEngine.class);
        when(engine.engineName()).thenReturn(name);
        return engine;
    }

    private record Fixture(
            ImageGenerationFacade facade,
            AppImageGenerationSettingsService settings,
            H5UserAiProviderService userProviders,
            AiRoutingService routing,
            ImageGenerationEngine byok,
            ImageGenerationEngine managed,
            ImageGenerationEngine comfy
    ) {}
}
