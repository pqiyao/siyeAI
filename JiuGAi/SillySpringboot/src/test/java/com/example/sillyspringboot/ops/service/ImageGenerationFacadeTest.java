package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.ops.dto.AppImageGenerationSettings;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

class ImageGenerationFacadeTest {

    @Test
    void systemModeUsesNovelAiAndIgnoresClientEngine() {
        Fixture fixture = fixture();
        AppImageGenerationSettings systemSettings = new AppImageGenerationSettings();
        systemSettings.setEngine("managed_openai_compatible");
        when(fixture.settings.getSettings()).thenReturn(systemSettings);
        when(fixture.userProviders.isCustomModeSelectedForClientUid("client")).thenReturn(false);
        when(fixture.novelAi.generate(eq("client"), any())).thenReturn(Map.of("source", "novelai"));

        assertThat(fixture.facade.generate("client", Map.of("engine", "openai_compatible", "prompt", "x")))
                .containsEntry("source", "novelai");
        verify(fixture.byok, never()).generate(any(), any());
        verify(fixture.managed, never()).generate(any(), any());
    }

    @Test
    void customModeUsesByokAndIgnoresClientEngine() {
        Fixture fixture = fixture();
        when(fixture.userProviders.isCustomModeSelectedForClientUid("client")).thenReturn(true);
        when(fixture.byok.generate(eq("client"), any())).thenReturn(Map.of("source", "byok"));

        assertThat(fixture.facade.generate("client", Map.of("engine", "managed_openai_compatible", "prompt", "x")))
                .containsEntry("source", "byok");
        verify(fixture.novelAi, never()).generate(any(), any());
    }

    @Test
    void systemModeUsesComfyOnlyWhenExplicitlyConfigured() {
        Fixture fixture = fixture();
        when(fixture.userProviders.isCustomModeSelectedForClientUid("client")).thenReturn(false);
        when(fixture.comfy.generate(eq("client"), any())).thenReturn(Map.of("source", "comfy"));

        assertThat(fixture.facade.generate("client", Map.of("prompt", "x")))
                .containsEntry("source", "comfy");
        verify(fixture.byok, never()).generate(any(), any());
        verify(fixture.managed, never()).generate(any(), any());
        verify(fixture.novelAi, never()).generate(any(), any());
    }

    @Test
    void legacySystemEngineValuesNormalizeToNovelAi() {
        Fixture fixture = fixture();
        AppImageGenerationSettings legacy = new AppImageGenerationSettings();
        legacy.setEngine("user_openai_compatible");
        when(fixture.settings.getSettings()).thenReturn(legacy);
        when(fixture.userProviders.isCustomModeSelectedForClientUid("client")).thenReturn(false);
        when(fixture.novelAi.generate(eq("client"), any())).thenReturn(Map.of("source", "novelai"));

        assertThat(fixture.facade.generate("client", Map.of("prompt", "x")))
                .containsEntry("source", "novelai");
        verify(fixture.byok, never()).generate(any(), any());
        verify(fixture.managed, never()).generate(any(), any());
        verify(fixture.comfy, never()).generate(any(), any());
    }

    @Test
    void completedDuplicateReturnsCachedResultWithoutGeneratingOrChargingAgain() {
        Fixture fixture = fixture();
        when(fixture.resultStore.get(7L, "image_request_cached"))
                .thenReturn(Optional.of(Map.of(
                        "status", "DONE",
                        "imageRequestId", "image_request_cached",
                        "images", List.of(Map.of("url", "data:image/png;base64,abc"))
                )));

        assertThat(fixture.facade.generate("client", Map.of(
                "prompt", "x",
                "imageRequestId", "image_request_cached"
        ))).containsEntry("status", "DONE");

        verify(fixture.byok, never()).generate(any(), any());
        verify(fixture.managed, never()).generate(any(), any());
        verify(fixture.comfy, never()).generate(any(), any());
        verify(fixture.novelAi, never()).generate(any(), any());
        verify(fixture.entitlement, never()).guardImage(any(), anyInt(), anyLong(), any());
    }

    @Test
    void requestClaimRaceReplaysResultWrittenByTheWinningInstance() {
        Fixture fixture = fixture();
        Map<String, Object> completed = Map.of(
                "status", "DONE",
                "imageRequestId", "image_request_race",
                "images", List.of(Map.of("url", "data:image/png;base64,abc"))
        );
        when(fixture.resultStore.get(7L, "image_request_race"))
                .thenReturn(Optional.empty(), Optional.of(completed));
        when(fixture.concurrencyGate.claimRequest(eq(7L), eq("image_request_race")))
                .thenThrow(new BusinessException(ErrorCode.SERVICE_BUSY, "该生图请求正在处理中，请稍后查看结果"));

        assertThat(fixture.facade.generate("client", Map.of(
                "prompt", "x",
                "imageRequestId", "image_request_race"
        ))).containsEntry("status", "DONE");

        verify(fixture.novelAi, never()).generate(any(), any());
        verify(fixture.byok, never()).generate(any(), any());
        verify(fixture.entitlement, never()).guardImage(any(), anyInt(), anyLong(), any());
    }

