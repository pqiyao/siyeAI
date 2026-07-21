package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.ops.service.ChatPresetService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/chat-preset")
@AdminPermitted("content:chat-preset:view")
public class AdminJiugaiChatPresetController {

    private final ChatPresetService chatPresetService;

    public AdminJiugaiChatPresetController(ChatPresetService chatPresetService) {
        this.chatPresetService = chatPresetService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String apiType,
            @RequestParam(required = false) Boolean enabled
    ) {
        Map<String, Object> data = chatPresetService.adminList(pageNum, pageSize, keyword, apiType, enabled);
        return AdminAjaxResult.table(
                ((Number) data.getOrDefault("total", 0)).longValue(),
                (java.util.List<?>) data.getOrDefault("rows", java.util.List.of())
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id) {
        Map<String, Object> data = chatPresetService.adminDetail(id);
        if (data.isEmpty()) {
            return AdminAjaxResult.error("\u9884\u8bbe\u4e0d\u5b58\u5728");
        }
        return AdminAjaxResult.okData(data);
    }

    @PostMapping("/sync-st")
    @AdminPermitted("content:chat-preset:edit")
    public Map<String, Object> syncSt() {
        return AdminAjaxResult.okData(chatPresetService.syncOpenAiPlatformPresetsFromSt());
    }

    @PutMapping("/{id}/status")
    @AdminPermitted("content:chat-preset:edit")
    public Map<String, Object> status(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        boolean enabled = body != null && Boolean.parseBoolean(String.valueOf(body.get("enabled")));
        if (!chatPresetService.updateStatus(id, enabled)) {
            return AdminAjaxResult.error("\u9884\u8bbe\u4e0d\u5b58\u5728");
        }
        return AdminAjaxResult.ok("\u4fdd\u5b58\u6210\u529f");
    }

    @PutMapping("/{id}/sort")
    @AdminPermitted("content:chat-preset:edit")
    public Map<String, Object> sort(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body) {
        int sortOrder = intVal(body == null ? null : body.get("sortOrder"), 100);
        if (!chatPresetService.updateSortOrder(id, sortOrder)) {
            return AdminAjaxResult.error("\u9884\u8bbe\u4e0d\u5b58\u5728");
        }
        return AdminAjaxResult.ok("\u4fdd\u5b58\u6210\u529f");
    }

    @DeleteMapping("/{id}")
    @AdminPermitted("content:chat-preset:edit")
    public Map<String, Object> delete(@PathVariable long id) {
        if (!chatPresetService.delete(id)) {
            return AdminAjaxResult.error("\u9884\u8bbe\u4e0d\u5b58\u5728");
        }
        return AdminAjaxResult.ok("\u5220\u9664\u6210\u529f");
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
