package com.example.sillyspringboot.integration.sillytavern.entity;

import java.time.LocalDateTime;

public class OpenRouterGenerationSettings {

    private Long id;
    private String defaultModel;
    private Double defaultTemperature;
    private Integer defaultMaxOutputTokens;
    private Double topP;
    private Double frequencyPenalty;
    private Double presencePenalty;
    private String stopSequences;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public Double getDefaultTemperature() {
        return defaultTemperature;
    }

    public void setDefaultTemperature(Double defaultTemperature) {
        this.defaultTemperature = defaultTemperature;
    }

    public Integer getDefaultMaxOutputTokens() {
        return defaultMaxOutputTokens;
    }

    public void setDefaultMaxOutputTokens(Integer defaultMaxOutputTokens) {
        this.defaultMaxOutputTokens = defaultMaxOutputTokens;
    }

    public Double getTopP() {
        return topP;
    }

    public void setTopP(Double topP) {
        this.topP = topP;
    }

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public void setFrequencyPenalty(Double frequencyPenalty) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public String getStopSequences() {
        return stopSequences;
    }

    public void setStopSequences(String stopSequences) {
        this.stopSequences = stopSequences;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
