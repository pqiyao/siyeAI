package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.compat.h5.mapper.AppInboxReadMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppNoticeMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppUserMessageMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5SocialService;
import com.example.sillyspringboot.compat.h5.service.H5StAssetUrls;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.ops.service.TagLibraryService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/app")
public class ApiV1AppController {

    private static final Logger log = LoggerFactory.getLogger(ApiV1AppController.class);

    private final H5ClientUidAuthService h5Auth;
    private final AppTokenService tokenService;
    private final AppNoticeMapper noticeMapper;
    private final AppUserMessageMapper userMessageMapper;
    private final AppInboxReadMapper inboxReadMapper;
    private final H5SocialService socialService;
    private final H5StAssetUrls stAssetUrls;
    private final H5EntitlementService entitlementService;
    private final TagLibraryService tagLibraryService;
    private final AppFeatureSettingsService featureSettingsService;

    public ApiV1AppController(
            H5ClientUidAuthService h5Auth,
            AppTokenService tokenService,
            AppNoticeMapper noticeMapper,
            AppUserMessageMapper userMessageMapper,
            AppInboxReadMapper inboxReadMapper,
            H5SocialService socialService,
            H5StAssetUrls stAssetUrls,
            H5EntitlementService entitlementService,
            TagLibraryService tagLibraryService,
            AppFeatureSettingsService featureSettingsService
    ) {
        this.h5Auth = h5Auth;
        this.tokenService = tokenService;
        this.noticeMapper = noticeMapper;
        this.userMessageMapper = userMessageMapper;
        this.inboxReadMapper = inboxReadMapper;
        this.socialService = socialService;
        this.stAssetUrls = stAssetUrls;
        this.entitlementService = entitlementService;
        this.tagLibraryService = tagLibraryService;
        this.featureSettingsService = featureSettingsService;
    }

    @GetMapping("/runtime-config")
    public ApiV1Result<Map<String, Object>> runtimeConfig() {
        return ApiV1Result.ok(featureSettingsService.toMap(featureSettingsService.getSettings()));
    }

