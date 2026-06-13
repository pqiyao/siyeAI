package com.example.sillyspringboot.humanchat.web;

import com.example.sillyspringboot.app.security.CurrentAppUserResolver;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.humanchat.service.HumanChatService;
import com.example.sillyspringboot.ops.service.SocialFeatureSettingsService;
import com.example.sillyspringboot.ratelimit.SocialUploadRateLimiter;
import com.example.sillyspringboot.shared.web.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/social-chat")
public class AppHumanChatController {

    private final HumanChatService chatService;
    private final CurrentAppUserResolver userResolver;
    private final SocialFeatureSettingsService socialSettingsService;
    private final SocialUploadRateLimiter rateLimiter;

    public AppHumanChatController(
            HumanChatService chatService,
            CurrentAppUserResolver userResolver,
            SocialFeatureSettingsService socialSettingsService,
            SocialUploadRateLimiter rateLimiter
    ) {
        this.chatService = chatService;
        this.userResolver = userResolver;
        this.socialSettingsService = socialSettingsService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/conversations")
    public ApiResult<Map<String, Object>> conversations(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request
    ) {
        socialSettingsService.ensureChatEntryEnabled();
        return ApiResult.ok(chatService.listConversations(userResolver.requireUser(request), pageNum, pageSize));
    }

    @GetMapping("/conversations/{peerId}/messages")
    public ApiResult<Map<String, Object>> messages(
            @PathVariable long peerId,
            @RequestParam(required = false) Long beforeMessageId,
            @RequestParam(defaultValue = "30") int limit,
            HttpServletRequest request
    ) {
        socialSettingsService.ensureChatReadable();
        return ApiResult.ok(chatService.listMessages(userResolver.requireUser(request), peerId, beforeMessageId, limit));
    }

    @PostMapping("/conversations/{peerId}/read")
    public ApiResult<Map<String, Object>> markRead(@PathVariable long peerId, HttpServletRequest request) {
        socialSettingsService.ensureChatReadable();
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "chat_read");
        return ApiResult.ok(chatService.markRead(user, peerId));
    }

    @PostMapping("/messages")
    public ApiResult<Map<String, Object>> send(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "chat_send");
        return ApiResult.ok(chatService.sendMessage(user, body));
    }

    @PostMapping("/messages/{messageId}/recall")
    public ApiResult<Map<String, Object>> recall(@PathVariable long messageId, HttpServletRequest request) {
        socialSettingsService.ensureRecallEnabled();
        AppUser user = userResolver.requireUser(request);
        rateLimiter.checkSocialWrite(user, request, "chat_recall");
        return ApiResult.ok(chatService.recall(user, messageId));
    }
}
