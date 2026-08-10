package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.integration.sillytavern.StWorldbookCatalogService;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final StWorldbookCatalogService worldbookCatalogService;
    private final AppFeatureSettingsService featureSettingsService;

    public ConversationMemoryAttachService(
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            ConversationMemoryWorldbookSyncService syncService,
            MemoryLlmProperties properties,
            StWorldbookCatalogService worldbookCatalogService
    ) {
        this(memoryMapper, entryMapper, syncService, properties, worldbookCatalogService, null);
    }

    @Autowired
    public ConversationMemoryAttachService(
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            ConversationMemoryWorldbookSyncService syncService,
            MemoryLlmProperties properties,
            StWorldbookCatalogService worldbookCatalogService,
            AppFeatureSettingsService featureSettingsService
    ) {
        this.memoryMapper = memoryMapper;
        this.entryMapper = entryMapper;
        this.syncService = syncService;
        this.properties = properties;
        this.worldbookCatalogService = worldbookCatalogService;
        this.featureSettingsService = featureSettingsService;
    }

    public List<String> attachMemoryWorldbookIfAvailable(long conversationId, List<String> worldNames) {
        return attachMemoryWorldbookIfAvailable(conversationId, null, worldNames);
    }

    public List<String> attachMemoryWorldbookIfAvailable(long conversationId, Long branchId, List<String> worldNames) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (worldNames != null) {
            for (String name : worldNames) {
                if (name != null && !name.isBlank()) {
                    out.add(name.trim());
                }
            }
        }
        if (!isLongTermMemoryEnabled()) {
            return out.isEmpty() ? List.of() : List.copyOf(out);
        }
        AppConversationMemory memory = hasBranch(branchId)
                ? memoryMapper.findByConversationBranchId(conversationId, branchId)
                : memoryMapper.findByConversationId(conversationId);
        if (memory == null || memory.getEnabledEntryCount() <= 0) {
            return out.isEmpty() ? List.of() : List.copyOf(out);
        }
        if (!ConversationMemoryWorldbookSyncService.SYNC_SUCCESS.equalsIgnoreCase(memory.getSyncStatus())) {
            log.debug("memory worldbook attach skipped conversationId={} branchId={} status={}",
                    conversationId, branchId, memory.getSyncStatus());
            return out.isEmpty() ? List.of() : List.copyOf(out);
        }
        String worldName = memory.getMemoryWorldName();
        if (worldName == null || worldName.isBlank()) {
            worldName = hasBranch(branchId)
                    ? syncService.resolveWorldName(conversationId, branchId)
                    : syncService.resolveWorldName(conversationId);
        }
        if (worldName == null || worldName.isBlank()) {
            log.warn("memory worldbook attach skipped because world name is empty conversationId={} branchId={}",
                    conversationId, branchId);
            return out.isEmpty() ? List.of() : List.copyOf(out);
        }
        if (!worldbookExists(worldName)) {
            markMissingWorldbook(conversationId, branchId, memory, worldName);
            return out.isEmpty() ? List.of() : List.copyOf(out);
        }
        out.add(worldName.trim());
        return List.copyOf(new ArrayList<>(out));
    }

    public boolean hasSyncedMemoryWorldbook(long conversationId) {
        return hasSyncedMemoryWorldbook(conversationId, null);
    }

    public boolean hasSyncedMemoryWorldbook(long conversationId, Long branchId) {
        if (!isLongTermMemoryEnabled()) {
            return false;
        }
        AppConversationMemory memory = hasBranch(branchId)
                ? memoryMapper.findByConversationBranchId(conversationId, branchId)
                : memoryMapper.findByConversationId(conversationId);
        return memory != null
                && memory.getEnabledEntryCount() > 0
                && ConversationMemoryWorldbookSyncService.SYNC_SUCCESS.equalsIgnoreCase(memory.getSyncStatus())
                && memory.getMemoryWorldName() != null
                && !memory.getMemoryWorldName().isBlank()
                && worldbookExists(memory.getMemoryWorldName());
    }

    public String buildTailMemoryPromptFallbackIfWorldbookUnavailable(long conversationId) {
        return buildTailMemoryPromptFallbackIfWorldbookUnavailable(conversationId, null);
    }

    public String buildTailMemoryPromptFallbackIfWorldbookUnavailable(long conversationId, Long branchId) {
        if (!isLongTermMemoryEnabled()) {
            return "";
        }
        if (hasSyncedMemoryWorldbook(conversationId, branchId)) {
            return "";
        }
        return buildTailMemoryPromptIfAvailable(conversationId, branchId);
    }

    public String buildTailMemoryPromptIfAvailable(long conversationId) {
        return buildTailMemoryPromptIfAvailable(conversationId, null);
    }

    public String buildTailMemoryPromptIfAvailable(long conversationId, Long branchId) {
        if (!isLongTermMemoryEnabled()) {
            return "";
        }
        AppConversationMemory memory = hasBranch(branchId)
                ? memoryMapper.findByConversationBranchId(conversationId, branchId)
                : memoryMapper.findByConversationId(conversationId);
        if (memory == null || memory.getEnabledEntryCount() <= 0) {
            return "";
        }

        List<AppConversationMemoryEntry> entries = hasBranch(branchId)
                ? entryMapper.listEnabledByConversationBranchId(conversationId, branchId)
                : entryMapper.listEnabledByConversationId(conversationId);
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

    private static boolean hasBranch(Long branchId) {
        return branchId != null && branchId > 0;
    }

    private boolean isLongTermMemoryEnabled() {
        return featureSettingsService == null || featureSettingsService.isLongTermMemoryEnabled();
    }

    private boolean worldbookExists(String worldName) {
        try {
            return worldbookCatalogService.resolveWorldNames(List.of(worldName)).missing().isEmpty();
        } catch (RuntimeException e) {
            log.warn("memory worldbook availability check failed worldName={} cause={}",
                    worldName, e.getMessage());
            return false;
        }
    }

    private void markMissingWorldbook(
            long conversationId,
            Long branchId,
            AppConversationMemory memory,
            String worldName
    ) {
        log.warn("memory worldbook is missing; database fallback enabled conversationId={} branchId={} worldName={}",
                conversationId, branchId, worldName);
        memoryMapper.updateSyncStatusForBranch(
                conversationId,
                hasBranch(branchId) ? branchId : 0L,
                worldName,
                memory.getEntryCount(),
                memory.getEnabledEntryCount(),
                ConversationMemoryWorldbookSyncService.SYNC_FAILED,
                "memory worldbook missing from SillyTavern"
        );
    }
}
