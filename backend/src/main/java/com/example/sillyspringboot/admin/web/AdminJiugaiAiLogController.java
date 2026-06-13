package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.mapper.AdminAiLogMapper;
import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/ai-log")
@AdminPermitted("ops:ailog:view")
public class AdminJiugaiAiLogController {

    private final AdminAiLogMapper aiLogMapper;

    public AdminJiugaiAiLogController(AdminAiLogMapper aiLogMapper) {
        this.aiLogMapper = aiLogMapper;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) String traceId
    ) {
        int p = Math.max(0, pageNum - 1);
        int size = Math.min(100, Math.max(1, pageSize));
        long total = aiLogMapper.countList(blankToNull(channel), success, blankToNull(traceId));
        List<Map<String, Object>> rows = aiLogMapper.listPage(blankToNull(channel), success, blankToNull(traceId), p * size, size);
        return AdminAjaxResult.table(total, rows);
    }

    @DeleteMapping("/clean/{beforeDays}")
    @Transactional
    @AdminPermitted("ops:ailog:clean")
    public Map<String, Object> clean(@PathVariable int beforeDays) {
        int days = Math.max(1, Math.min(3650, beforeDays));
        aiLogMapper.snapshotBeforeDays(days);
        int deleted = aiLogMapper.deleteBeforeDays(days);
        return AdminAjaxResult.ok("已清理 " + deleted + " 条日志");
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }
}
