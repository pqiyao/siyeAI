package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.ops.service.SocialFeatureSettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/social-settings")
@AdminPermitted("social:settings:view")
public class AdminJiugaiSocialSettingsController {

    private final SocialFeatureSettingsService settingsService;

    public AdminJiugaiSocialSettingsController(SocialFeatureSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public Map<String, Object> get() {
        return AdminAjaxResult.okData(settingsService.toMap(settingsService.getSettings()));
    }

    @PutMapping
    @AdminPermitted("social:settings:edit")
    public Map<String, Object> update(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> result = AdminAjaxResult.ok("保存成功");
        result.put("data", settingsService.toMap(settingsService.saveSettings(body)));
        return result;
    }
}
