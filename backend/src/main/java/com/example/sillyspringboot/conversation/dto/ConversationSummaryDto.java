package com.example.sillyspringboot.conversation.dto;

import java.time.LocalDateTime;

public record ConversationSummaryDto(
        Long conversationId,
        Long characterId,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}

