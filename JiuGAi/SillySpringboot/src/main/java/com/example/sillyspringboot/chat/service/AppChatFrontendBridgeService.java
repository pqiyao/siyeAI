package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.integration.sillytavern.StStreamControl;
import com.example.sillyspringboot.integration.sillytavern.StUnavailableException;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AppChatFrontendBridgeService {

    private static final Logger log = LoggerFactory.getLogger(AppChatFrontendBridgeService.class);

    private final AppChatProperties chatProperties;
    private final BlockingQueue<BridgeJob> queue;
    private final ConcurrentHashMap<String, BridgeJob> activeJobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WorkerState> workers = new ConcurrentHashMap<>();

    public AppChatFrontendBridgeService(AppChatProperties chatProperties) {
        this.chatProperties = chatProperties;
        int capacity = Math.max(1, chatProperties.getCompatibility().getFrontendBridgeMaxQueueSize());
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    public boolean enabled() {
        return chatProperties.getCompatibility().isFrontendBridgeEnabled();
    }

    public boolean hasOnlineWorker() {
        return onlineWorkerCount() > 0;
    }

    public void streamGenerate(ChatGenerateRequest request, java.util.function.Consumer<ChatGenerateChunk> onChunk, StStreamControl control) {
        if (!enabled()) {
            throw new StUnavailableException(new IllegalStateException("frontend bridge disabled"));
        }
        if (!hasOnlineWorker()) {
            throw new StUnavailableException(new IllegalStateException("frontend bridge worker offline"));
        }

        BridgeJob job = BridgeJob.from(request, requestTimeoutMillis());
        if (!queue.offer(job)) {
            throw new StUnavailableException(new IllegalStateException("frontend bridge queue full"));
        }
        control.addOnCancel(() -> cancelJob(job.id(), "client_cancelled"));

        try {
            BridgeCompletion completion = job.future().get(requestTimeoutMillis(), TimeUnit.MILLISECONDS);
            if (control.isCancelled()) {
                return;
            }
            String text = completion.text() == null ? "" : completion.text();
            String metrics = bridgeChunkMetrics(job.id(), completion.metrics());
            int chunkIndex = 0;
            if (!text.isBlank()) {
                onChunk.accept(new ChatGenerateChunk(
                        request.conversationId(),
                        completion.messageId(),
                        chunkIndex++,
                        text,
                        false,
                        "",
                        metrics
                ));
            }
            onChunk.accept(new ChatGenerateChunk(
                    request.conversationId(),
                    completion.messageId(),
                    chunkIndex,
                    "",
                    true,
                    "",
                    metrics
            ));
        } catch (CancellationException ex) {
            if (!control.isCancelled()) {
                throw new StUnavailableException(ex);
            }
        } catch (TimeoutException ex) {
            cancelJob(job.id(), "timeout");
            throw new StUnavailableException(ex);
        } catch (Exception ex) {
            if (control.isCancelled()) {
                return;
            }
            throw new StUnavailableException(ex);
        } finally {
            activeJobs.remove(job.id());
        }
    }

    public BridgeJobPayload pollNext(String workerId, long requestedWaitMillis) {
        String safeWorkerId = normalizeWorkerId(workerId);
        heartbeat(safeWorkerId);

        long waitMillis = Math.max(250L, Math.min(requestedWaitMillis, pollWaitMillis()));
        long deadline = System.currentTimeMillis() + waitMillis;
        while (System.currentTimeMillis() <= deadline) {
            if (hasActiveFrontendJob()) {
                sleepQuietly(Math.min(250L, Math.max(1L, deadline - System.currentTimeMillis())));
                continue;
            }
            BridgeJob job;
            try {
                long remaining = Math.max(1L, deadline - System.currentTimeMillis());
                job = queue.poll(Math.min(remaining, 1000L), TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
            if (job == null) {
                continue;
            }
            if (job.cancelled().get() || job.expired()) {
                job.future().completeExceptionally(new CancellationException("bridge job expired or cancelled"));
                continue;
            }
            job.markDispatched(safeWorkerId);
            activeJobs.put(job.id(), job);
            return job.payload();
        }
        return null;
    }

    public boolean complete(String jobId, BridgeCompletion completion) {
        if (!StringUtils.hasText(jobId)) {
            return false;
        }
        BridgeJob job = activeJobs.remove(jobId);
        if (job == null) {
            return false;
        }
        BridgeCompletion safe = completion == null ? BridgeCompletion.empty(jobId) : completion.withJobId(jobId);
        job.future().complete(safe);
        return true;
    }

    public boolean fail(String jobId, String error) {
        if (!StringUtils.hasText(jobId)) {
            return false;
        }
        BridgeJob job = activeJobs.remove(jobId);
        if (job == null) {
            return false;
        }
        job.future().completeExceptionally(new IllegalStateException(error == null ? "frontend bridge failed" : error));
        return true;
    }

    public boolean cancelJob(String jobId, String reason) {
        if (!StringUtils.hasText(jobId)) {
            return false;
        }
        BridgeJob job = activeJobs.get(jobId);
        if (job == null) {
            for (BridgeJob queued : queue) {
                if (queued.id().equals(jobId)) {
                    job = queued;
                    break;
                }
            }
        }
        if (job == null) {
            return false;
        }
        job.cancelled().set(true);
        job.future().completeExceptionally(new CancellationException(reason == null ? "cancelled" : reason));
        return true;
    }

    public boolean isJobCancelled(String jobId) {
        BridgeJob job = activeJobs.get(jobId);
        return job != null && job.cancelled().get();
    }

    public void heartbeat(String workerId) {
        String safeWorkerId = normalizeWorkerId(workerId);
        workers.put(safeWorkerId, new WorkerState(safeWorkerId, System.currentTimeMillis()));
    }

    public boolean validToken(String token) {
        if (!enabled()) {
            return false;
        }
        String expected = chatProperties.getCompatibility().getFrontendBridgeToken();
        if (!StringUtils.hasText(expected) || token == null) {
            return false;
        }
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = token.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    public BridgeStatus status() {
        long now = System.currentTimeMillis();
        List<Map<String, Object>> workerList = new ArrayList<>();
        for (WorkerState worker : workers.values()) {
            long ageMillis = Math.max(0L, now - worker.lastSeenMillis());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("workerId", worker.workerId());
            row.put("lastSeen", Instant.ofEpochMilli(worker.lastSeenMillis()).toString());
            row.put("ageMillis", ageMillis);
            row.put("online", ageMillis <= workerStaleMillis());
            workerList.add(row);
        }
        return new BridgeStatus(
                enabled(),
                queue.size(),
                activeJobs.size(),
                onlineWorkerCount(),
                requestTimeoutMillis(),
                pollWaitMillis(),
                workerStaleMillis(),
                workerList
        );
    }

    private int onlineWorkerCount() {
        long now = System.currentTimeMillis();
        long staleMillis = workerStaleMillis();
        int count = 0;
        for (WorkerState worker : workers.values()) {
            if (now - worker.lastSeenMillis() <= staleMillis) {
                count++;
            }
        }
        return count;
    }

    private boolean hasActiveFrontendJob() {
        for (Map.Entry<String, BridgeJob> entry : activeJobs.entrySet()) {
            BridgeJob job = entry.getValue();
            if (job == null || job.cancelled().get() || job.expired()) {
                activeJobs.remove(entry.getKey(), job);
                if (job != null) {
                    job.future().completeExceptionally(new CancellationException("bridge job expired or cancelled"));
                }
                continue;
            }
            return true;
        }
        return false;
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private long requestTimeoutMillis() {
        return Math.max(5, chatProperties.getCompatibility().getFrontendBridgeRequestTimeoutSeconds()) * 1000L;
    }

    private long workerStaleMillis() {
        return Math.max(5, chatProperties.getCompatibility().getFrontendBridgeWorkerStaleSeconds()) * 1000L;
    }

    private long pollWaitMillis() {
        return Math.max(1, chatProperties.getCompatibility().getFrontendBridgePollWaitSeconds()) * 1000L;
    }

    private static String normalizeWorkerId(String workerId) {
        String safe = workerId == null ? "" : workerId.trim();
        return safe.isBlank() ? "anonymous" : safe.substring(0, Math.min(96, safe.length()));
    }

    private static String bridgeChunkMetrics(String jobId, String workerMetrics) {
        String safeJobId = escapeJson(jobId == null ? "" : jobId);
        String safeWorkerMetrics = escapeJson(workerMetrics == null ? "" : workerMetrics);
        return "{\"source\":\"frontend_bridge\",\"jobId\":\"" + safeJobId + "\",\"workerMetrics\":\"" + safeWorkerMetrics + "\"}";
    }

    private static String escapeJson(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }

    private record BridgeJob(
            String id,
            BridgeJobPayload payload,
            CompletableFuture<BridgeCompletion> future,
            AtomicBoolean cancelled,
            long expiresAtMillis
    ) {
        static BridgeJob from(ChatGenerateRequest request, long timeoutMillis) {
            String id = UUID.randomUUID().toString();
            BridgeJobPayload payload = new BridgeJobPayload(
                    id,
                    request.conversationId(),
                    request.mode(),
                    request.stAvatarUrl(),
                    request.stChatFileName(),
                    request.userMessage(),
                    request.stMessageRef(),
                    request.userName(),
                    request.charName(),
                    request.stWorldNames() == null ? List.of() : List.copyOf(request.stWorldNames()),
                    System.currentTimeMillis()
            );
            return new BridgeJob(id, payload, new CompletableFuture<>(), new AtomicBoolean(false), System.currentTimeMillis() + timeoutMillis);
        }

        boolean expired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }

        void markDispatched(String workerId) {
            log.info("frontend bridge job dispatched jobId={} conversationId={} mode={} workerId={}",
                    id, payload.conversationId(), payload.mode(), workerId);
        }
    }

    private record WorkerState(String workerId, long lastSeenMillis) {
    }

    public record BridgeJobPayload(
            String jobId,
            Long conversationId,
            String mode,
            String avatarUrl,
            String fileName,
            String userMessage,
            String messageRef,
            String userName,
            String charName,
            List<String> worldNames,
            long createdAtMillis
    ) {
    }

    public record BridgeCompletion(
            String jobId,
            String messageId,
            String text,
            String finishReason,
            String metrics
    ) {
        static BridgeCompletion empty(String jobId) {
            return new BridgeCompletion(jobId, "", "", "", "");
        }

        BridgeCompletion withJobId(String nextJobId) {
            return new BridgeCompletion(nextJobId, messageId, text, finishReason, metrics);
        }
    }

    public record BridgeStatus(
            boolean enabled,
            int queuedJobs,
            int activeJobs,
            int onlineWorkers,
            long requestTimeoutMillis,
            long pollWaitMillis,
            long workerStaleMillis,
            List<Map<String, Object>> workers
    ) {
    }
}
