package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.ai.service.AiProviderProbeService;
import com.example.sillyspringboot.ai.service.AiChatModelService;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.ai.service.AiRoutingRuntimeSettingsService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/ai-routing")
@AdminPermitted("ops:openrouter:view")
public class AdminAiRoutingController {

    private final AiRoutingService routingService;
    private final AiProviderProbeService probeService;
    private final AiRoutingRuntimeSettingsService runtimeSettingsService;
    private final AiChatModelService chatModelService;

    public AdminAiRoutingController(
            AiRoutingService routingService,
            AiProviderProbeService probeService,
            AiRoutingRuntimeSettingsService runtimeSettingsService,
            AiChatModelService chatModelService
    ) {
        this.routingService = routingService;
        this.probeService = probeService;
        this.runtimeSettingsService = runtimeSettingsService;
        this.chatModelService = chatModelService;
    }

    @GetMapping
    public Map<String, Object> snapshot() {
        Map<String, Object> result = AdminAjaxResult.ok();
        Map<String, Object> snapshot = routingService.adminSnapshot();
        snapshot.put("chatModelCatalog", chatModelService.adminSnapshot());
        result.put("data", snapshot);
        return result;
    }

    @PutMapping("/chat-model-settings")
    @AdminPermitted("ops:openrouter:edit")
    public Map<String, Object> saveChatModelSettings(@RequestBody(required = false) Map<String, Object> body) {
        return dataResult("聊天模型开放策略已更新", () -> chatModelService.saveSettings(body));
    }

    @PutMapping("/chat-offering")
    @AdminPermitted("ops:openrouter:edit")
    public Map<String, Object> saveChatOffering(@RequestBody(required = false) Map<String, Object> body) {
        return dataResult("用户可选模型已保存", () -> chatModelService.saveOffering(body));
    }

    @PutMapping("/chat-offering-bundle")
    @AdminPermitted("ops:openrouter:edit")
    public Map<String, Object> saveChatOfferingBundle(@RequestBody(required = false) Map<String, Object> body) {
        return dataResult("用户可选模型与 fallback 路由已保存", () -> chatModelService.saveOfferingBundle(body));
    }

    @DeleteMapping("/chat-offering/{id}")
    @AdminPermitted({"ops:openrouter:delete", "ops:openrouter:edit"})
    public Map<String, Object> deleteChatOffering(@PathVariable("id") long id) {
        return actionResult("用户可选模型已删除", () -> chatModelService.deleteOffering(id));
    }

    @PostMapping("/models")
    @AdminPermitted("ops:openrouter:edit")
    public Map<String, Object> discoverModels(@RequestBody(required = false) Map<String, Object> body) {
        return dataResult("模型获取成功", () -> probeService.discoverModels(body));
    }

    @PostMapping("/probe")
    @AdminPermitted("ops:openrouter:edit")
    public Map<String, Object> probe(@RequestBody(required = false) Map<String, Object> body) {
        return dataResult("能力测试成功", () -> probeService.probe(body));
    }

    @PostMapping("/import-legacy-chat")
    @AdminPermitted("ops:openrouter:edit")
    public Map<String, Object> importLegacyChat() {
        return dataResult("旧聊天路由已复制", routingService::importLegacyChatRoute);
    }

    @PutMapping("/runtime-settings")
    @AdminPermitted("ops:openrouter:edit")
    public Map<String, Object> saveRuntimeSettings(@RequestBody(required = false) Map<String, Object> body) {
        return dataResult("运行开关已更新", () -> runtimeSettingsService.toMap(runtimeSettingsService.save(body)));
    }

    @DeleteMapping("/runtime-settings")
    @AdminPermitted("ops:openrouter:edit")
    public Map<String, Object> resetRuntimeSettings() {
        return dataResult("运行开关已恢复为环境默认值",
                () -> runtimeSettingsService.toMap(runtimeSettingsService.resetToEnvironment()));
    }

    @PutMapping("/provider")
    @AdminPermitted("ops:openrouter:edit")
    public Map<String, Object> saveProvider(@RequestBody(required = false) Map<String, Object> body) {
        return dataResult("供应商能力已保存", () -> routingService.saveProvider(body));
    }

    @DeleteMapping("/deployment/{id}")
    @AdminPermitted({"ops:openrouter:delete", "ops:openrouter:edit"})
    public Map<String, Object> deleteDeployment(@PathVariable("id") long id) {
        return actionResult("能力模型已删除", () -> routingService.deleteDeployment(id));
    }

    @PostMapping("/deployment/{id}/migrate-delete")
    @AdminPermitted({"ops:openrouter:delete", "ops:openrouter:edit"})
    public Map<String, Object> migrateAndDeleteDeployment(
            @PathVariable("id") long id,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        return dataResult("路由引用已迁移，能力模型已删除",
                () -> routingService.migrateAndDeleteDeployment(id, body));
    }

    @DeleteMapping("/account/{id}")
    @AdminPermitted({"ops:openrouter:delete", "ops:openrouter:edit"})
    public Map<String, Object> deleteAccount(@PathVariable("id") long id) {
        return actionResult("供应商账户已删除", () -> routingService.deleteAccount(id));
    }

    @PutMapping("/route")
    @AdminPermitted("ops:openrouter:edit")
    public Map<String, Object> saveRoute(@RequestBody(required = false) Map<String, Object> body) {
        return dataResult("能力路由已保存", () -> routingService.saveRoute(body));
    }

    @DeleteMapping("/route/{id}")
    @AdminPermitted({"ops:openrouter:delete", "ops:openrouter:edit"})
    public Map<String, Object> deleteRoute(@PathVariable("id") long id) {
        return actionResult("能力路由已删除", () -> routingService.deleteRoute(id));
    }

    private Map<String, Object> dataResult(String message, ResultSupplier action) {
        try {
            Map<String, Object> result = AdminAjaxResult.ok(message);
            result.put("data", action.get());
            return result;
        } catch (BusinessException ex) {
            return AdminAjaxResult.error(ex.getMessage());
        }
    }

    private Map<String, Object> actionResult(String message, Runnable action) {
        try {
            action.run();
            return AdminAjaxResult.ok(message);
        } catch (BusinessException ex) {
            return AdminAjaxResult.error(ex.getMessage());
        }
    }

    @FunctionalInterface
    private interface ResultSupplier {
        Object get();
    }
}
