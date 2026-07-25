package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.character.entity.AppCharacterMember;
import com.example.sillyspringboot.character.mapper.CharacterStudioMapper;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
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
    private final AiRoutingService routingService;
    private final H5EntitlementService entitlementService;
    private final ImageGenerationConcurrencyGate concurrencyGate;
    private final ImageGenerationPolicyService policyService;
    private final Map<String, ImageGenerationEngine> engines;

    @Autowired(required = false)
    private CharacterStudioMapper characterStudioMapper;

    public ImageGenerationFacade(
            AppImageGenerationSettingsService settingsService,
            H5UserAiProviderService userAiProviderService,
            AiRoutingService routingService,
            H5EntitlementService entitlementService,
            ImageGenerationConcurrencyGate concurrencyGate,
            ImageGenerationPolicyService policyService,
            List<ImageGenerationEngine> engines
    ) {
        this.settingsService = settingsService;
        this.userAiProviderService = userAiProviderService;
        this.routingService = routingService;
        this.entitlementService = entitlementService;
        this.concurrencyGate = concurrencyGate;
        this.policyService = policyService;
        this.engines = engines.stream().collect(Collectors.toMap(
                engine -> normalizeEngine(engine.engineName()),
                engine -> engine,
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    public Map<String, Object> generate(String clientUid, Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        String engineName = resolveEngine(clientUid);
        ImageGenerationEngine engine = engines.get(engineName);
        if (engine == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "生图服务暂不可用，请联系管理员检查配置"
            );
        }
        int count = 1;
        long characterId = longValue(safePayload.get("characterId"));
        String requestId = normalizeRequestId(safePayload.get("imageRequestId"));
        safePayload.put("imageRequestId", requestId);
        entitlementService.guardImageCharacterAccess(clientUid, characterId);
        applyMemberReferenceImage(characterId, safePayload);
        ImageGenerationPolicyService.Resolution policy = policyService.resolve(characterId, engineName, safePayload);
        safePayload = new LinkedHashMap<>(policy.payload());
        AppUser user = entitlementService.resolveUser(clientUid);
        try (ImageGenerationConcurrencyGate.RequestLease requestLease =
                     concurrencyGate.claimRequest(user.getId(), requestId)) {
            H5EntitlementService.AccessTicket ticket =
                    entitlementService.guardImage(clientUid, count, characterId, requestId);
            boolean confirmed = false;
            try (ImageGenerationConcurrencyGate.Lease ignored = concurrencyGate.acquire(user.getId())) {
                Map<String, Object> generated = engine.generate(clientUid, safePayload);
                entitlementService.recordSuccessfulImage(ticket, count);
                confirmed = true;
                try {
                    requestLease.markSucceeded();
                } catch (RuntimeException ex) {
                    log.warn("Failed to mark image request complete: userId={}, requestId={}", user.getId(), requestId, ex);
                }
                Map<String, Object> result = generated == null ? new LinkedHashMap<>() : new LinkedHashMap<>(generated);
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
                return result;
            } catch (RuntimeException ex) {
                if (!confirmed) {
                    entitlementService.releaseImageReservation(ticket);
                }
                throw ex;
            }
        }
    }

    private String resolveEngine(String clientUid) {
        if (userAiProviderService.isCustomModeSelectedForClientUid(clientUid)) {
            return "openai_compatible";
        }
        if (routingService.isCapabilityEnabled(AiCapability.IMAGE)) {
            return "managed_openai_compatible";
        }
        String compatibilityEngine = normalizeEngine(safe(settingsService.getSettings().getEngine()));
        if ("st_comfy".equals(compatibilityEngine)) {
            return compatibilityEngine;
        }
        throw new BusinessException(
                ErrorCode.SERVICE_BUSY,
                "系统生图尚未配置，请在模型路由启用 IMAGE，或在 AI 媒体中心启用 Comfy 兼容通道"
        );
    }

    private void applyMemberReferenceImage(long characterId, Map<String, Object> payload) {
        if (characterStudioMapper == null || characterId <= 0) {
            return;
        }
        long memberId = longValue(payload.get("speakerMemberId"));
        if (memberId <= 0) return;
        try {
            for (AppCharacterMember member : characterStudioMapper.listMembers(characterId)) {
                if (member != null && member.getId() != null && member.getId().longValue() == memberId
                        && StringUtils.hasText(member.getImageReferenceUrl())) {
                    payload.put("referenceImageUrl", member.getImageReferenceUrl().trim());
                    return;
                }
            }
        } catch (RuntimeException ex) {
            log.warn("member image reference skipped characterId={} memberId={}", characterId, memberId, ex);
        }
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
        if ("comfy".equals(text) || "st_comfyui".equals(text)) {
            return "st_comfy";
        }
        if ("sd_webui".equals(text) || "webui".equals(text)) {
            return "st_sd_webui";
        }
        return StringUtils.hasText(text) ? text : "openai_compatible";
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
