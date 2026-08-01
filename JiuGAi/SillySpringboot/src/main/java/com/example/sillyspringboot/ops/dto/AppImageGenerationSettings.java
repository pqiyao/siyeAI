package com.example.sillyspringboot.ops.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class AppImageGenerationSettings {

    private String engine = "user_openai_compatible";
    private int globalConcurrentLimit = 2;
    private int perUserConcurrentLimit = 1;
    private int counterTtlSeconds = 600;
    // Retained only so historical runtime-setting JSON can still be deserialized safely.
    private String managedProviderSource = "siliconflow";
    private String managedImageModelName = "";
    private String managedApiKeyCipher = "";
    private String managedCustomUrl = "";
    private String comfyUrl = "http://127.0.0.1:8188";
    private String workflow = "Default_Comfy_Workflow.json";
    private String referenceWorkflow = "Char_Avatar_Comfy_Workflow.json";
    private String model = "";
    private String sampler = "euler";
    private String scheduler = "normal";
    private String negativePrompt = "low quality, blurry, bad anatomy, extra fingers, watermark, text";
    private int steps = 28;
    private double scale = 7.0d;
    private long seed = -1L;
    private double denoise = 1.0d;
    private long requestTimeoutSeconds = 90L;
    private String defaultConsistencyMode = "balanced";
    private List<String> allowedConsistencyModes = new ArrayList<>(List.of("free", "balanced"));
    private String defaultReferenceSourceMode = "latest_generated_first";
    private List<String> allowedReferenceSourceModes = new ArrayList<>(List.of("latest_generated_first", "avatar_only"));
    private boolean referenceImagesEnabled = true;
    private boolean recentSceneContextEnabled = true;

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine == null ? "" : engine;
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

    public String getManagedProviderSource() {
        return managedProviderSource;
    }

    public void setManagedProviderSource(String managedProviderSource) {
        this.managedProviderSource = managedProviderSource == null ? "" : managedProviderSource;
    }

    public String getManagedImageModelName() {
        return managedImageModelName;
    }

    public void setManagedImageModelName(String managedImageModelName) {
        this.managedImageModelName = managedImageModelName == null ? "" : managedImageModelName;
    }

    public String getManagedApiKeyCipher() {
        return managedApiKeyCipher;
    }

    public void setManagedApiKeyCipher(String managedApiKeyCipher) {
        this.managedApiKeyCipher = managedApiKeyCipher == null ? "" : managedApiKeyCipher;
    }

    public String getManagedCustomUrl() {
        return managedCustomUrl;
    }

    public void setManagedCustomUrl(String managedCustomUrl) {
        this.managedCustomUrl = managedCustomUrl == null ? "" : managedCustomUrl;
    }

    public String getComfyUrl() {
        return comfyUrl;
    }

    public void setComfyUrl(String comfyUrl) {
        this.comfyUrl = comfyUrl == null ? "" : comfyUrl;
    }

    public String getWorkflow() {
        return workflow;
    }

    public void setWorkflow(String workflow) {
        this.workflow = workflow == null ? "" : workflow;
    }

    public String getReferenceWorkflow() {
        return referenceWorkflow;
    }

    public void setReferenceWorkflow(String referenceWorkflow) {
        this.referenceWorkflow = referenceWorkflow == null ? "" : referenceWorkflow;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model == null ? "" : model;
    }

    public String getSampler() {
        return sampler;
    }

    public void setSampler(String sampler) {
        this.sampler = sampler == null ? "" : sampler;
    }

    public String getScheduler() {
        return scheduler;
    }

    public void setScheduler(String scheduler) {
        this.scheduler = scheduler == null ? "" : scheduler;
    }

    public String getNegativePrompt() {
        return negativePrompt;
    }

    public void setNegativePrompt(String negativePrompt) {
        this.negativePrompt = negativePrompt == null ? "" : negativePrompt;
    }

    public int getSteps() {
        return steps;
    }

    public void setSteps(int steps) {
        this.steps = steps;
    }

    public double getScale() {
        return scale;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public double getDenoise() {
        return denoise;
    }

    public void setDenoise(double denoise) {
        this.denoise = denoise;
    }

    public long getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public void setRequestTimeoutSeconds(long requestTimeoutSeconds) {
        this.requestTimeoutSeconds = Math.max(1L, requestTimeoutSeconds);
    }

    @JsonIgnore
    public Duration getRequestTimeout() {
        return Duration.ofSeconds(Math.max(1L, requestTimeoutSeconds));
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeoutSeconds = requestTimeout == null ? 90L : Math.max(1L, requestTimeout.toSeconds());
    }

    public String getDefaultConsistencyMode() {
        return defaultConsistencyMode;
    }

    public void setDefaultConsistencyMode(String defaultConsistencyMode) {
        this.defaultConsistencyMode = defaultConsistencyMode == null ? "" : defaultConsistencyMode;
    }

    public List<String> getAllowedConsistencyModes() {
        return allowedConsistencyModes;
    }

    public void setAllowedConsistencyModes(List<String> allowedConsistencyModes) {
        this.allowedConsistencyModes = allowedConsistencyModes == null ? new ArrayList<>() : new ArrayList<>(allowedConsistencyModes);
    }

    public String getDefaultReferenceSourceMode() {
        return defaultReferenceSourceMode;
    }

    public void setDefaultReferenceSourceMode(String defaultReferenceSourceMode) {
        this.defaultReferenceSourceMode = defaultReferenceSourceMode == null ? "" : defaultReferenceSourceMode;
    }

    public List<String> getAllowedReferenceSourceModes() {
        return allowedReferenceSourceModes;
    }

    public void setAllowedReferenceSourceModes(List<String> allowedReferenceSourceModes) {
        this.allowedReferenceSourceModes = allowedReferenceSourceModes == null ? new ArrayList<>() : new ArrayList<>(allowedReferenceSourceModes);
    }

    public boolean isReferenceImagesEnabled() {
        return referenceImagesEnabled;
    }

    public void setReferenceImagesEnabled(boolean referenceImagesEnabled) {
        this.referenceImagesEnabled = referenceImagesEnabled;
    }

    public boolean isRecentSceneContextEnabled() {
        return recentSceneContextEnabled;
    }

    public void setRecentSceneContextEnabled(boolean recentSceneContextEnabled) {
        this.recentSceneContextEnabled = recentSceneContextEnabled;
    }
}
