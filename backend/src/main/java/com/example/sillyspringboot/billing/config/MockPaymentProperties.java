package com.example.sillyspringboot.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.payment.mock")
public class MockPaymentProperties {

    private boolean enabled = true;
    private boolean allowInProd = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAllowInProd() {
        return allowInProd;
    }

    public void setAllowInProd(boolean allowInProd) {
        this.allowInProd = allowInProd;
    }
}
