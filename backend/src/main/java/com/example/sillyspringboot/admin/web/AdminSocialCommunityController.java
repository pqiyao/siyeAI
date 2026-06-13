package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.community.service.CommunityService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/social-post")
@AdminPermitted("social:community:view")
public class AdminSocialCommunityController {

    private final CommunityService communityService;

    public AdminSocialCommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return AdminAjaxResult.okData(Map.of(
                "statusOptions", new String[]{"normal", "hidden"}
        ));
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long userId
    ) {
        try {
            return AdminAjaxResult.table(
                    communityService.countAdminPosts(keyword, status, userId),
                    communityService.listAdminPosts(keyword, status, userId, pageNum, pageSize)
            );
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/{postId}")
    public Map<String, Object> get(@PathVariable long postId) {
        try {
            return AdminAjaxResult.okData(communityService.getAdminPost(postId));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @PutMapping("/status")
    @AdminPermitted({"social:community:update-status", "social:community:delete"})
    public Map<String, Object> updateStatus(@RequestBody(required = false) Map<String, Object> body) {
        try {
            long postId = longValue(body == null ? null : body.get("postId"), "postId 不正确");
            String status = body == null ? null : stringValue(body.get("status"));
            return AdminAjaxResult.okData(communityService.updateAdminPostStatus(postId, status));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @DeleteMapping("/{ids}")
    @AdminPermitted("social:community:delete")
    public Map<String, Object> remove(@PathVariable String ids) {
        for (Long postId : parseIds(ids)) {
            int removed = communityService.removeAdminPost(postId);
            if (removed <= 0) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "动态不存在");
            }
        }
        return AdminAjaxResult.ok("删除成功");
    }

    private static Long[] parseIds(String ids) {
        if (ids == null || ids.isBlank()) {
            return new Long[0];
        }
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toArray(Long[]::new);
    }

    private static long longValue(Object value, String message) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, message);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
