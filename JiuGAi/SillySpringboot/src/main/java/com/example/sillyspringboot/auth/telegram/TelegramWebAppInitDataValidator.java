package com.example.sillyspringboot.auth.telegram;

import com.example.sillyspringboot.auth.config.TelegramProperties;
import com.example.sillyspringboot.auth.dto.TelegramAuthPayload;
import com.example.sillyspringboot.auth.dto.TelegramInitDataUser;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TelegramWebAppInitDataValidator {

    private final TelegramProperties telegramProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TelegramWebAppInitDataValidator(TelegramProperties telegramProperties) {
        this.telegramProperties = telegramProperties;
    }

    /**
     * Telegram WebApp initData 校验并解析。
     *
     * @param initData querystring（包含 hash）
     */
    public TelegramAuthPayload validate(String initData) {
        if (initData == null || initData.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录参数缺失");
        }
        if (telegramProperties.getBotToken() == null || telegramProperties.getBotToken().isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未配置 Telegram botToken");
        }

        Map<String, String> map = parseInitData(initData);
        String receivedHash = map.remove("hash");
        if (receivedHash == null || receivedHash.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "initData 校验失败");
        }
        if (!map.containsKey("auth_date")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "initData 校验失败");
        }

        long authDate = parseLong(map.get("auth_date"), 0);
        long now = Instant.now().getEpochSecond();
        long maxAge = telegramProperties.getAuthMaxAgeSeconds();
        if (authDate <= 0 || now - authDate > maxAge) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "initData 已过期");
        }

        String dataCheckString = map.keySet().stream()
                .sorted()
                .map(k -> k + "=" + map.get(k))
                .collect(Collectors.joining("\n"));

        // Telegram WebAppData 校验规则（按文档口径）：
        // secret_key = HMAC_SHA256(key="WebAppData", data=botToken)
        byte[] secretKey = hmacSha256(
                "WebAppData".getBytes(StandardCharsets.UTF_8),
                telegramProperties.getBotToken().getBytes(StandardCharsets.UTF_8)
        );
        byte[] digest = hmacSha256(secretKey, dataCheckString.getBytes(StandardCharsets.UTF_8));
        String computedHash = toHexLower(digest);

        if (!computedHash.equalsIgnoreCase(receivedHash.trim())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "initData 校验失败");
        }

        String queryId = map.getOrDefault("query_id", "");
        String userJson = map.get("user");
        if (userJson == null || userJson.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "initData 缺少 user");
        }

        try {
            TelegramInitDataUser user = objectMapper.readValue(userJson, TelegramInitDataUser.class);
            return new TelegramAuthPayload(queryId, authDate, user);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "initData user 解析失败", e);
        }
    }

    private Map<String, String> parseInitData(String initData) {
        Map<String, String> map = new HashMap<>();
        String[] pairs = initData.split("&");
        for (String pair : pairs) {
            if (pair.isBlank()) continue;
            int idx = pair.indexOf('=');
            if (idx <= 0) continue;
            String keyRaw = pair.substring(0, idx);
            String valueRaw = pair.substring(idx + 1);
            String key = URLDecoder.decode(keyRaw, StandardCharsets.UTF_8);
            String value = URLDecoder.decode(valueRaw, StandardCharsets.UTF_8);
            map.put(key, value);
        }
        return map;
    }

    private static long parseLong(String v, long def) {
        try {
            return Long.parseLong(v);
        } catch (Exception e) {
            return def;
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    private static String toHexLower(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}

