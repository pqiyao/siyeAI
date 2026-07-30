package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.ops.service.ChatPresetService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
        Long presetId = optionalPositiveLong(body, "presetId");
        return ApiV1Result.ok(chatPresetService.bindConversationPreset(userId, conversationId, presetId));
    }

    @PostMapping("/chat-presets/copy")
    public ApiV1Result<Map<String, Object>> copy(@RequestBody Map<String, Object> body) {
        long userId = userIdOf(requiredText(body, "clientUid"));
        long sourcePresetId = requiredPositiveLong(body, "sourcePresetId");
        return ApiV1Result.ok(chatPresetService.copyPlatformPreset(
                userId,
                sourcePresetId,
                optionalText(body, "name")
        ));
    }

    @PostMapping("/chat-presets")
    public ApiV1Result<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        long userId = userIdOf(requiredText(body, "clientUid"));
        return ApiV1Result.ok(chatPresetService.createPrivatePreset(
                userId,
                optionalText(body, "name")
        ));
    }

    @PutMapping("/chat-presets/{presetId}")
    public ApiV1Result<Map<String, Object>> update(
            @PathVariable long presetId,
            @RequestBody Map<String, Object> body
    ) {
        long userId = userIdOf(requiredText(body, "clientUid"));
        return ApiV1Result.ok(chatPresetService.updatePrivatePreset(
                userId,
                presetId,
                requiredText(body, "name"),
                requiredDouble(body, "temperature"),
                requiredDouble(body, "topP"),
                optionalDouble(body, "frequencyPenalty"),
                optionalDouble(body, "presencePenalty"),
                requiredInt(body, "maxTokens"),
                optionalBoolean(body, "enabled", true)
        ));
    }

    @DeleteMapping("/chat-presets/{presetId}")
    public ApiV1Result<Boolean> delete(
            @PathVariable long presetId,
            @RequestBody Map<String, Object> body
    ) {
        long userId = userIdOf(requiredText(body, "clientUid"));
        return ApiV1Result.ok(chatPresetService.deletePrivatePreset(userId, presetId));
    }

    private long userIdOf(String clientUid) {
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        AppUser user = tokenService.validateAndLoadUser(token);
        return user.getId();
    }

    private static Long optionalPositiveLong(Map<String, Object> body, String field) {
        if (body == null || !body.containsKey(field)) {
            throw validation(field + " missing");
        }
        if (body.get(field) == null) {
            return null;
        }
        Long value = wholeLong(body.get(field));
        if (value == null || value <= 0) {
            throw validation(field + " invalid");
        }
        return value;
    }

    private static String requiredText(Map<String, Object> body, String field) {
        String value = optionalText(body, field);
        if (value.isBlank()) {
            throw new com.example.sillyspringboot.shared.error.BusinessException(
                    com.example.sillyspringboot.shared.error.ErrorCode.VALIDATION_FAILED,
                    field + " missing"
            );
        }
        return value;
    }

    private static String optionalText(Map<String, Object> body, String field) {
        Object raw = body == null ? null : body.get(field);
        if (raw == null) return "";
        if (!(raw instanceof String value)) {
            throw validation(field + " invalid");
        }
        return value.trim();
    }

    private static long requiredPositiveLong(Map<String, Object> body, String field) {
        Object raw = body == null ? null : body.get(field);
        Long value = wholeLong(raw);
        if (value == null || value <= 0) throw validation(field + " invalid");
        return value;
    }

    private static int requiredInt(Map<String, Object> body, String field) {
        Long value = wholeLong(body == null ? null : body.get(field));
        if (value == null || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw validation(field + " invalid");
        }
        return value.intValue();
    }

    private static double requiredDouble(Map<String, Object> body, String field) {
        Object raw = body == null ? null : body.get(field);
        double value;
        try {
            value = raw instanceof Number number ? number.doubleValue() : Double.parseDouble(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            throw validation(field + " invalid");
        }
        if (!Double.isFinite(value)) throw validation(field + " invalid");
        return value;
    }

    private static Double optionalDouble(Map<String, Object> body, String field) {
        if (body == null || !body.containsKey(field) || body.get(field) == null) {
            return null;
        }
        return requiredDouble(body, field);
    }

    private static boolean optionalBoolean(Map<String, Object> body, String field, boolean fallback) {
        Object raw = body == null ? null : body.get(field);
        if (raw == null) return fallback;
        if (raw instanceof Boolean value) return value;
        if (raw instanceof Number number && (number.intValue() == 0 || number.intValue() == 1)) {
            return number.intValue() == 1;
        }
        throw validation(field + " invalid");
    }

    private static Long wholeLong(Object raw) {
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
            return ((Number) raw).longValue();
        }
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            return Double.isFinite(value) && value == Math.rint(value) ? (long) value : null;
        }
        try {
            return raw == null ? null : Long.parseLong(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static com.example.sillyspringboot.shared.error.BusinessException validation(String message) {
        return new com.example.sillyspringboot.shared.error.BusinessException(
                com.example.sillyspringboot.shared.error.ErrorCode.VALIDATION_FAILED,
                message
        );
    }
}
