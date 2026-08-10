package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.conversation.entity.AppConversationStBinding;
import com.example.sillyspringboot.integration.sillytavern.StClient;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterDetail;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AppChatCompatibilityService {

    private static final Logger log = LoggerFactory.getLogger(AppChatCompatibilityService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_MARKERS = 24;
    private static final boolean FRONTEND_BRIDGE_ADAPTER_READY = true;
    private static final Pattern MACRO_IDENTIFIER_PATTERN = Pattern.compile(
            "\\{\\{\\s*([a-z_][a-z0-9_.-]*)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> RUNTIME_SAFE_MACROS = Set.of(
            "user",
            "name1",
            "char",
            "name2",
            "lastchatmessage"
    );

    private static final List<Signature> FRONTEND_EXTENSION_SIGNATURES = List.of(
            new Signature("ejs_template", "<%"),
            new Signature("ejs_template", "%>"),
            new Signature("prompt_template_generate_before", "@@generate_before"),
            new Signature("prompt_template_generate_after", "@@generate_after"),
            new Signature("prompt_template_inject", "@inject"),
            new Signature("tavern_helper", "tavernhelper"),
            new Signature("tavern_helper", "setextensionprompt"),
            new Signature("tavern_helper", "extension_prompts"),
            new Signature("frontend_event_generation_after_commands", "generation_after_commands"),
            new Signature("frontend_event_generate_after_data", "generate_after_data"),
            new Signature("frontend_event_chat_completion_settings_ready", "chat_completion_settings_ready"),
            new Signature("frontend_event_worldinfo_entries_loaded", "worldinfo_entries_loaded"),
            new Signature("prompt_template_state", "is_ejs_processed"),
            new Signature("prompt_template_state", "variables_initialized"),
            new Signature("prompt_template_state", "tableeditmatches"),
            new Signature("prompt_template_state", "hash_sheets"),
            new Signature("prompt_template_custom_generation", "js_generation_before_end")
    );
    private static final List<RegexSignature> FRONTEND_WORLD_INFO_PATTERNS = List.of(
            new RegexSignature("world_info_special_position", "\\\"position\\\"\\s*:\\s*(?:2|3|5|6|7)(?:\\D|$)"),
            new RegexSignature("world_info_selective_logic", "\\\"selectivelogic\\\"\\s*:\\s*[1-9]\\d*"),
            new RegexSignature("world_info_sticky", "\\\"sticky\\\"\\s*:\\s*[1-9]\\d*"),
            new RegexSignature("world_info_group", "\\\"group\\\"\\s*:\\s*\\\"\\s*[^\\\"]"),
            new RegexSignature("world_info_group_scoring", "\\\"usegroupscoring\\\"\\s*:\\s*true"),
            new RegexSignature("world_info_vectorized", "\\\"vectorized\\\"\\s*:\\s*true"),
            new RegexSignature("world_info_ignore_budget", "\\\"ignorebudget\\\"\\s*:\\s*true"),
            new RegexSignature("world_info_extra_match_source", "\\\"match(?:personadescription|characterdescription|characterpersonality|characterdepthprompt|scenario|creatornotes)\\\"\\s*:\\s*true"),
            new RegexSignature("world_info_generation_triggers", "\\\"triggers\\\"\\s*:\\s*\\[\\s*(?!])"),
            new RegexSignature("world_info_outlet", "\\\"outletname\\\"\\s*:\\s*\\\"\\s*[^\\\"]")
    );

    private final AppChatProperties chatProperties;
    private final StClient stClient;
    private final ConcurrentHashMap<String, WorldbookProbe> worldbookProbeCache = new ConcurrentHashMap<>();

    public AppChatCompatibilityService(AppChatProperties chatProperties, StClient stClient) {
        this.chatProperties = chatProperties;
        this.stClient = stClient;
    }

    public Decision decideForGeneration(
            Long conversationId,
            AppCharacter character,
            StCharacterDetail stDetail,
            AppConversationStBinding binding,
            List<String> worldNames,
            String runtimePresetBundle
    ) {
        AppChatProperties.Compatibility config = chatProperties.getCompatibility();
        String configuredMode = normalizeMode(config.getMode());
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        LinkedHashSet<String> markers = new LinkedHashSet<>();

        if ("frontend_bridge".equals(configuredMode)) {
            reasons.add("mode_forced_frontend_bridge");
        }

        if (!"runtime".equals(configuredMode)) {
            inspectText("character", characterText(character, stDetail, binding), reasons, markers);
            inspectText("preset", runtimePresetBundle, reasons, markers);
            inspectPresetStructure(runtimePresetBundle, reasons, markers);
            inspectWorldbooks(worldNames, reasons, markers);
        }

        boolean frontendFeaturesDetected = !reasons.isEmpty();
        boolean frontendBridgeRequired = "frontend_bridge".equals(configuredMode);
        String recommendedMode = frontendFeaturesDetected ? "frontend_bridge" : "runtime";
        String effectiveMode;
        if (!frontendBridgeRequired) {
            effectiveMode = "runtime";
        } else if (config.isFrontendBridgeEnabled() && FRONTEND_BRIDGE_ADAPTER_READY) {
            effectiveMode = "frontend_bridge";
        } else if (config.isFallbackToRuntime()) {
            effectiveMode = "runtime_fallback";
        } else {
            effectiveMode = "blocked";
        }

        Decision decision = new Decision(
                conversationId == null ? 0L : conversationId,
                configuredMode,
                recommendedMode,
                effectiveMode,
                frontendBridgeRequired,
                config.isFrontendBridgeEnabled(),
                FRONTEND_BRIDGE_ADAPTER_READY,
                config.isFallbackToRuntime(),
                List.copyOf(reasons),
                limitMarkers(markers)
        );
        logDecision(decision);
        return decision;
    }

    public Snapshot snapshot() {
        AppChatProperties.Compatibility config = chatProperties.getCompatibility();
        return new Snapshot(
                normalizeMode(config.getMode()),
                config.isFrontendBridgeEnabled(),
                FRONTEND_BRIDGE_ADAPTER_READY,
                config.isFallbackToRuntime(),
                Math.max(0, config.getWorldbookProbeCacheSeconds()),
                worldbookProbeCache.size()
        );
    }

    private void inspectWorldbooks(List<String> worldNames, Set<String> reasons, Set<String> markers) {
        if (worldNames == null || worldNames.isEmpty()) {
            return;
        }
        for (String worldName : worldNames) {
            String safeName = worldName == null ? "" : worldName.trim();
            if (safeName.isBlank()) {
                continue;
            }
            WorldbookProbe probe = probeWorldbook(safeName);
            if (probe.needsFrontendBridge()) {
                reasons.add("worldbook:" + safeName);
                for (String marker : probe.markers()) {
                    addMarker(markers, "worldbook:" + safeName + ":" + marker);
                }
            }
        }
    }

    private WorldbookProbe probeWorldbook(String worldName) {
        String cacheKey = worldName.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        WorldbookProbe cached = worldbookProbeCache.get(cacheKey);
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached;
        }
        WorldbookProbe next;
        try {
            String raw = stClient.readWorldbookRaw(worldName);
            LinkedHashSet<String> markers = new LinkedHashSet<>();
            inspectMarkers(raw, markers);
            inspectWorldInfoPatterns(raw, markers);
            next = new WorldbookProbe(!markers.isEmpty(), limitMarkers(markers), "", nextProbeExpiry(now));
        } catch (Exception ex) {
            log.debug("compatibility worldbook probe skipped worldName={} cause={}", worldName, rootCauseMessage(ex));
            next = new WorldbookProbe(false, List.of(), rootCauseMessage(ex), nextProbeExpiry(now));
        }
        worldbookProbeCache.put(cacheKey, next);
        return next;
    }

    private void inspectText(String source, String text, Set<String> reasons, Set<String> markers) {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        inspectMarkers(text, found);
        if (found.isEmpty()) {
            return;
        }
        reasons.add(source);
        for (String marker : found) {
            addMarker(markers, source + ":" + marker);
        }
    }

    private void inspectMarkers(String text, Set<String> markers) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (Signature signature : FRONTEND_EXTENSION_SIGNATURES) {
            if (lower.contains(signature.needle())) {
                addMarker(markers, signature.name());
            }
        }
        Matcher macroMatcher = MACRO_IDENTIFIER_PATTERN.matcher(text);
        while (macroMatcher.find()) {
            String identifier = macroMatcher.group(1).toLowerCase(Locale.ROOT);
            if (!RUNTIME_SAFE_MACROS.contains(identifier)) {
                addMarker(markers, "advanced_macro:" + identifier);
            }
        }
    }

    private void inspectWorldInfoPatterns(String text, Set<String> markers) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        for (RegexSignature signature : FRONTEND_WORLD_INFO_PATTERNS) {
            if (signature.pattern().matcher(text).find()) {
                addMarker(markers, signature.name());
            }
        }
    }

    private void inspectPresetStructure(String rawBundle, Set<String> reasons, Set<String> markers) {
        if (!StringUtils.hasText(rawBundle)) {
            return;
        }
        try {
            JsonNode root = JSON.readTree(rawBundle);
            JsonNode generation = root.path("generation").isObject() ? root.path("generation") : root;
            JsonNode settings = generation.path("oai_settings").isObject()
                    ? generation.path("oai_settings")
                    : generation;
            LinkedHashSet<String> found = new LinkedHashSet<>();
            JsonNode prompts = settings.path("prompts");
            if (prompts.isArray()) {
                for (JsonNode prompt : prompts) {
                    String role = prompt.path("role").asText("system").trim().toLowerCase(Locale.ROOT);
                    if (!role.isBlank() && !"system".equals(role)) {
                        addMarker(found, "prompt_manager_role:" + role);
                    }
                    if (prompt.path("injection_position").asInt(0) == 1) {
                        addMarker(found, "prompt_manager_absolute_injection");
                    }
                    if (prompt.path("forbid_overrides").asBoolean(false)) {
                        addMarker(found, "prompt_manager_forbid_overrides");
                    }
                    JsonNode triggers = prompt.path("injection_trigger");
                    if (triggers.isArray() && !triggers.isEmpty()) {
                        addMarker(found, "prompt_manager_generation_trigger");
                    }
                }
            }

            JsonNode promptOrders = settings.path("prompt_order");
            if (promptOrders.isArray()) {
                for (JsonNode promptOrder : promptOrders) {
                    String characterId = promptOrder.path("character_id").asText("").trim();
                    if (!characterId.isBlank()
                            && !"100000".equals(characterId)
                            && !"100001".equals(characterId)) {
                        addMarker(found, "prompt_manager_character_order");
                    }
                }
            }

            if (!found.isEmpty()) {
                reasons.add("preset");
                for (String marker : found) {
                    addMarker(markers, "preset:" + marker);
                }
            }
        } catch (Exception ex) {
            log.debug("compatibility preset structure probe skipped cause={}", rootCauseMessage(ex));
        }
    }

    private String characterText(AppCharacter character, StCharacterDetail detail, AppConversationStBinding binding) {
        List<String> parts = new ArrayList<>();
        if (character != null) {
            parts.add(character.getName());
            parts.add(character.getDescription());
            parts.add(character.getBio());
            parts.add(character.getPersona());
            parts.add(character.getScenario());
            parts.add(character.getFirstMessage());
            parts.add(character.getAlternateGreetingsJson());
            parts.add(character.getMesExample());
            parts.add(character.getSystemPrompt());
            parts.add(character.getPostHistoryInstructions());
            parts.add(character.getCreatorNotes());
            parts.add(character.getStExtraJson());
            parts.add(character.getStWorldNamesJson());
        }
        if (detail != null) {
            parts.add(detail.name());
            parts.add(detail.description());
            parts.add(detail.scenario());
            parts.add(detail.firstMes());
            parts.add(detail.personality());
            parts.add(detail.mesExample());
            parts.add(detail.systemPrompt());
            parts.add(detail.postHistoryInstructions());
            parts.add(detail.creatorNotes());
            parts.add(detail.embeddedCharacterBookJson());
            parts.add(detail.rawJson());
        }
        if (binding != null) {
            parts.add(binding.getStWorldNamesJson());
            parts.add(binding.getStDisplayNameOverride());
        }
        return String.join("\n", parts.stream().filter(StringUtils::hasText).toList());
    }

    private long nextProbeExpiry(long now) {
        int seconds = Math.max(0, chatProperties.getCompatibility().getWorldbookProbeCacheSeconds());
        return now + seconds * 1000L;
    }

    private static List<String> limitMarkers(Set<String> markers) {
        if (markers == null || markers.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(Math.min(markers.size(), MAX_MARKERS));
        for (String marker : markers) {
            if (out.size() >= MAX_MARKERS) {
                break;
            }
            out.add(marker);
        }
        return List.copyOf(out);
    }

    private static void addMarker(Set<String> markers, String marker) {
        if (markers.size() < MAX_MARKERS && StringUtils.hasText(marker)) {
            markers.add(marker);
        }
    }

    private void logDecision(Decision decision) {
        if (!decision.frontendBridgeRequired()) {
            log.debug("chat.compatibility conversationId={} mode={} effective=runtime",
                    decision.conversationId(), decision.configuredMode());
            return;
        }
        log.info("chat.compatibility conversationId={} configured={} recommended={} effective={} bridgeEnabled={} bridgeReady={} fallback={} reasons={} markers={}",
                decision.conversationId(),
                decision.configuredMode(),
                decision.recommendedMode(),
                decision.effectiveMode(),
                decision.frontendBridgeEnabled(),
                decision.frontendBridgeReady(),
                decision.fallbackToRuntime(),
                decision.reasons(),
                decision.markers());
    }

    private static String normalizeMode(String raw) {
        String mode = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "runtime", "frontend_bridge", "auto" -> mode;
            case "frontend", "bridge", "high_compatibility", "high" -> "frontend_bridge";
            default -> "auto";
        };
    }

    private static String rootCauseMessage(Throwable ex) {
        Throwable cur = ex;
        while (cur != null && cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur == null || cur.getMessage() == null ? "" : cur.getMessage();
    }

    private record Signature(String name, String needle) {
    }

    private record RegexSignature(String name, Pattern pattern) {
        private RegexSignature(String name, String regex) {
            this(name, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
        }
    }

    private record WorldbookProbe(
            boolean needsFrontendBridge,
            List<String> markers,
            String error,
            long expiresAtMillis
    ) {
    }

    public record Decision(
            long conversationId,
            String configuredMode,
            String recommendedMode,
            String effectiveMode,
            boolean frontendBridgeRequired,
            boolean frontendBridgeEnabled,
            boolean frontendBridgeReady,
            boolean fallbackToRuntime,
            List<String> reasons,
            List<String> markers
    ) {
    }

    public record Snapshot(
            String mode,
            boolean frontendBridgeEnabled,
            boolean frontendBridgeReady,
            boolean fallbackToRuntime,
            int worldbookProbeCacheSeconds,
            int worldbookProbeCacheSize
    ) {
    }
}
