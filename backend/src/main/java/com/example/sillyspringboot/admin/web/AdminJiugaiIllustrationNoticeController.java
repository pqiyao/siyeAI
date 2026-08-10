package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.illustration.service.IllustrationNoticeService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/illustration-notice")
@AdminPermitted("content:illustration-notice:view")
public class AdminJiugaiIllustrationNoticeController {

    private final IllustrationNoticeService noticeService;

    public AdminJiugaiIllustrationNoticeController(IllustrationNoticeService noticeService) {
        this.noticeService = noticeService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled
    ) {
        try {
            return AdminAjaxResult.table(
                    noticeService.countAdminNotices(keyword, category, enabled),
                    noticeService.listAdminNotices(keyword, category, enabled, pageNum, pageSize)
            );
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        Map<String, Object> data = noticeService.getAdminNotice(id);
        return data == null ? AdminAjaxResult.error("通知不存在") : AdminAjaxResult.okData(data);
    }

    @PostMapping
    @AdminPermitted("content:illustration-notice:edit")
    public Map<String, Object> add(@RequestBody(required = false) Map<String, Object> body) {
        try {
            return AdminAjaxResult.okData(noticeService.saveAdminNotice(body));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PutMapping
    @AdminPermitted("content:illustration-notice:edit")
    public Map<String, Object> update(@RequestBody(required = false) Map<String, Object> body) {
        try {
            return AdminAjaxResult.okData(noticeService.saveAdminNotice(body));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @AdminPermitted("content:illustration-notice:edit")
    public Map<String, Object> remove(@PathVariable long id) {
        int deleted = noticeService.removeAdminNotice(id);
        return deleted > 0 ? AdminAjaxResult.ok("删除成功") : AdminAjaxResult.error("通知不存在");
    }
}
