package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.dto.ConversationMemoryRefreshResult;
import com.example.sillyspringboot.conversation.dto.ExtractedMemoryEntry;
import com.example.sillyspringboot.conversation.dto.StructuredMemoryExtraction;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class AppConversationMemoryService {

    private final AppConversationMemoryMapper memoryMapper;
    private final AppConversationMemoryEntryMapper entryMapper;
    private final AppMessageMapper messageMapper;
    private final ConversationMemoryLlmService memoryLlmService;
    private final ConversationMemorySanitizer memorySanitizer;
    private final ConversationMemoryWorldbookSyncService worldbookSyncService;
    private final MemoryLlmProperties memoryLlmProperties;

    public AppConversationMemoryService(
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            AppMessageMapper messageMapper,
            ConversationMemoryLlmService memoryLlmService,
            ConversationMemorySanitizer memorySanitizer,
            ConversationMemoryWorldbookSyncService worldbookSyncService,
            MemoryLlmProperties memoryLlmProperties) {
        this.memoryMapper = memoryMapper;
        this.entryMapper = entryMapper;
        this.messageMapper = messageMapper;
        this.memoryLlmService = memoryLlmService;
        this.memorySanitizer = memorySanitizer;
        this.worldbookSyncService = worldbookSyncService;
        this.memoryLlmProperties = memoryLlmProperties;
    }

    public ConversationMemoryRefreshResult refreshConversationMemory(long conversationId) {
        if (refreshStructuredEntries(conversationId)) {
            return toRefreshResult(conversationId);
        }
        Optional<ConversationMemoryLlmService.MemoryRollup> llm = memoryLlmService.tryLlmRollup(conversationId);
        if (llm.isPresent()) {
            ConversationMemoryLlmService.MemoryRollup r = llm.get();
            memoryMapper.upsertRollup(conversationId, r.summaryPreview(), r.factsCount());
            return toRefreshResult(conversationId);
        }
        if (memoryLlmProperties.isFallbackToHeuristic()) {
            refreshRollupFromMessages(conversationId);
        } else {
            touchRefresh(conversationId);
        }
        return toRefreshResult(conversationId);
    }

    private boolean refreshStructuredEntries(long conversationId) {
        List<AppConversationMemoryEntry> existingEntries = entryMapper.listAllByConversationId(conversationId);
        Optional<StructuredMemoryExtraction> structured =
                memoryLlmService.tryStructuredMemoryExtract(conversationId, existingEntries);
        if (structured.isEmpty()) {
            return false;
        }

        SourceRange sourceRange = resolveSourceRange(conversationId);
        StructuredMemoryExtraction extraction = structured.get();
        Set<String> disabledKeys = new LinkedHashSet<>();
        for (String key : memorySanitizer.sanitizeDisableKeys(extraction.disableEntryKeys())) {
            if (disabledKeys.add(key)) {
                entryMapper.disableByKey(conversationId, key);
            }
        }

        if (extraction.entries() != null) {
            for (ExtractedMemoryEntry extracted : extraction.entries()) {
                for (String key : memorySanitizer.sanitizeReplaceKeys(extracted)) {
                    if (disabledKeys.add(key)) {
                        entryMapper.disableByKey(conversationId, key);
                    }
                }
                AppConversationMemoryEntry entity =
                        memorySanitizer.toEntity(conversationId, extracted, sourceRange.firstMessageId(), sourceRange.lastMessageId());
                if (entity != null) {
                    entryMapper.upsert(entity);
                }
            }
        }

        int entryCount = entryMapper.countAllByConversationId(conversationId);
        int enabledEntryCount = entryMapper.countEnabledByConversationId(conversationId);
        String syncStatus = enabledEntryCount > 0 ? "PENDING" : ConversationMemoryWorldbookSyncService.SYNC_SKIPPED;
        memoryMapper.upsertRefreshState(
                conversationId,
                extraction.summaryPreview(),
                enabledEntryCount,
                null,
                entryCount,
                enabledEntryCount,
                sourceRange.lastMessageId(),
                sourceRange.messageCount(),
                syncStatus,
                null
        );
        try {
            worldbookSyncService.syncWorldbook(conversationId);
        } catch (RuntimeException ignored) {
            // syncWorldbook persists FAILED state first; return that state to H5 for retry UI.
        }
        return true;
    }

    private SourceRange resolveSourceRange(long conversationId) {
        List<AppMessage> rows = messageMapper.listRecentByConversationAsc(
                conversationId,
                Math.max(10, memoryLlmProperties.getMaxMessages())
        );
        Long firstId = null;
        Long lastId = null;
        int count = 0;
        if (rows != null) {
            for (AppMessage m : rows) {
                if (!isVisibleMemorySource(m)) {
                    continue;
                }
                if (m.getId() != null) {
                    if (firstId == null) {
                        firstId = m.getId();
                    }
                    lastId = m.getId();
                }
                count++;
            }
        }
        return new SourceRange(firstId, lastId, count);
    }

    private static boolean isVisibleMemorySource(AppMessage m) {
        if (m == null) {
            return false;
        }
        String st = m.getStatus() == null ? "" : m.getStatus();
        if ("FAILED".equalsIgnoreCase(st) || "DELETED".equalsIgnoreCase(st)) {
            return false;
        }
        String role = m.getRole() == null ? "" : m.getRole();
        if ("assistant".equalsIgnoreCase(role)) {
            return "SUCCESS".equalsIgnoreCase(st) || "STOPPED".equalsIgnoreCase(st);
        }
        return "user".equalsIgnoreCase(role);
    }

    private record SourceRange(Long firstMessageId, Long lastMessageId, int messageCount) {}

    public void touchRefresh(long conversationId) {
        memoryMapper.upsertTouch(conversationId);
    }

    public void refreshRollupFromMessages(long conversationId) {
        List<AppMessage> rows = messageMapper.listRecentByConversationAsc(conversationId, 160);
        StringBuilder sb = new StringBuilder();
        int userTurns = 0;
        int appended = 0;
        for (AppMessage m : rows) {
            if (m == null) {
                continue;
            }
            String st = m.getStatus() == null ? "" : m.getStatus();
            if ("FAILED".equalsIgnoreCase(st) || "DELETED".equalsIgnoreCase(st)) {
                continue;
            }
            String role = m.getRole() == null ? "" : m.getRole();
            if ("assistant".equalsIgnoreCase(role)) {
                if (!"SUCCESS".equalsIgnoreCase(st) && !"STOPPED".equalsIgnoreCase(st)) {
                    continue;
                }
            } else if (!"user".equalsIgnoreCase(role)) {
                continue;
            }
            if ("user".equalsIgnoreCase(role)) {
                userTurns++;
            }
            String text = m.getContent() == null ? "" : m.getContent().trim();
            if (text.isEmpty() || appended >= 14) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            String previewLine = text.replaceAll("\\s+", " ");
            if (previewLine.length() > 100) {
                previewLine = previewLine.substring(0, 100) + "...";
            }
            sb.append('[')
                    .append("user".equalsIgnoreCase(role) ? "User" : "AI")
                    .append("] ")
                    .append(previewLine);
            appended++;
        }

        String preview = sb.toString();
        if (preview.length() > 420) {
            preview = preview.substring(0, 420) + "...";
        }
        if (preview.isBlank()) {
            preview = "(No memorable turns yet)";
        }
        memoryMapper.upsertRollup(conversationId, preview, Math.min(Math.max(userTurns, 0), 99));
    }

    public Map<String, Object> toH5MemoryMap(long conversationId) {
        AppConversationMemory m = memoryMapper.findByConversationId(conversationId);
        if (m == null) {
            return null;
        }
        Map<String, Object> out = new HashMap<>();
        out.put("summaryPreview", m.getSummaryPreview());
        out.put("factsCount", m.getFactsCount());
        out.put("entryCount", m.getEntryCount());
        out.put("enabledEntryCount", m.getEnabledEntryCount());
        out.put("memoryWorldName", nullToEmpty(m.getMemoryWorldName()));
        out.put("lastSourceMessageId", m.getLastSourceMessageId());
        out.put("lastRefreshedMessageCount", m.getLastRefreshedMessageCount());
        out.put("syncStatus", nullToEmpty(m.getSyncStatus()));
        out.put("syncError", nullToEmpty(m.getSyncError()));
        if (m.getLastSyncedAt() != null) {
            out.put("lastSyncedAt", m.getLastSyncedAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        }
        if (m.getUpdatedAt() != null) {
            out.put("updatedAt", m.getUpdatedAt().atZone(ZoneId.systemDefault()).toInstant().toString());
        }
        return out;
    }

    private ConversationMemoryRefreshResult toRefreshResult(long conversationId) {
        AppConversationMemory m = memoryMapper.findByConversationId(conversationId);
        if (m == null) {
            return new ConversationMemoryRefreshResult(
                    conversationId,
                    "",
                    0,
                    0,
                    0,
                    "",
                    ConversationMemoryWorldbookSyncService.SYNC_SKIPPED,
                    "",
                    null
            );
        }
        return new ConversationMemoryRefreshResult(
                conversationId,
                nullToEmpty(m.getSummaryPreview()),
                m.getFactsCount(),
                m.getEntryCount(),
                m.getEnabledEntryCount(),
                nullToEmpty(m.getMemoryWorldName()),
                nullToEmpty(m.getSyncStatus()),
                nullToEmpty(m.getSyncError()),
                m.getUpdatedAt()
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
