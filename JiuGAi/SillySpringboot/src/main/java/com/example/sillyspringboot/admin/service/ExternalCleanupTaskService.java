package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.mapper.CharacterCleanupMapper;
import com.example.sillyspringboot.admin.model.CharacterUploadAssetRow;
import com.example.sillyspringboot.admin.mapper.ExternalCleanupTaskMapper;
import com.example.sillyspringboot.admin.model.ExternalCleanupTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.sillyspringboot.config.ExternalCleanupProperties;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StWorldbookOptionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    static final String TYPE_ST_WORLDBOOK = "ST_WORLDBOOK";
    static final String TYPE_ST_MEMORY_WORLDBOOK_SET = "ST_MEMORY_WORLDBOOK_SET";
    static final String TYPE_LOCAL_UPLOAD = "LOCAL_UPLOAD";
    static final String TYPE_CHARACTER_ST_BUNDLE = "CHARACTER_ST_BUNDLE";
    static final String TYPE_CHARACTER_LOCAL_UPLOAD = "CHARACTER_LOCAL_UPLOAD";

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_PROCESSING = "PROCESSING";
    static final String STATUS_RETRY = "RETRY";
    static final String STATUS_COMPLETED = "COMPLETED";
    static final String STATUS_DEAD = "DEAD";
    static final String STATUS_SKIPPED_SHARED = "SKIPPED_SHARED";

    private static final Logger log = LoggerFactory.getLogger(ExternalCleanupTaskService.class);
    private static final int MAX_ERROR_LENGTH = 4_000;
    private static final Pattern OWNED_UPLOAD_FILE = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:\\.[a-z0-9]{1,10})?"
    );

    private final ExternalCleanupTaskMapper mapper;
    private final CharacterCleanupMapper characterCleanupMapper;
    private final StAdapter stAdapter;
    private final ExternalCleanupProperties properties;
    private final Path h5UploadRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExternalCleanupTaskService(
            ExternalCleanupTaskMapper mapper,
            CharacterCleanupMapper characterCleanupMapper,
            StAdapter stAdapter,
            ExternalCleanupProperties properties,
            @Value("${app.upload.dir:${user.dir}/data/uploads}") String uploadRootDir
    ) {
        this.mapper = mapper;
        this.characterCleanupMapper = characterCleanupMapper;
        this.stAdapter = stAdapter;
        this.properties = properties;
        this.h5UploadRoot = Path.of(uploadRootDir).toAbsolutePath().normalize().resolve("h5").normalize();
    }

    public List<String> enqueueUserDeletionTasks(
            long userId,
            List<Map<String, Object>> stChats,
            List<Map<String, Object>> ownedCharacters,
            List<Map<String, Object>> memoryWorldbooks,
            Set<String> localAssetUrls
    ) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId must be positive");
        }
        Map<String, CleanupDraft> drafts = new LinkedHashMap<>();
        collectStChatDrafts(drafts, stChats);
        collectStCharacterDrafts(drafts, ownedCharacters);
        collectMemoryWorldbookDrafts(drafts, memoryWorldbooks);
        collectLocalUploadDrafts(drafts, localAssetUrls);

        return persistDrafts(userId, drafts);
    }

    public String enqueueCharacterStBundle(
            String operationId,
            long sourceCharacterId,
            Long sourceUserId,
            String stAvatarUrl,
            List<Long> targetCharacterIds
    ) {
        CharacterCleanupContext context = new CharacterCleanupContext(
                normalizeCharacterIds(targetCharacterIds),
                null,
                null,
                ""
        );
        return persistCharacterTask(
                operationId,
                sourceCharacterId,
                sourceUserId,
                TYPE_CHARACTER_ST_BUNDLE,
                text(stAvatarUrl),
                context
        );
    }

    public String enqueueCharacterLocalUpload(
            String operationId,
            long sourceCharacterId,
            long sourceUserId,
            String assetUrl,
            long assetId,
            String relativePath,
            List<Long> targetCharacterIds
    ) {
        String safeRelativePath = normalizeLocalUploadReference(relativePath);
        CharacterCleanupContext context = new CharacterCleanupContext(
                normalizeCharacterIds(targetCharacterIds),
                sourceUserId,
                assetId,
                text(assetUrl)
        );
        return persistCharacterTask(
                operationId,
                sourceCharacterId,
                sourceUserId,
                TYPE_CHARACTER_LOCAL_UPLOAD,
                safeRelativePath,
                context
        );
    }

    public List<String> enqueueUserDeletionTasks(
            long userId,
            List<Map<String, Object>> stChats,
            List<Map<String, Object>> ownedCharacters,
            Set<String> localAssetUrls
    ) {
        return enqueueUserDeletionTasks(userId, stChats, ownedCharacters, List.of(), localAssetUrls);
    }

    @Transactional
    public String enqueueMemoryWorldbookSetDeletion(
            long sourceUserId,
            long conversationId,
            String baseWorldName
    ) {
        String safeWorldName = text(baseWorldName);
        String expectedPrefix = "jg_memory_conv_" + conversationId + "_";
        if (sourceUserId <= 0 || conversationId <= 0 || !safeWorldName.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("memory worldbook cleanup context is invalid");
        }
        Map<String, CleanupDraft> drafts = new LinkedHashMap<>();
        addDraft(
                drafts,
                TYPE_ST_MEMORY_WORLDBOOK_SET,
                safeWorldName,
                String.valueOf(conversationId)
        );
        List<String> taskIds = persistDrafts(
                sourceUserId,
                drafts,
                "BRANCH_MEMORY_DELETION"
        );
        if (taskIds.size() != 1) {
            throw new IllegalStateException("memory worldbook cleanup task was not persisted");
        }
        return taskIds.get(0);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<String> enqueueArtifactRollbackTasks(
            long sourceUserId,
            String stAvatarUrl,
            Collection<String> localAssetUrls
    ) {
        if (sourceUserId <= 0) {
            throw new IllegalArgumentException("sourceUserId must be positive");
        }
        Map<String, CleanupDraft> drafts = new LinkedHashMap<>();
        addDraft(drafts, TYPE_ST_CHARACTER, stAvatarUrl, "");
        collectLocalUploadDrafts(drafts, localAssetUrls);
        return persistDrafts(sourceUserId, drafts);
    }

    private List<String> persistDrafts(long sourceUserId, Map<String, CleanupDraft> drafts) {
        return persistDrafts(sourceUserId, drafts, "USER_DELETION");
    }

    private List<String> persistDrafts(
            long sourceUserId,
            Map<String, CleanupDraft> drafts,
            String sourceType
    ) {
        List<String> taskIds = new ArrayList<>(drafts.size());
        LocalDateTime now = LocalDateTime.now();
        int maxAttempts = clamp(properties.getMaxAttempts(), 1, 100);
        for (CleanupDraft draft : drafts.values()) {
            String taskKey = taskKey(sourceUserId, draft);
            ExternalCleanupTask task = new ExternalCleanupTask();
            task.setId(UUID.randomUUID().toString());
            task.setTaskKey(taskKey);
            String safeSourceType = text(sourceType);
            task.setSourceType(safeSourceType.isBlank() ? "USER_DELETION" : safeSourceType);
            task.setSourceUserId(sourceUserId);
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

    private String persistCharacterTask(
            String operationId,
            long sourceCharacterId,
            Long sourceUserId,
            String resourceType,
            String primaryRef,
            CharacterCleanupContext context
    ) {
        String safeOperationId = text(operationId);
        if (safeOperationId.isBlank() || sourceCharacterId <= 0 || primaryRef.isBlank()) {
            throw new IllegalArgumentException("character cleanup task context is invalid");
        }
        String contextJson;
        try {
            contextJson = objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("character cleanup context is invalid", ex);
        }

        ExternalCleanupTask task = new ExternalCleanupTask();
        task.setId(UUID.randomUUID().toString());
        task.setOperationId(safeOperationId);
        task.setSourceType("CHARACTER_DELETION");
        task.setSourceUserId(sourceUserId);
        task.setSourceCharacterId(sourceCharacterId);
        task.setResourceType(resourceType);
        task.setPrimaryRef(primaryRef);
        task.setSecondaryRef("");
        task.setContextJson(contextJson);
        task.setTaskKey(taskKey(safeOperationId, resourceType, primaryRef));
        task.setStatus(STATUS_PENDING);
        task.setAttemptCount(0);
        task.setMaxAttempts(clamp(properties.getMaxAttempts(), 1, 100));
        task.setNextAttemptAt(LocalDateTime.now());
        mapper.insertOrKeep(task);

        ExternalCleanupTask persisted = mapper.findByTaskKey(task.getTaskKey());
        if (persisted == null || persisted.getId() == null || persisted.getId().isBlank()) {
            throw new IllegalStateException("character cleanup task was not persisted");
        }
        return persisted.getId();
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
        } catch (CleanupDependencyPendingException ex) {
            LocalDateTime deferredAt = LocalDateTime.now();
            String error = clip(ex.getMessage(), MAX_ERROR_LENGTH);
            int updated = mapper.deferClaim(
                    taskId,
                    lockToken,
                    deferredAt.plusSeconds(clamp(properties.getBaseBackoffSeconds(), 1L, 86_400L)),
                    error,
                    deferredAt
            );
            if (updated != 1) {
                return new CleanupAttempt(taskId, claimedTask.getResourceType(), "SUPERSEDED", "cleanup deferral was superseded");
            }
            return new CleanupAttempt(taskId, claimedTask.getResourceType(), "DEFERRED", error);
        } catch (CleanupDependencyFailedException ex) {
            LocalDateTime completedAt = LocalDateTime.now();
            String error = clip(ex.getMessage(), MAX_ERROR_LENGTH);
            int updated = mapper.markSkipped(taskId, lockToken, STATUS_DEAD, error, completedAt);
            if (updated != 1) {
                return new CleanupAttempt(taskId, claimedTask.getResourceType(), "SUPERSEDED", "cleanup dependency failure was superseded");
            }
            return new CleanupAttempt(taskId, claimedTask.getResourceType(), STATUS_DEAD, error);
        } catch (UnsafeCleanupReferenceException ex) {
            LocalDateTime completedAt = LocalDateTime.now();
            String error = clip(ex.getMessage(), MAX_ERROR_LENGTH);
            int updated = mapper.markSkipped(
                    taskId,
                    lockToken,
                    STATUS_SKIPPED_SHARED,
                    error,
                    completedAt
            );
            if (updated != 1) {
                return new CleanupAttempt(taskId, claimedTask.getResourceType(), "SUPERSEDED", "cleanup skip was superseded");
            }
            return new CleanupAttempt(taskId, claimedTask.getResourceType(), STATUS_SKIPPED_SHARED, error);
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
            case TYPE_ST_WORLDBOOK -> stAdapter.deleteWorldbook(primaryRef);
            case TYPE_ST_MEMORY_WORLDBOOK_SET -> deleteMemoryWorldbookSet(task);
            case TYPE_LOCAL_UPLOAD -> deleteLocalUpload(primaryRef);
            case TYPE_CHARACTER_ST_BUNDLE -> deleteCharacterStBundle(task);
            case TYPE_CHARACTER_LOCAL_UPLOAD -> deleteCharacterLocalUpload(task);
            default -> throw new IllegalArgumentException("unsupported cleanup resource type: " + resourceType);
        }
    }

    private void deleteMemoryWorldbookSet(ExternalCleanupTask task) {
        long conversationId = positiveLong(task.getSecondaryRef());
        String baseWorldName = text(task.getPrimaryRef());
        String expectedPrefix = "jg_memory_conv_" + conversationId + "_";
        if (conversationId <= 0
                || !baseWorldName.startsWith(expectedPrefix)
                || baseWorldName.contains("..")
                || baseWorldName.contains("/")
                || baseWorldName.contains("\\")) {
            throw new UnsafeCleanupReferenceException("memory worldbook cleanup reference is invalid");
        }

        Set<String> candidates = new LinkedHashSet<>();
        List<StWorldbookOptionDto> options = stAdapter.listWorldbooks();
        if (options != null) {
            for (StWorldbookOptionDto option : options) {
                if (option == null) {
                    continue;
                }
                addMemoryWorldbookVersion(candidates, option.fileId(), baseWorldName);
                addMemoryWorldbookVersion(candidates, option.name(), baseWorldName);
            }
        }
        for (String candidate : candidates) {
            stAdapter.deleteWorldbook(candidate);
        }
    }

    private static void addMemoryWorldbookVersion(
            Set<String> candidates,
            String candidate,
            String baseWorldName
    ) {
        String safeCandidate = text(candidate);
        String revisionPrefix = baseWorldName + "_r";
        boolean revisioned = safeCandidate.startsWith(revisionPrefix)
                && safeCandidate.length() > revisionPrefix.length()
                && safeCandidate.substring(revisionPrefix.length()).chars().allMatch(Character::isDigit);
        if (safeCandidate.equals(baseWorldName) || revisioned) {
            candidates.add(safeCandidate);
        }
    }

    private void deleteCharacterStBundle(ExternalCleanupTask task) {
        CharacterCleanupContext context = parseCharacterContext(task);
        String stAvatarUrl = text(task.getPrimaryRef());
        if (characterCleanupMapper.countOtherCharacterStReferences(stAvatarUrl, context.targetCharacterIds()) > 0
                || characterCleanupMapper.countOtherBindingStReferences(stAvatarUrl, context.targetCharacterIds()) > 0) {
            throw new UnsafeCleanupReferenceException("ST resource is shared by another character");
        }
        stAdapter.deleteCharacter(stAvatarUrl, true);
    }

    private void deleteCharacterLocalUpload(ExternalCleanupTask task) {
        CharacterCleanupContext context = parseCharacterContext(task);
        String operationId = text(task.getOperationId());
        if (operationId.isBlank()) {
            throw new UnsafeCleanupReferenceException("character cleanup operation is missing");
        }
        if (mapper.countDeadCharacterStTasks(operationId) > 0) {
            throw new CleanupDependencyFailedException("ST cleanup permanently failed; local media was retained");
        }
        if (mapper.countBlockingCharacterStTasks(operationId) > 0) {
            throw new CleanupDependencyPendingException("local media cleanup is waiting for ST cleanup");
        }
        String relativePath = normalizeOwnedUploadRelativePath(task.getPrimaryRef());
        String assetUrl = text(context.assetUrl());
        if (assetUrl.isBlank() || context.expectedOwnerUserId() == null || context.assetId() == null) {
            throw new UnsafeCleanupReferenceException("local upload ownership is unknown");
        }

        CharacterUploadAssetRow asset = characterCleanupMapper.findUploadAsset(assetUrl);
        Path target = resolveStoredUploadPath(relativePath);
        if (asset == null) {
            if (Files.notExists(target)) {
                return;
            }
            throw new UnsafeCleanupReferenceException("local upload ownership record is missing");
        }
        long actualAssetId = positiveLong(asset.getAssetId());
        long actualOwnerUserId = positiveLong(asset.getOwnerUserId());
        String actualRelativePath = text(asset.getRelativePath());
        if (actualAssetId != context.assetId()
                || actualOwnerUserId != context.expectedOwnerUserId()
                || !relativePath.equals(actualRelativePath)) {
            throw new UnsafeCleanupReferenceException("local upload ownership changed");
        }
        if (characterCleanupMapper.countOtherLocalAssetReferences(assetUrl, context.targetCharacterIds()) > 0) {
            throw new UnsafeCleanupReferenceException("local upload is shared by another resource");
        }

        deleteLocalUpload(relativePath);
        characterCleanupMapper.deleteUploadAsset(actualAssetId, actualOwnerUserId, actualRelativePath);
    }

    private CharacterCleanupContext parseCharacterContext(ExternalCleanupTask task) {
        try {
            CharacterCleanupContext context = objectMapper.readValue(task.getContextJson(), CharacterCleanupContext.class);
            if (context.targetCharacterIds() == null || context.targetCharacterIds().isEmpty()) {
                throw new UnsafeCleanupReferenceException("character cleanup target set is missing");
            }
            return new CharacterCleanupContext(
                    normalizeCharacterIds(context.targetCharacterIds()),
                    context.expectedOwnerUserId(),
                    context.assetId(),
                    text(context.assetUrl())
            );
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new UnsafeCleanupReferenceException("character cleanup context is invalid", ex);
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

    private void collectMemoryWorldbookDrafts(
            Map<String, CleanupDraft> drafts,
            List<Map<String, Object>> rows
    ) {
        if (rows == null) {
            return;
        }
        for (Map<String, Object> row : rows) {
            long conversationId = positiveLong(row == null ? null : row.get("conversationId"));
            String worldName = text(row == null ? null : row.get("memoryWorldName"));
            if (conversationId <= 0 || !worldName.startsWith("jg_memory_conv_" + conversationId + "_")) {
                continue;
            }
            addDraft(drafts, TYPE_ST_WORLDBOOK, worldName, String.valueOf(conversationId));
        }
    }

    private void collectLocalUploadDrafts(
            Map<String, CleanupDraft> drafts,
            Collection<String> assetUrls
    ) {
        if (assetUrls == null) {
            return;
        }
        for (String assetUrl : assetUrls) {
            String relativePath = normalizeLocalUploadReference(assetUrl);
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

    private static String normalizeLocalUploadReference(String rawPath) {
        String path = text(rawPath).replace('\\', '/');
        if (path.startsWith("/uploads/h5/")) {
            path = path.substring("/uploads/h5/".length());
        }
        return normalizeOwnedUploadRelativePath(path);
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

    private String taskKey(String operationId, String resourceType, String primaryRef) {
        String material = operationId + "\n" + resourceType + "\n" + primaryRef;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static List<Long> normalizeCharacterIds(Collection<Long> rawIds) {
        if (rawIds == null) {
            throw new IllegalArgumentException("character cleanup target set is missing");
        }
        List<Long> ids = rawIds.stream()
                .filter(java.util.Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .sorted()
                .toList();
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("character cleanup target set is missing");
        }
        return ids;
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

    private static long positiveLong(Object value) {
        if (value instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        try {
            return Math.max(0L, Long.parseLong(text(value)));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
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

    public record CharacterCleanupContext(
            List<Long> targetCharacterIds,
            Long expectedOwnerUserId,
            Long assetId,
            String assetUrl
    ) {
    }

    private static final class UnsafeCleanupReferenceException extends RuntimeException {
        private UnsafeCleanupReferenceException(String message) {
            super(message);
        }

        private UnsafeCleanupReferenceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class CleanupDependencyPendingException extends RuntimeException {
        private CleanupDependencyPendingException(String message) {
            super(message);
        }
    }

    private static final class CleanupDependencyFailedException extends RuntimeException {
        private CleanupDependencyFailedException(String message) {
            super(message);
        }
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
