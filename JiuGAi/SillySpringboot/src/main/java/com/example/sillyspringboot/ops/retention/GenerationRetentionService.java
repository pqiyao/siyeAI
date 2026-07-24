package com.example.sillyspringboot.ops.retention;

import com.example.sillyspringboot.config.GenerationRetentionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class GenerationRetentionService {

    private static final Logger log = LoggerFactory.getLogger(GenerationRetentionService.class);

    private final GenerationRetentionProperties properties;
    private final GenerationRetentionWriter writer;

    public GenerationRetentionService(
            GenerationRetentionProperties properties,
            GenerationRetentionWriter writer
    ) {
        this.properties = properties;
        this.writer = writer;
    }

    @Scheduled(
            initialDelayString = "${app.generation-retention.cleanup-initial-delay-ms:300000}",
            fixedDelayString = "${app.generation-retention.cleanup-interval-ms:21600000}"
    )
    public void scheduledCleanup() {
        try {
            int archived = cleanupNow();
            if (archived > 0) {
                log.info("archived and deleted {} generation detail rows", archived);
            }
        } catch (RuntimeException ex) {
            log.warn("generation detail retention cleanup failed", ex);
        }
    }

    int cleanupNow() {
        if (!properties.isEnabled()) {
            return 0;
        }
        int retentionDays = clamp(properties.getRetentionDays(), 1, 3650);
        int batchSize = clamp(properties.getBatchSize(), 100, 10_000);
        int maxBatches = clamp(properties.getMaxBatchesPerRun(), 1, 100);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int archived = 0;
        for (int i = 0; i < maxBatches; i++) {
            int deleted = writer.archiveTaskBatch(cutoff, batchSize);
            archived += Math.max(0, deleted);
            if (deleted < batchSize) {
                break;
            }
        }
        for (int i = 0; i < maxBatches; i++) {
            int deleted = writer.archiveOrphanAttemptBatch(cutoff, batchSize);
            archived += Math.max(0, deleted);
            if (deleted < batchSize) {
                break;
            }
        }
        for (int i = 0; i < maxBatches; i++) {
            int deleted = writer.archiveOrphanEventBatch(cutoff, batchSize);
            archived += Math.max(0, deleted);
            if (deleted < batchSize) {
                break;
            }
        }
        return archived;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
