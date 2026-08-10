package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.support.service.SupportTicketService;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/admin/jiugai/support-ticket")
@AdminPermitted("support:ticket:view")
public class AdminJiugaiSupportTicketController {

    private final SupportTicketService supportTicketService;

    public AdminJiugaiSupportTicketController(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    @GetMapping("/meta")
    @AdminPermitted("support:ticket:list")
    public Map<String, Object> meta() {
        return AdminAjaxResult.okData(supportTicketService.buildMeta());
    }

    @GetMapping("/list")
    @AdminPermitted("support:ticket:list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String ticketType,
            @RequestParam(required = false) String priority
    ) {
        return AdminAjaxResult.table(
                supportTicketService.countAdminTickets(keyword, status, ticketType, priority),
                supportTicketService.listAdminTickets(keyword, status, ticketType, priority, pageNum, pageSize)
        );
    }

    @GetMapping("/{ticketNo}")
    public Map<String, Object> get(@PathVariable String ticketNo) {
        try {
            return AdminAjaxResult.okData(supportTicketService.getAdminTicket(ticketNo));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/reply")
    @AdminPermitted("support:ticket:reply")
    public Map<String, Object> reply(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            String ticketNo = body == null ? null : stringValue(body.get("ticketNo"));
            String content = body == null ? null : stringValue(body.get("content"));
            String nextStatus = body == null ? null : stringValue(body.get("nextStatus"));
            String nextPriority = body == null ? null : stringValue(body.get("nextPriority"));
            String adminName = adminName(request);
            return AdminAjaxResult.okData(
                    supportTicketService.replyAsAdmin(ticketNo, adminName, content, nextStatus, nextPriority)
            );
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PutMapping("/status")
    @AdminPermitted("support:ticket:update")
    public Map<String, Object> updateStatus(@RequestBody Map<String, Object> body) {
        try {
            String ticketNo = body == null ? null : stringValue(body.get("ticketNo"));
            String nextStatus = body == null ? null : stringValue(body.get("nextStatus"));
            String nextPriority = body == null ? null : stringValue(body.get("nextPriority"));
            return AdminAjaxResult.okData(supportTicketService.updateAdminTicket(ticketNo, nextStatus, nextPriority));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    private static String adminName(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute("adminUsername");
        return value == null ? "客服" : String.valueOf(value);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
