package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ConversationMemoryAttachService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryAttachService.class);
    private static final String TAIL_MEMORY_HEADER = "Long-term memory for this conversation:";
    private static final String TAIL_MEMORY_GUIDANCE =
            "Use these memories naturally only when relevant. Keep the character's original personality and setting.";
    private static final int DEFAULT_TAIL_ENTRY_LIMIT = 6;

    private final AppConversationMemoryMapper memoryMapper;
    private final AppConversationMemoryEntryMapper entryMapper;
    private final ConversationMemoryWorldbookSyncService syncService;
    private final MemoryLlmProperties properties;

    public ConversationMemoryAttachService(
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            ConversationMemoryWorldbookSyncService syncService,
            MemoryLlmProperties properties
    ) {
        this.memoryMapper = memoryMapper;
        this.entryMapper = entryMapper;
        this.syncService = syncService;
        this.properties = properties;
    }

    public List<String> attachMemoryWorldbookIfAvailable(long conversationId, List<String> worldNames) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (worldNames != null) {
            for (String name : worldNames) {
                if (name != null && !name.isBlank()) {
                    out.add(name.trim());
                }
            }
        }
        AppConversationMemory memory = memoryMapper.findByConversationId(conversationId);
        if (memory == null || memory.getEnabledEntryCount() <= 0) {
            return out.isEmpty() ? List.of() : List.copyOf(out);
        }
        if (!ConversationMemoryWorldbookSyncService.SYNC_SUCCESS.equalsIgnoreCase(memory.getSyncStatus())) {
            log.debug("memory worldbook attach skipped conversationId={} status={}", conversationId, memory.getSyncStatus());
            return out.isEmpty() ? List.of() : List.copyOf(out);
        }
        String worldName = memory.getMemoryWorldName();
        if (worldName == null || worldName.isBlank()) {
            worldName = syncService.resolveWorldName(conversationId);
        }
        out.add(worldName.trim());
        return List.copyOf(new ArrayList<>(out));
    }

    public String buildTailMemoryPromptIfAvailable(long conversationId) {
        AppConversationMemory memory = memoryMapper.findByConversationId(conversationId);
        if (memory == null || memory.getEnabledEntryCount() <= 0) {
            return "";
        }
        List<AppConversationMemoryEntry> entries = entryMapper.listEnabledByConversationId(conversationId);
        if (entries == null || entries.isEmpty()) {
            return "";
        }

        int maxEntries = Math.max(1, Math.min(DEFAULT_TAIL_ENTRY_LIMIT, properties.getMaxConstantEntries()));
        int maxChars = Math.max(80, Math.min(220, properties.getMaxEntryContentChars()));
        List<String> lines = entries.stream()
                .filter(AppConversationMemoryEntry::isEnabled)
                .sorted(Comparator
                        .comparingInt(AppConversationMemoryEntry::getPriority).reversed()
                        .thenComparing(AppConversationMemoryEntry::getId, Comparator.nullsLast(Long::compareTo)))
                .map(AppConversationMemoryEntry::getContent)
                .map(this::normalizeTailMemoryContent)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(maxEntries)
                .map(content -> "- " + trimTo(content, maxChars))
                .toList();

        if (lines.isEmpty()) {
            return "";
        }
        return TAIL_MEMORY_HEADER + "\n"
                + String.join("\n", lines)
                + "\n"
                + TAIL_MEMORY_GUIDANCE;
    }

    private String normalizeTailMemoryContent(String rawContent) {
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.isBlank()) {
            return "";
        }
        if (content.startsWith("Long-term memory:")) {
            content = content.substring("Long-term memory:".length()).trim();
        }
        content = content.replace("Please use this memory naturally; do not repeat it mechanically.", "").trim();
        content = content.replaceAll("\\s+", " ").trim();
        return content;
    }

    private static String trimTo(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars).trim();
    }
}
