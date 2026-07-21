package com.example.sillyspringboot.conversation.dto;

import java.time.LocalDateTime;

public record ConversationMemoryRefreshResult(
        long conversationId,
        String summaryPreview,
        int factsCount,
        int entryCount,
        int enabledEntryCount,
        String memoryWorldName,
        String syncStatus,
        String syncError,
        LocalDateTime updatedAt
) {
}
