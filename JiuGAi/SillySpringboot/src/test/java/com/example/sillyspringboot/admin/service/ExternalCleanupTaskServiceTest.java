package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.mapper.ExternalCleanupTaskMapper;
import com.example.sillyspringboot.admin.model.ExternalCleanupTask;
import com.example.sillyspringboot.config.ExternalCleanupProperties;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExternalCleanupTaskServiceTest {

    @TempDir
    Path tempDir;

    private ExternalCleanupTaskMapper mapper;
    private StAdapter stAdapter;
    private ExternalCleanupProperties properties;
    private ExternalCleanupTaskService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ExternalCleanupTaskMapper.class);
        stAdapter = mock(StAdapter.class);
        properties = new ExternalCleanupProperties();
        properties.setMaxAttempts(3);
        properties.setBaseBackoffSeconds(10);
        properties.setMaxBackoffSeconds(25);
        properties.setProcessingLeaseSeconds(30);
        service = new ExternalCleanupTaskService(mapper, stAdapter, properties, tempDir.toString());
    }

    @Test
    void enqueueDeduplicatesResourcesAndRejectsUploadPathTraversal() {
        Map<String, ExternalCleanupTask> persisted = new LinkedHashMap<>();
        when(mapper.insertOrKeep(any())).thenAnswer(invocation -> {
            ExternalCleanupTask task = invocation.getArgument(0);
            persisted.putIfAbsent(task.getTaskKey(), task);
            return 1;
        });
        when(mapper.findByTaskKey(anyString())).thenAnswer(invocation -> persisted.get(invocation.getArgument(0)));

        String ownedFile = UUID.randomUUID() + ".png";
        List<String> ids = service.enqueueUserDeletionTasks(
                7L,
                List.of(
                        Map.of("stAvatarUrl", "alice.png", "stChatFileName", "chat.jsonl"),
                        Map.of("stAvatarUrl", "alice.png", "stChatFileName", "chat.jsonl")
                ),
                List.of(
                        Map.of("stAvatarUrl", "alice.png"),
                        Map.of("stAvatarUrl", "alice.png")
                ),
                Set.of(ownedFile)
        );

        assertThat(ids).hasSize(3).doesNotHaveDuplicates();
        assertThat(persisted.values())
                .extracting(ExternalCleanupTask::getResourceType)
                .containsExactly(
                        ExternalCleanupTaskService.TYPE_ST_CHAT,
                        ExternalCleanupTaskService.TYPE_ST_CHARACTER,
                        ExternalCleanupTaskService.TYPE_LOCAL_UPLOAD
                );
        assertThat(persisted.values())
                .filteredOn(task -> ExternalCleanupTaskService.TYPE_LOCAL_UPLOAD.equals(task.getResourceType()))
                .extracting(ExternalCleanupTask::getPrimaryRef)
                .containsExactly(ownedFile);
        assertThat(persisted.values())
                .allSatisfy(task -> {
                    assertThat(task.getTaskKey()).hasSize(64);
                    assertThat(task.getStatus()).isEqualTo(ExternalCleanupTaskService.STATUS_PENDING);
                    assertThat(task.getMaxAttempts()).isEqualTo(3);
                });
    }

    @Test
    void enqueueRejectsNonCanonicalOwnedUploadPaths() {
        assertThatThrownBy(() -> service.enqueueUserDeletionTasks(
                7L,
                List.of(),
                List.of(),
                Set.of("../outside.txt")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owned upload path is invalid");
    }

    @Test
    void immediateFailureIsRecordedForRetryWithExponentialBackoff() {
        ExternalCleanupTask task = task("task-retry", ExternalCleanupTaskService.TYPE_ST_CHAT, 0, 3);
        installClaimBehavior(task);
        when(stAdapter.deleteChat("alice.png", "chat.jsonl"))
                .thenThrow(new IllegalStateException("ST offline"));
        when(mapper.markFailed(anyString(), anyString(), anyString(), any(), anyString(), any()))
                .thenAnswer(invocation -> {
                    task.setStatus(invocation.getArgument(2));
                    task.setNextAttemptAt(invocation.getArgument(3));
                    task.setLastError(invocation.getArgument(4));
                    return 1;
                });

        ExternalCleanupTaskService.CleanupAttempt result = service.processImmediately(List.of(task.getId())).get(0);

        assertThat(result.status()).isEqualTo(ExternalCleanupTaskService.STATUS_RETRY);
        assertThat(result.message()).contains("ST offline");
        assertThat(task.getAttemptCount()).isEqualTo(1);
        assertThat(task.getNextAttemptAt()).isNotNull();
        assertThat(service.backoffSeconds(1)).isEqualTo(10);
        assertThat(service.backoffSeconds(2)).isEqualTo(20);
        assertThat(service.backoffSeconds(3)).isEqualTo(25);
    }

    @Test
    void finalFailureMovesTaskToDeadWithoutAnotherRetry() {
        ExternalCleanupTask task = task("task-dead", ExternalCleanupTaskService.TYPE_ST_CHAT, 1, 2);
        installClaimBehavior(task);
        when(stAdapter.deleteChat("alice.png", "chat.jsonl"))
                .thenThrow(new IllegalStateException("still offline"));
        when(mapper.markFailed(anyString(), anyString(), anyString(), any(), anyString(), any()))
                .thenAnswer(invocation -> {
                    task.setStatus(invocation.getArgument(2));
                    task.setNextAttemptAt(invocation.getArgument(3));
                    return 1;
                });

        ExternalCleanupTaskService.CleanupAttempt result = service.processImmediately(List.of(task.getId())).get(0);

        assertThat(result.status()).isEqualTo(ExternalCleanupTaskService.STATUS_DEAD);
        assertThat(task.getAttemptCount()).isEqualTo(2);
        assertThat(task.getNextAttemptAt()).isNull();
    }

    @Test
    void localUploadDeletionIsIdempotentAndCannotEscapeConfiguredRoot() throws Exception {
        String fileName = UUID.randomUUID() + ".png";
        Path upload = tempDir.resolve("h5").resolve(fileName);
        Files.createDirectories(upload.getParent());
        Files.writeString(upload, "image", StandardCharsets.UTF_8);
        ExternalCleanupTask task = task("task-file", ExternalCleanupTaskService.TYPE_LOCAL_UPLOAD, 0, 3);
        task.setPrimaryRef(fileName);
        task.setSecondaryRef("");
        installClaimBehavior(task);
        when(mapper.markCompleted(anyString(), anyString(), any())).thenAnswer(invocation -> {
            task.setStatus(ExternalCleanupTaskService.STATUS_COMPLETED);
            return 1;
        });

        ExternalCleanupTaskService.CleanupAttempt first = service.processImmediately(List.of(task.getId())).get(0);

        assertThat(first.succeeded()).isTrue();
        assertThat(upload).doesNotExist();

        ExternalCleanupTask absentTask = task("task-file-absent", ExternalCleanupTaskService.TYPE_LOCAL_UPLOAD, 0, 3);
        absentTask.setPrimaryRef(task.getPrimaryRef());
        absentTask.setSecondaryRef("");
        installClaimBehavior(absentTask);
        when(mapper.markCompleted(anyString(), anyString(), any())).thenReturn(1);

        assertThat(service.processImmediately(List.of(absentTask.getId())).get(0).succeeded()).isTrue();
    }

    @Test
    void missingStResourceIsAnIdempotentSuccess() {
        ExternalCleanupTask task = task("task-st-missing", ExternalCleanupTaskService.TYPE_ST_CHARACTER, 0, 3);
        task.setPrimaryRef("already-gone.png");
        task.setSecondaryRef("");
        installClaimBehavior(task);
        when(stAdapter.deleteCharacter("already-gone.png", true)).thenReturn(false);
        when(mapper.markCompleted(anyString(), anyString(), any())).thenReturn(1);

        ExternalCleanupTaskService.CleanupAttempt result = service.processImmediately(List.of(task.getId())).get(0);

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void scheduledBatchClaimsAndCompletesDueTasks() {
        ExternalCleanupTask task = task("task-batch", ExternalCleanupTaskService.TYPE_ST_CHAT, 0, 3);
        installClaimBehavior(task);
        when(mapper.expireExhaustedProcessingTasks(any(), anyString())).thenReturn(0);
        when(mapper.listDueTasks(any(), anyInt())).thenReturn(List.of(task));
        when(stAdapter.deleteChat("alice.png", "chat.jsonl")).thenReturn(true);
        when(mapper.markCompleted(anyString(), anyString(), any())).thenReturn(1);

        ExternalCleanupTaskService.CleanupBatchResult result = service.retryDueTasks();

        assertThat(result.processed()).isEqualTo(1);
        assertThat(result.completed()).isEqualTo(1);
        assertThat(result.retry()).isZero();
        assertThat(result.dead()).isZero();
        assertThat(result.deferred()).isZero();
    }

    private ExternalCleanupTask task(String id, String resourceType, int attemptCount, int maxAttempts) {
        ExternalCleanupTask task = new ExternalCleanupTask();
        task.setId(id);
        task.setTaskKey("a".repeat(64));
        task.setSourceUserId(7L);
        task.setResourceType(resourceType);
        task.setPrimaryRef("alice.png");
        task.setSecondaryRef("chat.jsonl");
        task.setStatus(ExternalCleanupTaskService.STATUS_PENDING);
        task.setAttemptCount(attemptCount);
        task.setMaxAttempts(maxAttempts);
        task.setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        return task;
    }

    private void installClaimBehavior(ExternalCleanupTask task) {
        when(mapper.findById(task.getId())).thenReturn(task);
        when(mapper.claimTask(anyString(), any(), any(), anyString())).thenAnswer(invocation -> {
            task.setStatus(ExternalCleanupTaskService.STATUS_PROCESSING);
            task.setAttemptCount(task.getAttemptCount() + 1);
            task.setLastAttemptAt(invocation.getArgument(1));
            task.setLockedUntil(invocation.getArgument(2));
            task.setLockToken(invocation.getArgument(3));
            return 1;
        });
    }
}
