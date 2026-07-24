package com.example.sillyspringboot.billing.service;

import com.example.sillyspringboot.billing.config.TelegramStarsPaymentProperties;
import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.mapper.AppPaymentOrderMapper;
import com.example.sillyspringboot.billing.service.provider.TelegramStarsBotClient;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Service
public class TelegramStarsWebhookService {

    private final TelegramStarsPaymentProperties paymentProperties;
    private final TelegramStarsBotClient botClient;
    private final AppPaymentOrderMapper orderMapper;
    private final StoreService storeService;

    public TelegramStarsWebhookService(
            TelegramStarsPaymentProperties paymentProperties,
            TelegramStarsBotClient botClient,
            AppPaymentOrderMapper orderMapper,
            StoreService storeService
    ) {
        this.paymentProperties = paymentProperties;
        this.botClient = botClient;
        this.orderMapper = orderMapper;
        this.storeService = storeService;
    }

    public void handleWebhook(String secretHeader, Map<String, Object> update) {
        verifySecret(secretHeader);
        if (update == null || update.isEmpty()) {
            return;
        }
        handlePreCheckout(asMap(update.get("pre_checkout_query")));
        handleSuccessfulPayment(findSuccessfulPayment(update));
    }

    private void verifySecret(String secretHeader) {
        String expected = paymentProperties.getWebhookSecret();
        if (expected == null || expected.isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Telegram webhook secret 未配置，已拒绝回调");
        }
        if (secretHeader == null || !constantTimeEquals(expected.trim(), secretHeader.trim())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Telegram webhook secret 校验失败");
        }
    }

    private void handlePreCheckout(Map<String, Object> preCheckoutQuery) {
        if (preCheckoutQuery == null || preCheckoutQuery.isEmpty()) {
            return;
        }
        String queryId = stringValue(preCheckoutQuery.get("id"));
        if (queryId.isBlank()) {
            return;
        }
        String error = validateOrderForTelegram(
                parseOrderNo(stringValue(preCheckoutQuery.get("invoice_payload"))),
                intValue(preCheckoutQuery.get("total_amount"))
        );
        if (error == null) {
            botClient.answerPreCheckoutQuery(queryId, true, null);
        } else {
            botClient.answerPreCheckoutQuery(queryId, false, error);
        }
    }

    private void handleSuccessfulPayment(Map<String, Object> successfulPayment) {
        if (successfulPayment == null || successfulPayment.isEmpty()) {
            return;
        }
        String currency = stringValue(successfulPayment.get("currency"));
        if (!"XTR".equalsIgnoreCase(currency)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Telegram Stars 币种无效");
        }
        String orderNo = parseOrderNo(stringValue(successfulPayment.get("invoice_payload")));
        if (orderNo.isBlank()) {
            return;
        }
        Integer totalAmount = intValue(successfulPayment.get("total_amount"));
        AppPaymentOrder order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (totalAmount == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Telegram Stars 支付金额缺失");
        }
        int expectedStars = expectedStars(nvl(order.getAmountCents()));
        if (totalAmount.intValue() != expectedStars) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Telegram Stars 支付金额与订单不一致");
        }
        String providerTradeNo = stringValue(successfulPayment.get("telegram_payment_charge_id"));
        storeService.confirmProviderPaid(
                orderNo,
                "telegram_star",
                order.getAmountCents(),
                providerTradeNo.isBlank() ? null : providerTradeNo,
                null
        );
    }

    private String validateOrderForTelegram(String orderNo, Integer totalAmount) {
        if (orderNo.isBlank()) {
            return "订单无效，请返回后重试";
        }
        AppPaymentOrder order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            return "订单不存在，请返回后重试";
        }
        String channel = order.getPaymentChannel() == null ? "" : order.getPaymentChannel().trim().toLowerCase();
        if (!"telegram_star".equals(channel) && !"telegram_stars".equals(channel)) {
            return "当前订单不支持 Telegram Stars 支付";
        }
        if ("PAID".equalsIgnoreCase(order.getStatus())) {
            return "订单已支付，请勿重复付款";
        }
        if (totalAmount == null) {
            return "支付金额缺失，请返回后重试";
        }
        int expectedStars = expectedStars(nvl(order.getAmountCents()));
        if (totalAmount.intValue() != expectedStars) {
            return "支付金额与订单不一致";
        }
        return null;
    }

    /** Stars 金额约等ceil(amountCents / 100)（按元取整上入） */
    static int expectedStars(int amountCents) {
        return Math.max(1, (int) Math.ceil(amountCents / 100.0d));
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] a = left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findSuccessfulPayment(Map<String, Object> update) {
        Map<String, Object> message = asMap(update.get("message"));
        if (message != null && message.get("successful_payment") instanceof Map<?, ?> success) {
            return (Map<String, Object>) success;
        }
        Map<String, Object> editedMessage = asMap(update.get("edited_message"));
        if (editedMessage != null && editedMessage.get("successful_payment") instanceof Map<?, ?> success) {
            return (Map<String, Object>) success;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private String parseOrderNo(String payload) {
        String value = payload == null ? "" : payload.trim();
        if (value.startsWith("store:")) {
            return value.substring("store:".length()).trim();
        }
        if (value.startsWith("telegram-stars:")) {
            return value.substring("telegram-stars:".length()).trim();
        }
        return value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }
}
