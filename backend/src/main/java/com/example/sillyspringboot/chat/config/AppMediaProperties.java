package com.example.sillyspringboot.chat.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.media")
public class AppMediaProperties {

    @Valid
    private Limits tts = new Limits(8, 1, 12);

    @Valid
    private Limits stt = new Limits(4, 1, 6);

    @Min(10)
    private int counterTtlSeconds = 180;

    @Min(10)
    private int rateWindowSeconds = 60;

    public Limits getTts() {
        return tts;
    }

    public void setTts(Limits tts) {
        this.tts = tts == null ? new Limits(8, 1, 12) : tts;
    }

    public Limits getStt() {
        return stt;
    }

    public void setStt(Limits stt) {
        this.stt = stt == null ? new Limits(4, 1, 6) : stt;
    }

    public int getCounterTtlSeconds() {
        return counterTtlSeconds;
    }

    public void setCounterTtlSeconds(int counterTtlSeconds) {
        this.counterTtlSeconds = counterTtlSeconds;
    }

    public int getRateWindowSeconds() {
        return rateWindowSeconds;
    }

    public void setRateWindowSeconds(int rateWindowSeconds) {
        this.rateWindowSeconds = rateWindowSeconds;
    }

    public static class Limits {
        @Min(1)
        private int globalConcurrentLimit;
        @Min(1)
        private int perUserConcurrentLimit;
        @Min(1)
        private int perUserRequestsPerWindow;

        public Limits() {
            this(1, 1, 6);
        }

        public Limits(int globalConcurrentLimit, int perUserConcurrentLimit, int perUserRequestsPerWindow) {
            this.globalConcurrentLimit = globalConcurrentLimit;
            this.perUserConcurrentLimit = perUserConcurrentLimit;
            this.perUserRequestsPerWindow = perUserRequestsPerWindow;
        }

        public int getGlobalConcurrentLimit() { return globalConcurrentLimit; }
        public void setGlobalConcurrentLimit(int value) { this.globalConcurrentLimit = value; }
        public int getPerUserConcurrentLimit() { return perUserConcurrentLimit; }
        public void setPerUserConcurrentLimit(int value) { this.perUserConcurrentLimit = value; }
        public int getPerUserRequestsPerWindow() { return perUserRequestsPerWindow; }
        public void setPerUserRequestsPerWindow(int value) { this.perUserRequestsPerWindow = value; }
    }
}
