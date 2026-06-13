package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.humanchat.service.HumanChatService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/human-chat-message")
@AdminPermitted("social:chat-message:view")
public class AdminJiugaiHumanChatMessageController {

    private final HumanChatService humanChatService;

    public AdminJiugaiHumanChatMessageController(HumanChatService humanChatService) {
        this.humanChatService = humanChatService;
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return AdminAjaxResult.okData(Map.of(
                "messageTypeOptions", new String[]{"text", "image"},
                "statusOptions", new String[]{"normal", "recalled"}
        ));
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String conversationKey,
            @RequestParam(required = false) Long fromUserId,
            @RequestParam(required = false) Long toUserId,
            @RequestParam(required = false) String messageType,
            @RequestParam(required = false) String status
    ) {
        try {
            return AdminAjaxResult.table(
                    humanChatService.countAdminMessages(keyword, conversationKey, fromUserId, toUserId, messageType, status),
                    humanChatService.listAdminMessages(keyword, conversationKey, fromUserId, toUserId, messageType, status, pageNum, pageSize)
            );
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/{messageId}")
    public Map<String, Object> get(@PathVariable long messageId) {
        try {
            return AdminAjaxResult.okData(humanChatService.getAdminMessage(messageId));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/admin-recall")
    @AdminPermitted("social:chat-message:recall")
    public Map<String, Object> adminRecall(@RequestBody(required = false) Map<String, Object> body) {
        try {
            long messageId = longValue(body == null ? null : body.get("messageId"));
            return AdminAjaxResult.okData(humanChatService.adminRecall(messageId));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new BusinessException(com.example.sillyspringboot.shared.error.ErrorCode.VALIDATION_FAILED, "messageId 不正确");
        }
    }
}
