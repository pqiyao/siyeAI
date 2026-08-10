package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StWorldbookSaveRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StWorldbookOptionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ConversationMemoryWorldbookSyncService {

    public static final String SYNC_SUCCESS = "SUCCESS";
    public static final String SYNC_FAILED = "FAILED";
    public static final String SYNC_SKIPPED = "SKIPPED";

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryWorldbookSyncService.class);

    private final StAdapter stAdapter;
    private final AppConversationMapper conversationMapper;
    private final AppConversationMemoryMapper memoryMapper;
    private final AppConversationMemoryEntryMapper entryMapper;
    private final ConversationMemorySanitizer sanitizer;
    private final MemoryLlmProperties properties;

    public ConversationMemoryWorldbookSyncService(
            StAdapter stAdapter,
            AppConversationMapper conversationMapper,
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            ConversationMemorySanitizer sanitizer,
            MemoryLlmProperties properties
    ) {
        this.stAdapter = stAdapter;
        this.conversationMapper = conversationMapper;
        this.memoryMapper = memoryMapper;
        this.entryMapper = entryMapper;
        this.sanitizer = sanitizer;
        this.properties = properties;
    }

    public String resolveWorldName(long conversationId) {
        return resolveWorldName(conversationId, null);
    }

    public String resolveWorldName(long conversationId, Long branchId) {
        AppConversation conversation = conversationMapper.findById(conversationId);
        Long userId = conversation == null ? null : conversation.getUserId();
        Long characterId = conversation == null ? null : conversation.getCharacterId();
        return buildWorldName(conversationId, branchId, userId, characterId);
    }

    public String syncWorldbook(long conversationId) {
        return syncWorldbook(conversationId, null);
    }

    public String syncWorldbook(long conversationId, Long branchId) {
        String baseWorldName = resolveWorldName(conversationId, branchId);
        AppConversationMemory memoryState = memoryMapper.findByConversationBranchId(
                conversationId,
                hasBranch(branchId) ? branchId : 0L
        );
        Long expectedMemoryRevision = memoryState == null ? null : memoryState.getMemoryRevision();
        String worldName = versionedWorldName(baseWorldName, expectedMemoryRevision);
        List<AppConversationMemoryEntry> enabled = hasBranch(branchId)
                ? entryMapper.listEnabledByConversationBranchId(conversationId, branchId)
                : entryMapper.listEnabledByConversationId(conversationId);
        int entryCount = hasBranch(branchId)
                ? entryMapper.countAllByConversationBranchId(conversationId, branchId)
                : entryMapper.countAllByConversationId(conversationId);
        int enabledCount = enabled == null ? 0 : enabled.size();
        if (enabledCount <= 0) {
            try {
                boolean current = updateSyncStatus(
                        conversationId, branchId, baseWorldName, entryCount, 0,
                        SYNC_SKIPPED, null, expectedMemoryRevision
                );
                if (current) {
                    deleteBranchWorldbooks(conversationId, branchId, Set.of(baseWorldName));
                }
                return worldName;
            } catch (Exception e) {
                String err = trimTo(rootCauseMessage(e), 512);
                updateSyncStatus(conversationId, branchId, worldName, entryCount, 0, SYNC_FAILED, err, expectedMemoryRevision);
                log.warn("memory worldbook delete failed conversationId={} branchId={} worldName={} cause={}",
                        conversationId, branchId, worldName, err);
                throw e;
            }
        }

        List<AppConversationMemoryEntry> selected = limitEntries(enabled);
        int syncedEntryCount = selected.size();
        Map<String, Object> data = buildWorldbookData(conversationId, branchId, worldName, selected);
        try {
            stAdapter.saveWorldbook(new StWorldbookSaveRequest(worldName, data));
            boolean current = updateSyncStatus(
                    conversationId, branchId, worldName, entryCount, syncedEntryCount,
                    SYNC_SUCCESS, null, expectedMemoryRevision
            );
            if (!current) {
                deleteWorldbookQuietly(conversationId, worldName);
                return worldName;
            }
            deleteSupersededVersions(conversationId, branchId, baseWorldName, worldName);
            return worldName;
        } catch (Exception e) {
            String err = trimTo(rootCauseMessage(e), 512);
            updateSyncStatus(conversationId, branchId, worldName, entryCount, syncedEntryCount, SYNC_FAILED, err, expectedMemoryRevision);
            log.warn("memory worldbook sync failed conversationId={} branchId={} worldName={} cause={}",
                    conversationId, branchId, worldName, err);
            throw e;
        }
    }

    public void deleteWorldbook(long conversationId) {
        deleteWorldbook(conversationId, null);
    }

    public void deleteWorldbook(long conversationId, Long branchId) {
        String worldName = resolveWorldName(conversationId, branchId);
        deleteWorldbookByName(conversationId, worldName);
    }

    public void deleteWorldbookByName(long conversationId, String worldName) {
        String safeWorldName = worldName == null ? "" : worldName.trim();
        validateWorldName(conversationId, safeWorldName);
        try {
            stAdapter.deleteWorldbook(safeWorldName);
        } catch (Exception e) {
            log.warn("memory worldbook delete failed conversationId={} worldName={} cause={}",
                    conversationId, safeWorldName, rootCauseMessage(e));
            throw e;
        }
    }

    public void deleteWorldbooksByName(long conversationId, Collection<String> worldNames) {
        Set<String> candidates = new LinkedHashSet<>();
        if (worldNames != null) {
            for (String worldName : worldNames) {
                String safeWorldName = worldName == null ? "" : worldName.trim();
                validateWorldName(conversationId, safeWorldName);
                candidates.add(safeWorldName);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }

        List<StWorldbookOptionDto> options = stAdapter.listWorldbooks();
        Set<String> existing = new LinkedHashSet<>();
        if (options != null) {
            for (StWorldbookOptionDto option : options) {
                if (option == null) {
                    continue;
                }
                addNonBlank(existing, option.fileId());
                addNonBlank(existing, option.name());
            }
        }

        for (String worldName : candidates) {
            if (!existing.contains(worldName)) {
                continue;
            }
            try {
                boolean deleted = stAdapter.deleteWorldbook(worldName);
                if (!deleted) {
                    throw new IllegalStateException("SillyTavern did not confirm worldbook deletion: " + worldName);
                }
            } catch (Exception e) {
                log.warn("memory worldbook delete failed conversationId={} worldName={} cause={}",
                        conversationId, worldName, rootCauseMessage(e));
                throw e;
            }
        }
    }

    public void deleteBranchWorldbooks(
            long conversationId,
            Long branchId,
            Collection<String> knownWorldNames
    ) {
        String baseWorldName = resolveWorldName(conversationId, branchId);
        Set<String> candidates = new LinkedHashSet<>();
        if (knownWorldNames != null) {
            knownWorldNames.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(String::trim)
                    .filter(name -> !name.isBlank())
                    .forEach(candidates::add);
        }
        try {
            List<StWorldbookOptionDto> options = stAdapter.listWorldbooks();
            if (options != null) {
                for (StWorldbookOptionDto option : options) {
                    if (option == null) {
                        continue;
                    }
                    addIfVersionOf(candidates, option.fileId(), baseWorldName);
                    addIfVersionOf(candidates, option.name(), baseWorldName);
                }
            }
            for (String candidate : candidates) {
                if (isVersionOf(candidate, baseWorldName)) {
                    deleteWorldbookQuietly(conversationId, candidate);
                }
            }
        } catch (RuntimeException e) {
            log.warn("memory branch worldbook cleanup deferred conversationId={} branchId={} cause={}",
                    conversationId, branchId, rootCauseMessage(e));
        }
    }

    private static void validateWorldName(long conversationId, String worldName) {
        String expectedPrefix = "jg_memory_conv_" + conversationId + "_";
        if (worldName == null || worldName.isBlank() || !worldName.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("memory worldbook does not belong to conversation " + conversationId);
        }
    }

    private static void addNonBlank(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.trim());
        }
    }

    private boolean updateSyncStatus(
            long conversationId,
            Long branchId,
            String worldName,
            int entryCount,
            int enabledCount,
            String syncStatus,
            String syncError,
            Long expectedMemoryRevision
    ) {
        if (expectedMemoryRevision != null) {
            int updated = memoryMapper.updateWorldbookSyncStatusWithRevision(
                    conversationId,
                    hasBranch(branchId) ? branchId : 0L,
                    worldName,
                    entryCount,
                    enabledCount,
                    syncStatus,
                    syncError,
                    expectedMemoryRevision
            );
            if (updated == 0) {
                log.info("memory worldbook sync status superseded conversationId={} branchId={} expectedRevision={} status={}",
                        conversationId, branchId, expectedMemoryRevision, syncStatus);
            }
            return updated > 0;
        }
        memoryMapper.updateSyncStatusForBranch(
                conversationId,
                hasBranch(branchId) ? branchId : 0L,
                worldName,
                entryCount,
                enabledCount,
                syncStatus,
                syncError
        );
        return true;
    }

    private void deleteSupersededVersions(
            long conversationId,
            Long branchId,
            String baseWorldName,
            String currentWorldName
    ) {
        try {
            List<StWorldbookOptionDto> options = stAdapter.listWorldbooks();
            if (options == null) {
                return;
            }
            Set<String> stale = new LinkedHashSet<>();
            for (StWorldbookOptionDto option : options) {
                if (option == null) {
                    continue;
                }
                addIfSuperseded(stale, option.fileId(), baseWorldName, currentWorldName);
                addIfSuperseded(stale, option.name(), baseWorldName, currentWorldName);
            }
            for (String candidate : stale) {
                deleteWorldbookQuietly(conversationId, candidate);
            }
        } catch (RuntimeException e) {
            log.warn("memory superseded worldbook cleanup deferred conversationId={} branchId={} cause={}",
                    conversationId, branchId, rootCauseMessage(e));
        }
    }

    private void deleteWorldbookQuietly(long conversationId, String worldName) {
        try {
            validateWorldName(conversationId, worldName);
            stAdapter.deleteWorldbook(worldName);
        } catch (RuntimeException e) {
            log.warn("memory worldbook cleanup deferred conversationId={} worldName={} cause={}",
                    conversationId, worldName, rootCauseMessage(e));
        }
    }

    private static void addIfVersionOf(Set<String> out, String candidate, String baseWorldName) {
        if (isVersionOf(candidate, baseWorldName)) {
            out.add(candidate.trim());
        }
    }

    private static void addIfSuperseded(
            Set<String> out,
            String candidate,
            String baseWorldName,
            String currentWorldName
    ) {
        if (isVersionOf(candidate, baseWorldName) && !candidate.trim().equals(currentWorldName)) {
            out.add(candidate.trim());
        }
    }

    private static boolean isVersionOf(String candidate, String baseWorldName) {
        if (candidate == null || baseWorldName == null) {
            return false;
        }
        String value = candidate.trim();
        return value.equals(baseWorldName) || value.startsWith(baseWorldName + "_r");
    }

    private static String versionedWorldName(String baseWorldName, Long revision) {
        if (revision == null || revision < 0) {
            return baseWorldName;
        }
        return baseWorldName + "_r" + revision;
    }

    private Map<String, Object> buildWorldbookData(long conversationId, Long branchId, String worldName, List<AppConversationMemoryEntry> entries) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", worldName);
        data.put("extensions", Map.of(
                "source", "SillySpringboot",
                "kind", "conversation_memory",
                "conversationId", conversationId,
                "branchId", branchId == null ? 0 : branchId,
                "version", 1
        ));
        Map<String, Object> outEntries = new LinkedHashMap<>();
        int insertionOrder = 0;
        for (AppConversationMemoryEntry entry : entries) {
            if (entry == null || entry.getEntryKey() == null || entry.getEntryKey().isBlank()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", sanitizer.readKeywords(entry.getKeywordsJson()));
            item.put("secondary_keys", sanitizer.readKeywords(entry.getSecondaryKeywordsJson()));
            item.put("content", buildLorebookContent(entry));
            item.put("enabled", entry.isEnabled());
            item.put("constant", entry.isConstantInjection());
            item.put("vectorized", !entry.isConstantInjection());
            item.put("selective", entry.isSelective());
            item.put("position", entry.getPosition() == null ? "before_char" : entry.getPosition());
            item.put("priority", entry.getPriority());
            item.put("scanDepth", 8);
            item.put("caseSensitive", false);
            item.put("matchWholeWords", false);
            item.put("useProbability", true);
            item.put("probability", 100);
            item.put("insertion_order", insertionOrder++);
            item.put("comment", entry.getTitle() == null || entry.getTitle().isBlank() ? entry.getMemoryType() : entry.getTitle());
            outEntries.put(entry.getEntryKey(), item);
        }
        data.put("entries", outEntries);
        return data;
    }

    private List<AppConversationMemoryEntry> limitEntries(List<AppConversationMemoryEntry> entries) {
        int maxEnabled = Math.max(1, properties.getMaxEnabledEntries());
        int maxConstant = Math.max(1, properties.getMaxConstantEntries());
        int[] constantCount = {0};
        return entries.stream()
                .filter(e -> {
                    if (e == null) {
                        return false;
                    }
                    if (!e.isConstantInjection()) {
                        return true;
                    }
                    if (constantCount[0] >= maxConstant) {
                        return false;
                    }
                    constantCount[0]++;
                    return true;
                })
                .limit(maxEnabled)
                .toList();
    }

    private static String buildLorebookContent(AppConversationMemoryEntry entry) {
        String content = entry.getContent() == null ? "" : entry.getContent().trim();
        if (content.startsWith("Long-term memory:")) {
            return content;
        }
        return "Long-term memory: " + content + " Please use this memory naturally; do not repeat it mechanically.";
    }

    private static String buildWorldName(long conversationId, Long branchId, Long userId, Long characterId) {
        long branch = branchId == null ? 0L : branchId;
        String seed = conversationId + ":" + branch + ":" + (userId == null ? 0 : userId) + ":" + (characterId == null ? 0 : characterId);
        if (branch > 0) {
            return "jg_memory_conv_" + conversationId + "_b" + branch + "_" + shortHash(seed, 10);
        }
        return "jg_memory_conv_" + conversationId + "_" + shortHash(seed, 10);
    }

    private static boolean hasBranch(Long branchId) {
        return branchId != null && branchId > 0;
    }

    private static String shortHash(String seed, int chars) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                out.append(String.format("%02x", b));
            }
            return out.substring(0, Math.max(1, Math.min(chars, out.length())));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String rootCauseMessage(Throwable error) {
        Throwable cursor = error;
        while (cursor != null && cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor == null ? "" : cursor.getMessage();
        if (message == null || message.isBlank()) {
            message = error == null ? "" : error.toString();
        }
        return message == null ? "" : message.trim();
    }

    private static String trimTo(String text, int maxChars) {
        String s = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (maxChars > 0 && s.length() > maxChars) {
            return s.substring(0, maxChars).trim();
        }
        return s;
    }
}
