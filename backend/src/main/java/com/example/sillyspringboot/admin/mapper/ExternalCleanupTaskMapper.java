package com.example.sillyspringboot.admin.mapper;

import com.example.sillyspringboot.admin.model.ExternalCleanupTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExternalCleanupTaskMapper {

    int insertOrKeep(@Param("task") ExternalCleanupTask task);

    ExternalCleanupTask findById(@Param("id") String id);

    ExternalCleanupTask findByTaskKey(@Param("taskKey") String taskKey);

    int countBlockingCharacterStTasks(@Param("operationId") String operationId);

    int countDeadCharacterStTasks(@Param("operationId") String operationId);

    List<ExternalCleanupTask> listDueTasks(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    int claimTask(
            @Param("id") String id,
            @Param("now") LocalDateTime now,
            @Param("lockedUntil") LocalDateTime lockedUntil,
            @Param("lockToken") String lockToken
    );

    int markCompleted(
            @Param("id") String id,
            @Param("lockToken") String lockToken,
            @Param("completedAt") LocalDateTime completedAt
    );

    int markSkipped(
            @Param("id") String id,
            @Param("lockToken") String lockToken,
            @Param("status") String status,
            @Param("lastError") String lastError,
            @Param("completedAt") LocalDateTime completedAt
    );

    int markFailed(
            @Param("id") String id,
            @Param("lockToken") String lockToken,
            @Param("status") String status,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int deferClaim(
            @Param("id") String id,
            @Param("lockToken") String lockToken,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    int expireExhaustedProcessingTasks(
            @Param("now") LocalDateTime now,
            @Param("lastError") String lastError
    );
}
