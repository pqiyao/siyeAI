package com.example.sillyspringboot.conversation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.memory")
public class MemoryLlmProperties {

    /**
     * 是否通过 ST /chat-completions/generate 生成记忆摘要（失败时可回退启发式）。
     */
    private boolean llmEnabled = true;

    /** 参与摘要的消息条数上限（从最早的有效消息起截断）。 */
    private int maxMessages = 80;

    /** 送入模型的对话文本最大字符（超出则截断尾部）。 */
    private int maxTranscriptChars = 14000;

    /** Enable stable-bucket incremental extraction for a limited rollout. */
    private boolean incrementalExtractionEnabled = true;

    /** Percentage of conversation branches assigned to incremental extraction. */
    private int incrementalRolloutPercent = 10;

    /** Earlier messages retained around the extraction cursor for continuity. */
    private int incrementalOverlapMessages = 12;

    /** Force a full extraction whenever this message-count boundary is crossed. */
    private int fullRecalibrationMessageInterval = 80;

    /** Non-protected existing memories supplied to one incremental extraction. */
    private int incrementalMaxRelevantEntries = 40;

    private boolean fallbackToHeuristic = true;

    private int autoEveryMessages = 20;

    private int autoMinMinutesBetween = 60;

    private int autoMinVisibleMessages = 6;

    private int historyRebuildCooldownMinutes = 10;

    private int autoRefreshWorkerThreads = 2;

    private int autoRefreshQueueCapacity = 64;

    private int autoRefreshLeaseSeconds = 900;

    private int manualRefreshCooldownSeconds = 60;

    private int manualRefreshLeaseSeconds = 300;

    private int maxEnabledEntries = 80;

    private int maxConstantEntries = 12;

    private int maxArchivedEntries = 40;

    private int maxEntryContentChars = 300;

    /** 用户手工记忆完整保存上限；注入 Prompt 时由独立 token 预算再次裁剪。 */
    private int maxManualEntryContentChars = 1200;

    private int maxKeywords = 8;

    private boolean worldbookSyncRetryEnabled = true;

    private int worldbookSyncRetryDelaySeconds = 30;

    private int worldbookSyncLeaseSeconds = 120;

    private int worldbookSyncRetryBatchSize = 16;

    public boolean isLlmEnabled() {
        return llmEnabled;
    }

