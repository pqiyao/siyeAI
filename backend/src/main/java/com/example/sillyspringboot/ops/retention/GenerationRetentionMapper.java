package com.example.sillyspringboot.ops.retention;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface GenerationRetentionMapper {

    int aggregateTaskBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);

    int aggregateAttemptsForTaskBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);

    int deleteAttemptsForTaskBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);

    int deleteStatEventsForTaskBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);

    int deleteTaskBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);

    int aggregateOrphanAttemptBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);

    int deleteOrphanAttemptBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);

    int aggregateOrphanEventBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);

    int deleteOrphanEventBatch(@Param("cutoff") LocalDateTime cutoff, @Param("batchSize") int batchSize);
}
