package com.example.sillyspringboot.app.web;

import com.example.sillyspringboot.shared.web.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/app")
public class AppPingController {

    @GetMapping("/ping")
    public ApiResult<Map<String, Object>> ping() {
        return ApiResult.ok(Map.of("pong", true));
    }
}
