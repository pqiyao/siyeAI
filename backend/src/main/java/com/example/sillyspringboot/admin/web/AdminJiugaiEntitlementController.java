package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.ops.dto.EntitlementPolicy;
import com.example.sillyspringboot.ops.service.AppImageGenerationSettingsService;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.EntitlementAuditLogService;
import com.example.sillyspringboot.ops.service.EntitlementPolicyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/entitlement")
@AdminPermitted("commerce:entitlement:view")
public class AdminJiugaiEntitlementController {

    private final EntitlementPolicyService entitlementPolicyService;
    private final EntitlementAuditLogService auditLogService;
    private final AppFeatureSettingsService featureSettingsService;
    private final AppImageGenerationSettingsService imageGenerationSettingsService;

    public AdminJiugaiEntitlementController(
            EntitlementPolicyService entitlementPolicyService,
            EntitlementAuditLogService auditLogService,
            AppFeatureSettingsService featureSettingsService,
            AppImageGenerationSettingsService imageGenerationSettingsService
    ) {
        this.entitlementPolicyService = entitlementPolicyService;
        this.auditLogService = auditLogService;
        this.featureSettingsService = featureSettingsService;
        this.imageGenerationSettingsService = imageGenerationSettingsService;
    }

    @GetMapping
    public Map<String, Object> get() {
        EntitlementPolicy policy = entitlementPolicyService.getPolicy();
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", entitlementPolicyService.toMap(policy));
        return result;
    }

    @PutMapping
    @AdminPermitted("commerce:entitlement:edit")
    public Map<String, Object> update(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> before = entitlementPolicyService.toMap(entitlementPolicyService.getPolicy());
        EntitlementPolicy policy = entitlementPolicyService.savePolicy(body);
        Map<String, Object> after = entitlementPolicyService.toMap(policy);
        auditLogService.recordPolicyUpdate(before, after, "admin");
        Map<String, Object> result = AdminAjaxResult.ok("保存成功");
        result.put("data", after);
        return result;
    }

    @GetMapping("/runtime-settings")
    public Map<String, Object> runtimeSettings() {
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", featureSettingsService.toMap(featureSettingsService.getSettings()));
        return result;
    }

    @PutMapping("/runtime-settings")
    @AdminPermitted("commerce:entitlement:edit")
    public Map<String, Object> updateRuntimeSettings(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> before = featureSettingsService.toMap(featureSettingsService.getSettings());
        Map<String, Object> after = featureSettingsService.toMap(featureSettingsService.saveSettings(body));
        auditLogService.recordPolicyUpdate(before, after, "admin");
        Map<String, Object> result = AdminAjaxResult.ok("保存成功");
        result.put("data", after);
        return result;
    }

    @GetMapping("/image-generation-settings")
    public Map<String, Object> imageGenerationSettings() {
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", imageGenerationSettingsService.toMap(imageGenerationSettingsService.getSettings()));
        return result;
    }

    @PutMapping("/image-generation-settings")
    @AdminPermitted("commerce:entitlement:edit")
    public Map<String, Object> updateImageGenerationSettings(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> before = imageGenerationSettingsService.toMap(imageGenerationSettingsService.getSettings());
        Map<String, Object> after = imageGenerationSettingsService.toMap(imageGenerationSettingsService.saveSettings(body));
        auditLogService.recordPolicyUpdate(before, after, "admin");
        Map<String, Object> result = AdminAjaxResult.ok("保存成功");
        result.put("data", after);
        return result;
    }
}
