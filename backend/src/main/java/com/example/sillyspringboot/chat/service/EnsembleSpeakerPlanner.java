package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.StStreamControl;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatMessage;
import com.example.sillyspringboot.integration.sillytavern.dto.UserModelOverride;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Service
public class EnsembleSpeakerPlanner {
    private static final Logger log = LoggerFactory.getLogger(EnsembleSpeakerPlanner.class);
    private static final int HISTORY_LIMIT = 10;
    private static final int MAX_OUTPUT_CHARS = 4096;
    private static final long TIMEOUT_SECONDS = 12;
    private static final String SYSTEM_PROMPT = """
            You are a fast speaker-selection planner for a multi-character roleplay turn.
            Select who should speak; do not write dialogue or narration.
            Use only roster tokens supplied by the user. Prefer silence over forcing every character to speak.
            Return JSON only:
            {"requiredSpeakers":["M1"],"preferredSpeakers":["M2"],"orderHint":["M1","M2"],"maxSegments":4,"narrationAllowed":true}
            Keep at most 3 distinct speakers. Directly addressed characters should normally be required.
            """;

    private final StAdapter stAdapter;
    private final AppMessageMapper messageMapper;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;

    public EnsembleSpeakerPlanner(StAdapter stAdapter, AppMessageMapper messageMapper, ObjectMapper objectMapper) {
        this.stAdapter = stAdapter;
        this.messageMapper = messageMapper;
        this.objectMapper = objectMapper;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "ensemble-speaker-planner");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(4, factory);
    }

    public String planIfStory(
            EnsembleChatService.EnsembleContext context,
            long conversationId,
            long branchId,
            String currentInput,
            UserModelOverride userModelOverride,
            String routeKey
    ) {
        if (context == null || !context.enabled()
                || !EnsembleChatService.MODE_STORY.equals(context.mode())) {
            return "";
        }
        StStreamControl control = new StStreamControl();
        Future<String> future = executor.submit(() -> runPlanner(
                context, conversationId, branchId, currentInput, userModelOverride, routeKey, control));
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            control.cancel();
            future.cancel(true);
            log.warn("ensemble story planner fallback conversationId={} cause={}", conversationId, ex.toString());
            return "";
        }
    }

    private String runPlanner(
            EnsembleChatService.EnsembleContext context,
            long conversationId,
            long branchId,
            String currentInput,
            UserModelOverride userModelOverride,
            String routeKey,
            StStreamControl control
    ) {
        List<ChatMessage> messages = List.of(
                ChatMessage.text("system", SYSTEM_PROMPT),
                ChatMessage.text("user", buildPlannerInput(context, conversationId, branchId, currentInput))
        );
        ChatGenerateRequest request = new ChatGenerateRequest(
                conversationId,
                "",
                messages,
                "ensemble_plan_" + System.nanoTime(),
                true,
                "ensemble_speaker_plan",
                Set.of(),
                "",
                "",
                List.of(),
                "",
                "",
                "",
                List.of(),
                userModelOverride,
                null,
                null,
                AiCapability.CHAT,
                routeKey,
                null
        );
        StringBuilder raw = new StringBuilder();
        stAdapter.streamGenerateAssistantReply(request, chunk -> {
            if (chunk == null || chunk.delta() == null || control.isCancelled()) {
                return;
            }
            if (raw.length() + chunk.delta().length() > MAX_OUTPUT_CHARS) {
                control.cancel();
                return;
            }
            raw.append(chunk.delta());
        }, control);
        if (control.isCancelled() || raw.isEmpty()) {
            return "";
        }
        return validateAndFormat(raw.toString(), context.members().size());
    }

    String validateAndFormat(String raw, int memberCount) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(raw));
            List<String> required = validTokens(root.get("requiredSpeakers"), memberCount, 3);
            List<String> preferred = validTokens(root.get("preferredSpeakers"), memberCount, 3);
            List<String> order = validTokens(root.get("orderHint"), memberCount, 3);
            LinkedHashSet<String> speakers = new LinkedHashSet<>();
            speakers.addAll(required);
            speakers.addAll(preferred);
            speakers.addAll(order);
            if (speakers.isEmpty()) {
                return "";
            }
            List<String> allowed = speakers.stream().limit(3).toList();
            required = required.stream().filter(allowed::contains).toList();
            preferred = preferred.stream().filter(allowed::contains).toList();
            order = order.stream().filter(allowed::contains).toList();
            int maxSegments = Math.max(1, Math.min(8,
                    root.path("maxSegments").asInt(Math.max(1, allowed.size()))));
            boolean narrationAllowed = root.path("narrationAllowed").asBoolean(true);
            return "Allowed speakers: " + String.join(", ", allowed) + ".\n"
                    + "Required speakers: " + (required.isEmpty() ? "none" : String.join(", ", required)) + ".\n"
                    + "Preferred speakers: " + (preferred.isEmpty() ? "none" : String.join(", ", preferred)) + ".\n"
                    + "Preferred order: " + (order.isEmpty() ? "natural" : String.join(" -> ", order)) + ".\n"
                    + "Maximum segments: " + maxSegments + ". Narration allowed: " + narrationAllowed + ".";
        } catch (Exception ex) {
            return "";
        }
    }

    private String buildPlannerInput(
            EnsembleChatService.EnsembleContext context,
            long conversationId,
            long branchId,
            String currentInput
    ) {
        StringBuilder input = new StringBuilder("Roster:\n");
        for (int i = 0; i < context.members().size(); i++) {
            var member = context.members().get(i);
            input.append("M").append(i + 1).append(" = ")
                    .append(clean(member.getName())).append(" | ")
                    .append(clean(member.getTagline())).append('\n');
        }
        input.append("Recent scene:\n");
        List<AppMessage> history = messageMapper.listRecentByConversationBranchAsc(
                conversationId, branchId, HISTORY_LIMIT);
        for (AppMessage message : history) {
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            input.append("assistant".equalsIgnoreCase(message.getRole()) ? "ASSISTANT: " : "USER: ")
                    .append(trim(clean(message.getContent()), 600)).append('\n');
        }
        input.append("Current turn: ").append(trim(clean(currentInput), 1000));
        return input.toString();
    }

    private static List<String> validTokens(JsonNode node, int memberCount, int limit) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String token = item.asText("").trim().toUpperCase();
            if (token.matches("M[1-8]")) {
                int index = Integer.parseInt(token.substring(1));
                if (index <= memberCount) {
                    result.add(token);
                }
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(result);
    }

    private static String extractJson(String raw) {
        String text = raw == null ? "" : raw.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }

    private static String clean(String value) {
        return EnsemblePromptText.sanitize(value);
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
