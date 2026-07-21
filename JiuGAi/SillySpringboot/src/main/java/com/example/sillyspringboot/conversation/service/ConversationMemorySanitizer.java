package com.example.sillyspringboot.conversation.service;

import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.dto.ExtractedMemoryEntry;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Component
public class ConversationMemorySanitizer {

    private static final List<String> VALID_TYPES = List.of(
            "identity", "relationship", "preference", "promise", "event", "setting", "boundary"
    );
    private static final List<String> GENERIC_KEYWORDS = List.of(
            "你", "我", "他", "她", "它", "今天", "明天", "昨天", "现在", "刚才", "以后",
            "聊天", "说话", "回复", "用户", "角色", "事情", "感觉", "内容", "消息", "对话"
    );
    private static final List<String> TRIVIAL_CONTENTS = List.of(
            "哈", "哈哈", "哈哈哈", "嗯", "嗯嗯", "嗯嗯嗯", "哦", "哦哦", "哦哦哦",
            "啊", "啊啊", "嘿", "嘿嘿", "你好", "早安", "晚安", "谢谢", "好的", "知道了", "收到"
    );

    private final MemoryLlmProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConversationMemorySanitizer(MemoryLlmProperties properties) {
        this.properties = properties;
    }

    public AppConversationMemoryEntry toEntity(long conversationId, ExtractedMemoryEntry src, Long fromId, Long toId) {
        if (src == null) {
            return null;
        }
        String content = normalizeText(src.content());
        if (content.isBlank()) {
            return null;
        }
        if (isTrivialContent(content)) {
            return null;
        }
        content = trimTo(content, Math.max(80, properties.getMaxEntryContentChars()));
        String type = normalizeType(src.memoryType());
        List<String> keywords = sanitizeKeywords(src.keywords(), content);
        boolean constant = allowConstant(type, src.constantInjection());
        if (!constant && keywords.isEmpty()) {
            keywords = fallbackKeywords(content);
        }
        if (!constant && keywords.isEmpty()) {
            return null;
        }

        AppConversationMemoryEntry e = new AppConversationMemoryEntry();
        e.setConversationId(conversationId);
        e.setEntryKey(normalizeEntryKey(src.entryKey(), type, content));
        e.setMemoryType(type);
        e.setTitle(trimTo(normalizeText(src.title()), 120));
        e.setContent(content);
        e.setKeywordsJson(toJson(keywords));
        e.setSecondaryKeywordsJson(toJson(sanitizeKeywords(src.secondaryKeywords(), "")));
        e.setPriority(clampPriority(src.priority(), type, constant));
        e.setPosition("before_char");
        e.setConstantInjection(constant);
        e.setSelective(src.selective() && e.getSecondaryKeywordsJson() != null && !"[]".equals(e.getSecondaryKeywordsJson()));
        BigDecimal confidence = sanitizeConfidence(src.confidence());
        e.setEnabled(src.enabled() && confidence.doubleValue() >= 0.55);
        e.setConfidence(confidence);
        e.setSourceMessageFromId(fromId);
        e.setSourceMessageToId(toId);
        return e;
    }

    public List<String> sanitizeDisableKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String key : keys) {
            String k = normalizeKeyToken(key);
            if (!k.isBlank()) {
                out.add(k);
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    public List<String> sanitizeReplaceKeys(ExtractedMemoryEntry entry) {
        return entry == null ? List.of() : sanitizeDisableKeys(entry.replaces());
    }

    public List<String> readKeywords(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<?> raw = objectMapper.readValue(json, List.class);
            List<String> out = new ArrayList<>();
            for (Object item : raw) {
                String s = normalizeText(item == null ? "" : String.valueOf(item));
                if (!s.isBlank()) {
                    out.add(s);
                }
            }
            return out.isEmpty() ? List.of() : List.copyOf(out);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<String> sanitizeKeywords(List<String> raw, String content) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw != null) {
            for (String item : raw) {
                String s = normalizeText(item);
                if (isUsefulKeyword(s)) {
                    out.add(trimTo(s, 24));
                }
                if (out.size() >= Math.max(2, properties.getMaxKeywords())) {
                    break;
                }
            }
        }
        if (out.isEmpty() && content != null && !content.isBlank()) {
            out.addAll(fallbackKeywords(content));
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private static boolean isUsefulKeyword(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        String value = s.trim();
        if (value.length() > 32) {
            return false;
        }
        if (GENERIC_KEYWORDS.contains(value)) {
            return false;
        }
        return value.length() >= 2 || value.matches("[A-Za-z0-9]{2,}");
    }

    private static boolean isTrivialContent(String raw) {
        String value = normalizeText(raw)
                .replaceAll("[\\p{Punct}，。！？、；：~～\\s]+", "")
                .toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return true;
        }
        if (TRIVIAL_CONTENTS.contains(value)) {
            return true;
        }
        return value.length() <= 8 && value.matches("[哈嗯哦啊嘿]+");
    }

    private static List<String> fallbackKeywords(String content) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String s = content == null ? "" : content;
        String[] quoted = s.split("[“”\"'「」『』，。；、\\s]+");
        for (String token : quoted) {
            String t = normalizeText(token);
            if (isUsefulKeyword(t)) {
                out.add(trimTo(t, 16));
            }
            if (out.size() >= 4) {
                break;
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private static boolean allowConstant(String type, boolean requested) {
        if (!requested) {
            return false;
        }
        return "identity".equals(type)
                || "relationship".equals(type)
                || "boundary".equals(type)
                || "setting".equals(type);
    }

    private static int clampPriority(int raw, String type, boolean constant) {
        int value = raw <= 0 ? defaultPriority(type) : raw;
        value = Math.max(40, Math.min(200, value));
        if (constant) {
            value = Math.max(value, 160);
        }
        return value;
    }

    private static int defaultPriority(String type) {
        return switch (type) {
            case "identity", "relationship", "boundary" -> 200;
            case "preference", "promise", "setting" -> 160;
            case "event" -> 120;
            default -> 100;
        };
    }

    private static String normalizeType(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return VALID_TYPES.contains(value) ? value : "event";
    }

    private static String normalizeEntryKey(String raw, String type, String content) {
        String key = normalizeKeyToken(raw);
        if (!key.isBlank()) {
            return trimTo(key, 120);
        }
        int hash = Math.abs(content == null ? 0 : content.hashCode());
        return trimTo(type + "_" + Integer.toHexString(hash), 120);
    }

    private static String normalizeKeyToken(String raw) {
        String s = normalizeText(raw);
        if (s.isBlank()) {
            return "";
        }
        s = Normalizer.normalize(s, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-\\u4e00-\\u9fa5]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        return s;
    }

    private static BigDecimal sanitizeConfidence(BigDecimal confidence) {
        if (confidence == null) {
            return BigDecimal.valueOf(0.80);
        }
        double value = Math.max(0.0, Math.min(1.0, confidence.doubleValue()));
        return BigDecimal.valueOf(value);
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static String normalizeText(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
    }

    private static String trimTo(String raw, int maxChars) {
        String s = normalizeText(raw);
        if (maxChars > 0 && s.length() > maxChars) {
            return s.substring(0, maxChars).trim();
        }
        return s;
    }
}
