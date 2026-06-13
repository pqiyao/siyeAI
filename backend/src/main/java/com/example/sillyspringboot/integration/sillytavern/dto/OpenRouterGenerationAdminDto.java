package com.example.sillyspringboot.integration.sillytavern.dto;

public class OpenRouterGenerationAdminDto {

    private String chatCompletionSource;
    private String defaultModel;
    private Double defaultTemperature;
    private Integer defaultMaxOutputTokens;
    private Boolean maxContextUnlocked;
    private Integer openaiMaxContext;
    private Double topP;
    private Integer topK;
    private Double minP;
    private Double topA;
    private Double frequencyPenalty;
    private Double presencePenalty;
    private Double repetitionPenalty;
    private String openrouterMiddleout;
    private String stopSequences;
    private Boolean stLinked;
    private String stError;

    public String getChatCompletionSource() {
        return chatCompletionSource;
    }

    public void setChatCompletionSource(String chatCompletionSource) {
        this.chatCompletionSource = chatCompletionSource;
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

    public Boolean getMaxContextUnlocked() {
        return maxContextUnlocked;
    }

    public void setMaxContextUnlocked(Boolean maxContextUnlocked) {
        this.maxContextUnlocked = maxContextUnlocked;
    }

    public Integer getOpenaiMaxContext() {
        return openaiMaxContext;
    }

    public void setOpenaiMaxContext(Integer openaiMaxContext) {
        this.openaiMaxContext = openaiMaxContext;
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

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }

    public Double getMinP() {
        return minP;
    }

    public void setMinP(Double minP) {
        this.minP = minP;
    }

    public Double getTopA() {
        return topA;
    }

    public void setTopA(Double topA) {
        this.topA = topA;
    }

    public Double getRepetitionPenalty() {
        return repetitionPenalty;
    }

    public void setRepetitionPenalty(Double repetitionPenalty) {
        this.repetitionPenalty = repetitionPenalty;
    }

    public String getOpenrouterMiddleout() {
        return openrouterMiddleout;
    }

    public void setOpenrouterMiddleout(String openrouterMiddleout) {
        this.openrouterMiddleout = openrouterMiddleout;
    }

    public String getStopSequences() {
        return stopSequences;
    }

    public void setStopSequences(String stopSequences) {
        this.stopSequences = stopSequences;
    }

    public Boolean getStLinked() {
        return stLinked;
    }

    public void setStLinked(Boolean stLinked) {
        this.stLinked = stLinked;
    }

    public String getStError() {
        return stError;
    }

    public void setStError(String stError) {
        this.stError = stError;
    }
}
