package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.service.AdminIdentityService;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.shared.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/system/admin-role")
@AdminPermitted("system:admin-role:view")
public class AdminAdminRoleController {

    private final AdminIdentityService identityService;

    public AdminAdminRoleController(AdminIdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return AdminAjaxResult.okData(identityService.buildRoleMeta());
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled
    ) {
        return AdminAjaxResult.table(
                identityService.countRoles(keyword, enabled),
                identityService.listRoles(keyword, enabled, pageNum, pageSize)
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        try {
            return AdminAjaxResult.okData(identityService.getRole(id));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping
    @AdminPermitted({"system:admin-role:create", "system:admin-role:edit"})
    public Map<String, Object> add(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        try {
            return AdminAjaxResult.okData(identityService.saveRole(toCommand(body), adminUsername(request)));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PutMapping
    @AdminPermitted({"system:admin-role:update", "system:admin-role:edit"})
    public Map<String, Object> update(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        try {
            return AdminAjaxResult.okData(identityService.saveRole(toCommand(body), adminUsername(request)));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PutMapping("/status")
    @AdminPermitted({"system:admin-role:status", "system:admin-role:edit"})
    public Map<String, Object> updateStatus(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        try {
            long id = asLong(body == null ? null : body.get("id"));
            boolean enabled = asBoolean(body == null ? null : body.get("enabled"), true);
            return AdminAjaxResult.okData(identityService.updateRoleStatus(id, enabled, adminUsername(request)));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @DeleteMapping("/{ids}")
    @AdminPermitted({"system:admin-role:delete", "system:admin-role:edit"})
    public Map<String, Object> remove(@PathVariable String ids, HttpServletRequest request) {
        try {
            identityService.deleteRoles(parseIds(ids), adminUsername(request));
            return AdminAjaxResult.ok("Deleted");
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    private static AdminIdentityService.RoleSaveCommand toCommand(Map<String, Object> body) {
        return new AdminIdentityService.RoleSaveCommand(
                asNullableLong(body == null ? null : body.get("id")),
                asString(body == null ? null : body.get("roleKey")),
                asString(body == null ? null : body.get("roleName")),
                asStringList(body == null ? null : body.get("permissionKeys")),
                asNullableBoolean(body == null ? null : body.get("enabled")),
                asNullableInt(body == null ? null : body.get("sortOrder")),
                asString(body == null ? null : body.get("remark"))
        );
    }

    private static String adminUsername(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute("adminUsername");
        return value == null ? "system" : String.valueOf(value);
    }

    private static List<Long> parseIds(String ids) {
        List<Long> list = new ArrayList<>();
        if (ids == null || ids.isBlank()) {
            return list;
        }
        for (String token : ids.split(",")) {
            Long value = asNullableLong(token);
            if (value != null) {
                list.add(value);
            }
        }
        return list;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long asLong(Object value) {
        Long longValue = asNullableLong(value);
        if (longValue == null) {
            throw new BusinessException(com.example.sillyspringboot.shared.error.ErrorCode.VALIDATION_FAILED, "id required");
        }
        return longValue;
    }

    private static Long asNullableLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer asNullableInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        Boolean b = asNullableBoolean(value);
        return b == null ? defaultValue : b;
    }

    private static Boolean asNullableBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String s && !s.isBlank()) {
            return Boolean.parseBoolean(s.trim());
        }
        return null;
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                out.add(String.valueOf(item));
            }
        }
        return out;
    }
}
