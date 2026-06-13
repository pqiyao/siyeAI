package com.example.sillyspringboot.community.web;

import com.example.sillyspringboot.app.security.CurrentAppUserResolver;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.community.service.CommunityService;
import com.example.sillyspringboot.ops.service.SocialFeatureSettingsService;
import com.example.sillyspringboot.ratelimit.SocialUploadRateLimiter;
import com.example.sillyspringboot.shared.web.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/community")
public class AppCommunityController {

    private final CommunityService communityService;
    private final CurrentAppUserResolver userResolver;
    private final SocialFeatureSettingsService socialSettingsService;
    private final SocialUploadRateLimiter rateLimiter;

    public AppCommunityController(
            CommunityService communityService,
            CurrentAppUserResolver userResolver,
            SocialFeatureSettingsService socialSettingsService,
            SocialUploadRateLimiter rateLimiter
    ) {
        this.communityService = communityService;
        this.userResolver = userResolver;
        this.socialSettingsService = socialSettingsService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/posts")
    public ApiResult<Map<String, Object>> createPost(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        socialSettingsService.ensurePostCreateEnabled();
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "post_create");
        return ApiResult.ok(communityService.createPost(user, body));
    }

    @GetMapping("/posts")
    public ApiResult<Map<String, Object>> list(
            @RequestParam(required = false, defaultValue = "recommend") String feed,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request
    ) {
        AppUser viewer = userResolver.optionalUser(request);
        socialSettingsService.ensureCommunityReadable(viewer != null);
        return ApiResult.ok(communityService.list(feed, userId, viewer, pageNum, pageSize));
    }

    @GetMapping("/posts/{postId}")
    public ApiResult<Map<String, Object>> detail(@PathVariable long postId, HttpServletRequest request) {
        AppUser viewer = userResolver.optionalUser(request);
        socialSettingsService.ensureCommunityReadable(viewer != null);
        return ApiResult.ok(communityService.detail(postId, viewer, true));
    }

