package com.example.sillyspringboot.integration.sillytavern.dto;

/**
 * 业务会话与 ST 运行时映射的核心标识（字段随后续表结构可扩展）。
 */
public record ConversationIdentity(
        Long conversationId,
        Long userId,
        Long characterId,
        String stCharacterRef,
        String stChatRef,
        String stRuntimeProfile,
        String stAvatarUrl,
        String stChatFileName
) {}
