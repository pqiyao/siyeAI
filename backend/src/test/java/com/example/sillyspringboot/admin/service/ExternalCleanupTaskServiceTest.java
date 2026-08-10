package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.mapper.ExternalCleanupTaskMapper;
import com.example.sillyspringboot.admin.mapper.CharacterCleanupMapper;
import com.example.sillyspringboot.admin.model.CharacterUploadAssetRow;
import com.example.sillyspringboot.admin.model.ExternalCleanupTask;
import com.example.sillyspringboot.config.ExternalCleanupProperties;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StWorldbookOptionDto;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalCleanupTaskServiceTest {

    @TempDir
    Path tempDir;

    private ExternalCleanupTaskMapper mapper;
    private CharacterCleanupMapper characterCleanupMapper;
    private StAdapter stAdapter;
    private ExternalCleanupProperties properties;
    private ExternalCleanupTaskService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ExternalCleanupTaskMapper.class);
        characterCleanupMapper = mock(CharacterCleanupMapper.class);
        stAdapter = mock(StAdapter.class);
        properties = new ExternalCleanupProperties();
        properties.setMaxAttempts(3);
        properties.setBaseBackoffSeconds(10);
        properties.setMaxBackoffSeconds(25);
        properties.setProcessingLeaseSeconds(30);
        service = new ExternalCleanupTaskService(
                mapper,
                characterCleanupMapper,
                stAdapter,
                properties,
                tempDir.toString()
        );
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
                List.of(Map.of(
                        "conversationId", 31L,
                        "memoryWorldName", "jg_memory_conv_31_abcdef1234"
                )),
                Set.of(ownedFile)
        );

        assertThat(ids).hasSize(4).doesNotHaveDuplicates();
        assertThat(persisted.values())
                .extracting(ExternalCleanupTask::getResourceType)
                .containsExactly(
                        ExternalCleanupTaskService.TYPE_ST_CHAT,
                        ExternalCleanupTaskService.TYPE_ST_CHARACTER,
                        ExternalCleanupTaskService.TYPE_ST_WORLDBOOK,
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
    void promotionRollbackPersistsStAndCopiedMediaForDurableCleanup() {
        Map<String, ExternalCleanupTask> persisted = new LinkedHashMap<>();
        when(mapper.insertOrKeep(any())).thenAnswer(invocation -> {
            ExternalCleanupTask task = invocation.getArgument(0);
            persisted.putIfAbsent(task.getTaskKey(), task);
            return 1;
        });
        when(mapper.findByTaskKey(anyString())).thenAnswer(invocation -> persisted.get(invocation.getArgument(0)));
        String copiedFile = UUID.randomUUID() + ".png";

        List<String> ids = service.enqueueArtifactRollbackTasks(
                7L,
                "system_copy_failed.png",
                List.of("/uploads/h5/" + copiedFile)
        );

        assertThat(ids).hasSize(2);
        assertThat(persisted.values())
                .extracting(ExternalCleanupTask::getResourceType)
                .containsExactly(
                        ExternalCleanupTaskService.TYPE_ST_CHARACTER,
                        ExternalCleanupTaskService.TYPE_LOCAL_UPLOAD
                );
        assertThat(persisted.values())
                .filteredOn(task -> ExternalCleanupTaskService.TYPE_LOCAL_UPLOAD.equals(task.getResourceType()))
                .extracting(ExternalCleanupTask::getPrimaryRef)
                .containsExactly(copiedFile);
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
    void memoryWorldbookSetDeletesOnlyBaseAndNumericRevisionFiles() {
        String baseName = "jg_memory_conv_31_b9";
        ExternalCleanupTask task = task(
                "task-memory-worldbooks",
                ExternalCleanupTaskService.TYPE_ST_MEMORY_WORLDBOOK_SET,
                0,
                3
        );
        task.setPrimaryRef(baseName);
        task.setSecondaryRef("31");
        installClaimBehavior(task);
        when(stAdapter.listWorldbooks()).thenReturn(List.of(
                new StWorldbookOptionDto(baseName, baseName),
                new StWorldbookOptionDto(baseName + "_r5", baseName + "_r5"),
                new StWorldbookOptionDto(baseName + "_r6", baseName + "_r6"),
                new StWorldbookOptionDto(baseName + "_rollback", baseName + "_rollback"),
                new StWorldbookOptionDto("jg_memory_conv_31_b10_r6", "jg_memory_conv_31_b10_r6")
        ));
        when(stAdapter.deleteWorldbook(anyString())).thenReturn(true);
        when(mapper.markCompleted(anyString(), anyString(), any())).thenReturn(1);

        ExternalCleanupTaskService.CleanupAttempt result =
                service.processImmediately(List.of(task.getId())).get(0);

        assertThat(result.succeeded()).isTrue();
        verify(stAdapter).deleteWorldbook(baseName);
        verify(stAdapter).deleteWorldbook(baseName + "_r5");
        verify(stAdapter).deleteWorldbook(baseName + "_r6");
        verify(stAdapter, never()).deleteWorldbook(baseName + "_rollback");
        verify(stAdapter, never()).deleteWorldbook("jg_memory_conv_31_b10_r6");
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

    @Test
    void sharedCharacterStBundleIsRetainedWithoutCallingSt() {
        ExternalCleanupTask task = task(
                "task-character-shared",
                ExternalCleanupTaskService.TYPE_CHARACTER_ST_BUNDLE,
                0,
                3
        );
        task.setContextJson("{\"targetCharacterIds\":[101],\"expectedOwnerUserId\":null,\"assetId\":null,\"assetUrl\":\"\"}");
        installClaimBehavior(task);
        when(characterCleanupMapper.countOtherCharacterStReferences("alice.png", List.of(101L))).thenReturn(1);
        when(mapper.markSkipped(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(1);

        ExternalCleanupTaskService.CleanupAttempt result = service.processImmediately(List.of(task.getId())).get(0);

        assertThat(result.status()).isEqualTo(ExternalCleanupTaskService.STATUS_SKIPPED_SHARED);
        assertThat(result.message()).contains("shared");
        verify(stAdapter, never()).deleteCharacter(anyString(), any(Boolean.class));
    }

    @Test
    void exclusiveCharacterUploadDeletesFileThenOwnershipRecord() throws Exception {
        String fileName = UUID.randomUUID() + ".png";
        String assetUrl = "/uploads/h5/" + fileName;
        Path upload = tempDir.resolve("h5").resolve(fileName);
        Files.createDirectories(upload.getParent());
        Files.writeString(upload, "image", StandardCharsets.UTF_8);

        ExternalCleanupTask task = task(
                "task-character-upload",
                ExternalCleanupTaskService.TYPE_CHARACTER_LOCAL_UPLOAD,
                0,
                3
        );
        task.setPrimaryRef(fileName);
        task.setSecondaryRef("");
        task.setOperationId("operation-character-upload");
        task.setContextJson("{\"targetCharacterIds\":[101],\"expectedOwnerUserId\":7,\"assetId\":88,\"assetUrl\":\""
                + assetUrl + "\"}");
        installClaimBehavior(task);
        when(mapper.countBlockingCharacterStTasks(task.getOperationId())).thenReturn(0);
        when(mapper.countDeadCharacterStTasks(task.getOperationId())).thenReturn(0);
        CharacterUploadAssetRow asset = new CharacterUploadAssetRow();
        asset.setAssetId(88L);
        asset.setOwnerUserId(7L);
        asset.setRelativePath(fileName);
        asset.setAssetUrl(assetUrl);
        when(characterCleanupMapper.findUploadAsset(assetUrl)).thenReturn(asset);
        when(characterCleanupMapper.countOtherLocalAssetReferences(assetUrl, List.of(101L))).thenReturn(0);
        when(characterCleanupMapper.deleteUploadAsset(88L, 7L, fileName)).thenReturn(1);
        when(mapper.markCompleted(anyString(), anyString(), any())).thenReturn(1);

        ExternalCleanupTaskService.CleanupAttempt result = service.processImmediately(List.of(task.getId())).get(0);

        assertThat(result.succeeded()).isTrue();
        assertThat(upload).doesNotExist();
        verify(characterCleanupMapper).deleteUploadAsset(88L, 7L, fileName);
    }

    @Test
    void characterUploadWaitsForStCleanupWithoutConsumingRetryBudget() {
        ExternalCleanupTask task = characterUploadTask("task-character-upload-wait", "operation-wait");
        installClaimBehavior(task);
        when(mapper.countDeadCharacterStTasks(task.getOperationId())).thenReturn(0);
        when(mapper.countBlockingCharacterStTasks(task.getOperationId())).thenReturn(1);
        when(mapper.deferClaim(anyString(), anyString(), any(), anyString(), any())).thenReturn(1);

        ExternalCleanupTaskService.CleanupAttempt result = service.processImmediately(List.of(task.getId())).get(0);

        assertThat(result.status()).isEqualTo("DEFERRED");
        assertThat(result.message()).contains("waiting for ST cleanup");
        verify(characterCleanupMapper, never()).findUploadAsset(anyString());
        verify(mapper).deferClaim(anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    void characterUploadIsRetainedWhenStCleanupIsDead() {
        ExternalCleanupTask task = characterUploadTask("task-character-upload-dead", "operation-dead");
        installClaimBehavior(task);
        when(mapper.countDeadCharacterStTasks(task.getOperationId())).thenReturn(1);
        when(mapper.markSkipped(anyString(), anyString(), anyString(), anyString(), any())).thenReturn(1);

        ExternalCleanupTaskService.CleanupAttempt result = service.processImmediately(List.of(task.getId())).get(0);

        assertThat(result.status()).isEqualTo(ExternalCleanupTaskService.STATUS_DEAD);
        assertThat(result.message()).contains("local media was retained");
        verify(characterCleanupMapper, never()).findUploadAsset(anyString());
    }

    private ExternalCleanupTask characterUploadTask(String id, String operationId) {
        ExternalCleanupTask task = task(
                id,
                ExternalCleanupTaskService.TYPE_CHARACTER_LOCAL_UPLOAD,
                0,
                3
        );
        task.setPrimaryRef(UUID.randomUUID() + ".png");
        task.setSecondaryRef("");
        task.setOperationId(operationId);
        task.setContextJson("{\"targetCharacterIds\":[101],\"expectedOwnerUserId\":7,\"assetId\":88,"
                + "\"assetUrl\":\"/uploads/h5/" + task.getPrimaryRef() + "\"}");
        return task;
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
