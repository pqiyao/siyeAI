package com.example.sillyspringboot.conversation.model;

public record ConversationMemoryRefreshMetric(
        String requestId,
        long conversationId,
        long branchId,
        String refreshMode,
        String extractionMode,
        String outcome,
        int inputMessageCount,
        int visibleMessageCount,
        int existingEntryCount,
        int modelOutputEntryCount,
        int acceptedEntryCount,
        int rejectedEntryCount,
        int conflictCount,
        int disableRequestedCount,
        long durationMs
) {
}
