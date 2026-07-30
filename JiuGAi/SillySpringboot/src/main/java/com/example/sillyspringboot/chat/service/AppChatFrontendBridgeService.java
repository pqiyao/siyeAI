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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class AppChatFrontendBridgeService {

    private static final Logger log = LoggerFactory.getLogger(AppChatFrontendBridgeService.class);

    private final AppChatProperties chatProperties;
    private final BlockingQueue<BridgeJob> queue;
    private final ConcurrentHashMap<String, BridgeJob> activeJobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cancelledJobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WorkerState> workers = new ConcurrentHashMap<>();
    private final Object dispatchLock = new Object();

    public AppChatFrontendBridgeService(AppChatProperties chatProperties) {
        this.chatProperties = chatProperties;
        int capacity = Math.max(1, chatProperties.getCompatibility().getFrontendBridgeMaxQueueSize());
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    public boolean enabled() {
        return chatProperties.getCompatibility().isFrontendBridgeEnabled();
    }

    public boolean hasOnlineWorker() {
        return readyWorkerCount() > 0;
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
            long deadline = System.currentTimeMillis() + requestTimeoutMillis();
            int chunkIndex = 0;
            StringBuilder streamedText = new StringBuilder();
            while (!job.future().isDone()) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new TimeoutException("frontend bridge request timed out");
                }
                BridgePartial partial = job.partials().poll(Math.min(250L, remaining), TimeUnit.MILLISECONDS);
                if (partial != null && !control.isCancelled()) {
                    streamedText.append(partial.text());
                    onChunk.accept(new ChatGenerateChunk(
                            request.conversationId(),
                            partial.messageId(),
                            chunkIndex++,
                            partial.text(),
                            false,
                            "",
                            bridgeChunkMetrics(job.id(), "")
                    ));
                }
            }
            BridgePartial trailing;
            while ((trailing = job.partials().poll()) != null && !control.isCancelled()) {
                streamedText.append(trailing.text());
                onChunk.accept(new ChatGenerateChunk(
                        request.conversationId(), trailing.messageId(), chunkIndex++, trailing.text(),
                        false, "", bridgeChunkMetrics(job.id(), "")
                ));
            }
            BridgeCompletion completion = job.future().get();
            if (control.isCancelled()) {
                return;
            }
            String text = completion.text() == null ? "" : completion.text();
            String metrics = bridgeChunkMetrics(job.id(), completion.metrics());
            String streamed = streamedText.toString();
            if (!text.startsWith(streamed)) {
                throw new IllegalStateException("frontend bridge final text rewrote an already streamed prefix");
            }
            String remainingText = text.substring(streamed.length());
            if (!remainingText.isEmpty()) {
                onChunk.accept(new ChatGenerateChunk(
                        request.conversationId(),
                        completion.messageId(),
                        chunkIndex++,
                        remainingText,
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
                throw bridgeFailure(job, ex);
            }
        } catch (TimeoutException ex) {
            cancelJob(job.id(), "timeout");
            throw bridgeFailure(job, ex);
        } catch (Exception ex) {
            if (control.isCancelled()) {
                return;
            }
            throw bridgeFailure(job, ex);
        } finally {
            activeJobs.remove(job.id());
        }
    }

    public BridgeJobPayload pollNext(String workerId, long requestedWaitMillis) {
        String safeWorkerId = normalizeWorkerId(workerId);

        long waitMillis = Math.max(250L, Math.min(requestedWaitMillis, pollWaitMillis()));
        long deadline = System.currentTimeMillis() + waitMillis;
        while (System.currentTimeMillis() <= deadline) {
            BridgeJobPayload payload = tryDispatchNext(safeWorkerId);
            if (payload != null) {
                return payload;
            }
            sleepQuietly(Math.min(100L, Math.max(1L, deadline - System.currentTimeMillis())));
        }
        return null;
    }

    private BridgeJobPayload tryDispatchNext(String workerId) {
        synchronized (dispatchLock) {
            WorkerState worker = workers.get(workerId);
            if (!isWorkerDispatchable(worker, System.currentTimeMillis()) || hasActiveFrontendJob()) {
                return null;
            }
            BridgeJob job;
            while ((job = queue.poll()) != null) {
                if (job.cancelled().get() || job.expired()) {
                    job.future().completeExceptionally(new CancellationException("bridge job expired or cancelled"));
                    continue;
                }
                job.markDispatched(workerId);
                activeJobs.put(job.id(), job);
                return job.payload();
            }
            return null;
        }
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

    public boolean publishPartial(BridgePartial partial) {
        if (partial == null || !StringUtils.hasText(partial.jobId()) || !StringUtils.hasText(partial.text())) {
            return false;
        }
        BridgeJob job = activeJobs.get(partial.jobId());
        return job != null && job.acceptPartial(partial.normalized());
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
        cancelledJobs.put(job.id(), System.currentTimeMillis() + cancelledJobRetentionMillis());
        job.future().completeExceptionally(new CancellationException(reason == null ? "cancelled" : reason));
        return true;
    }

    public boolean isJobCancelled(String jobId) {
        BridgeJob job = activeJobs.get(jobId);
        if (job != null && job.cancelled().get()) {
            return true;
        }
        Long retainedUntil = cancelledJobs.get(jobId);
        if (retainedUntil == null) {
            return false;
        }
        if (retainedUntil < System.currentTimeMillis()) {
            cancelledJobs.remove(jobId, retainedUntil);
            return false;
        }
        return true;
    }

    public void heartbeat(String workerId) {
        heartbeat(workerId, new WorkerHeartbeat(true, true, false, false, "", List.of()));
    }

    public void heartbeat(String workerId, WorkerHeartbeat heartbeat) {
        String safeWorkerId = normalizeWorkerId(workerId);
        WorkerHeartbeat safe = heartbeat == null ? WorkerHeartbeat.notReady() : heartbeat.normalized();
        workers.put(safeWorkerId, new WorkerState(
                safeWorkerId,
                System.currentTimeMillis(),
                safe.appReady(),
                safe.modelConnected(),
                safe.busy(),
                safe.generating(),
                safe.activeJobId(),
                safe.loadedExtensions()
        ));
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
            row.put("appReady", worker.appReady());
            row.put("modelConnected", worker.modelConnected());
            row.put("busy", worker.busy());
            row.put("generating", worker.generating());
            row.put("activeJobId", worker.activeJobId());
            row.put("loadedExtensions", worker.loadedExtensions());
            row.put("online", ageMillis <= workerStaleMillis());
            row.put("ready", isWorkerReady(worker, now));
            workerList.add(row);
        }
        return new BridgeStatus(
                enabled(),
                queue.size(),
                activeJobs.size(),
                onlineWorkerCount(),
                readyWorkerCount(),
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

    private int readyWorkerCount() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (WorkerState worker : workers.values()) {
            if (isWorkerReady(worker, now)) {
                count++;
            }
        }
        return count;
    }

    private boolean isWorkerReady(WorkerState worker, long now) {
        return worker != null
                && now - worker.lastSeenMillis() <= workerStaleMillis()
                && worker.appReady()
                && worker.modelConnected();
    }

    private boolean isWorkerDispatchable(WorkerState worker, long now) {
        return isWorkerReady(worker, now) && !worker.busy() && !worker.generating();
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

    private long cancelledJobRetentionMillis() {
        return Math.max(5_000L, workerStaleMillis());
    }

    private long pollWaitMillis() {
        return Math.max(1, chatProperties.getCompatibility().getFrontendBridgePollWaitSeconds()) * 1000L;
    }

    private static FrontendBridgeGenerationException bridgeFailure(BridgeJob job, Throwable cause) {
        return new FrontendBridgeGenerationException(cause, job != null && job.dispatched().get());
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
            BlockingQueue<BridgePartial> partials,
            AtomicBoolean cancelled,
            AtomicBoolean dispatched,
            AtomicInteger lastPartialSequence,
            AtomicReference<BridgePartial> lastPartial,
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
                    request.userPersona(),
                    request.charName(),
                    request.stWorldNames() == null ? List.of() : List.copyOf(request.stWorldNames()),
                    request.tailSystemPrompt(),
                    request.runtimePresetBundle(),
                    BridgeModelOverride.from(request.userModelOverride()),
                    System.currentTimeMillis()
            );
            return new BridgeJob(
                    id,
                    payload,
                    new CompletableFuture<>(),
                    new LinkedBlockingQueue<>(),
                    new AtomicBoolean(false),
                    new AtomicBoolean(false),
                    new AtomicInteger(-1),
                    new AtomicReference<>(),
                    System.currentTimeMillis() + timeoutMillis
            );
        }

        boolean expired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }

        void markDispatched(String workerId) {
            dispatched.set(true);
            log.info("frontend bridge job dispatched jobId={} conversationId={} mode={} workerId={}",
                    id, payload.conversationId(), payload.mode(), workerId);
        }

        synchronized boolean acceptPartial(BridgePartial partial) {
            if (cancelled.get() || expired()) {
                return false;
            }
            int previousSequence = lastPartialSequence.get();
            if (partial.sequence() == previousSequence) {
                return partial.equals(lastPartial.get());
            }
            if (partial.sequence() != previousSequence + 1) {
                return false;
            }
            if (!partials.offer(partial)) {
                return false;
            }
            lastPartialSequence.set(partial.sequence());
            lastPartial.set(partial);
            return true;
        }
    }

    public static final class FrontendBridgeGenerationException extends StUnavailableException {

        private final boolean dispatched;

        private FrontendBridgeGenerationException(Throwable cause, boolean dispatched) {
            super(cause);
            this.dispatched = dispatched;
        }

        public boolean wasDispatched() {
            return dispatched;
        }
    }

    private record WorkerState(
            String workerId,
            long lastSeenMillis,
            boolean appReady,
            boolean modelConnected,
            boolean busy,
            boolean generating,
            String activeJobId,
            List<String> loadedExtensions
    ) {
    }

    public record WorkerHeartbeat(
            boolean appReady,
            boolean modelConnected,
            boolean busy,
            boolean generating,
            String activeJobId,
            List<String> loadedExtensions
    ) {
        static WorkerHeartbeat notReady() {
            return new WorkerHeartbeat(false, false, false, false, "", List.of());
        }

        WorkerHeartbeat normalized() {
            String safeJobId = activeJobId == null ? "" : activeJobId.trim();
            if (safeJobId.length() > 96) {
                safeJobId = safeJobId.substring(0, 96);
            }
            List<String> safeExtensions = loadedExtensions == null
                    ? List.of()
                    : loadedExtensions.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .limit(128)
                    .toList();
            return new WorkerHeartbeat(
                    appReady, modelConnected, busy, generating, safeJobId, safeExtensions);
        }
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
            String userPersona,
            String charName,
            List<String> worldNames,
            String tailSystemPrompt,
            String runtimePresetBundle,
            BridgeModelOverride modelOverride,
            long createdAtMillis
    ) {
    }

    public record BridgeModelOverride(
            String providerSource,
            String modelName,
            String apiKey,
            String customUrl,
            String reverseProxy
    ) {
        private static final Map<String, String> PROVIDER_BASE_URLS = Map.of(
                "siliconflow", "https://api.siliconflow.cn/v1",
                "deepseek", "https://api.deepseek.com",
                "openrouter", "https://openrouter.ai/api/v1",
                "openai", "https://api.openai.com/v1",
                "groq", "https://api.groq.com/openai/v1",
                "mistralai", "https://api.mistral.ai/v1",
                "moonshot", "https://api.moonshot.cn/v1",
                "xai", "https://api.x.ai/v1",
                "fireworks", "https://api.fireworks.ai/inference/v1",
                "custom", ""
        );

        static BridgeModelOverride from(com.example.sillyspringboot.integration.sillytavern.dto.UserModelOverride source) {
            if (source == null) {
                return null;
            }
            String provider = normalize(source.providerSource()).toLowerCase(java.util.Locale.ROOT);
            String model = normalize(source.textModelOrFallback());
            String secret = normalize(source.apiKey());
            String customUrl = normalize(source.customUrl());
            if (!PROVIDER_BASE_URLS.containsKey(provider)) {
                throw new IllegalArgumentException("unsupported frontend bridge BYOK provider: " + provider);
            }
            if (model.isBlank() || secret.isBlank()) {
                throw new IllegalArgumentException("frontend bridge BYOK model/key missing");
            }
            if ("custom".equals(provider) && customUrl.isBlank()) {
                throw new IllegalArgumentException("frontend bridge custom BYOK URL missing");
            }
            return new BridgeModelOverride(
                    provider, model, secret, "custom".equals(provider) ? customUrl : "",
                    "custom".equals(provider) ? customUrl : PROVIDER_BASE_URLS.get(provider));
        }

        public static boolean supports(com.example.sillyspringboot.integration.sillytavern.dto.UserModelOverride source) {
            if (source == null) {
                return true;
            }
            try {
                from(source);
                return true;
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }

        @Override
        public String toString() {
            return "BridgeModelOverride[providerSource=" + providerSource
                    + ", modelName=" + modelName
                    + ", apiKey=[REDACTED], customUrl=" + (customUrl.isBlank() ? "" : "[CONFIGURED]")
                    + ", reverseProxy=" + (reverseProxy.isBlank() ? "" : "[CONFIGURED]") + "]";
        }
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

    public record BridgePartial(
            String jobId,
            int sequence,
            String messageId,
            String text
    ) {
        BridgePartial normalized() {
            String safeJobId = jobId == null ? "" : jobId.trim();
            String safeMessageId = messageId == null ? "" : messageId.trim();
            String safeText = text == null ? "" : text;
            return new BridgePartial(safeJobId, Math.max(0, sequence), safeMessageId, safeText);
        }
    }

    public record BridgeStatus(
            boolean enabled,
            int queuedJobs,
            int activeJobs,
            int onlineWorkers,
            int readyWorkers,
            long requestTimeoutMillis,
            long pollWaitMillis,
            long workerStaleMillis,
            List<Map<String, Object>> workers
    ) {
    }
}
