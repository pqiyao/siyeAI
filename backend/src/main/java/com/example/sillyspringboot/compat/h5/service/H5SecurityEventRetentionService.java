package com.example.sillyspringboot.compat.h5.service;

import com.example.sillyspringboot.compat.h5.mapper.AppH5SecurityEventMapper;
import com.example.sillyspringboot.config.H5SecurityEventProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class H5SecurityEventRetentionService {

    private static final Logger log = LoggerFactory.getLogger(H5SecurityEventRetentionService.class);

    private final AppH5SecurityEventMapper mapper;
    private final H5SecurityEventProperties properties;

    public H5SecurityEventRetentionService(
            AppH5SecurityEventMapper mapper,
            H5SecurityEventProperties properties
    ) {
        this.mapper = mapper;
        this.properties = properties;
    }

    @Scheduled(
            initialDelayString = "${app.h5-security-event.cleanup-initial-delay-ms:300000}",
            fixedDelayString = "${app.h5-security-event.cleanup-interval-ms:21600000}"
    )
    public void scheduledCleanup() {
        try {
            int deleted = cleanupNow();
            if (deleted > 0) {
                log.info("deleted {} expired H5 security events", deleted);
            }
        } catch (RuntimeException ex) {
            log.warn("H5 security event retention cleanup failed", ex);
        }
    }

    int cleanupNow() {
        if (!properties.isCleanupEnabled()) {
            return 0;
        }
        int retentionDays = clamp(properties.getRetentionDays(), 1, 3650);
        int batchSize = clamp(properties.getCleanupBatchSize(), 100, 10_000);
        int maxBatches = clamp(properties.getMaxBatchesPerRun(), 1, 100);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        int total = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            int deleted = mapper.deleteOldestBefore(cutoff, batchSize);
            total += Math.max(0, deleted);
            if (deleted < batchSize) {
                break;
            }
        }
        return total;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
