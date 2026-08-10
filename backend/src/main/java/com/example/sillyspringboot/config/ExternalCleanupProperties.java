package com.example.sillyspringboot.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.external-cleanup")
public class ExternalCleanupProperties {

    private boolean retryEnabled = true;

    @Min(1)
    @Max(500)
    private int batchSize = 50;

    @Min(1)
    @Max(100)
    private int maxAttempts = 8;

    @Min(1)
    @Max(86_400)
    private long baseBackoffSeconds = 60L;

    @Min(1)
    @Max(604_800)
    private long maxBackoffSeconds = 21_600L;

    @Min(30)
    @Max(3_600)
    private long processingLeaseSeconds = 900L;

    @Min(0)
    private long retryInitialDelayMs = 60_000L;

    @Min(10_000)
    private long retryIntervalMs = 60_000L;

    public boolean isRetryEnabled() {
        return retryEnabled;
    }

    public void setRetryEnabled(boolean retryEnabled) {
        this.retryEnabled = retryEnabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getBaseBackoffSeconds() {
        return baseBackoffSeconds;
    }

    public void setBaseBackoffSeconds(long baseBackoffSeconds) {
        this.baseBackoffSeconds = baseBackoffSeconds;
    }

    public long getMaxBackoffSeconds() {
        return maxBackoffSeconds;
    }

    public void setMaxBackoffSeconds(long maxBackoffSeconds) {
        this.maxBackoffSeconds = maxBackoffSeconds;
    }

    public long getProcessingLeaseSeconds() {
        return processingLeaseSeconds;
    }

    public void setProcessingLeaseSeconds(long processingLeaseSeconds) {
        this.processingLeaseSeconds = processingLeaseSeconds;
    }

    public long getRetryInitialDelayMs() {
        return retryInitialDelayMs;
    }

    public void setRetryInitialDelayMs(long retryInitialDelayMs) {
        this.retryInitialDelayMs = retryInitialDelayMs;
    }

    public long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    public void setRetryIntervalMs(long retryIntervalMs) {
        this.retryIntervalMs = retryIntervalMs;
    }
}
