package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.conversation.entity.AppConversationStBinding;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.entity.AppConversationBranch;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationStBindingMapper;
import com.example.sillyspringboot.conversation.service.ConversationBranchService;
import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.integration.sillytavern.SillyTavernProperties;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StChatGetRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StChatSaveRequest;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;

/**
 * 运营级：确保 ST chat 快照存在（get→不存在则save建档）。
 * <p>
 * 注意：真正的消息定位/双写一致性会在此基础上继续扩展。
 */
@Service
public class ChatSnapshotService {

    private final AppConversationStBindingMapper bindingMapper;
    private final AppConversationMapper conversationMapper;
    private final ConversationBranchService branchService;
    private final AppMessageMapper messageMapper;
    private final StAdapter stAdapter;
    private final SillyTavernProperties stProps;

    public ChatSnapshotService(
            AppConversationStBindingMapper bindingMapper,
            AppConversationMapper conversationMapper,
            ConversationBranchService branchService,
            AppMessageMapper messageMapper,
            StAdapter stAdapter,
            SillyTavernProperties stProps
    ) {
        this.bindingMapper = bindingMapper;
        this.conversationMapper = conversationMapper;
        this.branchService = branchService;
        this.messageMapper = messageMapper;
        this.stAdapter = stAdapter;
        this.stProps = stProps;
    }

