package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.compat.h5.service.H5LegacyUserCompatibilityService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class H5LegacyUserController {

    private final H5LegacyUserCompatibilityService legacyUserService;

    public H5LegacyUserController(H5LegacyUserCompatibilityService legacyUserCompatibilityService) {
        this.legacyUserService = legacyUserCompatibilityService;
    }

    @PostMapping(value = "/user_info", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> userInfo(
            @RequestHeader(name = "token", required = false) String headerToken,
            @RequestParam(name = "token", required = false) String requestToken
    ) {
        String token = legacyUserService.pickToken(headerToken, requestToken);
        try {
            return legacyOk(legacyUserService.buildLegacyProfileByToken(token));
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED) {
                return legacyFail(4003, safeMessage(ex.getMessage()), null);
            }
            return legacyFail(0, safeMessage(ex.getMessage()), null);
        }
    }

    @PostMapping(value = "/updLang", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> updateLanguage(
            @RequestHeader(name = "token", required = false) String headerToken,
            @RequestParam MultiValueMap<String, String> form
    ) {
        String token = legacyUserService.pickToken(headerToken, firstValue(form, "token"));
        try {
            legacyUserService.updateLanguageByToken(token, firstValue(form, "clang"));
            return legacyOk(new LinkedHashMap<>());
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED) {
                return legacyFail(4003, safeMessage(ex.getMessage()), null);
            }
            return legacyFail(0, safeMessage(ex.getMessage()), null);
        }
    }

    @PostMapping(value = "/reset_pwd", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> resetPassword(
            @RequestHeader(name = "token", required = false) String headerToken,
            @RequestParam MultiValueMap<String, String> form
    ) {
        String token = legacyUserService.pickToken(headerToken, firstValue(form, "token"));
        try {
            legacyUserService.resetPasswordByToken(token, firstValue(form, "old_pwd"), firstValue(form, "new_pwd"));
            return legacyFail(1, "Password updated", new LinkedHashMap<>());
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED) {
                return legacyFail(4003, safeMessage(ex.getMessage()), null);
            }
            return legacyFail(0, safeMessage(ex.getMessage()), null);
        }
    }

    private static Map<String, Object> legacyOk(Object data) {
        return legacyFail(1, "ok", data);
    }

    private static Map<String, Object> legacyFail(int code, String msg, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        body.put("data", data);
        return body;
    }

    private static String safeMessage(String message) {
        return message == null || message.isBlank() ? "请求失败" : message.trim();
    }

    private static String firstValue(MultiValueMap<String, String> form, String key) {
        if (form == null || !form.containsKey(key) || form.get(key) == null || form.get(key).isEmpty()) {
            return "";
        }
        return form.getFirst(key);
    }
}
