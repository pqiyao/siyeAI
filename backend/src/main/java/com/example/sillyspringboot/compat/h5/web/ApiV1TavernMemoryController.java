package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.conversation.dto.ConversationDetailDto;
import com.example.sillyspringboot.conversation.dto.ConversationMemoryRefreshResult;
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tavern/memory")
public class ApiV1TavernMemoryController {

    private final H5ClientUidAuthService h5Auth;
    private final AppConversationService conversationService;
    private final AppConversationMemoryService conversationMemoryService;

    public ApiV1TavernMemoryController(
            H5ClientUidAuthService h5Auth,
            AppConversationService conversationService,
            AppConversationMemoryService conversationMemoryService
    ) {
        this.h5Auth = h5Auth;
        this.conversationService = conversationService;
        this.conversationMemoryService = conversationMemoryService;
    }

    @PostMapping("/refresh")
    public ApiV1Result<ConversationMemoryRefreshResult> refresh(@RequestBody Map<String, Object> payload) {
        String clientUid = payload == null ? null : asString(payload.get("clientUid"));
        Long characterId = payload == null ? null : asLong(payload.get("characterId"));
        if (clientUid == null || clientUid.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid 缺失");
        if (characterId == null || characterId <= 0) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "characterId 缺失");
        String token = h5Auth.issueTokenForClientUid(clientUid);
        ConversationDetailDto detail = conversationService.findDetailByH5Character(clientUid, characterId, token);
        if (detail == null) {
            return ApiV1Result.ok(null);
        }
        long conversationId = detail.conversationId();
        return ApiV1Result.ok(conversationMemoryService.refreshConversationMemory(conversationId));
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Long asLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s) {
            try { return Long.parseLong(s); } catch (Exception ignored) { return null; }
        }
        return null;
    }
}

