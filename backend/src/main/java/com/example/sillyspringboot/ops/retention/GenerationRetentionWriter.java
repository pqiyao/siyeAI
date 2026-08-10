package com.example.sillyspringboot.ops.retention;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class GenerationRetentionWriter {

    private final GenerationRetentionMapper mapper;

    public GenerationRetentionWriter(GenerationRetentionMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public int archiveTaskBatch(LocalDateTime cutoff, int batchSize) {
        mapper.aggregateTaskBatch(cutoff, batchSize);
        mapper.aggregateAttemptsForTaskBatch(cutoff, batchSize);
        mapper.deleteAttemptsForTaskBatch(cutoff, batchSize);
        mapper.deleteStatEventsForTaskBatch(cutoff, batchSize);
        return mapper.deleteTaskBatch(cutoff, batchSize);
    }

    @Transactional
    public int archiveOrphanAttemptBatch(LocalDateTime cutoff, int batchSize) {
        mapper.aggregateOrphanAttemptBatch(cutoff, batchSize);
        return mapper.deleteOrphanAttemptBatch(cutoff, batchSize);
    }

    @Transactional
    public int archiveOrphanEventBatch(LocalDateTime cutoff, int batchSize) {
        mapper.aggregateOrphanEventBatch(cutoff, batchSize);
        return mapper.deleteOrphanEventBatch(cutoff, batchSize);
    }
}
