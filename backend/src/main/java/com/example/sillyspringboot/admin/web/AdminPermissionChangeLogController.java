package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.mapper.AppAdminPermissionChangeLogMapper;
import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/system/permission-log")
@AdminPermitted({"system:permission-log:view", "system:admin-role:view", "system:admin-user:view"})
public class AdminPermissionChangeLogController {

    private final AppAdminPermissionChangeLogMapper permissionChangeLogMapper;

    public AdminPermissionChangeLogController(AppAdminPermissionChangeLogMapper permissionChangeLogMapper) {
        this.permissionChangeLogMapper = permissionChangeLogMapper;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String keyword
    ) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePageNum - 1) * safePageSize;
        String safeTargetType = trimToNull(targetType);
        String safeAction = trimToNull(action);
        String safeOperator = trimToNull(operator);
        String safeKeyword = trimToNull(keyword);
        return AdminAjaxResult.table(
                permissionChangeLogMapper.countList(safeTargetType, safeAction, safeOperator, safeKeyword),
                permissionChangeLogMapper.listPage(safeTargetType, safeAction, safeOperator, safeKeyword, offset, safePageSize)
        );
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return AdminAjaxResult.okData(Map.of(
                "targetTypes", List.of(
                        Map.of("value", "ROLE", "label", "Role"),
                        Map.of("value", "ACCOUNT", "label", "Account")
                ),
                "actions", List.of(
                        Map.of("value", "ROLE_CREATE", "label", "Role created"),
                        Map.of("value", "ROLE_UPDATE", "label", "Role updated"),
                        Map.of("value", "ROLE_STATUS", "label", "Role status changed"),
                        Map.of("value", "ROLE_DELETE", "label", "Role deleted"),
                        Map.of("value", "ACCOUNT_CREATE", "label", "Account created"),
                        Map.of("value", "ACCOUNT_UPDATE", "label", "Account roles updated"),
                        Map.of("value", "ACCOUNT_STATUS", "label", "Account status changed"),
                        Map.of("value", "ACCOUNT_DELETE", "label", "Account deleted")
                )
        ));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
