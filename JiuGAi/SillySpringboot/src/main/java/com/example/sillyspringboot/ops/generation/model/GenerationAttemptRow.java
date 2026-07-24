package com.example.sillyspringboot.ops.generation.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class GenerationAttemptRow {

    private Long generationTaskId;
    private Long conversationId;
    private Long characterId;
    private Integer attemptNo;
    private String providerKey;
    private String routeKey;
    private String providerSource;
    private String model;
    private Boolean byok;
    private Boolean fallback;
    private LocalDateTime startedAt;
    private LocalDateTime firstTokenAt;
    private LocalDateTime finishedAt;
    private Integer ttftMs;
    private Integer durationMs;
    private Integer httpStatus;
    private String status;
    private String errorCode;
    private Integer promptTokens;
    private Integer completionTokens;
    private Boolean promptTokensEstimated;
    private Boolean completionTokensEstimated;
    private Long pricingId;
    private String pricingVersion;
    private String currency;
    private BigDecimal inputUsdPerMillionTokens;
    private BigDecimal outputUsdPerMillionTokens;
    private BigDecimal inputCostUsd;
    private BigDecimal outputCostUsd;
    private BigDecimal totalCostUsd;
    private Boolean costEstimated;
    private Boolean costPartial;

    public Long getGenerationTaskId() { return generationTaskId; }
    public void setGenerationTaskId(Long value) { this.generationTaskId = value; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long value) { this.conversationId = value; }
    public Long getCharacterId() { return characterId; }
    public void setCharacterId(Long value) { this.characterId = value; }
    public Integer getAttemptNo() { return attemptNo; }
    public void setAttemptNo(Integer value) { this.attemptNo = value; }
    public String getProviderKey() { return providerKey; }
    public void setProviderKey(String value) { this.providerKey = value; }
    public String getRouteKey() { return routeKey; }
    public void setRouteKey(String value) { this.routeKey = value; }
    public String getProviderSource() { return providerSource; }
    public void setProviderSource(String value) { this.providerSource = value; }
    public String getModel() { return model; }
    public void setModel(String value) { this.model = value; }
    public Boolean getByok() { return byok; }
    public void setByok(Boolean value) { this.byok = value; }
    public Boolean getFallback() { return fallback; }
    public void setFallback(Boolean value) { this.fallback = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { this.startedAt = value; }
    public LocalDateTime getFirstTokenAt() { return firstTokenAt; }
    public void setFirstTokenAt(LocalDateTime value) { this.firstTokenAt = value; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime value) { this.finishedAt = value; }
    public Integer getTtftMs() { return ttftMs; }
    public void setTtftMs(Integer value) { this.ttftMs = value; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer value) { this.durationMs = value; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer value) { this.httpStatus = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String value) { this.errorCode = value; }
    public Integer getPromptTokens() { return promptTokens; }
    public void setPromptTokens(Integer value) { this.promptTokens = value; }
    public Integer getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(Integer value) { this.completionTokens = value; }
    public Boolean getPromptTokensEstimated() { return promptTokensEstimated; }
    public void setPromptTokensEstimated(Boolean value) { this.promptTokensEstimated = value; }
    public Boolean getCompletionTokensEstimated() { return completionTokensEstimated; }
    public void setCompletionTokensEstimated(Boolean value) { this.completionTokensEstimated = value; }
    public Long getPricingId() { return pricingId; }
    public void setPricingId(Long value) { this.pricingId = value; }
    public String getPricingVersion() { return pricingVersion; }
    public void setPricingVersion(String value) { this.pricingVersion = value; }
    public String getCurrency() { return currency; }
    public void setCurrency(String value) { this.currency = value; }
    public BigDecimal getInputUsdPerMillionTokens() { return inputUsdPerMillionTokens; }
    public void setInputUsdPerMillionTokens(BigDecimal value) { this.inputUsdPerMillionTokens = value; }
    public BigDecimal getOutputUsdPerMillionTokens() { return outputUsdPerMillionTokens; }
    public void setOutputUsdPerMillionTokens(BigDecimal value) { this.outputUsdPerMillionTokens = value; }
    public BigDecimal getInputCostUsd() { return inputCostUsd; }
    public void setInputCostUsd(BigDecimal value) { this.inputCostUsd = value; }
    public BigDecimal getOutputCostUsd() { return outputCostUsd; }
    public void setOutputCostUsd(BigDecimal value) { this.outputCostUsd = value; }
    public BigDecimal getTotalCostUsd() { return totalCostUsd; }
    public void setTotalCostUsd(BigDecimal value) { this.totalCostUsd = value; }
    public Boolean getCostEstimated() { return costEstimated; }
    public void setCostEstimated(Boolean value) { this.costEstimated = value; }
    public Boolean getCostPartial() { return costPartial; }
    public void setCostPartial(Boolean value) { this.costPartial = value; }
}
