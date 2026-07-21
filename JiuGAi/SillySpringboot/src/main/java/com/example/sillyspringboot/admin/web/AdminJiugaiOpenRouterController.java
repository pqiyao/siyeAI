package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.integration.sillytavern.StModelRoutingService;
import com.example.sillyspringboot.integration.sillytavern.StSettingsAdminService;
import com.example.sillyspringboot.integration.sillytavern.StUnavailableException;
import com.example.sillyspringboot.integration.sillytavern.dto.OpenRouterGenerationAdminDto;
import com.example.sillyspringboot.integration.sillytavern.dto.StModelProviderAdminDto;
import com.example.sillyspringboot.integration.sillytavern.dto.StModelRouteAdminDto;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/openrouter-generation")
@AdminPermitted("ops:openrouter:view")
public class AdminJiugaiOpenRouterController {

    private final StSettingsAdminService stSettingsAdminService;
    private final StModelRoutingService modelRoutingService;

    public AdminJiugaiOpenRouterController(
            StSettingsAdminService stSettingsAdminService,
            StModelRoutingService modelRoutingService
    ) {
        this.stSettingsAdminService = stSettingsAdminService;
        this.modelRoutingService = modelRoutingService;
    }

    @GetMapping
    public Map<String, Object> get() {
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", stSettingsAdminService.getForAdmin());
        result.put("routing", modelRoutingService.getAdminSnapshot());

        Map<String, String> hint = new LinkedHashMap<>();
        hint.put("apiKey", "API Key 建议保存在环境变量或安全配置中，不建议直接写到后台页面。");
        hint.put("chatCompletionSource", "这里会直接写入 ST settings 的 chat_completion_source。");
        hint.put("defaultModel", "这里会直接写入 ST 当前 source 对应的模型字段；下方模型路由仍会覆盖正式聊天的 provider/model。");
        hint.put("defaultTemperature", "这里会直接写入 ST settings 的 temp_openai。");
        hint.put("defaultMaxOutputTokens", "这里会直接写入 ST settings 的 openai_max_tokens。填 0 表示不主动覆盖。");
        hint.put("topP", "这里会直接写入 ST settings 的 top_p_openai，常用区间 0 到 1。");
        hint.put("frequencyPenalty", "这里会直接写入 ST settings 的 freq_pen_openai，范围 -2 到 2。");
        hint.put("presencePenalty", "这里会直接写入 ST settings 的 pres_pen_openai，范围 -2 到 2。");
        hint.put("stopSequences", "ST 当前没有统一的全局 stop 写入口，这里仍作为网关 fallback stop 使用，多个值请用 | 分隔。");
        result.put("hint", hint);
        return result;
    }

    @PutMapping
    @AdminPermitted("ops:openrouter:edit")
    public Map<String, Object> update(@RequestBody(required = false) OpenRouterGenerationAdminDto body) {
        try {
            stSettingsAdminService.updateFromAdmin(body);
            return AdminAjaxResult.ok("ST 设置已保存");
        } catch (IllegalArgumentException e) {
            return AdminAjaxResult.error(e.getMessage());
        } catch (StUnavailableException e) {
            return AdminAjaxResult.error("保存失败：SillyTavern 当前不可用，请先确认 ST 服务已启动");
        }
    }

    @PutMapping("/provider")
    @AdminPermitted("ops:openrouter:edit")
    public Map<String, Object> saveProvider(@RequestBody(required = false) StModelProviderAdminDto body) {
        try {
            Map<String, Object> result = AdminAjaxResult.ok("保存成功");
            result.put("data", modelRoutingService.saveProvider(body));
            return result;
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @DeleteMapping("/provider/{id}")
    @AdminPermitted({"ops:openrouter:delete", "ops:openrouter:edit"})
    public Map<String, Object> deleteProvider(@PathVariable("id") Long id) {
        try {
            modelRoutingService.deleteProvider(id);
            return AdminAjaxResult.ok("删除成功");
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PutMapping("/route")
    @AdminPermitted("ops:openrouter:edit")
    public Map<String, Object> saveRoute(@RequestBody(required = false) StModelRouteAdminDto body) {
        try {
            Map<String, Object> result = AdminAjaxResult.ok("保存成功");
            result.put("data", modelRoutingService.saveRoute(body));
            return result;
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @DeleteMapping("/route/{id}")
    @AdminPermitted({"ops:openrouter:delete", "ops:openrouter:edit"})
    public Map<String, Object> deleteRoute(@PathVariable("id") Long id) {
        try {
            modelRoutingService.deleteRoute(id);
            return AdminAjaxResult.ok("删除成功");
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }
}
