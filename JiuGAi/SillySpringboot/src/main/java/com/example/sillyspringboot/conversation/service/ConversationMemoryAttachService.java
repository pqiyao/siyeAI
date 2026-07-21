package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class ConversationMemoryAttachService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryAttachService.class);

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

    public boolean hasSyncedMemoryWorldbook(long conversationId) {
        AppConversationMemory memory = memoryMapper.findByConversationId(conversationId);
        return memory != null
                && memory.getEnabledEntryCount() > 0
                && ConversationMemoryWorldbookSyncService.SYNC_SUCCESS.equalsIgnoreCase(memory.getSyncStatus());
    }

    public String buildTailMemoryPromptFallbackIfWorldbookUnavailable(long conversationId) {
        if (hasSyncedMemoryWorldbook(conversationId)) {
            return "";
        }
        return buildTailMemoryPromptIfAvailable(conversationId);
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

        int maxEntries = Math.max(1, Math.min(6, properties.getMaxConstantEntries()));
        int maxEntryChars = Math.max(40, Math.min(240, properties.getMaxEntryContentChars()));
        int maxChars = Math.max(300, Math.min(1200, maxEntryChars * maxEntries + 160));
        StringBuilder out = new StringBuilder();
        out.append("Long-term memory for this conversation:\n");

        int used = 0;
        for (AppConversationMemoryEntry entry : entries) {
            if (entry == null || !entry.isEnabled()) {
                continue;
            }
            String content = normalizeMemoryContent(entry.getContent(), maxEntryChars);
            if (content.isBlank()) {
                continue;
            }
            String line = "- " + content + "\n";
            if (out.length() + line.length() > maxChars) {
                break;
            }
            out.append(line);
            used++;
            if (used >= maxEntries) {
                break;
            }
        }
        if (used == 0) {
            return "";
        }
        out.append("Use these memories naturally only when relevant. Keep the character's original personality and setting.");
        return out.toString().trim();
    }

    private static String normalizeMemoryContent(String raw, int maxChars) {
        String content = raw == null ? "" : raw.trim();
        if (content.startsWith("Long-term memory:")) {
            content = content.substring("Long-term memory:".length()).trim();
        }
        String suffix = "Please use this memory naturally; do not repeat it mechanically.";
        if (content.endsWith(suffix)) {
            content = content.substring(0, content.length() - suffix.length()).trim();
        }
        content = content.replaceAll("\\s+", " ").trim();
        if (maxChars > 0 && content.length() > maxChars) {
            content = content.substring(0, maxChars).trim();
        }
        return content;
    }
}
