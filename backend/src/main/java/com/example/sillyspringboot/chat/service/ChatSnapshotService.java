package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.conversation.entity.AppConversationStBinding;
import com.example.sillyspringboot.conversation.mapper.AppConversationStBindingMapper;
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

/**
 * 运营级：确保 ST chat 快照存在（get→不存在则save建档）。
 * <p>
 * 注意：真正的消息定位/双写一致性会在此基础上继续扩展。
 */
@Service
public class ChatSnapshotService {

    private final AppConversationStBindingMapper bindingMapper;
    private final AppMessageMapper messageMapper;
    private final StAdapter stAdapter;
    private final SillyTavernProperties stProps;

    public ChatSnapshotService(
            AppConversationStBindingMapper bindingMapper,
            AppMessageMapper messageMapper,
            StAdapter stAdapter,
            SillyTavernProperties stProps
    ) {
        this.bindingMapper = bindingMapper;
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

        Object got = stAdapter.getChatSnapshot(new StChatGetRequest(avatarUrl, fileName));
        // ST chats/get 若目录不存在会返回 {}；若存在则返回数组
        if (!(got instanceof List)) {
            List<Map<String, Object>> headerOnly = List.of(Map.of(
                    "chat_metadata", Map.of(),
                    "user_name", "unused",
                    "character_name", "unused"
            ));
            stAdapter.saveChatSnapshot(new StChatSaveRequest(avatarUrl, fileName, headerOnly, Boolean.FALSE));
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
        SnapshotRef ref = ensureSnapshot(conversationId);
        List<AppMessage> rows = messageMapper.listRecentByConversationAsc(conversationId, limit);

        List<Map<String, Object>> chat = new ArrayList<>();
        chat.add(Map.of(
                "chat_metadata", Map.of(),
                "user_name", "unused",
                "character_name", "unused"
        ));
        for (AppMessage m : rows) {
            if (!AppChatService.includeVisibleMessage(m)) continue;
            boolean isUser = "user".equalsIgnoreCase(m.getRole());
            chat.add(Map.of(
                    "is_user", isUser,
                    "mes", m.getContent(),
                    // A：即使发生“快照覆盖”，也保留可追踪的 message_ref，避免抹掉 ST jsonl 内的定位信息
                    "extra", Map.of("message_ref", "root:" + (m.getId() == null ? 0L : m.getId()))
            ));
        }
        stAdapter.saveChatSnapshot(new StChatSaveRequest(ref.avatarUrl(), ref.fileName(), chat, Boolean.FALSE));
    }

    /** 仅保留 jsonl 头行，用于删会话/重新开始后的 ST 侧对齐。 */
    public void saveEmptySnapshot(long conversationId) {
        SnapshotRef ref = ensureSnapshot(conversationId);
        List<Map<String, Object>> chat = new ArrayList<>();
        chat.add(Map.of(
                "chat_metadata", Map.of(),
                "user_name", "unused",
                "character_name", "unused"
        ));
        stAdapter.saveChatSnapshot(new StChatSaveRequest(ref.avatarUrl(), ref.fileName(), chat, Boolean.FALSE));
    }

    public record SnapshotRef(String avatarUrl, String fileName) {}
}
