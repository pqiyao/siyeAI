package com.example.sillyspringboot.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.payment.telegram-stars")
public class TelegramStarsPaymentProperties {

    private boolean enabled = false;
    private String botUsername = "";
    private String webhookSecret = "";
    private String invoicePhotoUrl = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBotUsername() {
        return botUsername;
    }

    public void setBotUsername(String botUsername) {
        this.botUsername = botUsername;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getInvoicePhotoUrl() {
        return invoicePhotoUrl;
    }

    public void setInvoicePhotoUrl(String invoicePhotoUrl) {
        this.invoicePhotoUrl = invoicePhotoUrl;
    }
}
