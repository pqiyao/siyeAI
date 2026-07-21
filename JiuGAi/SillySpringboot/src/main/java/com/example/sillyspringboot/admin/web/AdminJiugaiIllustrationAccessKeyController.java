package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.illustration.service.IllustrationAccessKeyService;
import com.example.sillyspringboot.shared.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/admin/jiugai/illustration-access-key")
@AdminPermitted("content:illustration-key:view")
public class AdminJiugaiIllustrationAccessKeyController {

    private final IllustrationAccessKeyService accessKeyService;

    public AdminJiugaiIllustrationAccessKeyController(IllustrationAccessKeyService accessKeyService) {
        this.accessKeyService = accessKeyService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active
    ) {
        return AdminAjaxResult.table(
                accessKeyService.countAdminKeys(keyword, active),
                accessKeyService.listAdminKeys(keyword, active, pageNum, pageSize)
        );
    }

    @PostMapping("/generate")
    @AdminPermitted("content:illustration-key:edit")
    public Map<String, Object> generate(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        try {
            return AdminAjaxResult.okData(accessKeyService.generateAdminKey(
                    request == null ? null : request.getUserPrincipal() == null ? null : request.getUserPrincipal().getName(),
                    intVal(body == null ? null : body.get("ttlMinutes")),
                    intVal(body == null ? null : body.get("maxUses")),
                    stringVal(body == null ? null : body.get("note"))
            ));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PutMapping("/disable/{id}")
    @AdminPermitted("content:illustration-key:edit")
    public Map<String, Object> disable(@PathVariable long id) {
        return accessKeyService.disableAdminKey(id)
                ? AdminAjaxResult.ok("密钥已停用")
                : AdminAjaxResult.error("密钥不存在");
    }

    @DeleteMapping("/{id}")
    @AdminPermitted("content:illustration-key:edit")
    public Map<String, Object> delete(@PathVariable long id) {
        return accessKeyService.removeAdminKey(id)
                ? AdminAjaxResult.ok("密钥已删除")
                : AdminAjaxResult.error("密钥不存在");
    }

    private static String stringVal(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer intVal(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            String text = value == null ? null : String.valueOf(value).trim();
            return text == null || text.isEmpty() ? null : Integer.parseInt(text);
        } catch (Exception e) {
            return null;
        }
    }
}
