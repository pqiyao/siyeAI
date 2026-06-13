package com.example.sillyspringboot.integration.sillytavern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Golden Diff 回归辅助：抓取“由 ST runtime-chat/build 产出的 messages”，用于与 ST 网页端 messages 对比。
 * <p>
 * 默认仅在 {@code sillytavern.debug.enabled=true} 时记录；若未配置 debug token，则仅打日志不提供 HTTP 读取。
 */
@Component
public class StRuntimeMessagesCapture {

    private static final Logger log = LoggerFactory.getLogger(StRuntimeMessagesCapture.class);

    public record CaptureItem(
            Instant at,
            String traceId,
            long conversationId,
            String mode,
            List<ChatMessage> messages
    ) {
    }

    private final SillyTavernProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Object lock = new Object();
    private Deque<CaptureItem> ring;

    public StRuntimeMessagesCapture(SillyTavernProperties properties) {
        this.properties = properties;
        this.ring = new ArrayDeque<>(Math.max(1, properties.getDebug().getBufferSize()));
    }

    public boolean enabled() {
        return properties.getDebug() != null && properties.getDebug().isEnabled();
    }

    public String token() {
        return properties.getDebug() == null ? "" : properties.getDebug().getToken();
    }

    public void capture(long conversationId, String mode, List<ChatMessage> messages) {
        if (!enabled()) return;
        String traceId = safeTraceId();
        String compact = compactForLogs(messages);
        log.info("st.runtime.messages captured traceId={} conversationId={} mode={} messages={}",
                traceId, conversationId, mode == null ? "" : mode, compact);

        if (!StringUtils.hasText(token())) {
            return;
        }

        int max = Math.max(1, properties.getDebug().getBufferSize());
        synchronized (lock) {
            if (ring.size() >= max) {
                ring.removeFirst();
            }
            ring.addLast(new CaptureItem(Instant.now(), traceId, conversationId, mode == null ? "" : mode, messages));
        }
    }

    public List<CaptureItem> listRecent() {
        synchronized (lock) {
            return List.copyOf(ring);
        }
    }

    public CaptureItem latest() {
        synchronized (lock) {
            return ring.peekLast();
        }
    }

    private static String safeTraceId() {
        String id = MDC.get("traceId");
        return id == null ? "" : id;
    }

    private String compactForLogs(List<ChatMessage> messages) {
        try {
            String s = objectMapper.writeValueAsString(messages);
            if (s.length() > 2000) return s.substring(0, 2000) + "...(truncated)";
            return s;
        } catch (Exception e) {
            return String.valueOf(messages);
        }
    }
}

