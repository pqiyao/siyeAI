package com.example.sillyspringboot.ops.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.image-generation")
public class AppImageGenerationProperties {

    private String engine = "user_openai_compatible";

    @Min(1)
    private int globalConcurrentLimit = 2;

    @Min(1)
    private int perUserConcurrentLimit = 1;

    @Min(10)
    private int counterTtlSeconds = 600;

    private StComfy stComfy = new StComfy();

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

    public StComfy getStComfy() {
        return stComfy;
    }

    public void setStComfy(StComfy stComfy) {
        this.stComfy = stComfy == null ? new StComfy() : stComfy;
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
