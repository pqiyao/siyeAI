package com.example.sillyspringboot.conversation.dto;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;

import java.util.List;

/** Immutable input consumed by every LLM and fallback path of one memory refresh. */
public record ConversationMemoryRefreshSnapshot(
        long conversationId,
        long branchId,
        long sourceRevision,
        long manualRevision,
        long baseMemoryRevision,
        int visibleMessageCount,
        String currentSummaryPreview,
        int currentFactsCount,
        List<MessageSnapshot> messages,
        List<EntrySnapshot> existingEntries,
        String extractionMode,
        String refreshMode
) {
    public ConversationMemoryRefreshSnapshot {
        messages = messages == null ? List.of() : List.copyOf(messages);
        existingEntries = existingEntries == null ? List.of() : List.copyOf(existingEntries);
        extractionMode = extractionMode == null || extractionMode.isBlank() ? "FULL" : extractionMode;
        refreshMode = refreshMode == null || refreshMode.isBlank() ? "AUTO" : refreshMode;
    }

    public ConversationMemoryRefreshSnapshot(
            long conversationId,
            long branchId,
            long sourceRevision,
            long manualRevision,
            long baseMemoryRevision,
            int visibleMessageCount,
            String currentSummaryPreview,
            int currentFactsCount,
            List<MessageSnapshot> messages,
            List<EntrySnapshot> existingEntries
    ) {
        this(
                conversationId,
                branchId,
                sourceRevision,
                manualRevision,
                baseMemoryRevision,
                visibleMessageCount,
                currentSummaryPreview,
                currentFactsCount,
                messages,
                existingEntries,
                "FULL",
                "AUTO"
        );
    }

    public Long firstMessageId() {
        return messages.stream().map(MessageSnapshot::id).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    public Long lastMessageId() {
        Long last = null;
        for (MessageSnapshot message : messages) {
            if (message != null && message.id() != null) {
                last = message.id();
            }
        }
        return last;
    }

    public List<Long> messageIds() {
        return messages.stream()
                .filter(java.util.Objects::nonNull)
                .map(MessageSnapshot::id)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    public record MessageSnapshot(
            Long id,
            String role,
            String status,
            String content,
            String stMessageRef
    ) {
        public static MessageSnapshot from(AppMessage message) {
            return new MessageSnapshot(
                    message.getId(),
                    message.getRole(),
                    message.getStatus(),
                    message.getContent(),
                    message.getStMessageRef()
            );
        }
    }

    public record EntrySnapshot(
            String entryKey,
            String memoryType,
            String content,
            String keywordsJson,
            boolean enabled,
            boolean manualPinned,
            boolean manualDisabled,
            boolean manualDeleted
    ) {
        public static EntrySnapshot from(AppConversationMemoryEntry entry) {
            return new EntrySnapshot(
                    entry.getEntryKey(),
                    entry.getMemoryType(),
                    entry.getContent(),
                    entry.getKeywordsJson(),
                    entry.isEnabled(),
                    entry.isManualPinned(),
                    entry.isManualDisabled(),
                    entry.isManualDeleted()
            );
        }
    }
}
