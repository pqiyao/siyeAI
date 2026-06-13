package com.example.sillyspringboot.integration.sillytavern.web;

import com.example.sillyspringboot.integration.sillytavern.StGenerateBodyCapture;
import com.example.sillyspringboot.integration.sillytavern.StClient;
import com.example.sillyspringboot.integration.sillytavern.StRuntimeMessagesCapture;
import com.example.sillyspringboot.integration.sillytavern.StRuntimeObservationCapture;
import com.example.sillyspringboot.integration.sillytavern.StRuntimeChatWriteCapture;
import com.example.sillyspringboot.conversation.entity.AppConversationStBinding;
import com.example.sillyspringboot.conversation.mapper.AppConversationStBindingMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开发/联调接口：读取最近一次发往 ST /generate 的请求体。
 * <p>
 * 仅在 {@code sillytavern.debug.enabled=true} 且配置了 {@code sillytavern.debug.token} 时可用。
 */
@RestController
@RequestMapping("/api/dev/st-debug")
public class StDebugController {

    private final StGenerateBodyCapture capture;
    private final StRuntimeMessagesCapture runtimeMessages;
    private final StRuntimeObservationCapture runtimeObservation;
    private final StClient stClient;
    private final AppConversationStBindingMapper bindingMapper;
    private final StRuntimeChatWriteCapture runtimeChatWriteCapture;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StDebugController(
            StGenerateBodyCapture capture,
            StRuntimeMessagesCapture runtimeMessages,
            StRuntimeObservationCapture runtimeObservation,
            StClient stClient,
            AppConversationStBindingMapper bindingMapper,
            StRuntimeChatWriteCapture runtimeChatWriteCapture
    ) {
        this.capture = capture;
        this.runtimeMessages = runtimeMessages;
        this.runtimeObservation = runtimeObservation;
        this.stClient = stClient;
        this.bindingMapper = bindingMapper;
        this.runtimeChatWriteCapture = runtimeChatWriteCapture;
    }

