package com.example.sillyspringboot.billing.service.provider;

public class StorePaymentContext {

    private final String clientIp;
    private final String userAgent;

    public StorePaymentContext(String clientIp, String userAgent) {
        this.clientIp = clientIp == null ? "" : clientIp.trim();
        this.userAgent = userAgent == null ? "" : userAgent.trim();
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public static StorePaymentContext empty() {
        return new StorePaymentContext("", "");
    }
}
