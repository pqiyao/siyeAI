package com.example.sillyspringboot.integration.sillytavern;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * A：商用联调抓包——记录网关发往 ST runtime-chat 写入类接口的请求体（append/replace/pop 等）。
 * <p>
 * 只在 debug enabled 且配置 token 时提供 HTTP 读取；否则仅打日志。
 */
@Component
public class StRuntimeChatWriteCapture {

    private static final Logger log = LoggerFactory.getLogger(StRuntimeChatWriteCapture.class);

    public record CaptureItem(
            Instant at,
            String traceId,
            long conversationId,
            String op,
            URI url,
            Map<String, Object> body
    ) {
    }

    private final SillyTavernProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Object lock = new Object();
    private Deque<CaptureItem> ring;

    public StRuntimeChatWriteCapture(SillyTavernProperties properties) {
        this.properties = properties;
        this.ring = new ArrayDeque<>(Math.max(1, properties.getDebug().getBufferSize()));
    }

    public boolean enabled() {
        return properties.getDebug() != null && properties.getDebug().isEnabled();
    }

    public String token() {
        return properties.getDebug() == null ? "" : properties.getDebug().getToken();
    }

    public void capture(long conversationId, String op, URI url, Map<String, Object> body) {
        if (!enabled()) return;
        String traceId = safeTraceId();
        log.info("st.runtime.chat.write captured traceId={} conversationId={} op={} url={} body={}",
                traceId, conversationId, op == null ? "" : op, String.valueOf(url), compactForLogs(body));

        if (!StringUtils.hasText(token())) return;

        int max = Math.max(1, properties.getDebug().getBufferSize());
        synchronized (lock) {
            if (ring.size() >= max) ring.removeFirst();
            ring.addLast(new CaptureItem(Instant.now(), traceId, conversationId, op == null ? "" : op, url, body));
        }
    }

    public CaptureItem latest() {
        synchronized (lock) {
            return ring.peekLast();
        }
    }

    public List<CaptureItem> listRecent() {
        synchronized (lock) {
            return List.copyOf(ring);
        }
    }

    private static String safeTraceId() {
        String id = MDC.get("traceId");
        return id == null ? "" : id;
    }

    private String compactForLogs(Map<String, Object> body) {
        try {
            String s = objectMapper.writeValueAsString(body);
            if (s.length() > 2000) return s.substring(0, 2000) + "...(truncated)";
            return s;
        } catch (Exception e) {
            return String.valueOf(body);
        }
    }
}

