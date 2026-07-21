package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.security.RuoYiAdminAccessService;
import com.example.sillyspringboot.admin.service.AdminIdentityService;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
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
@RequestMapping("/admin/system/admin-account")
@AdminPermitted("system:admin-user:view")
public class AdminAdminAccountController {

    private final AdminIdentityService identityService;

    public AdminAdminAccountController(AdminIdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return AdminAjaxResult.okData(identityService.buildAccountMeta());
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        return AdminAjaxResult.table(
                identityService.countAccounts(keyword, status),
                identityService.listAccounts(keyword, status, pageNum, pageSize)
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        try {
            return AdminAjaxResult.okData(identityService.getAccount(id));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PostMapping
    @AdminPermitted({"system:admin-user:create", "system:admin-user:edit"})
    public Map<String, Object> add(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        try {
            return AdminAjaxResult.okData(identityService.saveAccount(toCommand(body), adminUsername(request), currentAccountId(request)));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PutMapping
    @AdminPermitted({"system:admin-user:update", "system:admin-user:edit"})
    public Map<String, Object> update(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        try {
            return AdminAjaxResult.okData(identityService.saveAccount(toCommand(body), adminUsername(request), currentAccountId(request)));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PutMapping("/status")
    @AdminPermitted({"system:admin-user:status", "system:admin-user:edit"})
    public Map<String, Object> updateStatus(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        try {
            long id = asRequiredLong(body == null ? null : body.get("id"), "id required");
            String status = asString(body == null ? null : body.get("status"));
            return AdminAjaxResult.okData(identityService.updateAccountStatus(id, status, adminUsername(request), currentAccountId(request)));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PutMapping("/reset-password")
    @AdminPermitted({"system:admin-user:reset-password", "system:admin-user:edit"})
    public Map<String, Object> resetPassword(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        try {
            long id = asRequiredLong(body == null ? null : body.get("id"), "id required");
            String password = asString(body == null ? null : body.get("password"));
            boolean mustResetPassword = asBoolean(body == null ? null : body.get("mustResetPassword"), false);
            return AdminAjaxResult.okData(identityService.resetPassword(id, password, mustResetPassword, adminUsername(request)));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @DeleteMapping("/{ids}")
    @AdminPermitted({"system:admin-user:delete", "system:admin-user:edit"})
    public Map<String, Object> remove(@PathVariable String ids, HttpServletRequest request) {
        try {
            identityService.deleteAccounts(parseIds(ids), currentAccountId(request), adminUsername(request));
            return AdminAjaxResult.ok("Deleted");
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    private static AdminIdentityService.AccountSaveCommand toCommand(Map<String, Object> body) {
        return new AdminIdentityService.AccountSaveCommand(
                asNullableLong(body == null ? null : body.get("id")),
                asString(body == null ? null : body.get("username")),
                asString(body == null ? null : body.get("nickName")),
                asString(body == null ? null : body.get("password")),
                asLongList(body == null ? null : body.get("roleIds")),
                asString(body == null ? null : body.get("status")),
                asNullableBoolean(body == null ? null : body.get("mustResetPassword")),
                asString(body == null ? null : body.get("remark"))
        );
    }

    private static Long currentAccountId(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute("adminSession");
        if (value instanceof RuoYiAdminAccessService.AdminSession session) {
            return session.getId();
        }
        return null;
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

    private static long asRequiredLong(Object value, String message) {
        Long longValue = asNullableLong(value);
        if (longValue == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        }
        return longValue;
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

    private static boolean asBoolean(Object value, boolean defaultValue) {
        Boolean bool = asNullableBoolean(value);
        return bool == null ? defaultValue : bool;
    }

    private static List<Long> asLongList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (Object item : list) {
            Long longValue = asNullableLong(item);
            if (longValue != null) {
                out.add(longValue);
            }
        }
        return out;
    }
}
