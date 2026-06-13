package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.service.AppChatService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5StAssetUrls;
import com.example.sillyspringboot.conversation.dto.ConversationDetailDto;
import com.example.sillyspringboot.conversation.dto.ConversationInboxItemDto;
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tavern")
public class ApiV1TavernInboxController {

    private final H5ClientUidAuthService h5Auth;
    private final AppConversationService conversationService;
    private final AppMessageMapper messageMapper;
    private final com.example.sillyspringboot.auth.token.AppTokenService tokenService;
    private final com.example.sillyspringboot.compat.h5.mapper.AppConversationArchiveMapper archiveMapper;
    private final com.example.sillyspringboot.compat.h5.service.H5TavernSessionService tavernSessionService;
    private final H5StAssetUrls stAssetUrls;
    private final AppConversationMemoryService conversationMemoryService;
    private final AppChatService chatService;

    public ApiV1TavernInboxController(
            H5ClientUidAuthService h5Auth,
            AppConversationService conversationService,
            AppMessageMapper messageMapper,
            com.example.sillyspringboot.auth.token.AppTokenService tokenService,
            com.example.sillyspringboot.compat.h5.mapper.AppConversationArchiveMapper archiveMapper,
            com.example.sillyspringboot.compat.h5.service.H5TavernSessionService tavernSessionService,
            H5StAssetUrls stAssetUrls,
            AppConversationMemoryService conversationMemoryService,
            AppChatService chatService
    ) {
        this.h5Auth = h5Auth;
        this.conversationService = conversationService;
        this.messageMapper = messageMapper;
        this.tokenService = tokenService;
        this.archiveMapper = archiveMapper;
        this.tavernSessionService = tavernSessionService;
        this.stAssetUrls = stAssetUrls;
        this.conversationMemoryService = conversationMemoryService;
        this.chatService = chatService;
    }

    @GetMapping("/sessions")
    public ApiV1Result<Map<String, Object>> sessions(@RequestParam("clientUid") String clientUid) {
        String token = h5Auth.issueTokenForClientUid(clientUid);
        List<ConversationInboxItemDto> list = conversationService.listInboxForUser(token, 200);
        Map<String, Object> data = new HashMap<>();
        data.put("sessions", list.stream().map(this::toSession).toList());
        return ApiV1Result.ok(data);
    }

