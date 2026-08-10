package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.dto.ConversationMemoryRefreshSnapshot;
import com.example.sillyspringboot.conversation.dto.ExtractedMemoryEntry;
import com.example.sillyspringboot.conversation.dto.StructuredMemoryExtraction;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.integration.sillytavern.StClient;
import com.example.sillyspringboot.integration.sillytavern.StStreamControl;
import com.example.sillyspringboot.integration.sillytavern.StUnavailableException;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatMessage;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ConversationMemoryLlmService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryLlmService.class);

    private final StClient stClient;
    private final AppMessageMapper messageMapper;
    private final MemoryLlmProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConversationMemoryLlmService(StClient stClient, AppMessageMapper messageMapper, MemoryLlmProperties properties) {
        this.stClient = stClient;
        this.messageMapper = messageMapper;
        this.properties = properties;
    }

    public record MemoryRollup(String summaryPreview, int factsCount, String requestId, long durationMs) {
        public MemoryRollup(String summaryPreview, int factsCount) {
            this(summaryPreview, factsCount, null, 0L);
        }
    }

    public Optional<StructuredMemoryExtraction> tryStructuredMemoryExtract(
            long conversationId,
            List<AppConversationMemoryEntry> existingEntries
    ) {
        return tryStructuredMemoryExtract(conversationId, null, existingEntries);
    }

    public Optional<StructuredMemoryExtraction> tryStructuredMemoryExtract(
            long conversationId,
            Long branchId,
            List<AppConversationMemoryEntry> existingEntries
    ) {
        if (!properties.isLlmEnabled()) {
            return Optional.empty();
        }
        List<AppMessage> rows = branchId == null || branchId <= 0
                ? messageMapper.listRecentMemorySourceByConversationAsc(conversationId, Math.max(10, properties.getMaxMessages()))
                : messageMapper.listRecentMemorySourceByConversationBranchAsc(
                        conversationId,
                        branchId,
                        Math.max(10, properties.getMaxMessages())
                );
        List<ConversationMemoryRefreshSnapshot.MessageSnapshot> messages = rows == null
                ? List.of()
                : rows.stream()
                .filter(java.util.Objects::nonNull)
                .map(ConversationMemoryRefreshSnapshot.MessageSnapshot::from)
                .toList();
        List<ConversationMemoryRefreshSnapshot.EntrySnapshot> entries = existingEntries == null
                ? List.of()
                : existingEntries.stream()
                .filter(java.util.Objects::nonNull)
                .map(ConversationMemoryRefreshSnapshot.EntrySnapshot::from)
                .toList();
        return tryStructuredMemoryExtract(conversationId, messages, entries);
    }

    public Optional<StructuredMemoryExtraction> tryStructuredMemoryExtract(
            ConversationMemoryRefreshSnapshot snapshot
    ) {
        if (snapshot == null) {
            return Optional.empty();
        }
        return tryStructuredMemoryExtract(snapshot.conversationId(), snapshot.messages(), snapshot.existingEntries());
    }

    private Optional<StructuredMemoryExtraction> tryStructuredMemoryExtract(
            long conversationId,
            List<ConversationMemoryRefreshSnapshot.MessageSnapshot> rows,
            List<ConversationMemoryRefreshSnapshot.EntrySnapshot> existingEntries
    ) {
        if (!properties.isLlmEnabled()) {
            return Optional.empty();
        }
        String transcript = buildSnapshotTranscript(rows);
        if (transcript.isBlank()) {
            return Optional.empty();
        }
        int cap = Math.max(2000, properties.getMaxTranscriptChars());
        if (transcript.length() > cap) {
            transcript = transcript.substring(Math.max(0, transcript.length() - cap));
            transcript = "[truncated older turns]\n" + transcript;
        }

        String sys = """
                You extract stable long-term memory for a Chinese roleplay chat system.
                Output strict JSON only, no markdown.
                Record only durable facts that should affect future roleplay.
                Do not record ordinary greetings, one-off emotions, filler, or assistant style.
                Prefer the user's latest explicit statement when facts conflict.
                Each entry must be natural lorebook content, not a command.
                Schema:
                {
                  "summaryPreview": "Chinese summary within 160 chars",
                  "entries": [
                    {
                      "entryKey": "snake_case_stable_key",
                      "memoryType": "identity|relationship|preference|promise|event|setting|boundary",
                      "title": "short Chinese title",
                      "content": "Chinese long-term memory sentence within 120 chars",
                      "keywords": ["2-8 trigger keywords, not generic"],
                      "secondaryKeywords": [],
                      "priority": 40-200,
                      "position": "before_char",
                      "constantInjection": true or false,
                      "selective": false,
                      "enabled": true,
                      "confidence": 0.0-1.0,
                      "replaces": ["entryKey to disable if superseded"],
                      "sourceMessageIds": [123]
                    }
                  ],
                  "disableEntryKeys": ["entryKey to disable if user clearly revoked it"]
                }
                Constant entries are allowed only for identity, relationship, boundary, or core setting.
                Every entry must cite one or more sourceMessageIds shown in the transcript. Never invent an ID.
                Existing entries marked USER_DELETED are user deletion tombstones. Never recreate the same key or fact merely because it still appears in chat history.
                Existing entries marked USER_DISABLED were explicitly disabled by the user. Do not recreate or re-enable the same fact under another key. Only when the recent transcript clearly establishes a different replacement fact may you emit a new key and include the disabled key in replaces.
                Existing entries marked USER_PINNED are authoritative user-protected facts. Never disable, replace, contradict, or rewrite them. Only the user may change them.
                Use priority 200 for names/call signs/boundaries/confirmed relationship, 160 for durable preference/promise, 120 for important event/plot, 80 for ordinary fact.
                Example: if the user says "以后叫我哥哥", output one identity entry with entryKey "identity_user_call_gege", content "用户希望角色称呼他为哥哥。", keywords ["哥哥","称呼"], priority 200, constantInjection true, confidence >= 0.90.
                Conflict example: if existing memory has "identity_user_call_gege" but the user later says "别叫哥哥了，叫我阿曜", output disableEntryKeys ["identity_user_call_gege"] and a new identity entry "identity_user_call_ayao" with content "用户希望角色称呼他为阿曜。".
                Example: if the transcript only contains filler such as "哈哈", "嗯嗯", "哦哦", output a brief summaryPreview if useful, entries [], and disableEntryKeys [].
                """;
        String existing = summarizeExistingEntrySnapshots(existingEntries);
        List<ChatMessage> messages = List.of(
                ChatMessage.text("system", sys),
                ChatMessage.text("user", "Existing memory entries:\n" + existing + "\n\nRecent transcript:\n" + transcript)
        );

        String requestId = "mem_struct_" + UUID.randomUUID().toString().replace("-", "");
        ChatGenerateRequest req = new ChatGenerateRequest(
                conversationId,
                "",
                messages,
                requestId,
                true,
                "memory_structured_extract",
                Set.of(),
                "",
                "",
                List.of(),
                "",
                "",
                "",
                List.of(),
                null
        );

        StringBuilder acc = new StringBuilder();
        StStreamControl control = new StStreamControl();
        long startedNanos = System.nanoTime();
        try {
            stClient.streamChatCompletionsGenerate(
                    req,
                    (ChatGenerateChunk c) -> {
                        if (c.delta() != null && !c.delta().isEmpty()) {
                            acc.append(c.delta());
                        }
                    },
                    control
            );
        } catch (StUnavailableException e) {
            log.warn("memory structured llm: st unavailable: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("memory structured llm failed: {}", e.getMessage());
            return Optional.empty();
        }

        long durationMs = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
        return parseStructured(acc.toString()).map(parsed -> new StructuredMemoryExtraction(
                parsed.summaryPreview(),
                parsed.entries(),
                parsed.disableEntryKeys(),
                requestId,
                durationMs,
                parsed.modelOutputEntryCount(),
                parsed.parseRejectedEntryCount()
        ));
    }

    public Optional<MemoryRollup> tryLlmRollup(long conversationId) {
        return tryLlmRollup(conversationId, (Long) null);
    }

    public Optional<MemoryRollup> tryLlmRollup(long conversationId, Long branchId) {
        if (!properties.isLlmEnabled()) {
            return Optional.empty();
        }
        List<AppMessage> rows = branchId == null || branchId <= 0
                ? messageMapper.listRecentMemorySourceByConversationAsc(conversationId, Math.max(10, properties.getMaxMessages()))
                : messageMapper.listRecentMemorySourceByConversationBranchAsc(
                        conversationId,
                        branchId,
                        Math.max(10, properties.getMaxMessages())
                );
        List<ConversationMemoryRefreshSnapshot.MessageSnapshot> messages = rows == null
                ? List.of()
                : rows.stream()
                .filter(java.util.Objects::nonNull)
                .map(ConversationMemoryRefreshSnapshot.MessageSnapshot::from)
                .toList();
        return tryLlmRollup(conversationId, messages);
    }

    public Optional<MemoryRollup> tryLlmRollup(ConversationMemoryRefreshSnapshot snapshot) {
        if (snapshot == null) {
            return Optional.empty();
        }
        return tryLlmRollup(snapshot.conversationId(), snapshot.messages());
    }

    private Optional<MemoryRollup> tryLlmRollup(
            long conversationId,
            List<ConversationMemoryRefreshSnapshot.MessageSnapshot> rows
    ) {
        if (!properties.isLlmEnabled()) {
            return Optional.empty();
        }
        String transcript = buildSnapshotTranscript(rows);
        if (transcript.isBlank()) {
            return Optional.empty();
        }
        int cap = Math.max(2000, properties.getMaxTranscriptChars());
        if (transcript.length() > cap) {
            transcript = transcript.substring(0, cap) + "\n...[truncated]";
        }

        String sys =
                "You summarize Chinese roleplay chats into long-term memory. "
                        + "Read the transcript and output exactly one line using ASCII || separators. "
                        + "Segment 1: a concise Chinese summary within 200 Chinese characters covering relationship, goals, and key facts. "
                        + "Segments 2-9: up to 8 very short Chinese facts, each within 24 Chinese characters. "
                        + "Do not output markdown, quotes, bullets, or extra explanation.";
        List<ChatMessage> messages = List.of(
                ChatMessage.text("system", sys),
                ChatMessage.text("user", "Transcript:\n" + transcript)
        );

        String requestId = "mem_llm_" + UUID.randomUUID().toString().replace("-", "");
        ChatGenerateRequest req = new ChatGenerateRequest(
                conversationId,
                "",
                messages,
                requestId,
                true,
                "memory_rollup",
                Set.of(),
                "",
                "",
                List.of(),
                "",
                "",
                "",
                List.of(),
                null
        );

        StringBuilder acc = new StringBuilder();
        StStreamControl control = new StStreamControl();
        long startedNanos = System.nanoTime();
        try {
            stClient.streamChatCompletionsGenerate(
                    req,
                    (ChatGenerateChunk c) -> {
                        if (c.delta() != null && !c.delta().isEmpty()) {
                            acc.append(c.delta());
                        }
                    },
                    control
            );
        } catch (StUnavailableException e) {
            log.warn("memory llm: st unavailable: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("memory llm failed: {}", e.getMessage());
            return Optional.empty();
        }

        String raw = acc.toString().replace("\r\n", "\n").trim();
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        long durationMs = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
        MemoryRollup parsed = parseRollup(raw);
        return Optional.of(new MemoryRollup(
                parsed.summaryPreview(),
                parsed.factsCount(),
                requestId,
                durationMs
        ));
    }

    private static MemoryRollup parseRollup(String raw) {
        String[] parts = raw.split("\\|\\|");
        String summary = parts[0].trim();
        if (summary.length() > 420) {
            summary = summary.substring(0, 420) + "...";
        }
        int facts = 0;
        List<String> factLines = new ArrayList<>();
        for (int i = 1; i < parts.length && facts < 8; i++) {
            String p = parts[i].trim();
            if (!p.isEmpty()) {
                factLines.add(p);
                facts++;
            }
        }
        if (facts == 0 && parts.length == 1) {
            return new MemoryRollup(summary, 0);
        }
        return new MemoryRollup(summary, facts);
    }

    private Optional<StructuredMemoryExtraction> parseStructured(String raw) {
        String json = extractJsonObject(raw);
        if (json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            String summary = trimTo(root.path("summaryPreview").asText(""), 420);
            List<ExtractedMemoryEntry> entries = new ArrayList<>();
            JsonNode arr = root.path("entries");
            int modelOutputEntryCount = arr.isArray() ? arr.size() : 0;
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    ExtractedMemoryEntry entry = parseEntryNode(n);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            }
            List<String> disables = readStringList(root.path("disableEntryKeys"));
            if (summary.isBlank() && entries.isEmpty() && disables.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new StructuredMemoryExtraction(
                    summary,
                    entries,
                    disables,
                    null,
                    0L,
                    modelOutputEntryCount,
                    Math.max(0, modelOutputEntryCount - entries.size())
            ));
        } catch (Exception e) {
            log.warn("memory structured parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private ExtractedMemoryEntry parseEntryNode(JsonNode n) {
        if (n == null || !n.isObject()) {
            return null;
        }
        String content = trimTo(n.path("content").asText(""), properties.getMaxEntryContentChars());
        if (content.isBlank()) {
            return null;
        }
        return new ExtractedMemoryEntry(
                n.path("entryKey").asText(""),
                n.path("memoryType").asText("event"),
                trimTo(n.path("title").asText(""), 120),
                content,
                readStringList(n.path("keywords")),
                readStringList(n.path("secondaryKeywords")),
                n.path("priority").asInt(100),
                n.path("position").asText("before_char"),
                n.path("constantInjection").asBoolean(false),
                n.path("selective").asBoolean(false),
                !n.has("enabled") || n.path("enabled").asBoolean(true),
                BigDecimal.valueOf(Math.max(0.0, Math.min(1.0, n.path("confidence").asDouble(0.80)))),
                readStringList(n.path("replaces")),
                readLongList(n.path("sourceMessageIds"))
        );
    }

    private static List<Long> readLongList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        LinkedHashSet<Long> out = new LinkedHashSet<>();
        for (JsonNode item : node) {
            if (item == null || !item.canConvertToLong()) {
                continue;
            }
            long value = item.asLong();
            if (value > 0) {
                out.add(value);
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private static List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String s = item == null ? "" : item.asText("").trim();
            if (!s.isBlank()) {
                out.add(s);
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private static String extractJsonObject(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isBlank()) {
            return "";
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return s.substring(start, end + 1);
    }

    private static String summarizeExistingEntries(List<AppConversationMemoryEntry> existingEntries) {
        if (existingEntries == null || existingEntries.isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (AppConversationMemoryEntry e : existingEntries) {
            if (e == null) {
                continue;
            }
            if (e.isManualDeleted()) {
                sb.append("- USER_DELETED ")
                        .append(e.getEntryKey())
                        .append(": ")
                        .append(trimTo(e.getContent(), 160))
                        .append(" (do not recreate)\n");
                count++;
                if (count >= 80) {
                    break;
                }
                continue;
            }
            if (e.isManualDisabled()) {
                sb.append("- USER_DISABLED ")
                        .append(e.getEntryKey())
                        .append(" [")
                        .append(e.getMemoryType())
                        .append("]: ")
                        .append(trimTo(e.getContent(), 160))
                        .append(" (do not recreate or enable under another key)\n");
                count++;
                if (count >= 80) {
                    break;
                }
                continue;
            }
            if (!e.isEnabled()) {
                continue;
            }
            sb.append("- ")
                    .append(e.getEntryKey())
                    .append(" [")
                    .append(e.getMemoryType())
                    .append("]: ")
                    .append(trimTo(e.getContent(), 160))
                    .append('\n');
            count++;
            if (count >= 80) {
                break;
            }
        }
        return sb.length() == 0 ? "(none)" : sb.toString().trim();
    }

    private static String summarizeExistingEntrySnapshots(
            List<ConversationMemoryRefreshSnapshot.EntrySnapshot> existingEntries
    ) {
        if (existingEntries == null || existingEntries.isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (ConversationMemoryRefreshSnapshot.EntrySnapshot entry : existingEntries) {
            if (entry == null) {
                continue;
            }
            if (entry.manualPinned()) {
                sb.append("- USER_PINNED ")
                        .append(entry.entryKey())
                        .append(" [")
                        .append(entry.memoryType())
                        .append("]: ")
                        .append(trimTo(entry.content(), 160))
                        .append(" (authoritative; never disable, replace, contradict, or rewrite)\n");
            } else if (entry.manualDeleted()) {
                sb.append("- USER_DELETED ")
                        .append(entry.entryKey())
                        .append(": ")
                        .append(trimTo(entry.content(), 160))
                        .append(" (do not recreate)\n");
            } else if (entry.manualDisabled()) {
                sb.append("- USER_DISABLED ")
                        .append(entry.entryKey())
                        .append(" [")
                        .append(entry.memoryType())
                        .append("]: ")
                        .append(trimTo(entry.content(), 160))
                        .append(" (do not recreate or enable under another key)\n");
            } else if (entry.enabled()) {
                sb.append("- ")
                        .append(entry.entryKey())
                        .append(" [")
                        .append(entry.memoryType())
                        .append("]: ")
                        .append(trimTo(entry.content(), 160))
                        .append('\n');
            } else {
                continue;
            }
            count++;
            if (count >= 80) {
                break;
            }
        }
        return sb.length() == 0 ? "(none)" : sb.toString().trim();
    }

    private static String trimTo(String text, int maxChars) {
        String s = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (maxChars > 0 && s.length() > maxChars) {
            return s.substring(0, maxChars).trim();
        }
        return s;
    }

    private static String buildTranscript(List<AppMessage> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
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
                if (!isDisplayedRootMessage(m)) {
                    continue;
                }
            } else if (!"user".equalsIgnoreCase(role)) {
                continue;
            }
            String text = m.getContent() == null ? "" : m.getContent().trim();
            if (text.isEmpty()) {
                continue;
            }
            if (text.length() > 800) {
                text = text.substring(0, 800) + "...";
            }
            sb.append("user".equalsIgnoreCase(role) ? "User: " : "AI: ")
                    .append(text)
                    .append('\n');
        }
        return sb.toString().trim();
    }

    private static String buildSnapshotTranscript(
            List<ConversationMemoryRefreshSnapshot.MessageSnapshot> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ConversationMemoryRefreshSnapshot.MessageSnapshot message : rows) {
            if (message == null) {
                continue;
            }
            String status = message.status() == null ? "" : message.status();
            if ("FAILED".equalsIgnoreCase(status) || "DELETED".equalsIgnoreCase(status)) {
                continue;
            }
            String role = message.role() == null ? "" : message.role();
            if ("assistant".equalsIgnoreCase(role)) {
                if ((!"SUCCESS".equalsIgnoreCase(status) && !"STOPPED".equalsIgnoreCase(status))
                        || !isDisplayedRootMessage(message)) {
                    continue;
                }
            } else if (!"user".equalsIgnoreCase(role)) {
                continue;
            }
            String content = message.content() == null ? "" : message.content().trim();
            if (!content.isBlank()) {
                sb.append("[messageId=").append(message.id()).append("] ")
                        .append("user".equalsIgnoreCase(role) ? "User: " : "Assistant: ")
                        .append(content)
                        .append('\n');
            }
        }
        return sb.toString().trim();
    }

    private static boolean isDisplayedRootMessage(
            ConversationMemoryRefreshSnapshot.MessageSnapshot message
    ) {
        String ref = message.stMessageRef();
        if (ref == null || !ref.startsWith("root:")) {
            return true;
        }
        try {
            long rootId = Long.parseLong(ref.substring("root:".length()));
            return message.id() != null && message.id() == rootId;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private static boolean isDisplayedRootMessage(AppMessage m) {
        if (m == null) {
            return false;
        }
        String ref = m.getStMessageRef();
        if (ref == null || !ref.startsWith("root:")) {
            return true;
        }
        try {
            long rootId = Long.parseLong(ref.substring("root:".length()));
            return m.getId() != null && m.getId().longValue() == rootId;
        } catch (Exception ignored) {
            return true;
        }
    }
}
