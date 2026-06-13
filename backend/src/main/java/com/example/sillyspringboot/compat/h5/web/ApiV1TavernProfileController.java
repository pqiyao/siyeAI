package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.compat.h5.entity.AppH5Profile;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ProfileMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.conversation.dto.ConversationDetailDto;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tavern")
public class ApiV1TavernProfileController {

    private final H5ClientUidAuthService h5Auth;
    private final AppTokenService tokenService;
    private final AppH5ProfileMapper profileMapper;
    private final AppConversationService conversationService;

    public ApiV1TavernProfileController(
            H5ClientUidAuthService h5Auth,
            AppTokenService tokenService,
            AppH5ProfileMapper profileMapper,
            AppConversationService conversationService
    ) {
        this.h5Auth = h5Auth;
        this.tokenService = tokenService;
        this.profileMapper = profileMapper;
        this.conversationService = conversationService;
    }

    @GetMapping("/profile")
    public ApiV1Result<Map<String, Object>> get(
            @RequestParam("clientUid") String clientUid,
            @RequestParam(name = "characterId", required = false) Long characterId,
            @RequestParam(name = "conversationId", required = false) Long conversationId
    ) {
        String token = h5Auth.issueTokenForClientUid(clientUid);
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppH5Profile profile = profileMapper.findByUserId(userId);
        ConversationDetailDto conversation = resolveConversationForRead(clientUid, characterId, conversationId, token);

        String displayName = valueOrEmpty(profile == null ? null : profile.getDisplayName());
        String persona = valueOrEmpty(profile == null ? null : profile.getPersona());
        String stDisplayName = valueOrEmpty(AppConversationService.normalizeStDisplayName(
                profile == null ? null : profile.getStDisplayName()
        ));
        String stDisplayNameOverride = conversation == null || conversation.stBinding() == null
                ? ""
                : valueOrEmpty(conversation.stBinding().stDisplayNameOverride());
        String effectiveStDisplayName = !stDisplayNameOverride.isBlank()
                ? stDisplayNameOverride
                : (!stDisplayName.isBlank() ? stDisplayName : displayName);

        Map<String, Object> data = new HashMap<>();
        data.put("display_name", displayName);
        data.put("persona", persona);
        data.put("st_display_name", stDisplayName);
        data.put("conversation_id", conversation == null ? null : conversation.conversationId());
        data.put("st_display_name_override", stDisplayNameOverride);
        data.put("effective_st_display_name", effectiveStDisplayName);
        return ApiV1Result.ok(data);
    }

    @PutMapping("/profile")
    public ApiV1Result<Boolean> put(
            @RequestParam("clientUid") String clientUid,
            @RequestParam(name = "characterId", required = false) Long characterId,
            @RequestParam(name = "conversationId", required = false) Long conversationId,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        if (clientUid == null || clientUid.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid 缺失");
        }
        String token = h5Auth.issueTokenForClientUid(clientUid);
        long userId = tokenService.validateAndLoadUser(token).getId();
        AppH5Profile existing = profileMapper.findByUserId(userId);

        String displayName = hasKey(body, "display_name")
                ? stringValue(body.get("display_name"))
                : valueOrEmpty(existing == null ? null : existing.getDisplayName());
        String persona = hasKey(body, "persona")
                ? stringValue(body.get("persona"))
                : valueOrEmpty(existing == null ? null : existing.getPersona());
        String stDisplayName = hasKey(body, "st_display_name")
                ? AppConversationService.normalizeStDisplayName(stringValue(body.get("st_display_name")))
                : AppConversationService.normalizeStDisplayName(existing == null ? null : existing.getStDisplayName());
        profileMapper.upsert(userId, displayName, persona, stDisplayName);

        if (hasKey(body, "st_display_name_override")) {
            ConversationDetailDto conversation = resolveConversationForWrite(clientUid, characterId, conversationId, token);
            conversationService.updateStDisplayNameOverride(
                    conversation.conversationId(),
                    stringValue(body.get("st_display_name_override")),
                    token
            );
        }
        return ApiV1Result.ok(true);
    }

    private ConversationDetailDto resolveConversationForRead(
            String clientUid,
            Long characterId,
            Long conversationId,
            String token
    ) {
        if (conversationId != null && conversationId > 0) {
            return conversationService.getDetail(conversationId, token);
        }
        if (characterId != null && characterId > 0) {
            return conversationService.findDetailByH5Character(clientUid, characterId, token);
        }
        return null;
    }

    private ConversationDetailDto resolveConversationForWrite(
            String clientUid,
            Long characterId,
            Long conversationId,
            String token
    ) {
        if (conversationId != null && conversationId > 0) {
            return conversationService.getDetail(conversationId, token);
        }
        if (characterId != null && characterId > 0) {
            return conversationService.ensureDetailByH5Character(clientUid, characterId, token);
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "characterId/conversationId 缺失");
    }

    private static boolean hasKey(Map<String, Object> body, String key) {
        return body != null && body.containsKey(key);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

}
