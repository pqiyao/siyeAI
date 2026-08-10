package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.service.CharacterReviewAuditLogService;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/character-review-log")
@AdminPermitted("content:review:view")
public class AdminJiugaiCharacterReviewLogController {

    private final CharacterReviewAuditLogService reviewAuditLogService;

    public AdminJiugaiCharacterReviewLogController(CharacterReviewAuditLogService reviewAuditLogService) {
        this.reviewAuditLogService = reviewAuditLogService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) String keyword
    ) {
        return AdminAjaxResult.table(
                reviewAuditLogService.countList(reviewStatus, keyword),
                reviewAuditLogService.listPage(reviewStatus, keyword, pageNum, pageSize)
        );
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("data", reviewAuditLogService.summary());
        return result;
    }

    @DeleteMapping("/{ids}")
    @AdminPermitted("content:review:edit")
    public Map<String, Object> delete(@PathVariable String ids) {
        int deleted = reviewAuditLogService.deleteByIds(ids);
        Map<String, Object> result = AdminAjaxResult.ok("删除成功");
        result.put("count", deleted);
        return result;
    }
}
