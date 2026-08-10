package com.example.sillyspringboot.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.payment")
public class PaymentSecretsProperties {

    /** AES Կڼ֧ config_cipherӦ APP_PAYMENT_SECRETS_MASTER_KEY */
    private String secretsMasterKey = "";

    public String getSecretsMasterKey() {
        return secretsMasterKey;
    }

    public void setSecretsMasterKey(String secretsMasterKey) {
        this.secretsMasterKey = secretsMasterKey;
    }
}