    @GetMapping("/messages")
    public ApiV1Result<Map<String, Object>> messages(
            @RequestParam("characterId") long characterId,
            @RequestParam("clientUid") String clientUid,
            @RequestParam(name = "beforeMessageId", required = false) String beforeMessageId,
            @RequestParam(name = "limit", required = false, defaultValue = "400") int limit
    ) {
        String token = h5Auth.issueTokenForClientUid(clientUid);
        long userId = tokenService.validateAndLoadUser(token).getId();
        long conversationId = ensureConversationId(characterId, clientUid, token);
        int safeLimit = normalizeMessagePageLimit(limit);

        Map<String, Object> envelope = new HashMap<>();
        envelope.put("conversationId", conversationId);
        envelope.put("tavernMeta", defaultTavernMeta());
        envelope.put("memory", conversationMemoryService.toH5MemoryMap(conversationId));

        if (archiveMapper.existsByUserAndConversation(userId, conversationId) > 0) {
            envelope.put("messages", List.of());
            return ApiV1Result.ok(envelope);
        }

        chatService.ensureOpeningAssistantMessage(conversationId, token);

        MessagePageSlice pageSlice = loadMessagePage(conversationId, beforeMessageId, safeLimit);
        List<Map<String, Object>> out = pageSlice.rows().stream()
                .filter(this::includeMessageForH5Chat)
                .map(m -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", "db_" + m.getId());
                    row.put("role", "assistant".equalsIgnoreCase(m.getRole()) ? "char" : "user");
                    row.put("text", m.getContent() == null ? "" : m.getContent());
                    row.put("messageKind", normalizeMessageKind(m.getMessageKind()));
                    if (m.getContinueFromMessageId() != null && m.getContinueFromMessageId() > 0) {
                        row.put("continueFromMessageId", "db_" + m.getContinueFromMessageId());
                    }
                    row.put("voiceUrl", m.getVoiceUrl());
                    row.put("voiceDurationMs", m.getVoiceDurationMs());
                    H5SwipeStateSupport.SwipeState swipeState = H5SwipeStateSupport.build(m, messageMapper);
                    row.put("swipes", swipeState.swipes());
                    row.put("swipeIndex", swipeState.swipeIndex());
                    return row;
                })
                .toList();
        envelope.put("messages", out);
        envelope.put("page", buildPageMeta(pageSlice, safeLimit, beforeMessageId, out.size()));
        return ApiV1Result.ok(envelope);
    }

    private static String normalizeMessageKind(String value) {
        String kind = value == null ? "" : value.trim().toUpperCase();
        return "CONTINUATION".equals(kind) ? "CONTINUATION" : "NORMAL";
    }

    private static Map<String, Object> defaultTavernMeta() {
        Map<String, Object> m = new HashMap<>();
        m.put("memoryAutoEveryMessages", 20);
        m.put("memoryAutoMinMinutesBetween", 30);
        return m;
    }

    private boolean includeMessageForH5Chat(AppMessage m) {
        if (m == null) {
            return false;
        }
        String status = m.getStatus() == null ? "" : m.getStatus();
        if ("FAILED".equalsIgnoreCase(status) || "DELETED".equalsIgnoreCase(status)) {
            return false;
        }
        if ("user".equalsIgnoreCase(m.getRole())) {
            return true;
        }
        if (!"assistant".equalsIgnoreCase(m.getRole())) {
            return false;
        }
        if (m.getContent() == null || m.getContent().isBlank()) {
            return false;
        }
        if (!"SUCCESS".equalsIgnoreCase(status) && !"STOPPED".equalsIgnoreCase(status)) {
            return false;
        }
        String ref = m.getStMessageRef();
        if (ref != null && ref.startsWith("root:")) {
            try {
                long rootId = Long.parseLong(ref.substring("root:".length()));
                return m.getId() != null && m.getId() == rootId;
            } catch (Exception ignored) {
                return true;
            }
        }
        return true;
    }

    private MessagePageSlice loadMessagePage(long conversationId, String beforeMessageId, int limit) {
        Long beforeId = parseDbMessageId(beforeMessageId);
        List<AppMessage> raw = beforeId == null
                ? messageMapper.listRecentByConversationAsc(conversationId, limit + 1)
                : messageMapper.listBeforeConversationAsc(conversationId, beforeId, limit + 1);
        boolean hasMore = raw.size() > limit;
        List<AppMessage> rows;
        if (hasMore) {
            rows = raw.subList(1, raw.size());
        } else {
            rows = raw;
        }
        Long nextBeforeId = rows.isEmpty() ? null : rows.get(0).getId();
        Long newestMessageId = rows.isEmpty() ? null : rows.get(rows.size() - 1).getId();
        return new MessagePageSlice(List.copyOf(rows), hasMore, nextBeforeId, newestMessageId);
    }

    private Map<String, Object> buildPageMeta(
            MessagePageSlice pageSlice,
            int requestedLimit,
            String beforeMessageId,
            int visibleCount
    ) {
        Map<String, Object> page = new HashMap<>();
        page.put("requestedLimit", requestedLimit);
        page.put("hasMore", pageSlice.hasMore());
        page.put("mode", beforeMessageId == null || beforeMessageId.isBlank() ? "latest" : "history");
        page.put("beforeMessageId", normalizeH5MessageId(parseDbMessageId(beforeMessageId)));
        page.put("nextBeforeMessageId", normalizeH5MessageId(pageSlice.nextBeforeId()));
        page.put("newestMessageId", normalizeH5MessageId(pageSlice.newestMessageId()));
        page.put("visibleCount", visibleCount);
        return page;
    }

    private static int normalizeMessagePageLimit(int limit) {
        return Math.max(20, Math.min(400, limit));
    }

    private static Long parseDbMessageId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("db_")) {
            value = value.substring(3);
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizeH5MessageId(Long dbId) {
        return dbId == null || dbId <= 0 ? "" : "db_" + dbId;
    }

    @PostMapping("/sessions/delete")
    public ApiV1Result<Boolean> delete(@RequestParam(name = "characterId", required = false) Long characterIdFromQuery,
                                       @RequestBody(required = false) Map<String, Object> body) {
        Long characterId = null;
        String clientUid = null;
        if (body != null) {
            Object cid = body.get("characterId");
            if (cid instanceof Number n) characterId = n.longValue();
            if (cid instanceof String s) {
                try {
                    characterId = Long.parseLong(s);
                } catch (Exception ignored) {
                }
            }
            Object cu = body.get("clientUid");
            if (cu != null) clientUid = String.valueOf(cu);
        }
        if (characterId == null) characterId = characterIdFromQuery;
        if (characterId == null || characterId <= 0) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "characterId missing");
        if (clientUid == null || clientUid.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid missing");

        String token = h5Auth.issueTokenForClientUid(clientUid);
        long userId = tokenService.validateAndLoadUser(token).getId();
        Long conversationId = conversationService.findConversationIdByH5CharacterForSessionCleanup(clientUid, characterId, token);
        if (conversationId == null) {
            return ApiV1Result.ok(true);
        }
        archiveMapper.deleteByUserAndConversation(userId, conversationId);
        tavernSessionService.restartFresh(conversationId);
        return ApiV1Result.ok(true);
    }

    @PostMapping("/sessions/restart")
    public ApiV1Result<Boolean> restart(@RequestBody(required = false) Map<String, Object> body) {
        Long characterId = null;
        String clientUid = null;
        if (body != null) {
            Object cid = body.get("characterId");
            if (cid instanceof Number n) characterId = n.longValue();
            if (cid instanceof String s) {
                try {
                    characterId = Long.parseLong(s);
                } catch (Exception ignored) {
                }
            }
            Object cu = body.get("clientUid");
            if (cu != null) clientUid = String.valueOf(cu);
        }
        if (characterId == null || characterId <= 0) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "characterId missing");
        if (clientUid == null || clientUid.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "clientUid missing");

        String token = h5Auth.issueTokenForClientUid(clientUid);
        long userId = tokenService.validateAndLoadUser(token).getId();
        Long conversationId = conversationService.findConversationIdByH5CharacterForSessionCleanup(clientUid, characterId, token);
        if (conversationId == null) {
            return ApiV1Result.ok(true);
        }
        archiveMapper.deleteByUserAndConversation(userId, conversationId);
        tavernSessionService.restartFresh(conversationId);
        return ApiV1Result.ok(true);
    }

    private long ensureConversationId(long characterId, String clientUid, String token) {
        return conversationService.ensureDetailByH5Character(clientUid, characterId, token).conversationId();
    }

    private Map<String, Object> toSession(ConversationInboxItemDto it) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", String.valueOf(it.conversationId()));
        row.put("characterId", it.characterId());
        row.put(
                "displayTitle",
                it.title() == null || it.title().isBlank()
                        ? (it.characterName() == null ? "Chat" : it.characterName())
                        : it.title()
        );
        row.put("nickname", it.characterName());
        String rawAvatar = it.characterAvatarUrl() == null ? "" : it.characterAvatarUrl();
        String abs = stAssetUrls.resolve(rawAvatar);
        String avatarThumb = stAssetUrls.resolveWithPreset(rawAvatar, "avatar");
        String coverThumb = stAssetUrls.resolveWithPreset(rawAvatar, "card");
        row.put("avatarUrl", abs);
        row.put("coverUrl", abs);
        row.put("avatarThumbUrl", avatarThumb);
        row.put("coverThumbUrl", coverThumb);
        String snippet = it.lastMessageContent() == null ? "" : it.lastMessageContent();
        if (snippet.length() > 120) snippet = snippet.substring(0, 120);
        row.put("snippet", snippet);
        row.put("lastMessage", snippet);
        row.put("updatedAt", it.updatedAt());
        return row;
    }

    private record MessagePageSlice(
            List<AppMessage> rows,
            boolean hasMore,
            Long nextBeforeId,
            Long newestMessageId
    ) {
    }
}
