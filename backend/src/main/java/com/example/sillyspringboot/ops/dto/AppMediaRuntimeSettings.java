package com.example.sillyspringboot.ops.dto;

public class AppMediaRuntimeSettings {

    private int counterTtlSeconds = 180;
    private int rateWindowSeconds = 60;
    private Limits tts = new Limits(8, 1, 12);
    private Limits stt = new Limits(4, 1, 6);
    private Limits voiceClone = new Limits(3, 1, 3);

    public int getCounterTtlSeconds() { return counterTtlSeconds; }
    public void setCounterTtlSeconds(int counterTtlSeconds) { this.counterTtlSeconds = counterTtlSeconds; }
    public int getRateWindowSeconds() { return rateWindowSeconds; }
    public void setRateWindowSeconds(int rateWindowSeconds) { this.rateWindowSeconds = rateWindowSeconds; }
    public Limits getTts() { return tts; }
    public void setTts(Limits tts) { this.tts = tts == null ? new Limits(8, 1, 12) : tts; }
    public Limits getStt() { return stt; }
    public void setStt(Limits stt) { this.stt = stt == null ? new Limits(4, 1, 6) : stt; }
    public Limits getVoiceClone() { return voiceClone; }
    public void setVoiceClone(Limits value) { this.voiceClone = value == null ? new Limits(3, 1, 3) : value; }

    public static class Limits {
        private int globalConcurrentLimit;
        private int perUserConcurrentLimit;
        private int perUserRequestsPerWindow;

        public Limits() { this(1, 1, 6); }
        public Limits(int globalConcurrentLimit, int perUserConcurrentLimit, int perUserRequestsPerWindow) {
            this.globalConcurrentLimit = globalConcurrentLimit;
            this.perUserConcurrentLimit = perUserConcurrentLimit;
            this.perUserRequestsPerWindow = perUserRequestsPerWindow;
        }

        public int getGlobalConcurrentLimit() { return globalConcurrentLimit; }
        public void setGlobalConcurrentLimit(int globalConcurrentLimit) { this.globalConcurrentLimit = globalConcurrentLimit; }
        public int getPerUserConcurrentLimit() { return perUserConcurrentLimit; }
        public void setPerUserConcurrentLimit(int perUserConcurrentLimit) { this.perUserConcurrentLimit = perUserConcurrentLimit; }
        public int getPerUserRequestsPerWindow() { return perUserRequestsPerWindow; }
        public void setPerUserRequestsPerWindow(int perUserRequestsPerWindow) { this.perUserRequestsPerWindow = perUserRequestsPerWindow; }
    }
}