    @GetMapping("/notices")
    public ApiV1Result<List<Map<String, Object>>> notices(
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit
    ) {
        int safeLimit = Math.max(1, Math.min(50, limit));
        List<Map<String, Object>> list = noticeMapper.listLatest(safeLimit).stream().map(notice -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", notice.getId());
            data.put("title", notice.getTitle());
            data.put("content", notice.getContent());
            data.put("level", notice.getLevel());
            data.put("displayType", normalizeNoticeDisplayType(notice.getDisplayType()));
            data.put("createdAt", notice.getCreatedAt());
            return data;
        }).toList();
        return ApiV1Result.ok(list);
    }

    @GetMapping("/messages")
    public ApiV1Result<List<Map<String, Object>>> messages(
            @RequestParam("clientUid") String clientUid,
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit
    ) {
        long userId = resolveUserId(clientUid);
        int safeLimit = Math.max(1, Math.min(50, limit));
        List<Map<String, Object>> list = userMessageMapper.listByUserId(userId, safeLimit).stream().map(message -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", message.getId());
            data.put("title", message.getTitle());
            data.put("content", message.getContent());
            data.put("messageType", message.getMessageType());
            data.put("relatedType", message.getRelatedType());
            data.put("relatedId", message.getRelatedId());
            data.put("readFlag", Boolean.TRUE.equals(message.getReadFlag()));
            data.put("readAt", message.getReadAt());
            data.put("createdAt", message.getCreatedAt());
            return data;
        }).toList();
        return ApiV1Result.ok(list);
    }

    @GetMapping("/inbox/unread")
    @Transactional
    public ApiV1Result<Map<String, Object>> inboxUnread(@RequestParam("clientUid") String clientUid) {
        long userId = resolveUserId(clientUid);
        ensureUnreadBaseline(userId);
        return ApiV1Result.ok(buildUnreadState(userId));
    }

    @PostMapping("/inbox/read-all")
    @Transactional
    public ApiV1Result<Map<String, Object>> inboxReadAll(@RequestBody(required = false) Map<String, Object> body) {
        String clientUid = body == null ? "" : asString(body.get("clientUid"));
        long userId = resolveUserId(clientUid);
        inboxReadMapper.markNoticesRead(userId);
        inboxReadMapper.markUserMessagesRead(userId);
        return ApiV1Result.ok(buildUnreadState(userId));
    }

    @PostMapping("/inbox/notice-read")
    @Transactional
    public ApiV1Result<Map<String, Object>> inboxNoticeRead(@RequestBody(required = false) Map<String, Object> body) {
        String clientUid = body == null ? "" : asString(body.get("clientUid"));
        long noticeId = asLong(body == null ? null : body.get("noticeId"));
        if (noticeId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "noticeId 缺失");
        }
        long userId = resolveUserId(clientUid);
        ensureUnreadBaseline(userId);
        inboxReadMapper.markNoticeRead(userId, noticeId);
        return ApiV1Result.ok(buildUnreadState(userId));
    }

    @GetMapping("/me/stats")
    public ApiV1Result<Map<String, Object>> meStats(@RequestParam("clientUid") String clientUid) {
        String token = h5Auth.issueTokenForClientUid(clientUid);
        H5SocialService.MeStats stats = socialService.getMeStats(token);
        Map<String, Object> data = new HashMap<>();
        data.put("fav", stats.favoriteCount());
        data.put("chats", stats.activeConversationCount());
        data.put("chars", stats.myCharacterCount());
        data.put("favorite_count", stats.favoriteCount());
        data.put("recent_chat_count", stats.activeConversationCount());
        return ApiV1Result.ok(data);
    }

    @GetMapping("/me/favorites")
    public ApiV1Result<List<Map<String, Object>>> meFavorites(
            @RequestParam("clientUid") String clientUid,
            @RequestParam(name = "limit", required = false, defaultValue = "50") int limit,
            @RequestParam(name = "sortBy", required = false, defaultValue = "favorite") String sortBy
    ) {
        try {
            String token = h5Auth.issueTokenForClientUid(clientUid);
            int safeLimit = Math.max(1, Math.min(200, limit));
            List<AppCharacter> list;
            try {
                list = socialService.listFavorites(token, safeLimit, sortBy);
            } catch (Exception ex) {
                log.warn("h5 favorites query fallback to empty, clientUid={}, sortBy={}, limit={}",
                        clientUid, sortBy, safeLimit, ex);
                return ApiV1Result.ok(List.of());
            }
            if (list == null || list.isEmpty()) {
                return ApiV1Result.ok(List.of());
            }
            int viewerVipLevel = 0;
            try {
                viewerVipLevel = entitlementService.currentVipLevel(clientUid);
            } catch (Exception ex) {
                log.warn("h5 favorites vip resolve failed, fallback to guest, clientUid={}", clientUid, ex);
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (AppCharacter character : list) {
                if (character == null
                        || character.getDeletedAt() != null
                        || Boolean.TRUE.equals(character.getPrivateCard())
                        || character.getOwnerUserId() != null
                        || Boolean.FALSE.equals(character.getClientVisible())) {
                    continue;
                }
                try {
                    out.add(toH5Card(character, clientUid, viewerVipLevel));
                } catch (Exception ex) {
                    log.warn("skip malformed favorite card, clientUid={}, characterId={}",
                            clientUid, character.getId(), ex);
                }
            }
            if (out.isEmpty()) {
                return ApiV1Result.ok(List.of());
            }
            try {
                socialService.enrichDiscoverCards(clientUid, out);
            } catch (Exception ex) {
                log.warn("enrich favorite cards failed, clientUid={}, size={}", clientUid, out.size(), ex);
            }
            return ApiV1Result.ok(out);
        } catch (Exception ex) {
            log.warn("h5 favorites outer fallback to empty, clientUid={}, sortBy={}, limit={}",
                    clientUid, sortBy, limit, ex);
            return ApiV1Result.ok(List.of());
        }
    }

    @PostMapping("/me/favorites/unfavorite-batch")
    public ApiV1Result<Boolean> unfavoriteBatch(@RequestBody Map<String, Object> payload) {
        String clientUid = payload == null ? null : asString(payload.get("clientUid"));
        if (clientUid == null || clientUid.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid 缺失");
        }
        List<Long> ids = coerceLongList(payload.get("characterIds"));
        String token = h5Auth.issueTokenForClientUid(clientUid);
        socialService.unfavoriteBatch(token, ids);
        return ApiV1Result.ok(true);
    }

    private long resolveUserId(String clientUid) {
        String token = h5Auth.issueTokenForClientUid(clientUid);
        return tokenService.validateAndLoadUser(token).getId();
    }

    private void ensureUnreadBaseline(long userId) {
        inboxReadMapper.ensureInboxReadState(userId);
        if (inboxReadMapper.claimNoticeBaselineInitialization(userId) > 0) {
            inboxReadMapper.markNoticesRead(userId);
        }
        if (inboxReadMapper.claimMessageBaselineInitialization(userId) > 0) {
            inboxReadMapper.markUserMessagesRead(userId);
        }
    }

    private Map<String, Object> buildUnreadState(long userId) {
        long noticeUnread = inboxReadMapper.countUnreadNotices(userId);
        long messageUnread = inboxReadMapper.countUnreadUserMessages(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("noticeUnread", noticeUnread);
        data.put("messageUnread", messageUnread);
        data.put("unreadCount", noticeUnread + messageUnread);
        return data;
    }

    private Map<String, Object> toH5Card(AppCharacter character, String clientUid, int viewerVipLevel) {
        H5EntitlementService.CharacterAccess access =
                entitlementService.resolveCharacterAccess(clientUid, character.getVipOnly(), Boolean.TRUE);

        Map<String, Object> data = new HashMap<>();
        data.put("id", character.getId());
        data.put("name", character.getName());
        data.put("nickname", character.getName());
        data.put("description", character.getDescription());
        data.put("tagline", blankToDefault(character.getTagline(), character.getDescription()));
        data.put("bio", blankToDefault(character.getBio(), character.getDescription()));
        String portrait = stAssetUrls.portraitForCharacter(
                character.getAvatarUrl(),
                character.getCoverUrl(),
                character.getStAvatarUrl()
        );
        String portraitAvatarThumb = stAssetUrls.portraitForCharacterThumb(
                character.getAvatarUrl(),
                character.getCoverUrl(),
                character.getStAvatarUrl(),
                "avatar"
        );
        String portraitCoverThumb = stAssetUrls.portraitForCharacterThumb(
                character.getAvatarUrl(),
                character.getCoverUrl(),
                character.getStAvatarUrl(),
                "card"
        );
        data.put("avatar_url", character.getStAvatarUrl());
        data.put("avatar", portrait);
        data.put("cover", portrait);
        data.put("avatar_thumb", portraitAvatarThumb);
        data.put("cover_thumb", portraitCoverThumb);
        data.put("unlocked", access.unlocked());
        data.put("vip_only", Boolean.TRUE.equals(character.getVipOnly()));
        data.put("lock_reason", access.lockReason());
        data.put("client_visible", !Boolean.FALSE.equals(character.getClientVisible()));
        int previewBlurVipLevel = normalizePreviewBlurVipLevel(character.getPreviewBlurVipLevel());
        data.put("preview_blur_vip_level", previewBlurVipLevel);
        data.put("preview_blur_active", previewBlurVipLevel > 0 && viewerVipLevel < previewBlurVipLevel);
        data.put("label_array", tagLibraryService.buildDiscoverLabelArray(character.getTagsJson()));
        data.put("like_count", character.getLikeCount() == null ? 0L : character.getLikeCount().longValue());
        data.put("dislike_count", character.getDislikeCount() == null ? 0L : character.getDislikeCount().longValue());
        data.put("is_favorite", true);
        data.put("user_vote", "");
        data.put("token_display", character.getTokenDisplay() == null ? "" : character.getTokenDisplay());
        data.put("gameplay_type", character.getGameplayType() == null || character.getGameplayType().isBlank() ? "对手戏" : character.getGameplayType());
        data.put("creator", blankToDefault(character.getCreatorName(), "SillyTavern"));
        return data;
    }

    private static int normalizePreviewBlurVipLevel(Integer value) {
        int level = value == null ? 0 : value;
        if (level < 0) {
            return 0;
        }
        if (level > 2) {
            return 2;
        }
        return level;
    }

    private static String blankToDefault(String value, String def) {
        return value == null || value.isBlank() ? (def == null ? "" : def) : value;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private static String normalizeNoticeDisplayType(String value) {
        String s = value == null ? "" : value.trim().toLowerCase();
        return switch (s) {
            case "banner", "popup" -> s;
            default -> "inbox";
        };
    }

    private static List<Long> coerceLongList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(item -> {
                if (item instanceof Number number) {
                    return number.longValue();
                }
                if (item instanceof String text) {
                    try {
                        return Long.parseLong(text);
                    } catch (Exception ignored) {
                        return null;
                    }
                }
                return null;
            }).filter(id -> id != null && id > 0).toList();
        }
        return List.of();
    }
}
