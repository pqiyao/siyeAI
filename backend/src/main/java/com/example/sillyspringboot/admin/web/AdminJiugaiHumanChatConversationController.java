package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.humanchat.service.HumanChatService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/human-chat-conversation")
@AdminPermitted("social:chat-conversation:view")
public class AdminJiugaiHumanChatConversationController {

    private final HumanChatService humanChatService;

    public AdminJiugaiHumanChatConversationController(HumanChatService humanChatService) {
        this.humanChatService = humanChatService;
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return AdminAjaxResult.okData(Map.of());
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long peerUserId
    ) {
        try {
            return AdminAjaxResult.table(
                    humanChatService.countAdminConversations(keyword, userId, peerUserId),
                    humanChatService.listAdminConversations(keyword, userId, peerUserId, pageNum, pageSize)
            );
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/{conversationId}")
    public Map<String, Object> get(@PathVariable long conversationId) {
        try {
            return AdminAjaxResult.okData(humanChatService.getAdminConversation(conversationId));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }
}
