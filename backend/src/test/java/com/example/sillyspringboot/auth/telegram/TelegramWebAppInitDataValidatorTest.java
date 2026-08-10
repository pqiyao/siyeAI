package com.example.sillyspringboot.auth.telegram;

import com.example.sillyspringboot.auth.config.TelegramProperties;
import com.example.sillyspringboot.auth.dto.TelegramAuthPayload;
import com.example.sillyspringboot.auth.dto.TelegramInitDataUser;
import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

public class TelegramWebAppInitDataValidatorTest {

    @Test
    void validate_realInitDataFormat_shouldPassByTelegramRule() {
        // Arrange
        String botToken = "123456789:ABCDEF_test_bot_token";
        TelegramProperties props = new TelegramProperties();
        props.setBotToken(botToken);
        props.setAuthMaxAgeSeconds(86400);

        TelegramWebAppInitDataValidator validator = new TelegramWebAppInitDataValidator(props);

        long authDate = Instant.now().getEpochSecond() - 60; // keep valid for 24h
        String queryId = "AAE8Xn9W...example";
        String userJson = """
                {"id":123,"username":"alice","first_name":"Alice","last_name":"L","language_code":"en","photo_url":"https://example.com/a.jpg"}
                """.trim();

        // Telegram data_check_string = sorted key=value joined by '\n' (excluding hash)
        Map<String, String> data = new TreeMap<>();
        data.put("auth_date", String.valueOf(authDate));
        data.put("query_id", queryId);
        data.put("user", userJson);

        String dataCheckString = data.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        // Telegram WebApp secret key rule (per your spec):
        // secret_key = HMAC_SHA256(key="WebAppData", data=botToken)
        byte[] secretKey = hmacSha256(
                "WebAppData".getBytes(StandardCharsets.UTF_8),
                botToken.getBytes(StandardCharsets.UTF_8));

        String expectedHash = hmacSha256Hex(secretKey, dataCheckString);

        // Build initData querystring with URL encoding.
        String initData = "query_id=" + urlEnc(queryId)
                + "&user=" + urlEnc(userJson)
                + "&auth_date=" + urlEnc(String.valueOf(authDate))
                + "&hash=" + urlEnc(expectedHash);

        // Act
        TelegramAuthPayload payload = validator.validate(initData);

        // Assert
        assertThat(payload.queryId()).isEqualTo(queryId);
        assertThat(payload.authDateEpochSeconds()).isEqualTo(authDate);
        TelegramInitDataUser u = payload.user();
        assertThat(u.id()).isEqualTo(123L);
        assertThat(u.username()).isEqualTo("alice");
    }

    private static String urlEnc(String s) {
        // Telegram-style querystring expects %XX not '+'.
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hmacSha256Hex(byte[] secretKey, String dataCheckString) {
        byte[] digest = hmacSha256(secretKey, dataCheckString.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}

