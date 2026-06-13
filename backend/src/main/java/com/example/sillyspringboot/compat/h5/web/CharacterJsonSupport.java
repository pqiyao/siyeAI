package com.example.sillyspringboot.compat.h5.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** H5 卡片：tags_json / chat_modes_json 解析与筛选 */
public final class CharacterJsonSupport {

    private static final ObjectMapper M = new ObjectMapper();

    private CharacterJsonSupport() {}

    public static List<String> parseStringArrayJson(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            JsonNode n = M.readTree(json);
            if (n.isArray()) {
                for (JsonNode el : n) {
                    if (el.isTextual()) {
                        String s = el.asText();
                        if (s != null && !s.isBlank()) {
                            out.add(s.trim());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static List<Map<String, String>> labelArrayFromTagsJson(String tagsJson) {
        List<Map<String, String>> labels = new ArrayList<>();
        for (String t : parseStringArrayJson(tagsJson)) {
            labels.add(Map.of("code", t));
        }
        return labels;
    }

    /** tags_json 有内容时只按标签匹配；无标签数据时返回 false（由调用方决定是否回退 name/desc） */
    public static boolean tagsJsonMatches(String tagsJson, String needle) {
        if (needle == null || needle.isBlank()) {
            return true;
        }
        List<String> tags = parseStringArrayJson(tagsJson);
        if (tags.isEmpty()) {
            return false;
        }
        String n = needle.trim().toLowerCase(Locale.ROOT);
        for (String t : tags) {
            if (t == null) {
                continue;
            }
            String x = t.toLowerCase(Locale.ROOT);
            if (x.equals(n) || x.contains(n) || n.contains(x)) {
                return true;
            }
        }
        return false;
    }

    public static boolean gameplayMatches(String gameplayType, String needle) {
        if (needle == null || needle.isBlank()) {
            return true;
        }
        if (gameplayType == null || gameplayType.isBlank()) {
            return false;
        }
        String g = gameplayType.trim().toLowerCase(Locale.ROOT);
        String n = needle.trim().toLowerCase(Locale.ROOT);
        return g.equals(n) || g.contains(n) || n.contains(g);
    }

    public static List<Map<String, Object>> chatModesFromJson(String json, List<Map<String, Object>> defaultModes) {
        if (json == null || json.isBlank()) {
            return defaultModes;
        }
        try {
            JsonNode arr = M.readTree(json);
            if (!arr.isArray() || arr.isEmpty()) {
                return defaultModes;
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode el : arr) {
                if (!el.isObject()) {
                    continue;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                putIfText(m, el, "icon");
                putIfText(m, el, "name");
                putIfText(m, el, "sub");
                if (el.has("recommend") && el.get("recommend").isBoolean()) {
                    m.put("recommend", el.get("recommend").asBoolean());
                }
                if (m.containsKey("name")) {
                    out.add(m);
                }
            }
            return out.isEmpty() ? defaultModes : out;
        } catch (Exception e) {
            return defaultModes;
        }
    }

    private static void putIfText(Map<String, Object> m, JsonNode el, String key) {
        if (el.has(key) && el.get(key).isTextual()) {
            m.put(key, el.get(key).asText());
        }
    }
}
