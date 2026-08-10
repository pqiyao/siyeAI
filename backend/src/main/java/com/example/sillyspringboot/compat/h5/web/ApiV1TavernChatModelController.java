package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.ai.service.AiChatModelService;
import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tavern")
public class ApiV1TavernChatModelController {

    private final AiChatModelService chatModelService;
    private final H5EntitlementService entitlementService;

    public ApiV1TavernChatModelController(
            AiChatModelService chatModelService,
            H5EntitlementService entitlementService
    ) {
        this.chatModelService = chatModelService;
        this.entitlementService = entitlementService;
    }

    @GetMapping("/chat-models")
    public ApiV1Result<Map<String, Object>> catalog(
            @RequestParam("clientUid") String clientUid,
            @RequestParam(value = "conversationId", required = false) Long conversationId
    ) {
        AppUser user = entitlementService.resolveUser(clientUid);
        return ApiV1Result.ok(chatModelService.userCatalog(user.getId(), conversationId));
    }

    @PutMapping("/chat-models/selection")
    public ApiV1Result<Map<String, Object>> select(@RequestBody(required = false) Map<String, Object> body) {
        String clientUid = text(body == null ? null : body.get("clientUid"));
        if (clientUid.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid 不能为空");
        AppUser user = entitlementService.resolveUser(clientUid);
        Long conversationId = longValue(body == null ? null : body.get("conversationId"));
        return ApiV1Result.ok(chatModelService.select(
                user.getId(), conversationId,
                text(body == null ? null : body.get("source")),
                text(body == null ? null : body.get("ref"))
        ));
    }

    @GetMapping("/ai-provider/chat-models")
    public ApiV1Result<List<Map<String, Object>>> userModels(@RequestParam("clientUid") String clientUid) {
        AppUser user = entitlementService.resolveUser(clientUid);
        return ApiV1Result.ok(chatModelService.userModels(user.getId()));
    }

    @PutMapping("/ai-provider/chat-models")
    public ApiV1Result<List<Map<String, Object>>> saveUserModels(
            @RequestParam("clientUid") String clientUid,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        AppUser user = entitlementService.resolveUser(clientUid);
        return ApiV1Result.ok(chatModelService.saveUserModels(user.getId(), body));
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { String text = text(value); return text.isBlank() ? null : Long.parseLong(text); }
        catch (NumberFormatException ignored) { return null; }
    }
}