    /**
     * 解析会话对应的 ST 角色卡文件名（avatar_url），供 /api/characters/get 与快照路径一致。
     */
    public String resolveStAvatarUrl(long conversationId) {
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话绑定不存在");
        }
        String avatarUrl = binding.getStAvatarUrl();
        if (avatarUrl == null || avatarUrl.isBlank()) {
            avatarUrl = stProps.getDefaultAvatarUrl();
        }
        return avatarUrl;
    }

    public SnapshotRef ensureSnapshot(long conversationId) {
        SnapshotRef ref = resolveSnapshotRef(conversationId);
        Object got = stAdapter.getChatSnapshot(new StChatGetRequest(ref.avatarUrl(), ref.fileName()));
        // ST chats/get 若目录不存在会返回 {}；若存在则返回数组
        if (!(got instanceof List)) {
            List<Map<String, Object>> headerOnly = List.of(defaultHeader());
            stAdapter.saveChatSnapshot(new StChatSaveRequest(ref.avatarUrl(), ref.fileName(), headerOnly, Boolean.FALSE));
        }
        return ref;
    }

    private SnapshotRef resolveSnapshotRef(long conversationId) {
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话绑定不存在");
        }

        String avatarUrl = binding.getStAvatarUrl();
        if (avatarUrl == null || avatarUrl.isBlank()) {
            avatarUrl = stProps.getDefaultAvatarUrl();
        }
        String fileName = binding.getStChatFileName();
        if (fileName == null || fileName.isBlank()) {
            fileName = binding.getStChatRef();
        }
        return new SnapshotRef(avatarUrl, fileName);
    }

    /**
     * 将 ST chat 快照转换为 OpenAI 风格 messages（用于 continue/regenerate）。
     */
    public List<Map<String, String>> buildMessagesFromSnapshot(long conversationId) {
        SnapshotRef ref = ensureSnapshot(conversationId);
        Object got = stAdapter.getChatSnapshot(new StChatGetRequest(ref.avatarUrl(), ref.fileName()));
        if (!(got instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, String>> messages = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            if (!(item instanceof Map<?, ?> m)) continue;
            // header 行通常包含 chat_metadata/user_name/character_name
            if (m.containsKey("chat_metadata") && m.containsKey("user_name") && m.containsKey("character_name")) {
                continue;
            }
            Object isUserObj = m.get("is_user");
            boolean isUser = isUserObj instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(isUserObj));
            String role = isUser ? "user" : "assistant";
            Object mesObj = m.get("mes");
            String content = mesObj == null ? "" : String.valueOf(mesObj);
            if (content.isBlank()) continue;
            messages.add(Map.of("role", role, "content", content));
        }
        return messages;
    }

    /**
     * 将业务库中的消息事实写回 ST chat snapshot（成功态写回即可）。
     * 业务库为事实源；ST 仅作为受控运行时与可恢复快照。
     */
    public void saveSnapshotFromDb(long conversationId, int limit) {
        Long branchId = resolveActiveBranchId(conversationId);
        if (branchId != null && branchId > 0) {
            saveSnapshotFromDb(conversationId, branchId, limit);
            return;
        }
        saveSnapshotFromRows(conversationId, messageMapper.listRecentByConversationAsc(conversationId, limit));
    }

    public void saveSnapshotFromDb(long conversationId, long branchId, int limit) {
        saveSnapshotFromRows(conversationId, messageMapper.listRecentByConversationBranchAsc(conversationId, branchId, limit));
    }

    /**
     * Writes a generation snapshot without the current user message. The runtime appends that
     * message after applying ST input regexes, so including it here would put it in the prompt twice.
     */
    public void saveSnapshotFromDb(
            long conversationId,
            long branchId,
            int limit,
            String excludedUserMessageRef
    ) {
        saveSnapshotFromRows(
                conversationId,
                messageMapper.listRecentByConversationBranchAsc(conversationId, branchId, limit),
                normalizeMessageRef(excludedUserMessageRef)
        );
    }

    private void saveSnapshotFromRows(long conversationId, List<AppMessage> rows) {
        saveSnapshotFromRows(conversationId, rows, "");
    }

    private void saveSnapshotFromRows(
            long conversationId,
            List<AppMessage> rows,
            String excludedUserMessageRef
    ) {
        SnapshotRef ref = resolveSnapshotRef(conversationId);
        Object existingSnapshot = stAdapter.getChatSnapshot(new StChatGetRequest(ref.avatarUrl(), ref.fileName()));
        List<Map<String, Object>> existingMessages = extractMessages(existingSnapshot);
        Set<Integer> usedExistingIndexes = new HashSet<>();
        Map<String, List<AppMessage>> variantsByRef = buildVariantsByRef(rows);

        List<Map<String, Object>> chat = new ArrayList<>();
        Map<String, Object> existingHeader = extractHeader(existingSnapshot);
        chat.add(existingHeader.isEmpty() ? defaultHeader() : existingHeader);
        Map<Long, Map<String, Object>> serializedByMessageId = new HashMap<>();
        for (AppMessage m : rows) {
            if (!AppChatService.includeVisibleMessage(m)) continue;
            if (isExcludedUserMessage(m, excludedUserMessageRef)) continue;
            if (mergeContinuationIntoAnchor(m, serializedByMessageId)) continue;
            Map<String, Object> serialized = mergeExistingMessage(
                    m,
                    existingMessages,
                    usedExistingIndexes,
                    variantsByRef.get(messageRefFor(m))
            );
            chat.add(serialized);
            if (m.getId() != null) {
                serializedByMessageId.put(m.getId(), serialized);
            }
        }
        if (existingSnapshot instanceof List<?> existingChat && existingChat.equals(chat)) {
            return;
        }
        stAdapter.saveChatSnapshot(new StChatSaveRequest(ref.avatarUrl(), ref.fileName(), chat, Boolean.FALSE));
    }

    private boolean mergeContinuationIntoAnchor(
            AppMessage message,
            Map<Long, Map<String, Object>> serializedByMessageId
    ) {
        if (message == null
                || !"assistant".equalsIgnoreCase(message.getRole())
                || !"CONTINUATION".equalsIgnoreCase(message.getMessageKind())
                || message.getContinueFromMessageId() == null) {
            return false;
        }
        Map<String, Object> anchor = serializedByMessageId.get(message.getContinueFromMessageId());
        if (anchor == null || isUserMessage(anchor)) {
            return false;
        }

        String base = String.valueOf(anchor.getOrDefault("mes", ""));
        String suffix = message.getContent() == null ? "" : message.getContent();
        String merged = base + suffix;
        anchor.put("mes", merged);

        int swipeId = intValue(anchor.get("swipe_id"), 0);
        List<Object> swipes = objectToList(anchor.get("swipes"));
        while (swipes.size() <= swipeId) {
            swipes.add("");
        }
        swipes.set(swipeId, merged);
        anchor.put("swipes", swipes);
        anchor.put("swipe_id", swipeId);
        if (message.getId() != null) {
            serializedByMessageId.put(message.getId(), anchor);
        }
        return true;
    }

    private boolean isExcludedUserMessage(AppMessage message, String excludedUserMessageRef) {
        return !excludedUserMessageRef.isBlank()
                && message != null
                && "user".equalsIgnoreCase(message.getRole())
                && excludedUserMessageRef.equals(messageRefFor(message));
    }

    private String normalizeMessageRef(String messageRef) {
        return messageRef == null ? "" : messageRef.trim();
    }

    private Long resolveActiveBranchId(long conversationId) {
        try {
            AppConversation conversation = conversationMapper.findById(conversationId);
            if (conversation == null) {
                return null;
            }
            AppConversationBranch branch = branchService.requireActiveBranch(conversation);
            return branch == null ? null : branch.getId();
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 仅保留 jsonl 头行，用于删会话/重新开始后的 ST 侧对齐。 */
    public void saveEmptySnapshot(long conversationId) {
        SnapshotRef ref = ensureSnapshot(conversationId);
        List<Map<String, Object>> chat = new ArrayList<>();
        chat.add(loadExistingHeader(ref));
        stAdapter.saveChatSnapshot(new StChatSaveRequest(ref.avatarUrl(), ref.fileName(), chat, Boolean.FALSE));
    }

    private Map<String, Object> loadExistingHeader(SnapshotRef ref) {
        Object got = stAdapter.getChatSnapshot(new StChatGetRequest(ref.avatarUrl(), ref.fileName()));
        Map<String, Object> existing = extractHeader(got);
        return existing.isEmpty() ? defaultHeader() : existing;
    }

    private Map<String, Object> extractHeader(Object snapshot) {
        if (!(snapshot instanceof List<?> list) || list.isEmpty()) {
            return Map.of();
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> raw) || !raw.containsKey("chat_metadata")) {
            return Map.of();
        }
        Map<String, Object> header = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key != null) {
                header.put(String.valueOf(key), value);
            }
        });
        header.putIfAbsent("chat_metadata", Map.of());
        header.putIfAbsent("user_name", "unused");
        header.putIfAbsent("character_name", "unused");
        return header;
    }

    private List<Map<String, Object>> extractMessages(Object snapshot) {
        if (!(snapshot instanceof List<?> list) || list.size() <= 1) {
            return List.of();
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (int i = 1; i < list.size(); i++) {
            Object item = list.get(i);
            if (item instanceof Map<?, ?> raw) {
                messages.add(copyMap(raw));
            }
        }
        return messages;
    }

    private Map<String, Object> mergeExistingMessage(
            AppMessage message,
            List<Map<String, Object>> existingMessages,
            Set<Integer> usedExistingIndexes,
            List<AppMessage> swipeVariants
    ) {
        boolean isUser = "user".equalsIgnoreCase(message.getRole());
        String content = message.getContent() == null ? "" : message.getContent();
        String messageRef = messageRefFor(message);
        Map<String, Object> merged = findExistingMessage(message, existingMessages, usedExistingIndexes);
        if (merged == null) {
            merged = new LinkedHashMap<>();
        }

        merged.put("is_user", isUser);
        merged.put("mes", content);
        mergeMessageRef(merged, messageRef);
        if (!isUser) {
            syncAssistantSwipeFields(merged, content, messageRef, message.getSwipeIndex(), swipeVariants);
        }
        return merged;
    }

    private Map<String, Object> findExistingMessage(
            AppMessage message,
            List<Map<String, Object>> existingMessages,
            Set<Integer> usedExistingIndexes
    ) {
        String messageRef = messageRefFor(message);
        if (!messageRef.isBlank()) {
            for (int i = 0; i < existingMessages.size(); i++) {
                if (usedExistingIndexes.contains(i)) continue;
                if (messageRef.equals(messageRefOf(existingMessages.get(i)))) {
                    usedExistingIndexes.add(i);
                    return copyMap(existingMessages.get(i));
                }
            }
        }

        boolean isUser = "user".equalsIgnoreCase(message.getRole());
        String content = message.getContent() == null ? "" : message.getContent();
        for (int i = 0; i < existingMessages.size(); i++) {
            if (usedExistingIndexes.contains(i)) continue;
            Map<String, Object> existing = existingMessages.get(i);
            if (isUserMessage(existing) == isUser && content.equals(String.valueOf(existing.getOrDefault("mes", "")))) {
                usedExistingIndexes.add(i);
                return copyMap(existing);
            }
        }

        for (int i = 0; i < existingMessages.size(); i++) {
            if (usedExistingIndexes.contains(i)) continue;
            Map<String, Object> existing = existingMessages.get(i);
            if (isUserMessage(existing) == isUser) {
                usedExistingIndexes.add(i);
                return copyMap(existing);
            }
        }

        return null;
    }

    private void mergeMessageRef(Map<String, Object> message, String messageRef) {
        Map<String, Object> extra = objectToMap(message.get("extra"));
        if (!messageRef.isBlank()) {
            extra.put("message_ref", messageRef);
        }
        message.put("extra", extra);
    }

    private void syncAssistantSwipeFields(
            Map<String, Object> message,
            String content,
            String messageRef,
            Integer preferredSwipeIndex,
            List<AppMessage> swipeVariants
    ) {
        int swipeId = preferredSwipeIndex != null ? Math.max(0, preferredSwipeIndex) : intValue(message.get("swipe_id"), 0);
        List<Object> swipes = objectToList(message.get("swipes"));
        if (swipeVariants != null) {
            for (AppMessage variant : swipeVariants) {
                if (variant == null || variant.getSwipeIndex() == null) {
                    continue;
                }
                int idx = Math.max(0, variant.getSwipeIndex());
                while (swipes.size() <= idx) {
                    swipes.add("");
                }
                swipes.set(idx, variant.getContent() == null ? "" : variant.getContent());
            }
        }
        while (swipes.size() <= swipeId) {
            swipes.add("");
        }
        swipes.set(swipeId, content);
        message.put("swipes", swipes);
        message.put("swipe_id", swipeId);

        List<Object> swipeInfo = objectToList(message.get("swipe_info"));
        if (swipeVariants != null) {
            for (AppMessage variant : swipeVariants) {
                if (variant == null || variant.getSwipeIndex() == null) {
                    continue;
                }
                int idx = Math.max(0, variant.getSwipeIndex());
                while (swipeInfo.size() <= idx) {
                    swipeInfo.add(new LinkedHashMap<String, Object>());
                }
                Object rawInfo = swipeInfo.get(idx);
                Map<String, Object> info = rawInfo instanceof Map<?, ?> raw ? copyMap(raw) : new LinkedHashMap<>();
                Map<String, Object> extra = objectToMap(info.get("extra"));
                if (!messageRef.isBlank()) {
                    extra.put("message_ref", messageRef);
                }
                info.put("extra", extra);
                swipeInfo.set(idx, info);
            }
        }
        while (swipeInfo.size() <= swipeId) {
            swipeInfo.add(new LinkedHashMap<String, Object>());
        }
        Object rawInfo = swipeInfo.get(swipeId);
        Map<String, Object> info = rawInfo instanceof Map<?, ?> raw ? copyMap(raw) : new LinkedHashMap<>();
        Map<String, Object> extra = objectToMap(info.get("extra"));
        if (!messageRef.isBlank()) {
            extra.put("message_ref", messageRef);
        }
        info.put("extra", extra);
        swipeInfo.set(swipeId, info);
        message.put("swipe_info", swipeInfo);
    }

    private Map<String, List<AppMessage>> buildVariantsByRef(List<AppMessage> rows) {
        Map<String, List<AppMessage>> variants = new HashMap<>();
        if (rows == null) {
            return variants;
        }
        for (AppMessage row : rows) {
            if (!includeSwipeVariant(row)) {
                continue;
            }
            String ref = messageRefFor(row);
            if (ref.isBlank()) {
                continue;
            }
            variants.computeIfAbsent(ref, ignored -> new ArrayList<>()).add(row);
        }
        return variants;
    }

    private boolean includeSwipeVariant(AppMessage message) {
        if (message == null || message.getContent() == null) {
            return false;
        }
        if (!"assistant".equalsIgnoreCase(message.getRole()) || message.getSwipeIndex() == null) {
            return false;
        }
        String status = message.getStatus() == null ? "" : message.getStatus();
        if (message.getContent().isBlank()) {
            return "SUCCESS".equalsIgnoreCase(status);
        }
        return "SUCCESS".equalsIgnoreCase(status) || "STOPPED".equalsIgnoreCase(status);
    }

    private String messageRefFor(AppMessage message) {
        String existing = message.getStMessageRef() == null ? "" : message.getStMessageRef().trim();
        if (!existing.isBlank()) {
            return existing;
        }
        Long id = message.getId();
        return id == null ? "" : "root:" + id;
    }

    private String messageRefOf(Map<String, Object> message) {
        Object extraObj = message.get("extra");
        if (!(extraObj instanceof Map<?, ?> extra)) {
            return "";
        }
        Object ref = extra.get("message_ref");
        return ref == null ? "" : String.valueOf(ref).trim();
    }

    private boolean isUserMessage(Map<String, Object> message) {
        Object raw = message.get("is_user");
        return raw instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(raw));
    }

    private int intValue(Object raw, int fallback) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Map<String, Object> objectToMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        return new LinkedHashMap<>();
    }

    private List<Object> objectToList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(list);
    }

    private Map<String, Object> copyMap(Map<?, ?> raw) {
        Map<String, Object> copied = new LinkedHashMap<>();
        raw.forEach((key, value) -> {
            if (key != null) {
                copied.put(String.valueOf(key), value);
            }
        });
        return copied;
    }

    private Map<String, Object> defaultHeader() {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("chat_metadata", Map.of());
        header.put("user_name", "unused");
        header.put("character_name", "unused");
        return header;
    }

    public record SnapshotRef(String avatarUrl, String fileName) {}
}
