package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.ops.service.ChatPresetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tavern")
public class ApiV1TavernChatPresetController {

    private final H5ClientUidAuthService h5Auth;
    private final AppTokenService tokenService;
    private final ChatPresetService chatPresetService;

    public ApiV1TavernChatPresetController(
            H5ClientUidAuthService h5Auth,
            AppTokenService tokenService,
            ChatPresetService chatPresetService
    ) {
        this.h5Auth = h5Auth;
        this.tokenService = tokenService;
        this.chatPresetService = chatPresetService;
    }

    @GetMapping("/chat-presets")
    public ApiV1Result<Map<String, Object>> list(
            @RequestParam String clientUid,
            @RequestParam(required = false) Long conversationId
    ) {
        long userId = userIdOf(clientUid);
        return ApiV1Result.ok(chatPresetService.listForH5(userId, conversationId));
    }

    @PutMapping("/conversations/{conversationId}/preset")
    public ApiV1Result<Map<String, Object>> bind(
            @PathVariable long conversationId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        String clientUid = body == null ? "" : String.valueOf(body.getOrDefault("clientUid", ""));
        long userId = userIdOf(clientUid);
        Long presetId = longOrNull(body == null ? null : body.get("presetId"));
        return ApiV1Result.ok(chatPresetService.bindConversationPreset(userId, conversationId, presetId));
    }

    private long userIdOf(String clientUid) {
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        AppUser user = tokenService.validateAndLoadUser(token);
        return user.getId();
    }

    private static Long longOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long n = number.longValue();
            return n > 0 ? n : null;
        }
        try {
            long n = Long.parseLong(String.valueOf(value).trim());
            return n > 0 ? n : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
