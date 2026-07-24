package com.example.sillyspringboot.chat.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 阶段 4：并发治理参数（强约束）。
 */
@Validated
@ConfigurationProperties(prefix = "app.chat")
public class AppChatProperties {

    /** 全局同时生成上限（初值 8） */
    @Min(1)
    private int globalConcurrentLimit = 8;

    /** 单用户同时生成上限（初值 1） */
    @Min(1)
    private int perUserConcurrentLimit = 1;

    /** Redis 计数 TTL（秒），防止极端情况下计数泄漏（初值 300s） */
    @Min(10)
    private int counterTtlSeconds = 300;

    /** 生成执行域线程数（初值 4） */
    @Min(1)
    private int generationWorkerThreads = 4;

    /** 生成排队容量（初值 32） */
    @Min(1)
    private int generationQueueCapacity = 32;

    /** 排队最长等待（秒），超时则返回繁忙/停止（初值 60s） */
    @Min(1)
    private int maxQueueWaitSeconds = 60;

    /** SSE 连接超时（秒），避免无限挂起导致资源泄漏（初值 600s） */
    @Min(10)
    private int sseTimeoutSeconds = 600;

    @Min(10)
    private int generationTimeoutSeconds = 600;

    private Compatibility compatibility = new Compatibility();

    public static class Compatibility {
        /**
         * runtime: always use the current runtime path.
         * auto: detect cards/books/presets that need frontend extensions.
         * frontend_bridge: require the high compatibility path for every chat.
         */
        private String mode = "auto";
        private boolean frontendBridgeEnabled = false;
        private boolean fallbackToRuntime = true;
        private int worldbookProbeCacheSeconds = 300;
        private String frontendBridgeToken = "";
        private int frontendBridgeRequestTimeoutSeconds = 600;
        private int frontendBridgeWorkerStaleSeconds = 30;
        private int frontendBridgeMaxQueueSize = 64;
        private int frontendBridgePollWaitSeconds = 20;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode == null ? "auto" : mode;
        }

        public boolean isFrontendBridgeEnabled() {
            return frontendBridgeEnabled;
        }

        public void setFrontendBridgeEnabled(boolean frontendBridgeEnabled) {
            this.frontendBridgeEnabled = frontendBridgeEnabled;
        }

        public boolean isFallbackToRuntime() {
            return fallbackToRuntime;
        }

        public void setFallbackToRuntime(boolean fallbackToRuntime) {
            this.fallbackToRuntime = fallbackToRuntime;
        }

        public int getWorldbookProbeCacheSeconds() {
            return worldbookProbeCacheSeconds;
        }

        public void setWorldbookProbeCacheSeconds(int worldbookProbeCacheSeconds) {
            this.worldbookProbeCacheSeconds = worldbookProbeCacheSeconds;
        }

        public String getFrontendBridgeToken() {
            return frontendBridgeToken;
        }

        public void setFrontendBridgeToken(String frontendBridgeToken) {
            this.frontendBridgeToken = frontendBridgeToken == null ? "" : frontendBridgeToken;
        }

        public int getFrontendBridgeRequestTimeoutSeconds() {
            return frontendBridgeRequestTimeoutSeconds;
        }

        public void setFrontendBridgeRequestTimeoutSeconds(int frontendBridgeRequestTimeoutSeconds) {
            this.frontendBridgeRequestTimeoutSeconds = frontendBridgeRequestTimeoutSeconds;
        }

        public int getFrontendBridgeWorkerStaleSeconds() {
            return frontendBridgeWorkerStaleSeconds;
        }

        public void setFrontendBridgeWorkerStaleSeconds(int frontendBridgeWorkerStaleSeconds) {
            this.frontendBridgeWorkerStaleSeconds = frontendBridgeWorkerStaleSeconds;
        }

        public int getFrontendBridgeMaxQueueSize() {
            return frontendBridgeMaxQueueSize;
        }

        public void setFrontendBridgeMaxQueueSize(int frontendBridgeMaxQueueSize) {
            this.frontendBridgeMaxQueueSize = frontendBridgeMaxQueueSize;
        }

        public int getFrontendBridgePollWaitSeconds() {
            return frontendBridgePollWaitSeconds;
        }

        public void setFrontendBridgePollWaitSeconds(int frontendBridgePollWaitSeconds) {
            this.frontendBridgePollWaitSeconds = frontendBridgePollWaitSeconds;
        }
    }

    public int getGlobalConcurrentLimit() {
        return globalConcurrentLimit;
    }

    public void setGlobalConcurrentLimit(int globalConcurrentLimit) {
        this.globalConcurrentLimit = globalConcurrentLimit;
    }

    public int getPerUserConcurrentLimit() {
        return perUserConcurrentLimit;
    }

    public void setPerUserConcurrentLimit(int perUserConcurrentLimit) {
        this.perUserConcurrentLimit = perUserConcurrentLimit;
    }

    public int getCounterTtlSeconds() {
        return counterTtlSeconds;
    }

    public void setCounterTtlSeconds(int counterTtlSeconds) {
        this.counterTtlSeconds = counterTtlSeconds;
    }

    public int getGenerationWorkerThreads() {
        return generationWorkerThreads;
    }

    public void setGenerationWorkerThreads(int generationWorkerThreads) {
        this.generationWorkerThreads = generationWorkerThreads;
    }

    public int getGenerationQueueCapacity() {
        return generationQueueCapacity;
    }

    public void setGenerationQueueCapacity(int generationQueueCapacity) {
        this.generationQueueCapacity = generationQueueCapacity;
    }

    public int getMaxQueueWaitSeconds() {
        return maxQueueWaitSeconds;
    }

    public void setMaxQueueWaitSeconds(int maxQueueWaitSeconds) {
        this.maxQueueWaitSeconds = maxQueueWaitSeconds;
    }

    public int getSseTimeoutSeconds() {
        return sseTimeoutSeconds;
    }

    public void setSseTimeoutSeconds(int sseTimeoutSeconds) {
        this.sseTimeoutSeconds = sseTimeoutSeconds;
    }

    public int getGenerationTimeoutSeconds() {
        return generationTimeoutSeconds;
    }

    public void setGenerationTimeoutSeconds(int generationTimeoutSeconds) {
        this.generationTimeoutSeconds = generationTimeoutSeconds;
    }

    public Compatibility getCompatibility() {
        return compatibility;
    }

    public void setCompatibility(Compatibility compatibility) {
        this.compatibility = compatibility == null ? new Compatibility() : compatibility;
    }
}