    @Test
    void persistsRecoverableResultBeforeRecordingSuccessfulImage() {
        Fixture fixture = fixture();
        when(fixture.novelAi.generate(eq("client"), any())).thenReturn(Map.of(
                "images", List.of(Map.of("url", "data:image/png;base64,abc"))
        ));

        fixture.facade.generate("client", Map.of(
                "prompt", "x",
                "imageRequestId", "image_request_order"
        ));

        var ordered = inOrder(fixture.resultStore, fixture.entitlement);
        ordered.verify(fixture.resultStore).put(eq(7L), eq("image_request_order"), any());
        ordered.verify(fixture.entitlement).recordSuccessfulImage(any(), eq(1));
    }

    @Test
    void returnsDurableResultWhenSuccessAuditFails() {
        Fixture fixture = fixture();
        when(fixture.novelAi.generate(eq("client"), any())).thenReturn(Map.of(
                "images", List.of(Map.of("url", "data:image/png;base64,abc"))
        ));
        doThrow(new IllegalStateException("audit unavailable"))
                .when(fixture.entitlement).recordSuccessfulImage(any(), eq(1));

        assertThat(fixture.facade.generate("client", Map.of(
                "prompt", "x",
                "imageRequestId", "image_request_audit_failure"
        )))
                .containsEntry("status", "DONE")
                .containsEntry("imageRequestId", "image_request_audit_failure");

        verify(fixture.resultStore).put(eq(7L), eq("image_request_audit_failure"), any());
        verify(fixture.entitlement, never()).releaseImageReservation(any());
    }

    @Test
    void cachesOnlyTheExternalizedImageUrl() {
        Fixture fixture = fixture();
        when(fixture.novelAi.generate(eq("client"), any())).thenReturn(Map.of(
                "images", List.of(Map.of("url", "data:image/png;base64,raw-image"))
        ));
        when(fixture.assetStorage.externalize(eq(7L), eq("image_request_externalized"), any())).thenReturn(Map.of(
                "images", List.of(Map.of("url", "/uploads/generated/user/image.png"))
        ));

        fixture.facade.generate("client", Map.of(
                "prompt", "x",
                "imageRequestId", "image_request_externalized"
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> resultCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.resultStore).put(eq(7L), eq("image_request_externalized"), resultCaptor.capture());
        assertThat(resultCaptor.getValue().toString())
                .contains("/uploads/generated/user/image.png")
                .doesNotContain("base64");
    }

    @Test
    void unavailableSystemRuntimeStopsBeforeQuotaReservationAndProviderCall() {
        Fixture fixture = fixture();
        AppImageGenerationSettings systemSettings = new AppImageGenerationSettings();
        systemSettings.setEngine("novelai");
        when(fixture.settings.getSettings()).thenReturn(systemSettings);
        doThrow(new BusinessException(ErrorCode.SERVICE_BUSY, "系统生图 Redis 当前不可用"))
                .when(fixture.readiness).guardReady("novelai");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.facade.generate(
                "client", Map.of("prompt", "x", "imageRequestId", "image_request_not_ready")
        )).isInstanceOf(BusinessException.class).hasMessageContaining("Redis");

        verify(fixture.entitlement, never()).guardImage(any(), anyInt(), anyLong(), any());
        verify(fixture.novelAi, never()).generate(any(), any());
    }

    private static Fixture fixture() {
        AppImageGenerationSettingsService settings = mock(AppImageGenerationSettingsService.class);
        H5UserAiProviderService userProviders = mock(H5UserAiProviderService.class);
        AiRoutingService routing = mock(AiRoutingService.class);
        H5EntitlementService entitlement = mock(H5EntitlementService.class);
        ImageGenerationConcurrencyGate concurrencyGate = mock(ImageGenerationConcurrencyGate.class);
        ImageGenerationPolicyService policyService = mock(ImageGenerationPolicyService.class);
        ImageGenerationResultStore resultStore = mock(ImageGenerationResultStore.class);
        ImageGenerationAssetStorageService assetStorageService = mock(ImageGenerationAssetStorageService.class);
        ImageGenerationReadinessService readinessService = mock(ImageGenerationReadinessService.class);
        ImageGenerationConcurrencyGate.Lease lease = mock(ImageGenerationConcurrencyGate.Lease.class);
        ImageGenerationConcurrencyGate.RequestLease requestLease = mock(ImageGenerationConcurrencyGate.RequestLease.class);
        AppUser user = mock(AppUser.class);
        ImageGenerationEngine byok = engine("openai_compatible");
        ImageGenerationEngine managed = engine("managed_openai_compatible");
        ImageGenerationEngine comfy = engine("st_comfy");
        ImageGenerationEngine novelAi = engine("novelai");
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
        when(assetStorageService.externalize(anyLong(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
        return new Fixture(
                new ImageGenerationFacade(
                        settings, userProviders, routing, entitlement, concurrencyGate,
                        policyService, resultStore, assetStorageService, readinessService,
                        List.of(byok, managed, comfy, novelAi)),
                settings,
                userProviders,
                routing,
                entitlement,
                resultStore,
                concurrencyGate,
                assetStorageService,
                readinessService,
                byok,
                managed,
                comfy,
                novelAi
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
            H5EntitlementService entitlement,
            ImageGenerationResultStore resultStore,
            ImageGenerationConcurrencyGate concurrencyGate,
            ImageGenerationAssetStorageService assetStorage,
            ImageGenerationReadinessService readiness,
            ImageGenerationEngine byok,
            ImageGenerationEngine managed,
            ImageGenerationEngine comfy,
            ImageGenerationEngine novelAi
    ) {}
}
