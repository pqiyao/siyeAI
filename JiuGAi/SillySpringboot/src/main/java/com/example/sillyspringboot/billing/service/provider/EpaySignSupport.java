package com.example.sillyspringboot.billing.service.provider;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ֧׼ MD5 ǩ key ֵƴΪ a=b&c=d + key MD5 Сд
 */
public final class EpaySignSupport {

    private static final Pattern MONEY_PATTERN = Pattern.compile("[0-9]+(?:\\.[0-9]{1,2})?");

    private EpaySignSupport() {
    }

    public static String sign(Map<String, String> params, String key) {
        String content = buildSignContent(params) + (key == null ? "" : key);
        return md5Hex(content);
    }

    public static boolean verify(Map<String, String> params, String key, String sign) {
        if (sign == null || sign.isBlank()) {
            return false;
        }
        String expected = sign(params, key);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                sign.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        );
    }

    public static String buildSignContent(Map<String, String> params) {
        TreeMap<String, String> sorted = new TreeMap<>();
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                if (k == null || k.isBlank() || "sign".equalsIgnoreCase(k) || "sign_type".equalsIgnoreCase(k)) {
                    continue;
                }
                if (v == null || v.isBlank()) {
                    continue;
                }
                sorted.put(k, v);
            }
        }
        return sorted.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
    }

    public static String md5Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hashed = digest.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("cannot compute epay md5", ex);
        }
    }

    public static String moneyYuan(int amountCents) {
        if (amountCents < 0) {
            throw new IllegalArgumentException("epay amount must not be negative");
        }
        return BigDecimal.valueOf(amountCents, 2).toPlainString();
    }

    public static int parseMoneyToCents(String money) {
        if (money == null || money.isBlank()) {
            return -1;
        }
        String value = money.trim();
        if (!MONEY_PATTERN.matcher(value).matches()) {
            return -1;
        }
        try {
            return new BigDecimal(value).movePointRight(2).intValueExact();
        } catch (ArithmeticException ignored) {
            return -1;
        }
    }
}
