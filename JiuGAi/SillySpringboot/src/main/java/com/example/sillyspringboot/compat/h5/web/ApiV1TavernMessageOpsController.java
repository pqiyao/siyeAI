package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.service.ChatSnapshotService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.conversation.dto.ConversationDetailDto;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryAutoRefreshService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tavern/messages")
public class ApiV1TavernMessageOpsController {

    private final H5ClientUidAuthService h5Auth;
    private final AppTokenService tokenService;
    private final AppConversationService conversationService;
    private final AppMessageMapper messageMapper;
    private final com.example.sillyspringboot.chat.service.AppChatService chatService;
    private final ChatSnapshotService snapshotService;
    private final ConversationMemoryAutoRefreshService memoryAutoRefreshService;

    public ApiV1TavernMessageOpsController(
            H5ClientUidAuthService h5Auth,
            AppTokenService tokenService,
            AppConversationService conversationService,
            AppMessageMapper messageMapper,
            com.example.sillyspringboot.chat.service.AppChatService chatService,
            ChatSnapshotService snapshotService,
            ConversationMemoryAutoRefreshService memoryAutoRefreshService
    ) {
        this.h5Auth = h5Auth;
        this.tokenService = tokenService;
        this.conversationService = conversationService;
        this.messageMapper = messageMapper;
        this.chatService = chatService;
        this.snapshotService = snapshotService;
        this.memoryAutoRefreshService = memoryAutoRefreshService;
    }