    @GetMapping("/latest")
    public Map<String, Object> latest(
            @RequestHeader(value = "X-Debug-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken
    ) {
        requireEnabledAndToken(firstNonBlank(headerToken, queryToken));
        StGenerateBodyCapture.CaptureItem item = capture.latest();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", capture.enabled());
        out.put("latest", item);
        return out;
    }

    @GetMapping("/recent")
    public Map<String, Object> recent(
            @RequestHeader(value = "X-Debug-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken
    ) {
        requireEnabledAndToken(firstNonBlank(headerToken, queryToken));
        List<StGenerateBodyCapture.CaptureItem> items = capture.listRecent();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", capture.enabled());
        out.put("items", items);
        return out;
    }

    @GetMapping("/runtime-latest")
    public Map<String, Object> runtimeLatest(
            @RequestHeader(value = "X-Debug-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken
    ) {
        requireEnabledAndToken(firstNonBlank(headerToken, queryToken));
        StRuntimeMessagesCapture.CaptureItem item = runtimeMessages.latest();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", runtimeMessages.enabled());
        out.put("latest", item);
        return out;
    }

    @GetMapping("/runtime-recent")
    public Map<String, Object> runtimeRecent(
            @RequestHeader(value = "X-Debug-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken
    ) {
        requireEnabledAndToken(firstNonBlank(headerToken, queryToken));
        List<StRuntimeMessagesCapture.CaptureItem> items = runtimeMessages.listRecent();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", runtimeMessages.enabled());
        out.put("items", items);
        return out;
    }

    @GetMapping("/runtime-observation-latest")
    public Map<String, Object> runtimeObservationLatest(
            @RequestHeader(value = "X-Debug-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken
    ) {
        requireEnabledAndToken(firstNonBlank(headerToken, queryToken));
        StRuntimeObservationCapture.CaptureItem item = runtimeObservation.latest();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", runtimeObservation.enabled());
        out.put("latest", item);
        return out;
    }

    @GetMapping("/runtime-observation-recent")
    public Map<String, Object> runtimeObservationRecent(
            @RequestHeader(value = "X-Debug-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken
    ) {
        requireEnabledAndToken(firstNonBlank(headerToken, queryToken));
        List<StRuntimeObservationCapture.CaptureItem> items = runtimeObservation.listRecent();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", runtimeObservation.enabled());
        out.put("items", items);
        return out;
    }

    /**
     * 验证用：读取 ST chat jsonl 最后 N 条消息（含 extra.message_ref）。
     * <p>
     * 需要先有一次 generate/continue/regenerate/swipe 发生过，能从 latest/runtime-latest 拿到 avatar_url + file_name。
     */
    @GetMapping("/runtime-chat-tail")
    public Map<String, Object> runtimeChatTail(
            @RequestHeader(value = "X-Debug-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestParam(value = "conversationId") long conversationId,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit
    ) {
        requireEnabledAndToken(firstNonBlank(headerToken, queryToken));

        if (conversationId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "conversationId missing");
        }

        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            throw new BusinessException(ErrorCode.CONFLICT, "ST binding missing (avatar_url/file_name)");
        }

        Object tail = stClient.runtimeChatTail(binding.getStAvatarUrl(), binding.getStChatFileName(), "", "", limit);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", capture.enabled());
        out.put("conversationId", conversationId);
        out.put("avatar_url", binding.getStAvatarUrl());
        out.put("file_name", binding.getStChatFileName());
        out.put("tail", tail);
        return out;
    }

    /**
     * 验证用（更易读）：返回 tail 中的 extra.message_ref 摘要。
     */
    @GetMapping("/runtime-chat-refs")
    public Map<String, Object> runtimeChatRefs(
            @RequestHeader(value = "X-Debug-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestParam(value = "conversationId") long conversationId,
            @RequestParam(value = "limit", required = false, defaultValue = "20") int limit
    ) {
        requireEnabledAndToken(firstNonBlank(headerToken, queryToken));
        if (conversationId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "conversationId missing");
        }
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            throw new BusinessException(ErrorCode.CONFLICT, "ST binding missing (avatar_url/file_name)");
        }

        Object tail = stClient.runtimeChatTail(binding.getStAvatarUrl(), binding.getStChatFileName(), "", "", limit);
        List<Map<String, Object>> items = new ArrayList<>();
        if (tail instanceof Map<?, ?> t) {
            Object rawMsgs = t.get("messages");
            if (rawMsgs instanceof List<?> list) {
                int idx = 0;
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> m)) {
                        idx++;
                        continue;
                    }
                    String mes = m.get("mes") == null ? "" : String.valueOf(m.get("mes"));
                    Object extra = m.get("extra");
                    String ref = "";
                    if (extra instanceof Map<?, ?> em) {
                        Object r = em.get("message_ref");
                        if (r != null) ref = String.valueOf(r);
                    }
                    boolean isUser = false;
                    Object iu = m.get("is_user");
                    if (iu instanceof Boolean b) isUser = b;
                    else if (iu != null) isUser = "true".equalsIgnoreCase(String.valueOf(iu));

                    String head = mes;
                    if (head.length() > 40) head = head.substring(0, 40) + "...";
                    items.add(Map.of(
                            "i", idx,
                            "is_user", isUser,
                            "message_ref", ref,
                            "mes_head", head
                    ));
                    idx++;
                }
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", capture.enabled());
        out.put("conversationId", conversationId);
        out.put("avatar_url", binding.getStAvatarUrl());
        out.put("file_name", binding.getStChatFileName());
        out.put("items", items);
        return out;
    }

    @GetMapping("/runtime-chat-write-latest")
    public Map<String, Object> runtimeChatWriteLatest(
            @RequestHeader(value = "X-Debug-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken
    ) {
        requireEnabledAndToken(firstNonBlank(headerToken, queryToken));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", capture.enabled());
        out.put("latest", runtimeChatWriteCapture.latest());
        return out;
    }

    @GetMapping("/golden-case/list")
    public Object goldenCaseList(
            @RequestHeader(value = "X-Debug-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken
    ) {
        requireEnabledAndToken(firstNonBlank(headerToken, queryToken));
        return stClient.runtimeChatGoldenCaseList();
    }

    @PostMapping("/golden-case/save")
    public Object goldenCaseSave(
            @RequestHeader(value = "X-Debug-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestParam(value = "conversationId") long conversationId,
            @RequestParam(value = "caseName") String caseName,
            @RequestParam(value = "goldenSource", required = false, defaultValue = "browser") String goldenSource,
            @RequestParam(value = "loreMode", required = false, defaultValue = "full") String loreMode,
            @RequestParam(value = "mode", required = false, defaultValue = "generate") String mode
    ) {
        requireEnabledAndToken(firstNonBlank(headerToken, queryToken));
        AppConversationStBinding binding = requireBinding(conversationId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("avatar_url", binding.getStAvatarUrl());
        body.put("file_name", binding.getStChatFileName());
        body.put("case_name", caseName);
        body.put("golden_source", goldenSource);
        body.put("lore_mode", loreMode);
        body.put("mode", mode);
        body.put("world_names", parseWorldNames(binding.getStWorldNamesJson()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("conversationId", conversationId);
        out.put("avatar_url", binding.getStAvatarUrl());
        out.put("file_name", binding.getStChatFileName());
        out.put("result", stClient.runtimeChatGoldenCaseSave(body));
        return out;
    }

    @GetMapping("/golden-case/run")
    public Object goldenCaseRun(
            @RequestHeader(value = "X-Debug-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken,
            @RequestParam(value = "caseName") String caseName
    ) {
        requireEnabledAndToken(firstNonBlank(headerToken, queryToken));
        return stClient.runtimeChatGoldenCaseRun(caseName);
    }

    private void requireEnabledAndToken(String provided) {
        if (!capture.enabled()) {
            throw new ResponseStatusException(FORBIDDEN);
        }
        String expected = capture.token();
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(provided) || !expected.equals(provided)) {
            throw new ResponseStatusException(FORBIDDEN);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) return a;
        if (StringUtils.hasText(b)) return b;
        return "";
    }

    private AppConversationStBinding requireBinding(long conversationId) {
        if (conversationId <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "conversationId missing");
        }
        AppConversationStBinding binding = bindingMapper.findByConversationId(conversationId);
        if (binding == null || !StringUtils.hasText(binding.getStAvatarUrl()) || !StringUtils.hasText(binding.getStChatFileName())) {
            throw new BusinessException(ErrorCode.CONFLICT, "ST binding missing (avatar_url/file_name)");
        }
        return binding;
    }

    private List<String> parseWorldNames(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<String> items = objectMapper.readValue(raw, new TypeReference<>() {});
            return items == null ? List.of() : items.stream().filter(StringUtils::hasText).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }
}

