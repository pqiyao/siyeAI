package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ImageGenerationFacade {

    private static final Logger log = LoggerFactory.getLogger(ImageGenerationFacade.class);

    private final AppImageGenerationSettingsService settingsService;
    private final H5UserAiProviderService userAiProviderService;
    private final H5EntitlementService entitlementService;
    private final ImageGenerationConcurrencyGate concurrencyGate;
    private final ImageGenerationPolicyService policyService;
    private final ImageGenerationResultStore resultStore;
    private final ImageGenerationAssetStorageService assetStorageService;
    private final ImageGenerationReadinessService readinessService;
    private final Map<String, ImageGenerationEngine> engines;

    public ImageGenerationFacade(
            AppImageGenerationSettingsService settingsService,
            H5UserAiProviderService userAiProviderService,
            com.example.sillyspringboot.ai.service.AiRoutingService routingService,
            H5EntitlementService entitlementService,
            ImageGenerationConcurrencyGate concurrencyGate,
            ImageGenerationPolicyService policyService,
            ImageGenerationResultStore resultStore,
            ImageGenerationAssetStorageService assetStorageService,
            ImageGenerationReadinessService readinessService,
            List<ImageGenerationEngine> engines
    ) {
        this.settingsService = settingsService;
        this.userAiProviderService = userAiProviderService;
        this.entitlementService = entitlementService;
        this.concurrencyGate = concurrencyGate;
        this.policyService = policyService;
        this.resultStore = resultStore;
        this.assetStorageService = assetStorageService;
        this.readinessService = readinessService;
        this.engines = engines.stream().collect(Collectors.toMap(
                engine -> normalizeEngine(engine.engineName()),
                engine -> engine,
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    public Map<String, Object> generate(String clientUid, Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        int count = 1;
        long characterId = longValue(safePayload.get("characterId"));
        String requestId = normalizeRequestId(safePayload.get("imageRequestId"));
        safePayload.put("imageRequestId", requestId);
        entitlementService.guardImageCharacterAccess(clientUid, characterId);
        AppUser user = entitlementService.resolveUser(clientUid);
        Map<String, Object> cached = resultStore.get(user.getId(), requestId).orElse(null);
        if (cached != null) {
            return cached;
        }
        String engineName = resolveEngine(clientUid);
        readinessService.guardReady(engineName);
        ImageGenerationEngine engine = engines.get(engineName);
        if (engine == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "生图服务暂不可用，请联系管理员检查配置"
            );
        }
        ImageGenerationPolicyService.Resolution policy = policyService.resolve(characterId, engineName, safePayload);
        safePayload = new LinkedHashMap<>(policy.payload());
        ImageGenerationConcurrencyGate.RequestLease claimedRequest;
        try {
            claimedRequest = concurrencyGate.claimRequest(user.getId(), requestId);
        } catch (BusinessException ex) {
            Map<String, Object> completed = resultStore.get(user.getId(), requestId).orElse(null);
            if (completed != null) {
                return completed;
            }
            throw ex;
        }
        try (ImageGenerationConcurrencyGate.RequestLease requestLease = claimedRequest) {
            H5EntitlementService.AccessTicket ticket =
                    entitlementService.guardImage(clientUid, count, characterId, requestId);
            boolean confirmed = false;
            try (ImageGenerationConcurrencyGate.Lease ignored = concurrencyGate.acquire(user.getId())) {
                Map<String, Object> generated = engine.generate(clientUid, safePayload);
                Map<String, Object> result = new LinkedHashMap<>(assetStorageService.externalize(
                        user.getId(), requestId,
                        generated == null ? new LinkedHashMap<>() : generated
                ));
                result.put("remainingCount", entitlementService.currentRemainingImageQuota(user.getId()));
                result.put("imageRequestId", requestId);
                result.put("effectiveConsistencyMode", policy.effectiveMode());
                result.put("effectiveReferenceSourceMode", policy.referenceSourceMode());
                result.put("effectiveReferencePolicy", policy.referencePolicy());
                result.put("policyWarnings", policy.warnings());
                if (!policy.warnings().isEmpty()) {
                    String policyWarning = String.join("；", policy.warnings());
                    String engineWarning = safe(result.get("warning"));
                    result.put("warning", engineWarning.isBlank() ? policyWarning : policyWarning + "；" + engineWarning);
                }
                result.put("status", "DONE");
                resultStore.validate(result);
                resultStore.put(user.getId(), requestId, result);
                // At this point the user-visible result is durably recoverable.
                // Audit failures must not turn a completed, cached image into a refund.
                confirmed = true;
                try {
                    entitlementService.recordSuccessfulImage(ticket, count);
                } catch (RuntimeException ex) {
                    log.warn("Failed to record image success audit: userId={}, requestId={}",
                            user.getId(), requestId, ex);
                }
                try {
                    requestLease.markSucceeded();
                } catch (RuntimeException ex) {
                    log.warn("Failed to mark cached image request complete: userId={}, requestId={}",
                            user.getId(), requestId, ex);
                }
                return result;
            } catch (RuntimeException ex) {
                if (!confirmed) {
                    entitlementService.releaseImageReservation(ticket);
                }
                throw ex;
            }
        }
    }

    public Map<String, Object> findResult(String clientUid, String requestId) {
        String normalizedRequestId = normalizeRequestId(requestId);
        AppUser user = entitlementService.resolveUser(clientUid);
        return resultStore.get(user.getId(), normalizedRequestId)
                .map(LinkedHashMap::new)
                .map(result -> {
                    result.put("status", "DONE");
                    return (Map<String, Object>) result;
                })
                .orElseGet(() -> {
                    Map<String, Object> pending = new LinkedHashMap<>();
                    pending.put("status", "PENDING");
                    pending.put("imageRequestId", normalizedRequestId);
                    return pending;
                });
    }

    private String resolveEngine(String clientUid) {
        if (userAiProviderService.isCustomModeSelectedForClientUid(clientUid)) {
            return "openai_compatible";
        }
        String compatibilityEngine = normalizeEngine(safe(settingsService.getSettings().getEngine()));
        if ("st_comfy".equals(compatibilityEngine)) {
            return compatibilityEngine;
        }
        // System mode is deliberately pinned to the tested NovelAI adapter.
        // Legacy managed/OpenAI settings are not allowed to select a random provider pool.
        return "novelai";
    }

    private static String normalizeEngine(String value) {
        String text = safe(value).toLowerCase()
                .replace('-', '_')
                .replace(' ', '_');
        if ("openai".equals(text)
                || "provider".equals(text)
                || "openai_compatible".equals(text)
                || "user".equals(text)
                || "user_openai".equals(text)
                || "user_openai_compatible".equals(text)) {
            return "openai_compatible";
        }
        if ("managed".equals(text)
                || "platform".equals(text)
                || "managed_openai".equals(text)
                || "platform_openai".equals(text)
                || "managed_openai_compatible".equals(text)
                || "platform_openai_compatible".equals(text)) {
            return "managed_openai_compatible";
        }
        if ("comfy".equals(text) || "st_comfy".equals(text) || "st_comfyui".equals(text)) {
            return "st_comfy";
        }
        if ("novel".equals(text) || "nai".equals(text) || "novelai".equals(text)) {
            return "novelai";
        }
        if ("sd_webui".equals(text) || "webui".equals(text)) {
            return "st_sd_webui";
        }
        return "novelai";
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        try {
            return Math.max(0L, Long.parseLong(safe(value)));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String normalizeRequestId(Object value) {
        String requestId = safe(value);
        if (requestId.isBlank()) {
            // Keep older clients working; current clients always send a stable id.
            return "legacy-image-" + UUID.randomUUID().toString().replace("-", "");
        }
        if (requestId.length() < 8 || requestId.length() > 160
                || !requestId.matches("[A-Za-z0-9._:-]+")) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "生图请求标识不合法");
        }
        return requestId;
    }
}
