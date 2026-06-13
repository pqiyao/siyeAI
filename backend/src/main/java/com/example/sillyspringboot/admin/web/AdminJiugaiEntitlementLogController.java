package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.ops.service.EntitlementAuditLogService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/entitlement-log")
@AdminPermitted("commerce:entitlement-log:view")
public class AdminJiugaiEntitlementLogController {

    private final EntitlementAuditLogService auditLogService;

    public AdminJiugaiEntitlementLogController(EntitlementAuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String scopeType,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String targetUserId
    ) {
        Long safeTargetUserId = parsePositiveId(targetUserId);
        long total = auditLogService.countList(scopeType, actionType, keyword, safeTargetUserId);
        List<Map<String, Object>> rows = auditLogService.listPage(scopeType, actionType, keyword, safeTargetUserId, pageNum, pageSize);
        return AdminAjaxResult.table(total, rows);
    }

    @DeleteMapping("/{ids}")
    @AdminPermitted({"commerce:entitlement-log:delete", "commerce:entitlement:edit"})
    public Map<String, Object> delete(@PathVariable String ids) {
        int deleted = auditLogService.deleteByIds(ids);
        Map<String, Object> result = AdminAjaxResult.ok("删除成功");
        result.put("count", deleted);
        return result;
    }

    private static Long parsePositiveId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
