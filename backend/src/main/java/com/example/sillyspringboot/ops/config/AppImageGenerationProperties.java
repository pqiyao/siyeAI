package com.example.sillyspringboot.ops.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.image-generation")
public class AppImageGenerationProperties {

    private String engine = "novelai";

    @Min(1)
    private int globalConcurrentLimit = 2;

    @Min(1)
    private int perUserConcurrentLimit = 1;

    @Min(10)
    private int counterTtlSeconds = 600;

    @Min(60)
    private int resultTtlSeconds = 86400;

    @Min(1048576)
    private int maxCachedResultBytes = 1024 * 1024;

    @Min(1048576)
    private int maxStoredImageBytes = 32 * 1024 * 1024;

    private StComfy stComfy = new StComfy();
    private NovelAi novelAi = new NovelAi();

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine == null ? "user_openai_compatible" : engine;
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

    public int getResultTtlSeconds() { return resultTtlSeconds; }
    public void setResultTtlSeconds(int resultTtlSeconds) { this.resultTtlSeconds = resultTtlSeconds; }
    public int getMaxCachedResultBytes() { return maxCachedResultBytes; }
    public void setMaxCachedResultBytes(int maxCachedResultBytes) { this.maxCachedResultBytes = maxCachedResultBytes; }
    public int getMaxStoredImageBytes() { return maxStoredImageBytes; }
    public void setMaxStoredImageBytes(int maxStoredImageBytes) { this.maxStoredImageBytes = maxStoredImageBytes; }

    public StComfy getStComfy() {
        return stComfy;
    }

    public void setStComfy(StComfy stComfy) {
        this.stComfy = stComfy == null ? new StComfy() : stComfy;
    }

    public NovelAi getNovelAi() {
        return novelAi;
    }

    public void setNovelAi(NovelAi novelAi) {
        this.novelAi = novelAi == null ? new NovelAi() : novelAi;
    }

    public static class NovelAi {

        private String baseUrl = "https://image.novelai.net";
        private String token = "";
        private String model = "nai-diffusion-4-5-full";
        private String sampler = "k_dpmpp_2m";
        private String scheduler = "karras";
        private int steps = 28;
        private double scale = 9.0d;
        private long seed = -1L;
        private boolean sm = false;
        private boolean smDyn = false;
        private boolean decrisper = false;
        private boolean varietyBoost = false;
        private Duration requestTimeout = Duration.ofSeconds(120);
        private int maxResponseBytes = 24 * 1024 * 1024;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl == null ? "" : baseUrl; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token == null ? "" : token; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model == null ? "" : model; }
        public String getSampler() { return sampler; }
        public void setSampler(String sampler) { this.sampler = sampler == null ? "" : sampler; }
        public String getScheduler() { return scheduler; }
        public void setScheduler(String scheduler) { this.scheduler = scheduler == null ? "" : scheduler; }
        public int getSteps() { return steps; }
        public void setSteps(int steps) { this.steps = steps; }
        public double getScale() { return scale; }
        public void setScale(double scale) { this.scale = scale; }
        public long getSeed() { return seed; }
        public void setSeed(long seed) { this.seed = seed; }
        public boolean isSm() { return sm; }
        public void setSm(boolean sm) { this.sm = sm; }
        public boolean isSmDyn() { return smDyn; }
        public void setSmDyn(boolean smDyn) { this.smDyn = smDyn; }
        public boolean isDecrisper() { return decrisper; }
        public void setDecrisper(boolean decrisper) { this.decrisper = decrisper; }
        public boolean isVarietyBoost() { return varietyBoost; }
        public void setVarietyBoost(boolean varietyBoost) { this.varietyBoost = varietyBoost; }
        public Duration getRequestTimeout() { return requestTimeout; }
        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(120) : requestTimeout;
        }
        public int getMaxResponseBytes() { return maxResponseBytes; }
        public void setMaxResponseBytes(int maxResponseBytes) { this.maxResponseBytes = maxResponseBytes; }
    }

    public static class StComfy {

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
        private Duration requestTimeout = Duration.ofSeconds(90);
        private boolean promptEnhancementEnabled = false;

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

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout == null ? Duration.ofSeconds(90) : requestTimeout;
        }

        public boolean isPromptEnhancementEnabled() {
            return promptEnhancementEnabled;
        }

        public void setPromptEnhancementEnabled(boolean promptEnhancementEnabled) {
            this.promptEnhancementEnabled = promptEnhancementEnabled;
        }
    }
}
