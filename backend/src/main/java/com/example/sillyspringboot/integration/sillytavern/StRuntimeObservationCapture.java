package com.example.sillyspringboot.integration.sillytavern;

import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 商用排障视角的运行时观测：
 * 记录一次聊天请求最终是按哪条 ST 运行时链路发出去的，以及关键运行时绑定。
 */
@Component
public class StRuntimeObservationCapture {

    private static final Logger log = LoggerFactory.getLogger(StRuntimeObservationCapture.class);

    public record CaptureItem(
            Instant at,
            String traceId,
            long conversationId,
            String mode,
            String runtimeSource,
            String effectiveStDisplayName,
            String charName,
            String avatarUrl,
            String fileName,
            List<String> worldNames,
            String goldenDiffStatus
    ) {
    }

    private final SillyTavernProperties properties;
    private final Object lock = new Object();
    private Deque<CaptureItem> ring;

    public StRuntimeObservationCapture(SillyTavernProperties properties) {
        this.properties = properties;
        this.ring = new ArrayDeque<>(Math.max(1, properties.getDebug().getBufferSize()));
    }

    public boolean enabled() {
        return properties.getDebug() != null && properties.getDebug().isEnabled();
    }

    public String token() {
        return properties.getDebug() == null ? "" : properties.getDebug().getToken();
    }

    public void capture(ChatGenerateRequest request, String runtimeSource, String goldenDiffStatus) {
        if (request == null) {
            return;
        }
        CaptureItem item = new CaptureItem(
                Instant.now(),
                safeTraceId(),
                request.conversationId() == null ? 0L : request.conversationId(),
                trimToEmpty(request.mode(), 32),
                trimToEmpty(runtimeSource, 48),
                trimToEmpty(request.userName(), 64),
                trimToEmpty(request.charName(), 64),
                trimToEmpty(request.stAvatarUrl(), 160),
                trimToEmpty(request.stChatFileName(), 160),
                normalizeWorldNames(request.stWorldNames()),
                trimToEmpty(StringUtils.hasText(goldenDiffStatus) ? goldenDiffStatus : "not_compared", 48)
        );
        log.info(
                "st.runtime.observation traceId={} conversationId={} mode={} source={} userName={} charName={} avatar={} file={} worldNames={} goldenDiff={}",
                item.traceId(),
                item.conversationId(),
                item.mode(),
                item.runtimeSource(),
                item.effectiveStDisplayName(),
                item.charName(),
                item.avatarUrl(),
                item.fileName(),
                item.worldNames(),
                item.goldenDiffStatus()
        );

        if (!enabled() || !StringUtils.hasText(token())) {
            return;
        }

        int max = Math.max(1, properties.getDebug().getBufferSize());
        synchronized (lock) {
            if (ring.size() >= max) {
                ring.removeFirst();
            }
            ring.addLast(item);
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

    private static List<String> normalizeWorldNames(List<String> worldNames) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (worldNames != null) {
            for (String item : worldNames) {
                String text = trimToEmpty(item, 128);
                if (!text.isBlank()) {
                    ordered.add(text);
                }
                if (ordered.size() >= 10) {
                    break;
                }
            }
        }
        return List.copyOf(new ArrayList<>(ordered));
    }

    private static String trimToEmpty(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max).trim();
    }
}
