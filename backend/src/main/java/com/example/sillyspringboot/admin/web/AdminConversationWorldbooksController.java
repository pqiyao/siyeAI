package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.conversation.dto.UpdateConversationStDisplayNameRequest;
import com.example.sillyspringboot.conversation.dto.UpdateConversationWorldbooksRequest;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.integration.sillytavern.StWorldbookCatalogService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 后台：会话级 ST 运行时配置。
 */
@RestController
@RequestMapping("/admin/jiugai/conversations")
@AdminPermitted("conversation:runtime:view")
public class AdminConversationWorldbooksController {

    private final AppConversationService conversationService;
    private final StWorldbookCatalogService worldbookCatalogService;

    public AdminConversationWorldbooksController(
            AppConversationService conversationService,
            StWorldbookCatalogService worldbookCatalogService
    ) {
        this.conversationService = conversationService;
        this.worldbookCatalogService = worldbookCatalogService;
    }

    @GetMapping("/worldbooks/options")
    public Map<String, Object> worldbookOptions() {
        return AdminAjaxResult.okData(worldbookCatalogService.listAvailableWorldbooks());
    }

    @PutMapping("/{conversationId}/worldbooks")
    @AdminPermitted("conversation:runtime:edit")
    public Map<String, Object> updateWorldbooks(
            @PathVariable long conversationId,
            @RequestBody(required = false) UpdateConversationWorldbooksRequest body
    ) {
        if (body == null || body.getWorldNames() == null) {
            return AdminAjaxResult.error("缺少 worldNames");
        }
        conversationService.updateWorldbooksAdmin(conversationId, body.getWorldNames());
        return AdminAjaxResult.ok("保存成功");
    }

    @PutMapping("/{conversationId}/st-display-name")
    @AdminPermitted("conversation:runtime:edit")
    public Map<String, Object> updateStDisplayName(
            @PathVariable long conversationId,
            @RequestBody(required = false) UpdateConversationStDisplayNameRequest body
    ) {
        conversationService.updateStDisplayNameOverrideAdmin(
                conversationId,
                body == null ? null : body.getStDisplayNameOverride()
        );
        return AdminAjaxResult.ok("保存成功");
    }
}
