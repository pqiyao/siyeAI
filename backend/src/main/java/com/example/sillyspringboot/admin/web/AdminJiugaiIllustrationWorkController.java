package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.illustration.service.IllustrationWorkService;
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
@RequestMapping("/admin/jiugai/illustration-work")
@AdminPermitted("content:illustration:view")
public class AdminJiugaiIllustrationWorkController {

    private final IllustrationWorkService illustrationWorkService;

    public AdminJiugaiIllustrationWorkController(IllustrationWorkService illustrationWorkService) {
        this.illustrationWorkService = illustrationWorkService;
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return AdminAjaxResult.okData(illustrationWorkService.buildMeta());
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String contentLevel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source
    ) {
        try {
            return AdminAjaxResult.table(
                    illustrationWorkService.countAdminWorks(keyword, category, contentLevel, status, source),
                    illustrationWorkService.listAdminWorks(keyword, category, contentLevel, status, source, pageNum, pageSize)
            );
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        Map<String, Object> data = illustrationWorkService.getAdminWork(id);
        return data == null ? AdminAjaxResult.error("作品不存在") : AdminAjaxResult.okData(data);
    }

    @PostMapping
    @AdminPermitted("content:illustration:edit")
    public Map<String, Object> add(@RequestBody(required = false) Map<String, Object> body) {
        try {
            return AdminAjaxResult.okData(illustrationWorkService.saveAdminWork(body));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PutMapping
    @AdminPermitted("content:illustration:edit")
    public Map<String, Object> update(@RequestBody(required = false) Map<String, Object> body) {
        try {
            return AdminAjaxResult.okData(illustrationWorkService.saveAdminWork(body));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PutMapping("/status")
    @AdminPermitted("content:illustration:review")
    public Map<String, Object> updateStatus(@RequestBody(required = false) Map<String, Object> body) {
        try {
            long id = longVal(body == null ? null : body.get("id"));
            String status = stringVal(body == null ? null : body.get("status"));
            String auditNote = stringVal(body == null ? null : body.get("auditNote"));
            return AdminAjaxResult.okData(illustrationWorkService.updateAdminStatus(id, status, auditNote));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @AdminPermitted("content:illustration:delete")
    public Map<String, Object> remove(@PathVariable long id) {
        int deleted = illustrationWorkService.removeAdminWork(id);
        return deleted > 0 ? AdminAjaxResult.ok("删除成功") : AdminAjaxResult.error("作品不存在");
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long longVal(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new BusinessException(com.example.sillyspringboot.shared.error.ErrorCode.VALIDATION_FAILED, "作品 ID 不正确");
        }
    }
}
