package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.mapper.ExternalCleanupTaskMapper;
import com.example.sillyspringboot.admin.model.ExternalCleanupTask;
import com.example.sillyspringboot.config.ExternalCleanupProperties;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ExternalCleanupTaskService {

    static final String TYPE_ST_CHAT = "ST_CHAT";
    static final String TYPE_ST_CHARACTER = "ST_CHARACTER";
    static final String TYPE_LOCAL_UPLOAD = "LOCAL_UPLOAD";

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_PROCESSING = "PROCESSING";
    static final String STATUS_RETRY = "RETRY";
    static final String STATUS_COMPLETED = "COMPLETED";
    static final String STATUS_DEAD = "DEAD";

    private static final Logger log = LoggerFactory.getLogger(ExternalCleanupTaskService.class);
    private static final int MAX_ERROR_LENGTH = 4_000;
    private static final Pattern OWNED_UPLOAD_FILE = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:\\.[a-z0-9]{1,10})?"
    );

    private final ExternalCleanupTaskMapper mapper;
    private final StAdapter stAdapter;
    private final ExternalCleanupProperties properties;
    private final Path h5UploadRoot;

    public ExternalCleanupTaskService(
            ExternalCleanupTaskMapper mapper,
            StAdapter stAdapter,
            ExternalCleanupProperties properties,
            @Value("${app.upload.dir:${user.dir}/data/uploads}") String uploadRootDir
    ) {
        this.mapper = mapper;
        this.stAdapter = stAdapter;
        this.properties = properties;
        this.h5UploadRoot = Path.of(uploadRootDir).toAbsolutePath().normalize().resolve("h5").normalize();
    }

    public List<String> enqueueUserDeletionTasks(
            long userId,
            List<Map<String, Object>> stChats,
            List<Map<String, Object>> ownedCharacters,
            Set<String> localAssetUrls
    ) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Map<String, CleanupDraft> drafts = new LinkedHashMap<>();
        collectStChatDrafts(drafts, stChats);
        collectStCharacterDrafts(drafts, ownedCharacters);
        collectLocalUploadDrafts(drafts, localAssetUrls);

        List<String> taskIds = new ArrayList<>(drafts.size());
        LocalDateTime now = LocalDateTime.now();
        int maxAttempts = clamp(properties.getMaxAttempts(), 1, 100);
        for (CleanupDraft draft : drafts.values()) {
            String taskKey = taskKey(userId, draft);
            ExternalCleanupTask task = new ExternalCleanupTask();
            task.setId(UUID.randomUUID().toString());
            task.setTaskKey(taskKey);
            task.setSourceUserId(userId);
            task.setResourceType(draft.resourceType());
            task.setPrimaryRef(draft.primaryRef());
            task.setSecondaryRef(draft.secondaryRef());
            task.setStatus(STATUS_PENDING);
            task.setAttemptCount(0);
            task.setMaxAttempts(maxAttempts);
            task.setNextAttemptAt(now);
            mapper.insertOrKeep(task);

            ExternalCleanupTask persisted = mapper.findByTaskKey(taskKey);
            if (persisted == null || persisted.getId() == null || persisted.getId().isBlank()) {
                throw new IllegalStateException("external cleanup task was not persisted");
            }
            taskIds.add(persisted.getId());
        }
        return List.copyOf(taskIds);
    }

    public List<CleanupAttempt> processImmediately(Collection<String> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        Set<String> distinctIds = new LinkedHashSet<>();
        for (String taskId : taskIds) {
            if (taskId != null && !taskId.isBlank()) {
                distinctIds.add(taskId.trim());
            }
        }
        List<CleanupAttempt> results = new ArrayList<>(distinctIds.size());
        for (String taskId : distinctIds) {
            results.add(processOneSafely(taskId));
        }
        return List.copyOf(results);
    }

    @Scheduled(
            initialDelayString = "${app.external-cleanup.retry-initial-delay-ms:60000}",
            fixedDelayString = "${app.external-cleanup.retry-interval-ms:60000}"
    )
    public void scheduledRetry() {
        if (!properties.isRetryEnabled()) {
            return;
        }
        try {
            CleanupBatchResult result = retryDueTasks();
            if (result.expiredToDead() > 0) {
                log.warn("external cleanup exhausted leases moved to DEAD count={}", result.expiredToDead());
            }
            if (result.processed() > 0) {
                log.info(
                        "external cleanup retry batch processed={} completed={} retry={} dead={} deferred={}",
                        result.processed(),
                        result.completed(),
                        result.retry(),
                        result.dead(),
                        result.deferred()
                );
            }
        } catch (RuntimeException ex) {
            log.warn("external cleanup retry batch failed", ex);
        }
    }

    CleanupBatchResult retryDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        int expiredToDead = mapper.expireExhaustedProcessingTasks(
                now,
                "processing lease expired after final attempt"
        );
        int batchSize = clamp(properties.getBatchSize(), 1, 500);
        List<ExternalCleanupTask> due = mapper.listDueTasks(now, batchSize);
        if (due == null || due.isEmpty()) {
            return new CleanupBatchResult(0, 0, 0, 0, 0, Math.max(0, expiredToDead));
        }

        int completed = 0;
        int retry = 0;
        int dead = 0;
        int deferred = 0;
        for (ExternalCleanupTask task : due) {
            CleanupAttempt attempt = processOneSafely(task.getId());
            switch (attempt.status()) {
                case STATUS_COMPLETED -> completed++;
                case STATUS_RETRY -> retry++;
                case STATUS_DEAD -> dead++;
                default -> deferred++;
            }
        }
        return new CleanupBatchResult(
                due.size(),
                completed,
                retry,
                dead,
                deferred,
                Math.max(0, expiredToDead)
        );
    }

    private CleanupAttempt processOneSafely(String taskId) {
        try {
            return processOne(taskId);
        } catch (RuntimeException ex) {
            log.warn("external cleanup processing unavailable taskId={}", taskId, ex);
            return new CleanupAttempt(
                    taskId,
                    "",
                    "UNAVAILABLE",
                    "cleanup processing unavailable: " + clip(rootMessage(ex), MAX_ERROR_LENGTH)
            );
        }
    }

    private CleanupAttempt processOne(String taskId) {
        ExternalCleanupTask beforeClaim = mapper.findById(taskId);
        if (beforeClaim == null) {
            return new CleanupAttempt(taskId, "", "MISSING", "cleanup task no longer exists");
        }
        if (STATUS_COMPLETED.equals(beforeClaim.getStatus())) {
            return new CleanupAttempt(taskId, beforeClaim.getResourceType(), STATUS_COMPLETED, "already completed");
        }
        if (STATUS_DEAD.equals(beforeClaim.getStatus())) {
            return new CleanupAttempt(taskId, beforeClaim.getResourceType(), STATUS_DEAD, safeError(beforeClaim));
        }

        LocalDateTime now = LocalDateTime.now();
        String lockToken = UUID.randomUUID().toString();
        long leaseSeconds = clamp(properties.getProcessingLeaseSeconds(), 30L, 3_600L);
        int claimed = mapper.claimTask(taskId, now, now.plusSeconds(leaseSeconds), lockToken);
        if (claimed != 1) {
            ExternalCleanupTask current = mapper.findById(taskId);
            String currentStatus = current == null ? "MISSING" : current.getStatus();
            String resourceType = current == null ? beforeClaim.getResourceType() : current.getResourceType();
            return new CleanupAttempt(taskId, resourceType, currentStatus, "cleanup task is not currently claimable");
        }

        ExternalCleanupTask claimedTask = mapper.findById(taskId);
        if (claimedTask == null || !lockToken.equals(claimedTask.getLockToken())) {
            return new CleanupAttempt(taskId, beforeClaim.getResourceType(), "SUPERSEDED", "cleanup task claim was superseded");
        }

        try {
            deleteExternalResource(claimedTask);
            int updated = mapper.markCompleted(taskId, lockToken, LocalDateTime.now());
            if (updated != 1) {
                return new CleanupAttempt(taskId, claimedTask.getResourceType(), "SUPERSEDED", "cleanup completion was superseded");
            }
            return new CleanupAttempt(taskId, claimedTask.getResourceType(), STATUS_COMPLETED, "cleanup completed");
        } catch (RuntimeException ex) {
            return recordFailure(claimedTask, lockToken, ex);
        }
    }

    private CleanupAttempt recordFailure(ExternalCleanupTask task, String lockToken, RuntimeException failure) {
        int attemptCount = safeInt(task.getAttemptCount());
        int maxAttempts = Math.max(1, safeInt(task.getMaxAttempts()));
        boolean exhausted = attemptCount >= maxAttempts;
        String status = exhausted ? STATUS_DEAD : STATUS_RETRY;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextAttemptAt = exhausted ? null : now.plusSeconds(backoffSeconds(attemptCount));
        String error = clip(rootMessage(failure), MAX_ERROR_LENGTH);
        int updated = mapper.markFailed(task.getId(), lockToken, status, nextAttemptAt, error, now);
        if (updated != 1) {
            return new CleanupAttempt(task.getId(), task.getResourceType(), "SUPERSEDED", "cleanup failure was superseded");
        }

        if (exhausted) {
            log.error(
                    "external cleanup permanently failed taskId={} type={} attempt={}/{} error={}",
                    task.getId(),
                    task.getResourceType(),
                    attemptCount,
                    maxAttempts,
                    error
            );
        } else {
            log.warn(
                    "external cleanup scheduled for retry taskId={} type={} attempt={}/{} nextAttemptAt={} error={}",
                    task.getId(),
                    task.getResourceType(),
                    attemptCount,
                    maxAttempts,
                    nextAttemptAt,
                    error
            );
        }
        return new CleanupAttempt(task.getId(), task.getResourceType(), status, error);
    }

    private void deleteExternalResource(ExternalCleanupTask task) {
        String resourceType = text(task.getResourceType());
        String primaryRef = text(task.getPrimaryRef());
        switch (resourceType) {
            case TYPE_ST_CHAT -> stAdapter.deleteChat(primaryRef, text(task.getSecondaryRef()));
            case TYPE_ST_CHARACTER -> stAdapter.deleteCharacter(primaryRef, true);
            case TYPE_LOCAL_UPLOAD -> deleteLocalUpload(primaryRef);
            default -> throw new IllegalArgumentException("unsupported cleanup resource type: " + resourceType);
        }
    }

    private void deleteLocalUpload(String relativePath) {
        Path target = resolveStoredUploadPath(relativePath);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to delete local upload", ex);
        }
    }

    private Path resolveStoredUploadPath(String relativePath) {
        String safe = normalizeOwnedUploadRelativePath(relativePath);
        Path target = h5UploadRoot.resolve(safe).normalize();
        if (!target.startsWith(h5UploadRoot) || target.equals(h5UploadRoot)) {
            throw new IllegalArgumentException("local upload path escapes upload root");
        }
        return target;
    }

    private void collectStChatDrafts(
            Map<String, CleanupDraft> drafts,
            List<Map<String, Object>> rows
    ) {
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            String avatarUrl = text(row == null ? null : row.get("stAvatarUrl"));
            String chatFileName = text(row == null ? null : row.get("stChatFileName"));
            addDraft(drafts, TYPE_ST_CHAT, avatarUrl, chatFileName);
        }
    }

    private void collectStCharacterDrafts(
            Map<String, CleanupDraft> drafts,
            List<Map<String, Object>> rows
    ) {
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            String avatarUrl = text(row == null ? null : row.get("stAvatarUrl"));
            addDraft(drafts, TYPE_ST_CHARACTER, avatarUrl, "");
        }
    }

    private void collectLocalUploadDrafts(
            Map<String, CleanupDraft> drafts,
            Set<String> assetUrls
    ) {
        if (assetUrls == null) {
            return;
        }
        for (String assetUrl : assetUrls) {
            String relativePath = normalizeOwnedUploadRelativePath(assetUrl);
            addDraft(drafts, TYPE_LOCAL_UPLOAD, relativePath, "");
        }
    }

    private static void addDraft(
            Map<String, CleanupDraft> drafts,
            String resourceType,
            String primaryRef,
            String secondaryRef
    ) {
        String safePrimary = text(primaryRef);
        String safeSecondary = text(secondaryRef);
        if (safePrimary.isBlank() || (TYPE_ST_CHAT.equals(resourceType) && safeSecondary.isBlank())) {
            return;
        }
        CleanupDraft draft = new CleanupDraft(resourceType, safePrimary, safeSecondary);
        drafts.putIfAbsent(resourceType + "\n" + safePrimary + "\n" + safeSecondary, draft);
    }

    private static String normalizeOwnedUploadRelativePath(String rawPath) {
        String path = text(rawPath).replace('\\', '/');
        if (!OWNED_UPLOAD_FILE.matcher(path).matches()) {
            throw new IllegalArgumentException("owned upload path is invalid");
        }
        return path;
    }

    private String taskKey(long userId, CleanupDraft draft) {
        String material = userId
                + "\n" + draft.resourceType()
                + "\n" + draft.primaryRef()
                + "\n" + draft.secondaryRef();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    long backoffSeconds(int attemptCount) {
        long base = clamp(properties.getBaseBackoffSeconds(), 1L, 86_400L);
        long configuredMax = clamp(properties.getMaxBackoffSeconds(), 1L, 604_800L);
        long maximum = Math.max(base, configuredMax);
        int exponent = Math.max(0, Math.min(attemptCount - 1, 30));
        long multiplier = 1L << exponent;
        if (base > maximum / multiplier) {
            return maximum;
        }
        return Math.min(maximum, base * multiplier);
    }

    private static int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private static String safeError(ExternalCleanupTask task) {
        String error = text(task == null ? null : task.getLastError());
        return error.isBlank() ? "cleanup attempts exhausted" : error;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null && cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor == null ? "" : cursor.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private static String clip(String value, int maxLength) {
        String safe = text(value);
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record CleanupDraft(String resourceType, String primaryRef, String secondaryRef) {
    }

    public record CleanupAttempt(String taskId, String resourceType, String status, String message) {

        public boolean succeeded() {
            return STATUS_COMPLETED.equals(status);
        }
    }

    record CleanupBatchResult(
            int processed,
            int completed,
            int retry,
            int dead,
            int deferred,
            int expiredToDead
    ) {
    }
}
