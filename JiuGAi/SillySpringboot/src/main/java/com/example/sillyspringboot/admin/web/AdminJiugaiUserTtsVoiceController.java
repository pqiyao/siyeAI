package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.ops.service.UserTtsVoiceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/user-tts-voice")
@AdminPermitted("ops:media:user-voice:view")
public class AdminJiugaiUserTtsVoiceController {

    private final UserTtsVoiceService voiceService;

    public AdminJiugaiUserTtsVoiceController(UserTtsVoiceService voiceService) {
        this.voiceService = voiceService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status
    ) {
        Map<String, Object> page = voiceService.listAdmin(keyword, status, pageNum, pageSize);
        return AdminAjaxResult.table(
                ((Number) page.getOrDefault("total", 0L)).longValue(),
                (List<?>) page.getOrDefault("rows", List.of()));
    }

    @PutMapping("/{voiceId}/disabled")
    @AdminPermitted("ops:media:user-voice:manage")
    public Map<String, Object> disabled(
            @PathVariable long voiceId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        boolean disabled = body != null && Boolean.parseBoolean(String.valueOf(body.getOrDefault("disabled", false)));
        voiceService.setAdminDisabled(voiceId, disabled);
        return AdminAjaxResult.ok(disabled ? "已停用" : "已恢复");
    }

    @PutMapping("/{voiceId}/finish-provisioning")
    @AdminPermitted("ops:media:user-voice:manage")
    public Map<String, Object> finishProvisioning(@PathVariable long voiceId) {
        voiceService.finishAdminProvisioning(voiceId);
        return AdminAjaxResult.ok("异常创建任务已结束，用户音色名额已释放");
    }
}
