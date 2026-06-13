package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
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

    public record MemoryRollup(String summaryPreview, int factsCount) {}

    public Optional<StructuredMemoryExtraction> tryStructuredMemoryExtract(
            long conversationId,
            List<AppConversationMemoryEntry> existingEntries
    ) {
        if (!properties.isLlmEnabled()) {
            return Optional.empty();
        }
        List<AppMessage> rows = messageMapper.listRecentByConversationAsc(
                conversationId,
                Math.max(10, properties.getMaxMessages())
        );
        String transcript = buildTranscript(rows);
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
                      "replaces": ["entryKey to disable if superseded"]
                    }
                  ],
                  "disableEntryKeys": ["entryKey to disable if user clearly revoked it"]
                }
                Constant entries are allowed only for identity, relationship, boundary, or core setting.
                Use priority 200 for names/call signs/boundaries/confirmed relationship, 160 for durable preference/promise, 120 for important event/plot, 80 for ordinary fact.
                Example: if the user says "以后叫我哥哥", output one identity entry with entryKey "identity_user_call_gege", content "用户希望角色称呼他为哥哥。", keywords ["哥哥","称呼"], priority 200, constantInjection true, confidence >= 0.90.
                Conflict example: if existing memory has "identity_user_call_gege" but the user later says "别叫哥哥了，叫我阿曜", output disableEntryKeys ["identity_user_call_gege"] and a new identity entry "identity_user_call_ayao" with content "用户希望角色称呼他为阿曜。".
                Example: if the transcript only contains filler such as "哈哈", "嗯嗯", "哦哦", output a brief summaryPreview if useful, entries [], and disableEntryKeys [].
                """;
        String existing = summarizeExistingEntries(existingEntries);
        List<ChatMessage> messages = List.of(
                ChatMessage.text("system", sys),
                ChatMessage.text("user", "Existing memory entries:\n" + existing + "\n\nRecent transcript:\n" + transcript)
        );

        ChatGenerateRequest req = new ChatGenerateRequest(
                conversationId,
                "",
                messages,
                "mem_struct_" + System.currentTimeMillis(),
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

        return parseStructured(acc.toString());
    }

    public Optional<MemoryRollup> tryLlmRollup(long conversationId) {
        if (!properties.isLlmEnabled()) {
            return Optional.empty();
        }
        List<AppMessage> rows = messageMapper.listRecentByConversationAsc(
                conversationId,
                Math.max(10, properties.getMaxMessages())
        );
        String transcript = buildTranscript(rows);
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

        ChatGenerateRequest req = new ChatGenerateRequest(
                conversationId,
                "",
                messages,
                "mem_llm_" + System.currentTimeMillis(),
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
        return Optional.of(parseRollup(raw));
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
            return Optional.of(new StructuredMemoryExtraction(summary, entries, disables));
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
                readStringList(n.path("replaces"))
        );
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
            if (e == null || !e.isEnabled()) {
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
}
