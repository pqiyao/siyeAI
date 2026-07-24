package com.example.sillyspringboot.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.generation-retention")
public class GenerationRetentionProperties {

    private boolean enabled = true;

    @Min(1)
    @Max(3650)
    private int retentionDays = 30;

    @Min(100)
    @Max(10_000)
    private int batchSize = 2000;

    @Min(1)
    @Max(100)
    private int maxBatchesPerRun = 10;

    @Min(0)
    private long cleanupInitialDelayMs = 300_000L;

    @Min(60_000)
    private long cleanupIntervalMs = 21_600_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxBatchesPerRun() {
        return maxBatchesPerRun;
    }

    public void setMaxBatchesPerRun(int maxBatchesPerRun) {
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    public long getCleanupInitialDelayMs() {
        return cleanupInitialDelayMs;
    }

    public void setCleanupInitialDelayMs(long cleanupInitialDelayMs) {
        this.cleanupInitialDelayMs = cleanupInitialDelayMs;
    }

    public long getCleanupIntervalMs() {
        return cleanupIntervalMs;
    }

    public void setCleanupIntervalMs(long cleanupIntervalMs) {
        this.cleanupIntervalMs = cleanupIntervalMs;
    }
}
