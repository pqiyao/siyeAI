package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.admin.service.AdminH5UserLifecycleService;
import com.example.sillyspringboot.auth.dto.AppAuthSessionResponse;
import com.example.sillyspringboot.auth.dto.H5AccountLoginRequest;
import com.example.sillyspringboot.auth.dto.H5AccountRegisterRequest;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.service.AppAuthService;
import com.example.sillyspringboot.compat.h5.service.H5LegacyUserCompatibilityService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/index")
public class H5LegacyIndexController {

    private final AppAuthService appAuthService;
    private final H5LegacyUserCompatibilityService legacyUserService;
    private final AdminH5UserLifecycleService userLifecycleService;

    public H5LegacyIndexController(
            AppAuthService appAuthService,
            H5LegacyUserCompatibilityService legacyUserService,
            AdminH5UserLifecycleService userLifecycleService
    ) {
        this.appAuthService = appAuthService;
        this.legacyUserService = legacyUserService;
        this.userLifecycleService = userLifecycleService;
    }

    @PostMapping(value = "/article_info", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> articleInfo(@RequestParam(name = "id", required = false) String idStr) {
        long id = 1L;
        try {
            if (idStr != null && !idStr.isBlank()) {
                id = Long.parseLong(idStr.trim());
            }
        } catch (NumberFormatException ignored) {
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", "说明");
        data.put("content", "<p>（本地联调：内容由 SillySpringboot 占位返回，id=" + id + "）</p>");
        return legacyOk(data);
    }

    @PostMapping(value = "/getCountryForIp", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> getCountryForIp() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("country", "CN");
        data.put("language", "zh-cn");
        return legacyOk(data);
    }

    @PostMapping(value = "/emslogin", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> emsLogin(
            @RequestParam(name = "account", required = false) String account,
            @RequestParam(name = "password", required = false) String password
    ) {
        H5AccountLoginRequest request = new H5AccountLoginRequest();
        request.setAccount(account);
        request.setPassword(password);
        try {
            AppAuthSessionResponse session = appAuthService.loginWithH5Account(request);
            return legacyAuthOk(legacyUserService.buildLegacyUserInfoByToken(session.token()));
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED) {
                return legacyFail(4002, safeMessage(ex.getMessage()), null);
            }
            return legacyFail(0, safeMessage(ex.getMessage()), null);
        }
    }

    @PostMapping(value = "/emsregister", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> emsRegister(
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "password", required = false) String password,
            @RequestParam(name = "client_uid", required = false) String clientUid
    ) {
        H5AccountRegisterRequest request = new H5AccountRegisterRequest();
        request.setAccount(username);
        request.setPassword(password);
        try {
            AppAuthSessionResponse session = appAuthService.registerWithH5Account(request, clientUid);
            return legacyAuthOk(legacyUserService.buildLegacyUserInfoByToken(session.token()));
        } catch (BusinessException ex) {
            return legacyFail(0, safeMessage(ex.getMessage()), null);
        }
    }

    @PostMapping(value = "/profile", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> profile(
            @RequestHeader(name = "token", required = false) String headerToken,
            @RequestParam MultiValueMap<String, String> form
    ) {
        String token = legacyUserService.pickToken(headerToken, firstValue(form, "token"));
        try {
            Map<String, Object> data = legacyUserService.updateProfileByToken(token, copyWithoutToken(form));
            return legacyOk(data);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED) {
                return legacyFail(4003, safeMessage(ex.getMessage()), null);
            }
            return legacyFail(0, safeMessage(ex.getMessage()), null);
        }
    }

    @PostMapping(value = "/forever_exit", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> foreverExit(
            @RequestHeader(name = "token", required = false) String headerToken,
            @RequestParam(name = "token", required = false) String requestToken
    ) {
        String token = legacyUserService.pickToken(headerToken, requestToken);
        try {
            AppUser user = legacyUserService.requireUserByToken(token);
            userLifecycleService.deleteUserById(user.getId());
            return legacyOk(new LinkedHashMap<>());
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED) {
                return legacyFail(4003, safeMessage(ex.getMessage()), null);
            }
            return legacyFail(0, safeMessage(ex.getMessage()), null);
        }
    }

    private static Map<String, Object> legacyAuthOk(Map<String, Object> userinfo) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userinfo", userinfo);
        return legacyOk(data);
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

    private static MultiValueMap<String, String> copyWithoutToken(MultiValueMap<String, String> form) {
        MultiValueMap<String, String> copied = new LinkedMultiValueMap<>();
        if (form == null) {
            return copied;
        }
        for (Map.Entry<String, java.util.List<String>> entry : form.entrySet()) {
            if ("token".equals(entry.getKey())) {
                continue;
            }
            copied.put(entry.getKey(), entry.getValue());
        }
        return copied;
    }
}