    @PostMapping("/swipe")
    @Transactional
    public ApiV1Result<Map<String, Object>> swipe(@RequestBody Map<String, Object> payload) {
        long characterId = requireLong(payload, "characterId");
        String clientUid = requireString(payload, "clientUid");
        String messageId = requireString(payload, "messageId");
        long delta = requireLong(payload, "delta");

        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        long conversationId = requireExistingConversationId(characterId, clientUid, token);
        long activeBranchId = chatService.requireActiveBranchId(conversationId, token);
        long userId = tokenService.validateAndLoadUser(token).getId();
        if (userId <= 0) throw new BusinessException(ErrorCode.UNAUTHORIZED, "unauthorized");

        long mid = parseDbMessageId(messageId);
        AppMessage m = messageMapper.findById(mid);
        if (m == null || m.getConversationId() == null || m.getConversationId().longValue() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "message not found");
        }
        if (m.getBranchId() == null || m.getBranchId().longValue() != activeBranchId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "message not found");
        }
        if (!"assistant".equalsIgnoreCase(m.getRole())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "only assistant messages support swipe");
        }
        if (isOpeningMessage(m)) {
            throw new BusinessException(ErrorCode.CONFLICT, "select an opening branch instead");
        }

        String stRef = m.getStMessageRef();
        if (stRef == null || stRef.isBlank()) {
            H5SwipeStateSupport.SwipeState swipeState = H5SwipeStateSupport.build(m, messageMapper);
            return ApiV1Result.ok(toH5Row(m, swipeState.swipeIndex(), swipeState.swipes()));
        }

        H5SwipeStateSupport.SwipeState currentState = H5SwipeStateSupport.build(m, messageMapper);
        if (currentState.swipes().size() <= 1) {
            return ApiV1Result.ok(toH5Row(m, currentState.swipeIndex(), currentState.swipes()));
        }

        int logicalTarget = currentState.swipeIndex() + (int) delta;
        if (logicalTarget < 0) logicalTarget = 0;
        if (logicalTarget >= currentState.swipes().size()) logicalTarget = currentState.swipes().size() - 1;
        int target = currentState.logicalIndexes().get(logicalTarget);

        Integer previousSwipeIndex = m.getSwipeIndex();
        AppMessage targetVariant = messageMapper.findByStMessageRefAndSwipeIndexAndBranch(stRef, target, activeBranchId);
        if (targetVariant != null) {
            messageMapper.updateStatusAndContent(
                    m.getId(),
                    m.getStatus(),
                    targetVariant.getContent(),
                    m.getErrorCode(),
                    m.getTraceId()
            );
            messageMapper.updateVariantMeta(m.getId(), stRef, target, m.getTraceId());
            m = messageMapper.findById(m.getId());
        }

        // A：同步到 ST runtime（商用一致性）
        try {
            chatService.syncSwipeSelectionToSt(conversationId, m.getId(), token);
        } catch (Exception ignored) {
        }
        if (targetVariant != null && (previousSwipeIndex == null || previousSwipeIndex != target)) {
            chatService.markMemorySourceChanged(conversationId, activeBranchId);
            memoryAutoRefreshService.triggerAfterHistoryChange(conversationId, activeBranchId);
        }

        H5SwipeStateSupport.SwipeState updatedState = H5SwipeStateSupport.build(m, messageMapper);
        return ApiV1Result.ok(toH5Row(m, updatedState.swipeIndex(), updatedState.swipes()));
    }

    @Transactional
    @PostMapping("/delete-branch")
    public ApiV1Result<Boolean> deleteMessageBranch(@RequestBody Map<String, Object> payload) {
        long characterId = requireLong(payload, "characterId");
        String clientUid = requireString(payload, "clientUid");
        String messageId = requireString(payload, "messageId");

        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        long conversationId = requireExistingConversationId(characterId, clientUid, token);
        long activeBranchId = chatService.requireActiveBranchId(conversationId, token);

        long mid = parseDbMessageId(messageId);
        AppMessage m = messageMapper.findById(mid);
        if (m == null || m.getConversationId() == null || m.getConversationId().longValue() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "message not found");
        }
        if (m.getBranchId() == null || m.getBranchId().longValue() != activeBranchId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "message not found");
        }
        String role = m.getRole() == null ? "" : m.getRole();
        if (!"user".equalsIgnoreCase(role) && !"assistant".equalsIgnoreCase(role)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "message not deletable");
        }

        rewindConversationFromMessage(conversationId, m.getId(), false, null);
        chatService.markMemorySourceChanged(conversationId, activeBranchId);
        memoryAutoRefreshService.triggerAfterHistoryChange(conversationId, activeBranchId);
        return ApiV1Result.ok(true);
    }

    @Transactional
    @PostMapping("/edit-user-branch")
    public ApiV1Result<Boolean> editUserBranch(@RequestBody Map<String, Object> payload) {
        long characterId = requireLong(payload, "characterId");
        String clientUid = requireString(payload, "clientUid");
        String messageId = requireString(payload, "messageId");
        String newText = requireString(payload, "newText");

        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        long conversationId = requireExistingConversationId(characterId, clientUid, token);
        long activeBranchId = chatService.requireActiveBranchId(conversationId, token);

        long mid = parseDbMessageId(messageId);
        AppMessage m = messageMapper.findById(mid);
        if (m == null || m.getConversationId() == null || m.getConversationId().longValue() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "message not found");
        }
        if (m.getBranchId() == null || m.getBranchId().longValue() != activeBranchId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "message not found");
        }
        if (!"user".equalsIgnoreCase(m.getRole())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "only user messages are editable");
        }

        rewindConversationFromMessage(conversationId, m.getId(), true, newText);
        chatService.markMemorySourceChanged(conversationId, activeBranchId);
        memoryAutoRefreshService.triggerAfterHistoryChange(conversationId, activeBranchId);
        return ApiV1Result.ok(true);
    }

    private void rewindConversationFromMessage(
            long conversationId,
            long targetMessageId,
            boolean preserveTarget,
            String replacementText
    ) {
        AppMessage target = messageMapper.findById(targetMessageId);
        if (target == null || target.getConversationId() == null || target.getConversationId().longValue() != conversationId) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "message not found");
        }
        if (preserveTarget) {
            messageMapper.updateStatusAndContent(
                    target.getId(),
                    activeStatus(target),
                    replacementText == null ? "" : replacementText,
                    target.getErrorCode(),
                    target.getTraceId()
            );
        }
        long branchId = target.getBranchId() == null ? 0L : target.getBranchId();
        if (branchId <= 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "message not found");
        }
        messageMapper.softDeleteBranchFromIdInBranch(
                conversationId,
                branchId,
                targetMessageId,
                !preserveTarget,
                target.getTraceId() == null ? "rewind" : target.getTraceId()
        );
        snapshotService.saveSnapshotFromDb(conversationId, branchId, 800);
    }

    private static String activeStatus(AppMessage row) {
        String status = row == null || row.getStatus() == null ? "" : row.getStatus().trim();
        if (!status.isBlank() && !"DELETED".equalsIgnoreCase(status)) {
            return status;
        }
        return "SUCCESS";
    }

    private long requireExistingConversationId(long characterId, String clientUid, String token) {
        ConversationDetailDto detail = conversationService.findDetailByH5Character(clientUid, characterId, token);
        if (detail == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "message not found");
        }
        return detail.conversationId();
    }

    private static Map<String, Object> toH5Row(AppMessage m, int swipeIndex, List<String> swipes) {
        Map<String, Object> out = new HashMap<>();
        out.put("id", "db_" + m.getId());
        out.put("branchId", m.getBranchId());
        out.put("role", "assistant".equalsIgnoreCase(m.getRole()) ? "char" : "user");
        out.put("text", m.getContent() == null ? "" : m.getContent());
        out.put("openingMessage", isOpeningMessage(m));
        out.put("messageKind", normalizeMessageKind(m.getMessageKind()));
        if (m.getContinueFromMessageId() != null && m.getContinueFromMessageId() > 0) {
            out.put("continueFromMessageId", "db_" + m.getContinueFromMessageId());
        }
        out.put("swipes", swipes);
        out.put("swipeIndex", swipeIndex);
        return out;
    }

    private static String normalizeMessageKind(String value) {
        String kind = value == null ? "" : value.trim().toUpperCase();
        return "CONTINUATION".equals(kind) ? "CONTINUATION" : "NORMAL";
    }

    private static boolean isOpeningMessage(AppMessage message) {
        String clientMessageId = message == null || message.getClientMessageId() == null
                ? ""
                : message.getClientMessageId();
        return "assistant".equalsIgnoreCase(message == null ? "" : message.getRole())
                && clientMessageId.startsWith("opening_");
    }

    private static long parseDbMessageId(String messageId) {
        if (messageId == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED, "messageId missing");
        String s = messageId.trim();
        if (s.startsWith("db_")) s = s.substring(3);
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "messageId invalid");
        }
    }

    private static String requireString(Map<String, Object> payload, String key) {
        if (payload == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED, key + " missing");
        Object v = payload.get(key);
        String s = v == null ? "" : String.valueOf(v).trim();
        if (s.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_FAILED, key + " missing");
        return s;
    }

    private static long requireLong(Map<String, Object> payload, String key) {
        if (payload == null) throw new BusinessException(ErrorCode.VALIDATION_FAILED, key + " missing");
        Object v = payload.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (Exception ignored) {
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, key + " missing");
    }
}
