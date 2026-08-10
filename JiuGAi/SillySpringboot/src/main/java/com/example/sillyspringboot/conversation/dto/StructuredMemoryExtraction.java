package com.example.sillyspringboot.conversation.dto;

import java.util.List;

public record StructuredMemoryExtraction(
        String summaryPreview,
        List<ExtractedMemoryEntry> entries,
        List<String> disableEntryKeys,
        String requestId,
        long durationMs,
        int modelOutputEntryCount,
        int parseRejectedEntryCount
) {
    public StructuredMemoryExtraction(
            String summaryPreview,
            List<ExtractedMemoryEntry> entries,
            List<String> disableEntryKeys
    ) {
        this(
                summaryPreview,
                entries,
                disableEntryKeys,
                null,
                0L,
                entries == null ? 0 : entries.size(),
                0
        );
    }

    public StructuredMemoryExtraction(
            String summaryPreview,
            List<ExtractedMemoryEntry> entries,
            List<String> disableEntryKeys,
            String requestId,
            long durationMs
    ) {
        this(
                summaryPreview,
                entries,
                disableEntryKeys,
                requestId,
                durationMs,
                entries == null ? 0 : entries.size(),
                0
        );
    }
}
