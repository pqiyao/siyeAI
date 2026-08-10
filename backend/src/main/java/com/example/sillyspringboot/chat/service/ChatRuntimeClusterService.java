package com.example.sillyspringboot.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatRuntimeClusterService {

    private static final Logger log = LoggerFactory.getLogger(ChatRuntimeClusterService.class);
    private static final String INSTANCE_INDEX_KEY = "chat:runtime:instances";
    private static final String INSTANCE_KEY_PREFIX = "chat:runtime:instance:";
    private static final String CANCEL_KEY_PREFIX = "chat:runtime:cancel:";
    private static final Duration INSTANCE_TTL = Duration.ofSeconds(20);
    private static final long INSTANCE_STALE_MILLIS = 15_000L;
    private static final int MAX_INSTANCES = 100;
    private static final int MAX_REPORTED_IDS = 500;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AppChatRuntimeRegistry runtimeRegistry;
    private final ChatGenerationDispatcher dispatcher;
    private final AppChatFrontendBridgeService bridge;
    private final Duration cancellationTtl;
    private final String instanceId;
    private final AtomicBoolean redisFailureLogged = new AtomicBoolean(false);

    public ChatRuntimeClusterService(
            ObjectProvider<StringRedisTemplate> redisProvider,
            ObjectMapper objectMapper,
            AppChatRuntimeRegistry runtimeRegistry,
            ChatGenerationDispatcher dispatcher,
            AppChatFrontendBridgeService bridge,
            com.example.sillyspringboot.chat.config.AppChatProperties chatProperties,
            @Value("${APP_INSTANCE_ID:}") String configuredInstanceId
    ) {
        this.redis = redisProvider.getIfAvailable();
        this.objectMapper = objectMapper;
        this.runtimeRegistry = runtimeRegistry;
        this.dispatcher = dispatcher;
        this.bridge = bridge;
        long cancelSeconds = Math.max(
                900L,
                (long) chatProperties.getGenerationTimeoutSeconds()
                        + chatProperties.getMaxQueueWaitSeconds()
                        + 120L
        );
        this.cancellationTtl = Duration.ofSeconds(cancelSeconds);
        this.instanceId = resolveInstanceId(configuredInstanceId);
    }

    @Scheduled(initialDelay = 1_000L, fixedDelay = 1_000L)
    public void synchronizeRuntime() {
        pollDistributedCancellations();
        publishLocalSnapshot();
    }

    public ClusterOverview overview() {
        InstanceSnapshot local = localSnapshot();
        if (redis == null) {
            return aggregate(List.of(local), false);
        }
        try {
            writeSnapshot(local);
            long cutoff = System.currentTimeMillis() - INSTANCE_STALE_MILLIS;
            redis.opsForZSet().removeRangeByScore(INSTANCE_INDEX_KEY, 0, cutoff - 1);
            Set<String> activeIds = redis.opsForZSet().rangeByScore(
                    INSTANCE_INDEX_KEY,
                    cutoff,
                    Double.POSITIVE_INFINITY,
                    0,
                    MAX_INSTANCES
            );
            if (activeIds == null || activeIds.isEmpty()) {
                return aggregate(List.of(local), true);
            }
            List<String> orderedIds = activeIds.stream().sorted().toList();
            List<String> payloads = redis.opsForValue().multiGet(
                    orderedIds.stream().map(this::instanceKey).toList()
            );
            List<InstanceSnapshot> snapshots = new ArrayList<>();
            if (payloads != null) {
                for (String payload : payloads) {
                    InstanceSnapshot snapshot = parseSnapshot(payload);
                    if (snapshot != null && snapshot.observedAtMillis() >= cutoff) {
                        snapshots.add(snapshot);
                    }
                }
            }
            if (snapshots.stream().noneMatch(snapshot -> instanceId.equals(snapshot.instanceId()))) {
                snapshots.add(local);
            }
            markRedisHealthy();
            return aggregate(snapshots, true);
        } catch (RuntimeException ex) {
            logRedisFailure("reading chat runtime cluster status", ex);
            return aggregate(List.of(local), false);
        }
    }

    public CancellationSignal requestCancellation(long taskId) {
        boolean localSignalled = runtimeRegistry.cancelTask(taskId);
        if (redis == null) {
            return new CancellationSignal(localSignalled, false);
        }
        try {
            redis.opsForValue().set(cancelKey(taskId), instanceId, cancellationTtl);
            markRedisHealthy();
            return new CancellationSignal(localSignalled, true);
        } catch (RuntimeException ex) {
            logRedisFailure("publishing chat task cancellation", ex);
            return new CancellationSignal(localSignalled, false);
        }
    }

    public String instanceId() {
        return instanceId;
    }

    private void pollDistributedCancellations() {
        if (redis == null) {
            return;
        }
        List<Long> taskIds = runtimeRegistry.status().taskIds();
        if (taskIds.isEmpty()) {
            return;
        }
        try {
            List<String> values = redis.opsForValue().multiGet(
                    taskIds.stream().map(this::cancelKey).toList()
            );
            if (values != null) {
                for (int i = 0; i < Math.min(taskIds.size(), values.size()); i++) {
                    if (values.get(i) != null) {
                        runtimeRegistry.cancelTask(taskIds.get(i));
                    }
                }
            }
            markRedisHealthy();
        } catch (RuntimeException ex) {
            logRedisFailure("polling chat task cancellations", ex);
        }
    }

    private void publishLocalSnapshot() {
        if (redis == null) {
            return;
        }
        try {
            writeSnapshot(localSnapshot());
            markRedisHealthy();
        } catch (RuntimeException ex) {
            logRedisFailure("publishing chat runtime heartbeat", ex);
        }
    }

    private void writeSnapshot(InstanceSnapshot snapshot) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize chat runtime snapshot", ex);
        }
        redis.opsForValue().set(instanceKey(instanceId), payload, INSTANCE_TTL);
        redis.opsForZSet().add(INSTANCE_INDEX_KEY, instanceId, snapshot.observedAtMillis());
    }

    private InstanceSnapshot parseSnapshot(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, InstanceSnapshot.class);
        } catch (Exception ex) {
            log.warn("ignored malformed chat runtime snapshot: {}", ex.getMessage());
            return null;
        }
    }

    private InstanceSnapshot localSnapshot() {
        return new InstanceSnapshot(
                instanceId,
                System.currentTimeMillis(),
                dispatcher.status(),
                runtimeRegistry.status(),
                bridge.status()
        );
    }

    private ClusterOverview aggregate(List<InstanceSnapshot> rawSnapshots, boolean distributed) {
        List<InstanceSnapshot> snapshots = rawSnapshots.stream()
                .filter(snapshot -> snapshot != null && snapshot.instanceId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        InstanceSnapshot::instanceId,
                        snapshot -> snapshot,
                        (left, right) -> left.observedAtMillis() >= right.observedAtMillis() ? left : right,
                        LinkedHashMap::new
                ))
                .values().stream()
                .sorted(Comparator.comparing(InstanceSnapshot::instanceId))
                .toList();

        int queued = 0;
        int remainingCapacity = 0;
        int active = 0;
        int poolSize = 0;
        long completed = 0L;
        long submitted = 0L;
        int activeConversations = 0;
        int activeTasks = 0;
        LinkedHashSet<Long> conversationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> taskIds = new LinkedHashSet<>();
        boolean bridgeEnabled = false;
        int bridgeQueued = 0;
        int bridgeActive = 0;
        int onlineWorkers = 0;
        int readyWorkers = 0;
        long requestTimeoutMillis = 0L;
        long pollWaitMillis = 0L;
        long workerStaleMillis = 0L;
        List<Map<String, Object>> workers = new ArrayList<>();
        List<InstanceView> instances = new ArrayList<>();

        for (InstanceSnapshot snapshot : snapshots) {
            ChatGenerationDispatcher.DispatcherStatus dispatcherStatus = snapshot.dispatcher();
            if (dispatcherStatus != null) {
                queued += dispatcherStatus.queued();
                remainingCapacity += dispatcherStatus.remainingCapacity();
                active += dispatcherStatus.active();
                poolSize += dispatcherStatus.poolSize();
                completed += dispatcherStatus.completed();
                submitted += dispatcherStatus.submitted();
            }
            AppChatRuntimeRegistry.RuntimeStatus runtimeStatus = snapshot.runtime();
            if (runtimeStatus != null) {
                activeConversations += runtimeStatus.activeConversations();
                activeTasks += runtimeStatus.activeTasks();
                addLimited(conversationIds, runtimeStatus.conversationIds());
                addLimited(taskIds, runtimeStatus.taskIds());
            }
            AppChatFrontendBridgeService.BridgeStatus bridgeStatus = snapshot.bridge();
            if (bridgeStatus != null) {
                bridgeEnabled |= bridgeStatus.enabled();
                bridgeQueued += bridgeStatus.queuedJobs();
                bridgeActive += bridgeStatus.activeJobs();
                onlineWorkers += bridgeStatus.onlineWorkers();
                readyWorkers += bridgeStatus.readyWorkers();
                requestTimeoutMillis = Math.max(requestTimeoutMillis, bridgeStatus.requestTimeoutMillis());
                pollWaitMillis = Math.max(pollWaitMillis, bridgeStatus.pollWaitMillis());
                workerStaleMillis = Math.max(workerStaleMillis, bridgeStatus.workerStaleMillis());
                if (bridgeStatus.workers() != null) {
                    for (Map<String, Object> worker : bridgeStatus.workers()) {
                        Map<String, Object> row = new LinkedHashMap<>(worker);
                        row.put("instanceId", snapshot.instanceId());
                        workers.add(row);
                    }
                }
            }
            instances.add(new InstanceView(
                    snapshot.instanceId(),
                    snapshot.instanceId().equals(instanceId),
                    snapshot.observedAtMillis(),
                    dispatcherStatus == null ? 0 : dispatcherStatus.active(),
                    runtimeStatus == null ? 0 : runtimeStatus.activeTasks()
            ));
        }

        return new ClusterOverview(
                distributed,
                instanceId,
                instances.size(),
                new DispatcherAggregate(queued, remainingCapacity, active, poolSize, completed, submitted),
                new RuntimeAggregate(
                        activeConversations,
                        activeTasks,
                        conversationIds.stream().sorted().toList(),
                        taskIds.stream().sorted().toList()
                ),
                new BridgeAggregate(
                        bridgeEnabled,
                        bridgeQueued,
                        bridgeActive,
                        onlineWorkers,
                        readyWorkers,
                        requestTimeoutMillis,
                        pollWaitMillis,
                        workerStaleMillis,
                        workers
                ),
                instances
        );
    }

    private static void addLimited(LinkedHashSet<Long> destination, List<Long> values) {
        if (values == null) {
            return;
        }
        for (Long value : values) {
            if (destination.size() >= MAX_REPORTED_IDS) {
                return;
            }
            if (value != null) {
                destination.add(value);
            }
        }
    }

    private String instanceKey(String id) {
        return INSTANCE_KEY_PREFIX + id;
    }

    private String cancelKey(long taskId) {
        return CANCEL_KEY_PREFIX + taskId;
    }

    private void markRedisHealthy() {
        if (redisFailureLogged.compareAndSet(true, false)) {
            log.info("Redis coordination recovered for chat runtime center");
        }
    }

    private void logRedisFailure(String action, RuntimeException ex) {
        if (redisFailureLogged.compareAndSet(false, true)) {
            log.warn("Redis unavailable while {}: {}", action, ex.getMessage());
        }
    }

    private static String resolveInstanceId(String configured) {
        String safe = configured == null ? "" : configured.trim();
        if (!safe.isBlank()) {
            return sanitizeInstanceId(safe);
        }
        String host = "app";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
        }
        return sanitizeInstanceId(host + "-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private static String sanitizeInstanceId(String value) {
        String normalized = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return normalized.substring(0, Math.min(96, normalized.length()));
    }

    public record InstanceSnapshot(
            String instanceId,
            long observedAtMillis,
            ChatGenerationDispatcher.DispatcherStatus dispatcher,
            AppChatRuntimeRegistry.RuntimeStatus runtime,
            AppChatFrontendBridgeService.BridgeStatus bridge
    ) {
    }

    public record DispatcherAggregate(
            int queued,
            int remainingCapacity,
            int active,
            int poolSize,
            long completed,
            long submitted
    ) {
    }

    public record RuntimeAggregate(
            int activeConversations,
            int activeTasks,
            List<Long> conversationIds,
            List<Long> taskIds
    ) {
    }

    public record BridgeAggregate(
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

    public record InstanceView(
            String instanceId,
            boolean current,
            long observedAtMillis,
            int activeThreads,
            int activeTasks
    ) {
    }

    public record ClusterOverview(
            boolean distributed,
            String currentInstanceId,
            int instanceCount,
            DispatcherAggregate dispatcher,
            RuntimeAggregate runtime,
            BridgeAggregate bridge,
            List<InstanceView> instances
    ) {
    }

    public record CancellationSignal(boolean localSignalled, boolean distributedAccepted) {
        public boolean accepted() {
            return localSignalled || distributedAccepted;
        }
    }
}
