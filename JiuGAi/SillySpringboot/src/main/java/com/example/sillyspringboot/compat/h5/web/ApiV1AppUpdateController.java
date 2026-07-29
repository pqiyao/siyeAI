package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.ops.service.AppUpdateService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/app/update")
public class ApiV1AppUpdateController {
    private final AppUpdateService appUpdateService;

    public ApiV1AppUpdateController(AppUpdateService appUpdateService) {
        this.appUpdateService = appUpdateService;
    }

    @PostMapping("/check")
    public ApiV1Result<Map<String, Object>> check(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) {
            return ApiV1Result.fail("请求参数不能为空");
        }
        int versionCode = asInt(body.get("versionCode"), -1);
        if (versionCode < 0) {
            return ApiV1Result.fail("versionCode 不正确");
        }
        Map<String, Object> data = appUpdateService.checkAndroid(
                asString(body.get("appId")),
                asString(body.get("packageName")),
                asString(body.get("channel")),
                versionCode
        );
        return ApiV1Result.ok(data);
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(asString(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
