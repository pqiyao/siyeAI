package com.example.sillyspringboot.conversation.web;

import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.conversation.dto.ConversationDetailDto;
import com.example.sillyspringboot.conversation.dto.ConversationInboxItemDto;
import com.example.sillyspringboot.conversation.dto.ConversationSummaryDto;
import com.example.sillyspringboot.conversation.dto.CreateConversationRequest;
import com.example.sillyspringboot.conversation.dto.UpdateConversationStDisplayNameRequest;
import com.example.sillyspringboot.conversation.dto.UpdateConversationWorldbooksRequest;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.example.sillyspringboot.shared.web.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/app/conversations")
public class AppConversationController {

    private final AppConversationService service;

    public AppConversationController(AppConversationService service) {
        this.service = service;
    }

    @PostMapping
    public ApiResult<ConversationDetailDto> create(@Valid @RequestBody CreateConversationRequest request,
                                                      @RequestHeader(name = "Authorization", required = false) String authorization) {
        String token = extractToken(authorization);
        return ApiResult.ok(service.createOrEnsure(request, token));
    }

    @GetMapping
    public ApiResult<List<ConversationSummaryDto>> list(@RequestHeader(name = "Authorization", required = false) String authorization) {
        String token = extractToken(authorization);
        return ApiResult.ok(service.listForUser(token));
    }

    @GetMapping("/inbox")
    public ApiResult<List<ConversationInboxItemDto>> inbox(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit
    ) {
        String token = extractToken(authorization);
        return ApiResult.ok(service.listInboxForUser(token, limit));
    }

    @GetMapping("/{conversationId}")
    public ApiResult<ConversationDetailDto> detail(
            @PathVariable long conversationId,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        String token = extractToken(authorization);
        return ApiResult.ok(service.getDetail(conversationId, token));
    }

    @PutMapping("/{conversationId}/worldbooks")
    public ApiResult<Boolean> updateWorldbooks(
            @PathVariable long conversationId,
            @Valid @RequestBody UpdateConversationWorldbooksRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        String token = extractToken(authorization);
        service.updateWorldbooks(conversationId, request.getWorldNames(), token);
        return ApiResult.ok(true);
    }

    @PutMapping("/{conversationId}/st-display-name")
    public ApiResult<Boolean> updateStDisplayName(
            @PathVariable long conversationId,
            @RequestBody(required = false) UpdateConversationStDisplayNameRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        String token = extractToken(authorization);
        service.updateStDisplayNameOverride(
                conversationId,
                request == null ? null : request.getStDisplayNameOverride(),
                token
        );
        return ApiResult.ok(true);
    }

    private static String extractToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证");
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }
}
