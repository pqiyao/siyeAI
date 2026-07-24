package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.ops.dto.AppFeatureSettings;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.AppImageGenerationSettingsService;
import com.example.sillyspringboot.ops.service.AppMediaRuntimeSettingsService;
import com.example.sillyspringboot.ops.service.EntitlementAuditLogService;
import com.example.sillyspringboot.ops.service.EntitlementPolicyService;
import com.example.sillyspringboot.ops.service.ImageGenerationPolicyService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/media")
@AdminPermitted("ops:media:view")
public class AdminJiugaiMediaController {

    private final AppFeatureSettingsService featureSettingsService;
    private final AppImageGenerationSettingsService imageSettingsService;
    private final ImageGenerationPolicyService imagePolicyService;
    private final AppMediaRuntimeSettingsService mediaRuntimeSettingsService;
    private final EntitlementPolicyService entitlementPolicyService;
    private final EntitlementAuditLogService auditLogService;
    private final AiRoutingService aiRoutingService;

    public AdminJiugaiMediaController(
            AppFeatureSettingsService featureSettingsService,
            AppImageGenerationSettingsService imageSettingsService,
            ImageGenerationPolicyService imagePolicyService,
            AppMediaRuntimeSettingsService mediaRuntimeSettingsService,
            EntitlementPolicyService entitlementPolicyService,
            EntitlementAuditLogService auditLogService,
            AiRoutingService aiRoutingService
    ) {
        this.featureSettingsService = featureSettingsService;
        this.imageSettingsService = imageSettingsService;
        this.imagePolicyService = imagePolicyService;
        this.mediaRuntimeSettingsService = mediaRuntimeSettingsService;
        this.entitlementPolicyService = entitlementPolicyService;
        this.auditLogService = auditLogService;
        this.aiRoutingService = aiRoutingService;
    }

    @GetMapping("/image-policy")
    public Map<String, Object> imagePolicy() {
        return AdminAjaxResult.okData(imagePolicyData());
    }

    @PutMapping("/image-policy")
    @AdminPermitted("ops:media:image:edit")
    public Map<String, Object> updateImagePolicy(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> before = imagePolicyData();
        if (body != null && body.containsKey("featureEnabled")) {
            featureSettingsService.saveSettings(Map.of("imageGenerationEnabled", body.get("featureEnabled")));
        }
        imageSettingsService.saveSettings(body);
        Map<String, Object> after = imagePolicyData();
        auditLogService.recordPolicyUpdate(before, after, "admin-media-image");
        return AdminAjaxResult.okData(after);
    }

    @GetMapping("/image-policy/characters")
    public Map<String, Object> imagePolicyCharacters(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword
    ) {
        Map<String, Object> page = imagePolicyService.listAdminCharacters(pageNum, pageSize, keyword);
        return AdminAjaxResult.table(
                ((Number) page.getOrDefault("total", 0L)).longValue(),
                (java.util.List<?>) page.getOrDefault("rows", java.util.List.of()));
    }

    @GetMapping("/image-policy/characters/{characterId}")
    public Map<String, Object> imagePolicyCharacter(@PathVariable long characterId) {
        return AdminAjaxResult.okData(imagePolicyService.characterAdminSnapshot(characterId));
    }

    @PutMapping("/image-policy/characters/{characterId}")
    @AdminPermitted("ops:media:image:edit")
    public Map<String, Object> updateImagePolicyCharacter(
            @PathVariable long characterId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return AdminAjaxResult.okData(imagePolicyService.saveCharacterOverride(characterId, body));
    }

    @DeleteMapping("/image-policy/characters/{characterId}")
    @AdminPermitted("ops:media:image:edit")
    public Map<String, Object> deleteImagePolicyCharacter(@PathVariable long characterId) {
        imagePolicyService.deleteCharacterOverride(characterId);
        return AdminAjaxResult.ok("已恢复继承全局策略");
    }

    @GetMapping("/voice-policy")
    public Map<String, Object> voicePolicy() {
        return AdminAjaxResult.okData(voicePolicyData());
    }

    @PutMapping("/voice-policy")
    @AdminPermitted("ops:media:voice:edit")
    public Map<String, Object> updateVoicePolicy(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> before = voicePolicyData();
        if (body != null && body.containsKey("featureEnabled")) {
            featureSettingsService.saveSettings(Map.of("voiceFeatureEnabled", body.get("featureEnabled")));
        }
        Object runtime = body == null ? null : body.get("runtime");
        if (runtime instanceof Map<?, ?> values) {
            Map<String, Object> safe = new LinkedHashMap<>();
            values.forEach((key, value) -> safe.put(String.valueOf(key), value));
            mediaRuntimeSettingsService.saveSettings(safe);
        }
        Map<String, Object> after = voicePolicyData();
        auditLogService.recordPolicyUpdate(before, after, "admin-media-voice");
        return AdminAjaxResult.okData(after);
    }

    private Map<String, Object> voicePolicyData() {
        AppFeatureSettings feature = featureSettingsService.getSettings();
        Map<String, Object> entitlement = entitlementPolicyService.toMap(entitlementPolicyService.getPolicy());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("featureEnabled", feature.isVoiceFeatureEnabled());
        data.put("userByokEnabled", feature.isUserByokEnabled());
        data.put("userByokVipMinLevel", feature.getUserByokVipMinLevel());
        data.put("runtime", mediaRuntimeSettingsService.toMap(mediaRuntimeSettingsService.getSettings()));
        data.put("ttsRouting", aiRoutingService.capabilitySummary(AiCapability.TTS));
        data.put("sttRouting", aiRoutingService.capabilitySummary(AiCapability.STT));
        data.put("ttsScoreCost", entitlement.getOrDefault("ttsScoreCost", 0));
        data.put("ttsGoldCost", entitlement.getOrDefault("ttsGoldCost", 0));
        data.put("sttScoreCost", entitlement.getOrDefault("sttScoreCost", 0));
        data.put("sttGoldCost", entitlement.getOrDefault("sttGoldCost", 0));
        return data;
    }

    private Map<String, Object> imagePolicyData() {
        AppFeatureSettings feature = featureSettingsService.getSettings();
        Map<String, Object> data = new LinkedHashMap<>(imagePolicyService.globalAdminSnapshot());
        data.put("featureEnabled", feature.isImageGenerationEnabled());
        data.put("userByokEnabled", feature.isUserByokEnabled());
        data.put("userByokVipMinLevel", feature.getUserByokVipMinLevel());
        data.put("imageRouting", aiRoutingService.capabilitySummary(AiCapability.IMAGE));
        return data;
    }
}
