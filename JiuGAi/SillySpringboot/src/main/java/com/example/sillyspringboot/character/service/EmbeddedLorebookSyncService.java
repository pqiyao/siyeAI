package com.example.sillyspringboot.character.service;

import com.example.sillyspringboot.character.entity.AppLorebookEntry;
import com.example.sillyspringboot.character.mapper.AppLorebookEntryMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class EmbeddedLorebookSyncService {

    public static final String SOURCE_EMBEDDED_CHARACTER_BOOK = "embedded_character_book";

    private static final Logger log = LoggerFactory.getLogger(EmbeddedLorebookSyncService.class);
    private static final int MAX_KEYWORDS_CSV = 2048;
    private static final int DEFAULT_SCAN_DEPTH = 4;

    private final AppLorebookEntryMapper lorebookEntryMapper;
    private final ObjectMapper objectMapper;

    public EmbeddedLorebookSyncService(AppLorebookEntryMapper lorebookEntryMapper, ObjectMapper objectMapper) {
        this.lorebookEntryMapper = lorebookEntryMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int replaceEmbeddedLorebook(long characterId, String embeddedCharacterBookJson) {
        if (characterId <= 0) {
            return 0;
        }
        lorebookEntryMapper.deleteImportedByCharacterId(characterId);
        String raw = trimToEmpty(embeddedCharacterBookJson);
        if (raw.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            List<JsonNode> entries = extractEntries(root);
            int imported = 0;
            for (JsonNode entryNode : entries) {
                if (!entryNode.isObject()) {
                    continue;
                }
                AppLorebookEntry entry = toEntry(characterId, entryNode);
                if (entry == null) {
                    continue;
                }
                lorebookEntryMapper.insert(entry);
                imported++;
            }
            return imported;
        } catch (Exception e) {
            log.warn("Failed to sync embedded character_book for characterId={}: {}", characterId, e.toString());
            return 0;
        }
    }

    @Transactional
    public int deleteAllForCharacter(long characterId) {
        if (characterId <= 0) {
            return 0;
        }
        return lorebookEntryMapper.deleteByCharacterId(characterId);
    }

    private List<JsonNode> extractEntries(JsonNode root) {
        List<JsonNode> entries = new ArrayList<>();
        if (root == null || root.isMissingNode() || root.isNull()) {
            return entries;
        }
        if (root.isArray()) {
            root.forEach(entries::add);
            return entries;
        }
        JsonNode entriesNode = root.get("entries");
        if (entriesNode == null || entriesNode.isNull()) {
            return entries;
        }
        if (entriesNode.isArray()) {
            entriesNode.forEach(entries::add);
            return entries;
        }
        if (entriesNode.isObject()) {
            Iterator<JsonNode> values = entriesNode.elements();
            while (values.hasNext()) {
                entries.add(values.next());
            }
        }
        return entries;
    }

    private AppLorebookEntry toEntry(long characterId, JsonNode node) {
        String content = firstText(node, "content", "entry", "text");
        if (content.isBlank()) {
            return null;
        }
        AppLorebookEntry row = new AppLorebookEntry();
        row.setCharacterId(characterId);
        row.setKeywordsCsv(buildKeywordsCsv(node));
        row.setContent(content);
        row.setPriority(firstInt(node, 0, "priority", "order", "insertion_order", "insertionOrder"));
        row.setConstantInjection(firstBoolean(node, false, "constant", "constant_injection", "constantInjection"));
        row.setScanDepth(firstInt(node, DEFAULT_SCAN_DEPTH, "scan_depth", "scanDepth", "depth"));
        row.setEnabled(isEnabled(node));
        row.setSource(SOURCE_EMBEDDED_CHARACTER_BOOK);
        row.setRawEntryJson(writeRaw(node));
        return row;
    }

    private String buildKeywordsCsv(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        collectTextValues(values, node.get("keys"));
        collectTextValues(values, node.get("key"));
        collectTextValues(values, node.get("secondary_keys"));
        collectTextValues(values, node.get("secondaryKeys"));
        collectTextValues(values, node.get("keysecondary"));
        String joined = String.join(",", values);
        if (joined.length() <= MAX_KEYWORDS_CSV) {
            return joined;
        }
        return joined.substring(0, MAX_KEYWORDS_CSV);
    }

    private void collectTextValues(Set<String> out, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectTextValues(out, item);
            }
            return;
        }
        String value = node.isTextual() ? node.asText() : String.valueOf(node);
        for (String part : value.split(",")) {
            String trimmed = trimToEmpty(part);
            if (!trimmed.isBlank()) {
                out.add(trimmed);
            }
        }
    }

    private boolean isEnabled(JsonNode node) {
        boolean enabled = firstBoolean(node, true, "enabled");
        boolean disabled = firstBoolean(node, false, "disable", "disabled");
        return enabled && !disabled;
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) {
                continue;
            }
            String text = value.isTextual() ? value.asText() : value.asText("");
            if (!trimToEmpty(text).isBlank()) {
                return text.trim();
            }
        }
        return "";
    }

    private int firstInt(JsonNode node, int fallback, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isInt() || value.isLong()) {
                return value.asInt(fallback);
            }
            try {
                return Integer.parseInt(value.asText("").trim());
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }

    private boolean firstBoolean(JsonNode node, boolean fallback, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            if (value.isNumber()) {
                return value.asInt() != 0;
            }
            String text = value.asText("").trim();
            if ("true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text) || "0".equals(text) || "no".equalsIgnoreCase(text)) {
                return false;
            }
        }
        return fallback;
    }

    private String writeRaw(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
