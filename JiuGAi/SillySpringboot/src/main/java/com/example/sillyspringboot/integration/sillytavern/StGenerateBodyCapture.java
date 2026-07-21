package com.example.sillyspringboot.integration.sillytavern;

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
import java.util.regex.Pattern;

/**
 * 开发/联调用：抓取并暂存网关发往 ST /generate 的请求体，便于与浏览器 ST 的请求体做 Golden Diff。
 * <p>
 * 注意：该能力可能包含用户输入与 prompt，默认关闭，仅建议本机开启。
 */
@Component
public class StGenerateBodyCapture {

    private static final Logger log = LoggerFactory.getLogger(StGenerateBodyCapture.class);
    private static final Pattern SECRET_FIELD_PATTERN = Pattern.compile(
            "(\\\"(?:proxy_password|api_key|authorization)\\\"\\s*:\\s*\\\")([^\\\"]*)(\\\")",
            Pattern.CASE_INSENSITIVE
    );

    public record CaptureItem(
            Instant at,
            String traceId,
            long conversationId,
            String mode,
            URI url,
            String body
    ) {
    }

    private final SillyTavernProperties properties;
    private final Object lock = new Object();
    private Deque<CaptureItem> ring;

    public StGenerateBodyCapture(SillyTavernProperties properties) {
        this.properties = properties;
        this.ring = new ArrayDeque<>(Math.max(1, properties.getDebug().getBufferSize()));
    }

    public boolean enabled() {
        return properties.getDebug() != null && properties.getDebug().isEnabled();
    }

    public String token() {
        return properties.getDebug() == null ? "" : properties.getDebug().getToken();
    }

    public void capture(long conversationId, String mode, URI url, String body) {
        if (!enabled()) return;
        String traceId = safeTraceId();
        String redactedBody = redactSensitiveBody(body);
        String compact = compactBodyForLogs(redactedBody);
        log.info("st.generate body captured traceId={} conversationId={} mode={} url={} body={}",
                traceId, conversationId, mode, String.valueOf(url), compact);

        if (!StringUtils.hasText(token())) {
            return; // 不提供 HTTP 读取，仅打日志
        }

        int max = Math.max(1, properties.getDebug().getBufferSize());
        synchronized (lock) {
            if (ring.size() >= max) {
                ring.removeFirst();
            }
            ring.addLast(new CaptureItem(Instant.now(), traceId, conversationId, mode == null ? "" : mode, url, redactedBody));
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

    private static String compactBodyForLogs(String body) {
        if (body == null) return "";
        String s = body.replace("\r", "").replace("\n", "");
        if (s.length() > 2000) {
            return s.substring(0, 2000) + "...(truncated)";
        }
        return s;
    }

    private static String redactSensitiveBody(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        return SECRET_FIELD_PATTERN.matcher(body).replaceAll("$1***$3");
    }
}