    @PostMapping("/posts/{postId}/like")
    public ApiResult<Map<String, Object>> like(@PathVariable long postId, HttpServletRequest request) {
        socialSettingsService.ensureLikeEnabled();
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "post_like");
        return ApiResult.ok(communityService.like(postId, user));
    }

    @DeleteMapping("/posts/{postId}/like")
    public ApiResult<Map<String, Object>> unlike(@PathVariable long postId, HttpServletRequest request) {
        socialSettingsService.ensureLikeEnabled();
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "post_unlike");
        return ApiResult.ok(communityService.unlike(postId, user));
    }

    @PostMapping("/posts/{postId}/comments")
    public ApiResult<Map<String, Object>> comment(
            @PathVariable long postId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        socialSettingsService.ensureCommentEnabled();
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "comment_create");
        return ApiResult.ok(communityService.addComment(postId, user, body));
    }

    @GetMapping("/posts/{postId}/comments")
    public ApiResult<Object> comments(
            @PathVariable long postId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request
    ) {
        socialSettingsService.ensureCommunityReadable(userResolver.optionalUser(request) != null);
        return ApiResult.ok(communityService.listComments(postId, pageNum, pageSize));
    }

    @PostMapping("/comments/{commentId}/replies")
    public ApiResult<Map<String, Object>> reply(
            @PathVariable long commentId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        socialSettingsService.ensureCommentEnabled();
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "reply_create");
        return ApiResult.ok(communityService.addReply(commentId, user, body));
    }

    @GetMapping("/comments/{commentId}/replies")
    public ApiResult<Object> replies(
            @PathVariable long commentId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request
    ) {
        socialSettingsService.ensureCommunityReadable(userResolver.optionalUser(request) != null);
        return ApiResult.ok(communityService.listReplies(commentId, pageNum, pageSize));
    }

    @PostMapping("/users/{userId}/follow")
    public ApiResult<Map<String, Object>> follow(@PathVariable long userId, HttpServletRequest request) {
        socialSettingsService.ensureFollowEnabled();
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "follow");
        return ApiResult.ok(communityService.follow(userId, user));
    }

    @DeleteMapping("/users/{userId}/follow")
    public ApiResult<Map<String, Object>> unfollow(@PathVariable long userId, HttpServletRequest request) {
        socialSettingsService.ensureFollowEnabled();
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "unfollow");
        return ApiResult.ok(communityService.unfollow(userId, user));
    }

    @GetMapping("/friends")
    public ApiResult<Map<String, Object>> friends(
            @RequestParam(required = false, defaultValue = "all") String relation,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request
    ) {
        socialSettingsService.ensureFriendFeatureEnabled();
        return ApiResult.ok(communityService.friends(userResolver.requireUser(request), relation, pageNum, pageSize));
    }

    @PostMapping("/friends/requests")
    public ApiResult<Map<String, Object>> requestFriend(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        socialSettingsService.ensureFriendRequestEnabled();
        long targetUserId = longValue(body == null ? null : body.get("targetUserId"), "targetUserId 不正确");
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "friend_request");
        return ApiResult.ok(communityService.requestFriend(user, targetUserId, body));
    }

    @GetMapping("/friends/requests")
    public ApiResult<Map<String, Object>> friendRequests(
            @RequestParam(required = false, defaultValue = "received") String box,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request
    ) {
        socialSettingsService.ensureFriendFeatureEnabled();
        return ApiResult.ok(communityService.friendRequests(userResolver.requireUser(request), box, status, pageNum, pageSize));
    }

    @PostMapping("/friends/requests/{requestId}/accept")
    public ApiResult<Map<String, Object>> acceptFriendRequest(@PathVariable long requestId, HttpServletRequest request) {
        socialSettingsService.ensureFriendFeatureEnabled();
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "friend_request_accept");
        return ApiResult.ok(communityService.handleFriendRequest(user, requestId, "accept"));
    }

    @PostMapping("/friends/requests/{requestId}/reject")
    public ApiResult<Map<String, Object>> rejectFriendRequest(@PathVariable long requestId, HttpServletRequest request) {
        socialSettingsService.ensureFriendFeatureEnabled();
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "friend_request_reject");
        return ApiResult.ok(communityService.handleFriendRequest(user, requestId, "reject"));
    }

    @DeleteMapping("/friends/{friendUserId}")
    public ApiResult<Map<String, Object>> removeFriend(@PathVariable long friendUserId, HttpServletRequest request) {
        socialSettingsService.ensureFriendFeatureEnabled();
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "friend_remove");
        return ApiResult.ok(communityService.removeFriend(user, friendUserId));
    }

    @PostMapping("/blocks/{blockedUserId}")
    public ApiResult<Map<String, Object>> blockUser(
            @PathVariable long blockedUserId,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        socialSettingsService.ensureBlockEnabled();
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "block");
        return ApiResult.ok(communityService.blockUser(user, blockedUserId, body));
    }

    @DeleteMapping("/blocks/{blockedUserId}")
    public ApiResult<Map<String, Object>> unblockUser(@PathVariable long blockedUserId, HttpServletRequest request) {
        socialSettingsService.ensureBlockEnabled();
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "unblock");
        return ApiResult.ok(communityService.unblockUser(user, blockedUserId));
    }

    @GetMapping("/users/{userId}/posts")
    public ApiResult<Map<String, Object>> userPosts(
            @PathVariable long userId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request
    ) {
        AppUser viewer = userResolver.optionalUser(request);
        socialSettingsService.ensureCommunityReadable(viewer != null);
        return ApiResult.ok(communityService.list("recommend", userId, viewer, pageNum, pageSize));
    }

    @GetMapping("/following/posts")
    public ApiResult<Map<String, Object>> followingPosts(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest request
    ) {
        socialSettingsService.ensureCommunityReadable(true);
        return ApiResult.ok(communityService.list("following", null, userResolver.requireUser(request), pageNum, pageSize));
    }

    private static long longValue(Object value, String message) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new com.example.sillyspringboot.shared.error.BusinessException(
                    com.example.sillyspringboot.shared.error.ErrorCode.VALIDATION_FAILED,
                    message
            );
        }
    }
}
