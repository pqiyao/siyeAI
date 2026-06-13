package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.service.AdminVoiceStatService;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/voice-stat")
@AdminPermitted("ops:ailog:view")
public class AdminJiugaiVoiceStatController {

    private final AdminVoiceStatService voiceStatService;

    public AdminJiugaiVoiceStatController(AdminVoiceStatService voiceStatService) {
        this.voiceStatService = voiceStatService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String providerSource,
            @RequestParam(required = false) String modelName,
            @RequestParam(required = false) String errorCode,
            @RequestParam(defaultValue = "7") int days
    ) {
        return AdminAjaxResult.table(
                voiceStatService.countList(scope, status, providerSource, modelName, errorCode, days),
                voiceStatService.listPage(scope, status, providerSource, modelName, errorCode, days, pageNum, pageSize)
        );
    }

    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(defaultValue = "7") int days) {
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", voiceStatService.summary(days));
        return result;
    }
}
