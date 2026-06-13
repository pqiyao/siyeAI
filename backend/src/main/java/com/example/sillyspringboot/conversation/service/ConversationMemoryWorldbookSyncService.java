package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StWorldbookSaveRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        AppConversation conversation = conversationMapper.findById(conversationId);
        Long userId = conversation == null ? null : conversation.getUserId();
        Long characterId = conversation == null ? null : conversation.getCharacterId();
        return buildWorldName(conversationId, userId, characterId);
    }

    public String syncWorldbook(long conversationId) {
        String worldName = resolveWorldName(conversationId);
        List<AppConversationMemoryEntry> enabled = entryMapper.listEnabledByConversationId(conversationId);
        int entryCount = entryMapper.countAllByConversationId(conversationId);
        int enabledCount = enabled == null ? 0 : enabled.size();
        if (enabledCount <= 0) {
            deleteWorldbook(conversationId);
            memoryMapper.updateSyncStatus(conversationId, worldName, entryCount, 0, SYNC_SKIPPED, null);
            return worldName;
        }

        List<AppConversationMemoryEntry> selected = limitEntries(enabled);
        Map<String, Object> data = buildWorldbookData(conversationId, worldName, selected);
        try {
            stAdapter.saveWorldbook(new StWorldbookSaveRequest(worldName, data));
            memoryMapper.updateSyncStatus(conversationId, worldName, entryCount, enabledCount, SYNC_SUCCESS, null);
            return worldName;
        } catch (Exception e) {
            String err = trimTo(rootCauseMessage(e), 512);
            memoryMapper.updateSyncStatus(conversationId, worldName, entryCount, enabledCount, SYNC_FAILED, err);
            log.warn("memory worldbook sync failed conversationId={} worldName={} cause={}", conversationId, worldName, err);
            throw e;
        }
    }

    public void deleteWorldbook(long conversationId) {
        String worldName = resolveWorldName(conversationId);
        try {
            stAdapter.deleteWorldbook(worldName);
        } catch (Exception e) {
            log.warn("memory worldbook delete failed conversationId={} worldName={} cause={}",
                    conversationId, worldName, rootCauseMessage(e));
        }
    }

    private Map<String, Object> buildWorldbookData(long conversationId, String worldName, List<AppConversationMemoryEntry> entries) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", worldName);
        data.put("extensions", Map.of(
                "source", "SillySpringboot",
                "kind", "conversation_memory",
                "conversationId", conversationId,
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

    private static String buildWorldName(long conversationId, Long userId, Long characterId) {
        String seed = conversationId + ":" + (userId == null ? 0 : userId) + ":" + (characterId == null ? 0 : characterId);
        return "jg_memory_conv_" + conversationId + "_" + shortHash(seed, 10);
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
