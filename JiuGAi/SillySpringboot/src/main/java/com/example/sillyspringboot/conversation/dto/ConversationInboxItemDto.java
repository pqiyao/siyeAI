package com.example.sillyspringboot.conversation.dto;

import java.time.LocalDateTime;

/**
 * 阶段 6：收件箱/最近会话列表（对齐 H5 tavernInbox）需要的“会话 + 角色摘要”。
 */
public record ConversationInboxItemDto(
        long conversationId,
        long characterId,
        String title,
        LocalDateTime updatedAt,
        String characterName,
        String characterAvatarUrl,
        String characterDescription,
        String lastMessageRole,
        String lastMessageContent,
        LocalDateTime lastMessageAt
) {}

