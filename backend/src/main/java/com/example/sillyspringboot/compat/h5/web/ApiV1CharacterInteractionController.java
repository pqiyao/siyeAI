package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5SocialService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/characters")
public class ApiV1CharacterInteractionController {

    private final H5ClientUidAuthService h5Auth;
    private final H5SocialService socialService;

    public ApiV1CharacterInteractionController(H5ClientUidAuthService h5Auth, H5SocialService socialService) {
        this.h5Auth = h5Auth;
        this.socialService = socialService;
    }

    @PostMapping("/interaction")
    public ApiV1Result<Map<String, Object>> interaction(@RequestBody Map<String, Object> payload) {
        String clientUid = payload == null ? null : asString(payload.get("clientUid"));
        Long characterId = payload == null ? null : asLong(payload.get("characterId"));
        String action = payload == null ? null : asString(payload.get("action"));
        if (clientUid == null || clientUid.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid 缺失");
        if (characterId == null || characterId <= 0) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "characterId 缺失");
        if (action == null || action.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "action 缺失");

        String token = h5Auth.issueTokenForClientUid(clientUid);
        H5SocialService.InteractionResult r = socialService.toggleInteraction(token, characterId, action);
        Map<String, Object> data = new HashMap<>();
        data.put("like_count", r.likeCount());
        data.put("dislike_count", r.dislikeCount());
        data.put("is_favorite", r.isFavorite());
        data.put("user_vote", r.userVote());
        return ApiV1Result.ok(data);
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

