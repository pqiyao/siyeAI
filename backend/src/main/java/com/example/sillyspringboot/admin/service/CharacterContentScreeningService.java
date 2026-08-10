package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.character.entity.AppCharacter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CharacterContentScreeningService {

    public record ScreeningResult(String level, int hitCount, List<String> flags, String summary) {
        public boolean hasRisk() {
            return hitCount > 0 && !"NONE".equals(level);
        }
    }

    private static final Map<String, List<String>> FLAG_RULES = createRules();

    public ScreeningResult screen(AppCharacter character) {
        if (character == null) {
            return new ScreeningResult("NONE", 0, List.of(), "");
        }
        return screen(
                character.getName(),
                character.getTagline(),
                character.getDescription(),
                character.getBio(),
                character.getPersona(),
                character.getScenario(),
                character.getFirstMessage(),
                character.getMesExample(),
                character.getCreatorNotes(),
                character.getTagsJson()
        );
    }

    public ScreeningResult screen(String... texts) {
        StringBuilder merged = new StringBuilder();
        if (texts != null) {
            for (String text : texts) {
                if (text != null && !text.isBlank()) {
                    if (merged.length() > 0) {
                        merged.append('\n');
                    }
                    merged.append(text.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        if (merged.length() == 0) {
            return new ScreeningResult("NONE", 0, List.of(), "");
        }

        String content = merged.toString();
        Set<String> flags = new LinkedHashSet<>();
        int hits = 0;
        boolean adult = false;
        boolean minor = false;

        for (Map.Entry<String, List<String>> entry : FLAG_RULES.entrySet()) {
            boolean matched = false;
            for (String keyword : entry.getValue()) {
                if (content.contains(keyword)) {
                    hits++;
                    matched = true;
                }
            }
            if (matched) {
                flags.add(entry.getKey());
                adult = adult || "成人向".equals(entry.getKey());
                minor = minor || "未成年/校园".equals(entry.getKey());
            }
        }

        String level = "NONE";
        if (hits > 0) {
            level = "MEDIUM";
        }
        if ((adult && minor) || hits >= 4 || flags.contains("极端暴力") || flags.contains("站外导流")) {
            level = "HIGH";
        }

        return new ScreeningResult(level, hits, new ArrayList<>(flags), buildSummary(level, flags, hits));
    }

    private static String buildSummary(String level, Set<String> flags, int hits) {
        if (hits <= 0 || flags.isEmpty()) {
            return "";
        }
        return "系统初筛命中 " + hits + " 个关键词，风险等级 " + level + "，涉及：" + String.join("、", flags);
    }

    private static Map<String, List<String>> createRules() {
        Map<String, List<String>> rules = new LinkedHashMap<>();
        rules.put("成人向", List.of("性交", "口交", "肛交", "强奸", "调教", "射精", "发情", "乱伦", "足交"));
        rules.put("未成年/校园", List.of("未成年", "幼女", "幼男", "小学生", "初中生", "高中生", "萝莉", "正太", "班主任", "校服", "教室"));
        rules.put("极端暴力", List.of("虐杀", "肢解", "囚禁", "药物控制", "自杀", "杀人分尸"));
        rules.put("站外导流", List.of("vx", "vx号", "微信", "qq", "tg群", "telegram", "联系方式"));
        return rules;
    }
}
