package com.example.sillyspringboot.conversation.dto;

import java.util.List;

public record StructuredMemoryExtraction(
        String summaryPreview,
        List<ExtractedMemoryEntry> entries,
        List<String> disableEntryKeys
) {
}
