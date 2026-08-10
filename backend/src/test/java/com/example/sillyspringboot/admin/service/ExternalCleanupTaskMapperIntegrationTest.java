package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.mapper.ExternalCleanupTaskMapper;
import com.example.sillyspringboot.admin.model.ExternalCleanupTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExternalCleanupTaskMapperIntegrationTest {

    @Autowired
    private ExternalCleanupTaskMapper mapper;

    @Autowired
    private ExternalCleanupTaskService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void claimsWithLeaseAndLockTokenThenPersistsRetryAndDeadStates() {
        LocalDateTime now = LocalDateTime.now().minusSeconds(1);
        ExternalCleanupTask task = newTask("ST_CHAT", 2, now);
        task.setOperationId(UUID.randomUUID().toString());
        task.setSourceType("CHARACTER_DELETION");
        task.setSourceUserId(null);
        task.setSourceCharacterId(123L);
        task.setContextJson("{\"targetCharacterIds\":[123]}");
        mapper.insertOrKeep(task);

        List<ExternalCleanupTask> due = mapper.listDueTasks(LocalDateTime.now(), 10);
        assertThat(due).extracting(ExternalCleanupTask::getId).contains(task.getId());

        String firstToken = UUID.randomUUID().toString();
        LocalDateTime firstClaimAt = LocalDateTime.now();
        assertThat(mapper.claimTask(task.getId(), firstClaimAt, firstClaimAt.plusMinutes(5), firstToken)).isEqualTo(1);
        assertThat(mapper.claimTask(task.getId(), firstClaimAt, firstClaimAt.plusMinutes(5), UUID.randomUUID().toString())).isZero();

        ExternalCleanupTask claimed = mapper.findById(task.getId());
        assertThat(claimed.getStatus()).isEqualTo(ExternalCleanupTaskService.STATUS_PROCESSING);
        assertThat(claimed.getAttemptCount()).isEqualTo(1);
        assertThat(claimed.getLockToken()).isEqualTo(firstToken);
        assertThat(claimed.getOperationId()).isEqualTo(task.getOperationId());
        assertThat(claimed.getSourceType()).isEqualTo("CHARACTER_DELETION");
        assertThat(claimed.getSourceUserId()).isNull();
        assertThat(claimed.getSourceCharacterId()).isEqualTo(123L);
        assertThat(claimed.getContextJson()).isEqualTo("{\"targetCharacterIds\":[123]}");
        assertThat(mapper.markFailed(
                task.getId(),
                "wrong-token",
                ExternalCleanupTaskService.STATUS_RETRY,
                now.plusSeconds(10),
                "failure",
                now
        )).isZero();

        LocalDateTime retryAt = now.plusSeconds(10);
        assertThat(mapper.markFailed(
                task.getId(),
                firstToken,
                ExternalCleanupTaskService.STATUS_RETRY,
                retryAt,
                "failure",
                now
        )).isEqualTo(1);
        assertThat(mapper.listDueTasks(retryAt.minusSeconds(1), 10))
                .extracting(ExternalCleanupTask::getId)
                .doesNotContain(task.getId());

        String secondToken = UUID.randomUUID().toString();
        LocalDateTime secondClaimAt = retryAt.plusNanos(1_000);
        assertThat(mapper.claimTask(task.getId(), secondClaimAt, secondClaimAt.plusMinutes(5), secondToken)).isEqualTo(1);
        assertThat(mapper.markFailed(
                task.getId(),
                secondToken,
                ExternalCleanupTaskService.STATUS_DEAD,
                null,
                "final failure",
                retryAt
        )).isEqualTo(1);

        ExternalCleanupTask dead = mapper.findById(task.getId());
        assertThat(dead.getStatus()).isEqualTo(ExternalCleanupTaskService.STATUS_DEAD);
        assertThat(dead.getAttemptCount()).isEqualTo(2);
        assertThat(dead.getNextAttemptAt()).isNull();
        assertThat(dead.getLastError()).isEqualTo("final failure");
    }

    @Test
    void duplicateTaskKeyKeepsOriginalTaskAndExpiredFinalLeaseBecomesDead() {
        LocalDateTime now = LocalDateTime.now().minusMinutes(10);
        ExternalCleanupTask original = newTask("ST_CHARACTER", 1, now);
        mapper.insertOrKeep(original);

        ExternalCleanupTask duplicate = newTask("ST_CHARACTER", 1, now);
        duplicate.setTaskKey(original.getTaskKey());
        mapper.insertOrKeep(duplicate);

        assertThat(mapper.findByTaskKey(original.getTaskKey()).getId()).isEqualTo(original.getId());

        String token = UUID.randomUUID().toString();
        LocalDateTime claimAt = LocalDateTime.now();
        assertThat(mapper.claimTask(original.getId(), claimAt, claimAt.minusSeconds(1), token)).isEqualTo(1);
        assertThat(mapper.expireExhaustedProcessingTasks(
                LocalDateTime.now(),
                "processing lease expired after final attempt"
        )).isEqualTo(1);

        ExternalCleanupTask expired = mapper.findById(original.getId());
        assertThat(expired.getStatus()).isEqualTo(ExternalCleanupTaskService.STATUS_DEAD);
        assertThat(expired.getLockToken()).isNull();
        assertThat(expired.getLastError()).isEqualTo("processing lease expired after final attempt");
    }

    @Test
    void enqueueParticipatesInCallerTransactionAndRollsBackWithDatabaseDeletionFailure() {
        long sourceUserId = 9_900_099L;
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.enqueueUserDeletionTasks(
                    sourceUserId,
                    List.of(),
                    List.of(),
                    Set.of("00000000-0000-0000-0000-000000000099.png")
            );
            throw new IllegalStateException("simulate database deletion failure");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulate database deletion failure");

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM app_external_cleanup_task WHERE source_user_id = ?",
                Integer.class,
                sourceUserId
        );
        assertThat(count).isZero();
    }

    @Test
    void localCharacterTaskDefersWithoutUsingAttemptWhileStTaskIsBlocking() {
        LocalDateTime now = LocalDateTime.now().minusSeconds(1);
        String operationId = UUID.randomUUID().toString();
        ExternalCleanupTask stTask = newTask(ExternalCleanupTaskService.TYPE_CHARACTER_ST_BUNDLE, 3, now);
        stTask.setOperationId(operationId);
        stTask.setSourceType("CHARACTER_DELETION");
        stTask.setSourceCharacterId(101L);
        ExternalCleanupTask localTask = newTask(ExternalCleanupTaskService.TYPE_CHARACTER_LOCAL_UPLOAD, 3, now);
        localTask.setOperationId(operationId);
        localTask.setSourceType("CHARACTER_DELETION");
        localTask.setSourceCharacterId(101L);
        mapper.insertOrKeep(stTask);
        mapper.insertOrKeep(localTask);

        assertThat(mapper.countBlockingCharacterStTasks(operationId)).isEqualTo(1);
        assertThat(mapper.countDeadCharacterStTasks(operationId)).isZero();

        String localToken = UUID.randomUUID().toString();
        LocalDateTime claimAt = LocalDateTime.now();
        assertThat(mapper.claimTask(localTask.getId(), claimAt, claimAt.plusMinutes(5), localToken)).isEqualTo(1);
        assertThat(mapper.deferClaim(
                localTask.getId(),
                localToken,
                claimAt.plusSeconds(10),
                "waiting for ST cleanup",
                claimAt
        )).isEqualTo(1);
        ExternalCleanupTask deferred = mapper.findById(localTask.getId());
        assertThat(deferred.getStatus()).isEqualTo(ExternalCleanupTaskService.STATUS_RETRY);
        assertThat(deferred.getAttemptCount()).isZero();
        assertThat(deferred.getLastError()).isEqualTo("waiting for ST cleanup");

        String stToken = UUID.randomUUID().toString();
        assertThat(mapper.claimTask(stTask.getId(), claimAt, claimAt.plusMinutes(5), stToken)).isEqualTo(1);
        assertThat(mapper.markFailed(
                stTask.getId(),
                stToken,
                ExternalCleanupTaskService.STATUS_DEAD,
                null,
                "ST unavailable",
                claimAt
        )).isEqualTo(1);
        assertThat(mapper.countBlockingCharacterStTasks(operationId)).isZero();
        assertThat(mapper.countDeadCharacterStTasks(operationId)).isEqualTo(1);
    }

    private static ExternalCleanupTask newTask(String resourceType, int maxAttempts, LocalDateTime dueAt) {
        ExternalCleanupTask task = new ExternalCleanupTask();
        task.setId(UUID.randomUUID().toString());
        task.setTaskKey(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        task.setSourceUserId(9_900_001L);
        task.setResourceType(resourceType);
        task.setPrimaryRef("resource.png");
        task.setSecondaryRef("chat.jsonl");
        task.setStatus(ExternalCleanupTaskService.STATUS_PENDING);
        task.setAttemptCount(0);
        task.setMaxAttempts(maxAttempts);
        task.setNextAttemptAt(dueAt);
        return task;
    }
}
