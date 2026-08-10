package com.example.sillyspringboot.character.service;

import com.example.sillyspringboot.character.entity.AppCharacter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class CharacterPublicProfileService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int SUMMARY_LIMIT = 180;
    private static final int TAG_LIMIT = 12;
    private static final int TAG_TEXT_LIMIT = 24;
    private static final int LONG_CONTEXT_CHAR_LIMIT = 14000;
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern MACRO = Pattern.compile("\\{\\{[^}]{1,120}}}");
    private static final Pattern ST_BLOCK = Pattern.compile("\\[(?:character|scenario|personality|system|prompt|example|mes_example)[^\\]]*]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROLE_LABEL = Pattern.compile("(?i)\\b(?:description|personality|scenario|first_mes|first message|system prompt|post history instructions|creator notes|mes_example)\\s*[:：]");
    private static final Pattern TEMPLATE_TOKEN = Pattern.compile("(?i)(\\{\\{\\s*(?:char|user|random|slot|input)[^}]*}}|<START>|<BOT>|<USER>|@@\\w+)");
    private static final Pattern VARIABLE_TOKEN = Pattern.compile("\\{\\{[^}]+}}|\\[[a-zA-Z_][a-zA-Z0-9_]{1,40}\\([^\\]]*\\)]");
    private static final Pattern RELATION_HINT = Pattern.compile(
            "(?:你|您|玩家|用户|主人|恋人|爱人|伴侣|朋友|同学|同事|室友|家人|父亲|母亲|哥哥|姐姐|弟弟|妹妹|"
                    + "上司|下属|老师|学生|导师|徒弟|敌人|对手|搭档|奴隶|奴仆|仆人|雇主|客户|囚犯|俘虏|"
                    + "追随者|守护者|青梅竹马|陌生人|\\buser\\b)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RELATION_SENTENCE_BOUNDARY = Pattern.compile("(?<=[。！？!?；;])|[\\r\\n]+");
    private static final Pattern MINOR_HINT = Pattern.compile(
            "(?:未成年|幼女|幼男|儿童|小学生|初中生|(?<!\\d)(?:[0-9]|1[0-7])\\s*岁|underage|minor|child|schoolgirl|schoolboy)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SEXUAL_HINT = Pattern.compile(
            "(?:裸体|裸露|乳房|乳头|玉乳|雪峰|阴道|阴茎|小穴|性器|性交|做爱|精液|高潮|处女|调教|性奴|强奸|猥亵|下体|私处|"
                    + "nude|naked|breast|vagina|penis|sexual|orgasm)",
            Pattern.CASE_INSENSITIVE
    );

    public void apply(AppCharacter row) {
        if (row == null) {
            return;
        }
        PublicProfile profile = build(row);
        row.setPublicSummary(profile.publicSummary());
        row.setPublicTagsJson(toJson(profile.publicTags()));
        row.setPublicWarningsJson(toJson(profile.publicWarnings()));
        row.setHealthScore(profile.healthScore());
        row.setHealthIssuesJson(toJson(profile.healthIssues()));
    }

    public PublicProfile build(AppCharacter row) {
        if (row == null) {
            return new PublicProfile("", List.of(), List.of(), 0, List.of("missing_character"));
        }
        List<String> warnings = new ArrayList<>();
        List<String> healthIssues = new ArrayList<>();

        String rawSummary = firstNonBlank(row.getTagline(), row.getBio(), row.getDescription());
        String publicSummary = cleanPublicText(rawSummary, SUMMARY_LIMIT);
        if (publicSummary.isBlank()) {
            publicSummary = fallbackSummary(row);
        }
        if (looksPromptLike(rawSummary) || containsTemplateToken(rawSummary)) {
            warnings.add("prompt_trace_removed");
        }

        List<String> publicTags = cleanTags(row.getTagsJson(), warnings);
        int healthScore = score(row, warnings, healthIssues);
        return new PublicProfile(publicSummary, publicTags, warnings, healthScore, healthIssues);
    }

    public String cleanPublicSection(String value, int maxLength) {
        if (containsSexualMinorContent(value)) {
            return "";
        }
        int safeLimit = Math.max(40, Math.min(800, maxLength));
        String cleaned = cleanPublicText(value, safeLimit);
        if (cleaned.isBlank() || looksPromptLike(cleaned) || containsTemplateToken(cleaned)) {
            return "";
        }
        return cleaned;
    }

    public String buildRelationshipHook(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (containsSexualMinorContent(value)) {
                continue;
            }
            String source = normalizeRelationshipSource(value);
            if (source.isBlank()) {
                continue;
            }
            for (String sentence : RELATION_SENTENCE_BOUNDARY.split(source)) {
                String candidate = sentence == null ? "" : sentence.trim();
                if (candidate.isBlank() || !RELATION_HINT.matcher(candidate).find()) {
                    continue;
                }
                String cleaned = cleanPublicSection(candidate, 320);
                if (!cleaned.isBlank()) {
                    return cleaned;
                }
            }
        }
        return "";
    }

    public boolean containsSexualMinorContent(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return MINOR_HINT.matcher(value).find() && SEXUAL_HINT.matcher(value).find();
    }

    private int score(AppCharacter row, List<String> warnings, List<String> issues) {
        int score = 100;
        if (isBlank(row.getAvatarUrl()) && isBlank(row.getCoverUrl()) && isBlank(row.getStAvatarUrl())) {
            score -= 20;
            issues.add("missing_avatar");
        }
        if (isBlank(row.getFirstMessage())) {
            score -= 14;
            issues.add("missing_opening_message");
        }
        if (isBlank(row.getPersona()) && isBlank(row.getDescription()) && isBlank(row.getBio())) {
            score -= 18;
            issues.add("missing_personality");
        }
        if (isBlank(row.getScenario())) {
            score -= 8;
            issues.add("missing_scenario");
        }
        if (isBlank(row.getTagline()) && isBlank(row.getBio()) && isBlank(row.getDescription())) {
            score -= 10;
            issues.add("missing_public_summary");
        }
        if (cleanTags(row.getTagsJson(), new ArrayList<>()).isEmpty()) {
            score -= 8;
            issues.add("missing_public_tags");
        }
        String all = joinText(
                row.getDescription(),
                row.getBio(),
                row.getPersona(),
                row.getScenario(),
                row.getFirstMessage(),
                row.getMesExample(),
                row.getSystemPrompt(),
                row.getPostHistoryInstructions(),
                row.getCreatorNotes()
        );
        if (all.length() > LONG_CONTEXT_CHAR_LIMIT) {
            score -= 12;
            issues.add("very_long_context");
        }
        if (VARIABLE_TOKEN.matcher(all).find()) {
            String cleaned = MACRO.matcher(all).replaceAll("").trim();
            if (VARIABLE_TOKEN.matcher(cleaned).find() || containsBrokenVariable(all)) {
                score -= 10;
                issues.add("variable_format_review");
            }
        }
        if (containsSensitiveHint(all)) {
            score -= 16;
            issues.add("sensitive_content_review");
            if (!warnings.contains("sensitive_content_review")) {
                warnings.add("sensitive_content_review");
            }
        }
        return Math.max(0, Math.min(100, score));
    }

    private static String cleanPublicText(String value, int max) {
        String text = value == null ? "" : value;
        text = HTML_TAG.matcher(text).replaceAll(" ");
        text = MACRO.matcher(text).replaceAll(" ");
        text = ST_BLOCK.matcher(text).replaceAll(" ");
        text = ROLE_LABEL.matcher(text).replaceAll(" ");
        text = text.replaceAll("(?m)^\\s*(?:#|//|---|\\*\\*)\\s*", " ");
        text = text.replaceAll("[\\r\\n\\t]+", " ");
        text = text.replaceAll("\\s+", " ").trim();
        text = stripPromptResidue(text);
        if (text.length() > max) {
            text = text.substring(0, max).replaceAll("\\s+$", "") + "...";
        }
        return text;
    }

    private static String stripPromptResidue(String text) {
        String value = text == null ? "" : text.trim();
        int macro = value.indexOf("{{");
        if (macro >= 0) {
            value = value.substring(0, macro).trim();
        }
        int stBlock = value.toLowerCase(Locale.ROOT).indexOf("[character(");
        if (stBlock >= 0) {
            value = value.substring(0, stBlock).trim();
        }
        return value.replaceAll("^[\\s|/,:：，。-]+|[\\s|/,:：，。-]+$", "").trim();
    }

    private static String normalizeRelationshipSource(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replaceAll("(?i)\\{\\{\\s*user\\s*}}", "你")
                .replaceAll("(?i)\\{\\{\\s*char\\s*}}", "角色")
                .replaceAll("(?i)<USER>", "你")
                .replaceAll("(?i)<BOT>", "角色");
    }

    private List<String> cleanTags(String tagsJson, List<String> warnings) {
        List<String> source = parseTags(tagsJson);
        Set<String> out = new LinkedHashSet<>();
        for (String item : source) {
            String tag = cleanPublicText(item, TAG_TEXT_LIMIT).replaceAll("[#\\[\\]{}<>]", "").trim();
            if (tag.isBlank()) {
                continue;
            }
            if (looksPromptLike(tag) || containsTemplateToken(tag)) {
                if (!warnings.contains("tag_prompt_trace_removed")) {
                    warnings.add("tag_prompt_trace_removed");
                }
                continue;
            }
            out.add(tag);
            if (out.size() >= TAG_LIMIT) {
                break;
            }
        }
        return new ArrayList<>(out);
    }

    private List<String> parseTags(String tagsJson) {
        String raw = tagsJson == null ? "" : tagsJson.trim();
        if (raw.isBlank()) {
            return List.of();
        }
        try {
            List<?> list = JSON.readValue(raw, List.class);
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    out.add(String.valueOf(item));
                }
            }
            return out;
        } catch (Exception ignored) {
            String[] parts = raw.split("[,，/|]");
            List<String> out = new ArrayList<>();
            for (String part : parts) {
                if (part != null && !part.isBlank()) {
                    out.add(part.trim());
                }
            }
            return out;
        }
    }

    private static String fallbackSummary(AppCharacter row) {
        String gameplay = cleanPublicText(row.getGameplayType(), 32);
        String tag = cleanPublicText(row.getOccupationLabel(), 32);
        if (!gameplay.isBlank() && !tag.isBlank()) {
            return gameplay + " · " + tag;
        }
        if (!gameplay.isBlank()) {
            return gameplay;
        }
        if (!tag.isBlank()) {
            return tag;
        }
        return "适合沉浸式角色扮演的角色卡。";
    }

    private static boolean looksPromptLike(String value) {
        String text = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return text.contains("{{char}}")
                || text.contains("{{user}}")
                || text.contains("[character(")
                || text.contains("system prompt")
                || text.contains("post_history")
                || text.contains("first_mes")
                || text.contains("mes_example");
    }

    private static boolean containsTemplateToken(String value) {
        return value != null && TEMPLATE_TOKEN.matcher(value).find();
    }

    private static boolean containsBrokenVariable(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        long open = value.chars().filter(ch -> ch == '{').count();
        long close = value.chars().filter(ch -> ch == '}').count();
        return Math.abs(open - close) >= 2;
    }

    private static boolean containsSensitiveHint(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String text = value.toLowerCase(Locale.ROOT);
        return text.contains("未成年")
                || text.contains("幼女")
                || text.contains("幼男")
                || text.contains("小学生")
                || text.contains("初中生")
                || text.contains("强奸")
                || text.contains("自杀")
                || text.contains("肢解")
                || text.contains("telegram")
                || text.contains("微信")
                || text.contains("联系方式");
    }

    private static String joinText(String... values) {
        StringBuilder sb = new StringBuilder();
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(value);
        }
        return sb.toString();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String toJson(List<String> values) {
        try {
            return JSON.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record PublicProfile(
            String publicSummary,
            List<String> publicTags,
            List<String> publicWarnings,
            int healthScore,
            List<String> healthIssues
    ) {
    }
}
