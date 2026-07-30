package com.example.sillyspringboot.conversation.dto;

import java.math.BigDecimal;
import java.util.List;

public record ExtractedMemoryEntry(
        String entryKey,
        String memoryType,
        String title,
        String content,
        List<String> keywords,
        List<String> secondaryKeywords,
        int priority,
        String position,
        boolean constantInjection,
        boolean selective,
        boolean enabled,
        BigDecimal confidence,
        List<String> replaces,
        List<Long> sourceMessageIds
) {
    public ExtractedMemoryEntry(
            String entryKey,
            String memoryType,
            String title,
            String content,
            List<String> keywords,
            List<String> secondaryKeywords,
            int priority,
            String position,
            boolean constantInjection,
            boolean selective,
            boolean enabled,
            BigDecimal confidence,
            List<String> replaces
    ) {
        this(entryKey, memoryType, title, content, keywords, secondaryKeywords, priority, position,
                constantInjection, selective, enabled, confidence, replaces, List.of());
    }
}
