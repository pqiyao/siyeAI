package com.example.sillyspringboot.social.web;

import com.example.sillyspringboot.ops.service.SocialFeatureSettingsService;
import com.example.sillyspringboot.shared.web.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/social")
public class AppSocialConfigController {

    private final SocialFeatureSettingsService settingsService;

    public AppSocialConfigController(SocialFeatureSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/config")
    public ApiResult<Map<String, Object>> config() {
        return ApiResult.ok(settingsService.toMap(settingsService.getSettings()));
    }
}
