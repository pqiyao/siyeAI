package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.community.service.CommunityService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/social-comment")
@AdminPermitted("social:community:view")
public class AdminJiugaiSocialCommentController {

    private final CommunityService communityService;

    public AdminJiugaiSocialCommentController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return AdminAjaxResult.okData(Map.of());
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long postId,
            @RequestParam(required = false) Long userId
    ) {
        try {
            return AdminAjaxResult.table(
                    communityService.countAdminComments(keyword, postId, userId),
                    communityService.listAdminComments(keyword, postId, userId, pageNum, pageSize)
            );
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/{commentId}")
    public Map<String, Object> get(@PathVariable long commentId) {
        try {
            return AdminAjaxResult.okData(communityService.getAdminComment(commentId));
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    @DeleteMapping("/{ids}")
    @AdminPermitted("social:community:delete")
    public Map<String, Object> remove(@PathVariable String ids) {
        try {
            Map<String, Object> last = Map.of();
            for (Long commentId : parseIds(ids)) {
                last = communityService.removeAdminComment(commentId);
            }
            return AdminAjaxResult.okData(last);
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
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
}