    public void setLlmEnabled(boolean llmEnabled) {
        this.llmEnabled = llmEnabled;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    public int getMaxTranscriptChars() {
        return maxTranscriptChars;
    }

    public void setMaxTranscriptChars(int maxTranscriptChars) {
        this.maxTranscriptChars = maxTranscriptChars;
    }

    public boolean isIncrementalExtractionEnabled() {
        return incrementalExtractionEnabled;
    }

    public void setIncrementalExtractionEnabled(boolean incrementalExtractionEnabled) {
        this.incrementalExtractionEnabled = incrementalExtractionEnabled;
    }

    public int getIncrementalRolloutPercent() {
        return incrementalRolloutPercent;
    }

    public void setIncrementalRolloutPercent(int incrementalRolloutPercent) {
        this.incrementalRolloutPercent = incrementalRolloutPercent;
    }

    public int getIncrementalOverlapMessages() {
        return incrementalOverlapMessages;
    }

    public void setIncrementalOverlapMessages(int incrementalOverlapMessages) {
        this.incrementalOverlapMessages = incrementalOverlapMessages;
    }

    public int getFullRecalibrationMessageInterval() {
        return fullRecalibrationMessageInterval;
    }

    public void setFullRecalibrationMessageInterval(int fullRecalibrationMessageInterval) {
        this.fullRecalibrationMessageInterval = fullRecalibrationMessageInterval;
    }

    public int getIncrementalMaxRelevantEntries() {
        return incrementalMaxRelevantEntries;
    }

    public void setIncrementalMaxRelevantEntries(int incrementalMaxRelevantEntries) {
        this.incrementalMaxRelevantEntries = incrementalMaxRelevantEntries;
    }

    public boolean isFallbackToHeuristic() {
        return fallbackToHeuristic;
    }

    public void setFallbackToHeuristic(boolean fallbackToHeuristic) {
        this.fallbackToHeuristic = fallbackToHeuristic;
    }

    public int getAutoEveryMessages() {
        return autoEveryMessages;
    }

    public void setAutoEveryMessages(int autoEveryMessages) {
        this.autoEveryMessages = autoEveryMessages;
    }

    public int getAutoMinMinutesBetween() {
        return autoMinMinutesBetween;
    }

    public void setAutoMinMinutesBetween(int autoMinMinutesBetween) {
        this.autoMinMinutesBetween = autoMinMinutesBetween;
    }

    public int getAutoMinVisibleMessages() {
        return autoMinVisibleMessages;
    }

    public void setAutoMinVisibleMessages(int autoMinVisibleMessages) {
        this.autoMinVisibleMessages = autoMinVisibleMessages;
    }

    public int getHistoryRebuildCooldownMinutes() {
        return historyRebuildCooldownMinutes;
    }

    public void setHistoryRebuildCooldownMinutes(int historyRebuildCooldownMinutes) {
        this.historyRebuildCooldownMinutes = historyRebuildCooldownMinutes;
    }

    public int getAutoRefreshWorkerThreads() {
        return autoRefreshWorkerThreads;
    }

    public void setAutoRefreshWorkerThreads(int autoRefreshWorkerThreads) {
        this.autoRefreshWorkerThreads = autoRefreshWorkerThreads;
    }

    public int getAutoRefreshQueueCapacity() {
        return autoRefreshQueueCapacity;
    }

    public void setAutoRefreshQueueCapacity(int autoRefreshQueueCapacity) {
        this.autoRefreshQueueCapacity = autoRefreshQueueCapacity;
    }

    public int getAutoRefreshLeaseSeconds() {
        return autoRefreshLeaseSeconds;
    }

    public void setAutoRefreshLeaseSeconds(int autoRefreshLeaseSeconds) {
        this.autoRefreshLeaseSeconds = autoRefreshLeaseSeconds;
    }

    public int getManualRefreshCooldownSeconds() {
        return manualRefreshCooldownSeconds;
    }

    public void setManualRefreshCooldownSeconds(int manualRefreshCooldownSeconds) {
        this.manualRefreshCooldownSeconds = manualRefreshCooldownSeconds;
    }

    public int getManualRefreshLeaseSeconds() {
        return manualRefreshLeaseSeconds;
    }

    public void setManualRefreshLeaseSeconds(int manualRefreshLeaseSeconds) {
        this.manualRefreshLeaseSeconds = manualRefreshLeaseSeconds;
    }

    public int getMaxEnabledEntries() {
        return maxEnabledEntries;
    }

    public void setMaxEnabledEntries(int maxEnabledEntries) {
        this.maxEnabledEntries = maxEnabledEntries;
    }

    public int getMaxConstantEntries() {
        return maxConstantEntries;
    }

    public void setMaxConstantEntries(int maxConstantEntries) {
        this.maxConstantEntries = maxConstantEntries;
    }

    public int getMaxArchivedEntries() {
        return maxArchivedEntries;
    }

    public void setMaxArchivedEntries(int maxArchivedEntries) {
        this.maxArchivedEntries = maxArchivedEntries;
    }

    public int getMaxEntryContentChars() {
        return maxEntryContentChars;
    }

    public void setMaxEntryContentChars(int maxEntryContentChars) {
        this.maxEntryContentChars = maxEntryContentChars;
    }

    public int getMaxManualEntryContentChars() {
        return maxManualEntryContentChars;
    }

    public void setMaxManualEntryContentChars(int maxManualEntryContentChars) {
        this.maxManualEntryContentChars = maxManualEntryContentChars;
    }

    public int getMaxKeywords() {
        return maxKeywords;
    }

    public void setMaxKeywords(int maxKeywords) {
        this.maxKeywords = maxKeywords;
    }

    public boolean isWorldbookSyncRetryEnabled() {
        return worldbookSyncRetryEnabled;
    }

    public void setWorldbookSyncRetryEnabled(boolean worldbookSyncRetryEnabled) {
        this.worldbookSyncRetryEnabled = worldbookSyncRetryEnabled;
    }

    public int getWorldbookSyncRetryDelaySeconds() {
        return worldbookSyncRetryDelaySeconds;
    }

    public void setWorldbookSyncRetryDelaySeconds(int worldbookSyncRetryDelaySeconds) {
        this.worldbookSyncRetryDelaySeconds = worldbookSyncRetryDelaySeconds;
    }

    public int getWorldbookSyncLeaseSeconds() {
        return worldbookSyncLeaseSeconds;
    }

    public void setWorldbookSyncLeaseSeconds(int worldbookSyncLeaseSeconds) {
        this.worldbookSyncLeaseSeconds = worldbookSyncLeaseSeconds;
    }

    public int getWorldbookSyncRetryBatchSize() {
        return worldbookSyncRetryBatchSize;
    }

    public void setWorldbookSyncRetryBatchSize(int worldbookSyncRetryBatchSize) {
        this.worldbookSyncRetryBatchSize = worldbookSyncRetryBatchSize;
    }
}
