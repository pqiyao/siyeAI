package com.example.sillyspringboot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.social-upload-rate-limit")
public class SocialUploadRateLimitProperties {

    private boolean enabled = true;

    @Valid
    private Bucket upload = new Bucket(60, 12);

    @Valid
    private Bucket uploadTotal = new Bucket(300, 40);

    @Valid
    private Bucket socialWrite = new Bucket(60, 30);

    @Valid
    private Bucket socialWriteTotal = new Bucket(300, 120);

    @Valid
    private Bucket gatewayUpload = new Bucket(60, 30);

    @Valid
    private Bucket gatewayUploadTotal = new Bucket(300, 100);

    @Valid
    private Bucket gatewaySocialWrite = new Bucket(60, 120);

    @Valid
    private Bucket gatewaySocialWriteTotal = new Bucket(300, 300);

    @Valid
    private Bucket websocketHandshake = new Bucket(60, 20);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Bucket getUpload() {
        return upload;
    }

    public void setUpload(Bucket upload) {
        this.upload = upload;
    }

    public Bucket getUploadTotal() {
        return uploadTotal;
    }

    public void setUploadTotal(Bucket uploadTotal) {
        this.uploadTotal = uploadTotal;
    }

    public Bucket getSocialWrite() {
        return socialWrite;
    }

    public void setSocialWrite(Bucket socialWrite) {
        this.socialWrite = socialWrite;
    }

    public Bucket getSocialWriteTotal() {
        return socialWriteTotal;
    }

    public void setSocialWriteTotal(Bucket socialWriteTotal) {
        this.socialWriteTotal = socialWriteTotal;
    }

    public Bucket getGatewayUpload() {
        return gatewayUpload;
    }

    public void setGatewayUpload(Bucket gatewayUpload) {
        this.gatewayUpload = gatewayUpload;
    }

    public Bucket getGatewayUploadTotal() {
        return gatewayUploadTotal;
    }

    public void setGatewayUploadTotal(Bucket gatewayUploadTotal) {
        this.gatewayUploadTotal = gatewayUploadTotal;
    }

    public Bucket getGatewaySocialWrite() {
        return gatewaySocialWrite;
    }

    public void setGatewaySocialWrite(Bucket gatewaySocialWrite) {
        this.gatewaySocialWrite = gatewaySocialWrite;
    }

    public Bucket getGatewaySocialWriteTotal() {
        return gatewaySocialWriteTotal;
    }

    public void setGatewaySocialWriteTotal(Bucket gatewaySocialWriteTotal) {
        this.gatewaySocialWriteTotal = gatewaySocialWriteTotal;
    }

    public Bucket getWebsocketHandshake() {
        return websocketHandshake;
    }

    public void setWebsocketHandshake(Bucket websocketHandshake) {
        this.websocketHandshake = websocketHandshake;
    }

    public static class Bucket {
        @Min(1)
        private int windowSeconds;

        @Min(1)
        private int maxRequests;

        public Bucket() {
        }

        public Bucket(int windowSeconds, int maxRequests) {
            this.windowSeconds = windowSeconds;
            this.maxRequests = maxRequests;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public int getMaxRequests() {
            return maxRequests;
        }

        public void setMaxRequests(int maxRequests) {
            this.maxRequests = maxRequests;
        }
    }
}
