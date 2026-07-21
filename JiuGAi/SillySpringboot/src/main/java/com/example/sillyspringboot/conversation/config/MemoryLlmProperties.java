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

    private boolean fallbackToHeuristic = true;

    private int autoEveryMessages = 20;

    private int autoMinMinutesBetween = 30;

    private int autoMinVisibleMessages = 6;

    private int maxEnabledEntries = 80;

    private int maxConstantEntries = 12;

    private int maxEntryContentChars = 300;

    private int maxKeywords = 8;

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

    public int getMaxEntryContentChars() {
        return maxEntryContentChars;
    }

    public void setMaxEntryContentChars(int maxEntryContentChars) {
        this.maxEntryContentChars = maxEntryContentChars;
    }

    public int getMaxKeywords() {
        return maxKeywords;
    }

    public void setMaxKeywords(int maxKeywords) {
        this.maxKeywords = maxKeywords;
    }
}
