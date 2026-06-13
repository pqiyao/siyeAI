package com.example.sillyspringboot.billing.service.provider;

import com.example.sillyspringboot.auth.config.TelegramProperties;
import com.example.sillyspringboot.billing.config.TelegramStarsPaymentProperties;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TelegramStarsBotClient {

    private final TelegramProperties telegramProperties;
    private final TelegramStarsPaymentProperties paymentProperties;
    private final RestClient restClient;

    public TelegramStarsBotClient(
            TelegramProperties telegramProperties,
            TelegramStarsPaymentProperties paymentProperties
    ) {
        this.telegramProperties = telegramProperties;
        this.paymentProperties = paymentProperties;
        this.restClient = RestClient.builder().build();
    }

    public boolean hasBotToken() {
        return telegramProperties.getBotToken() != null && !telegramProperties.getBotToken().isBlank();
    }

    public String botUsername() {
        return paymentProperties.getBotUsername() == null ? "" : paymentProperties.getBotUsername().trim();
    }

    public String createInvoiceLink(String title, String description, String payload, int starsAmount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", truncate(title, 32));
        body.put("description", truncate(description, 255));
        body.put("payload", payload);
        body.put("provider_token", "");
        body.put("currency", "XTR");
        body.put("prices", List.of(Map.of(
                "label", truncate(title, 32),
                "amount", Math.max(1, starsAmount)
        )));
        if (paymentProperties.getInvoicePhotoUrl() != null && !paymentProperties.getInvoicePhotoUrl().isBlank()) {
            body.put("photo_url", paymentProperties.getInvoicePhotoUrl().trim());
        }
        Map<String, Object> response = post("createInvoiceLink", body);
        Object result = response.get("result");
        return result == null ? "" : String.valueOf(result);
    }

    public void answerPreCheckoutQuery(String preCheckoutQueryId, boolean ok, String errorMessage) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("pre_checkout_query_id", preCheckoutQueryId);
        body.put("ok", ok);
        if (!ok && errorMessage != null && !errorMessage.isBlank()) {
            body.put("error_message", truncate(errorMessage, 200));
        }
        post("answerPreCheckoutQuery", body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String method, Map<String, Object> body) {
        if (!hasBotToken()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Telegram Bot Token 未配置");
        }
        Map<String, Object> response = restClient.post()
                .uri("https://api.telegram.org/bot" + telegramProperties.getBotToken() + "/" + method)
                .body(body)
                .retrieve()
                .body(Map.class);
        if (response == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Telegram 支付接口返回为空");
        }
        Object okValue = response.get("ok");
        if (!(okValue instanceof Boolean ok) || !ok) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    response.get("description") == null
                            ? "Telegram 支付接口调用失败"
                            : String.valueOf(response.get("description"))
            );
        }
        return response;
    }

    private static String truncate(String value, int maxLen) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen);
    }
}
