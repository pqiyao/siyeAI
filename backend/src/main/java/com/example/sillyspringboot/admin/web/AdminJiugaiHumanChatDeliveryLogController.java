package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.humanchat.service.HumanChatDeliveryLogService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/human-chat-delivery-log")
@AdminPermitted("social:chat-delivery-log:view")
public class AdminJiugaiHumanChatDeliveryLogController {

    private final HumanChatDeliveryLogService deliveryLogService;

    public AdminJiugaiHumanChatDeliveryLogController(HumanChatDeliveryLogService deliveryLogService) {
        this.deliveryLogService = deliveryLogService;
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return AdminAjaxResult.okData(Map.of(
                "statusOptions", new String[]{"success", "offline", "failed", "partial"},
                "eventTypeOptions", new String[]{"private_message_sent", "private_message_received", "messages_read", "message_recalled"}
        ));
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long targetUserId,
            @RequestParam(required = false) Long messageId
    ) {
        try {
            return AdminAjaxResult.table(
                    deliveryLogService.countAdminDeliveryLogs(keyword, eventType, status, targetUserId, messageId),
                    deliveryLogService.listAdminDeliveryLogs(keyword, eventType, status, targetUserId, messageId, pageNum, pageSize)
            );
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        try {
            return AdminAjaxResult.okData(deliveryLogService.getAdminDeliveryLog(id));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }
}
