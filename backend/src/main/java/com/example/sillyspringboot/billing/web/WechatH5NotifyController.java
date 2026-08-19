package com.example.sillyspringboot.billing.web;

import com.example.sillyspringboot.billing.config.WechatH5PaymentProperties;
import com.example.sillyspringboot.billing.service.StoreService;
import com.example.sillyspringboot.billing.service.provider.PaymentPublicKeySupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/payment/wechat-h5")
public class WechatH5NotifyController {
    private static final Logger log = LoggerFactory.getLogger(WechatH5NotifyController.class);
    private static final long MAX_CLOCK_SKEW_SECONDS = 300L;

    private final WechatH5PaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final StoreService storeService;

    public WechatH5NotifyController(WechatH5PaymentProperties properties,
                                    ObjectMapper objectMapper,
                                    StoreService storeService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.storeService = storeService;
    }

    @PostMapping("/notify")
    public ResponseEntity<Map<String, String>> notify(
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestBody String body) {
        try {
            long requestTime = Long.parseLong(timestamp);
            if (Math.abs(Instant.now().getEpochSecond() - requestTime) > MAX_CLOCK_SKEW_SECONDS) {
                return failure(HttpStatus.UNAUTHORIZED, "timestamp expired");
            }
            PublicKey publicKey = PaymentPublicKeySupport.loadPublicKey(
                    properties.getPlatformPublicKeyPem(), properties.getPlatformPublicKeyPath());
            String signed = timestamp + "\n" + nonce + "\n" + body + "\n";
            if (!PaymentPublicKeySupport.verify(signed, signature, publicKey)) {
                return failure(HttpStatus.UNAUTHORIZED, "signature invalid");
            }
            JsonNode root = objectMapper.readTree(body);
            JsonNode resource = root.path("resource");
            String plain = decryptResource(
                    resource.path("ciphertext").asText(""),
                    resource.path("nonce").asText(""),
                    resource.path("associated_data").asText(""));
            JsonNode transaction = objectMapper.readTree(plain);
            if (!"SUCCESS".equalsIgnoreCase(transaction.path("trade_state").asText(""))) {
                return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "ignored"));
            }
            if (!secureEquals(properties.getMerchantId(), transaction.path("mchid").asText(""))
                    || !secureEquals(properties.getAppId(), transaction.path("appid").asText(""))) {
                return failure(HttpStatus.BAD_REQUEST, "merchant mismatch");
            }
            String orderNo = transaction.path("out_trade_no").asText("").trim();
            String tradeNo = transaction.path("transaction_id").asText("").trim();
            int paidCents = transaction.path("amount").path("total").asInt(-1);
            if (orderNo.isBlank() || tradeNo.isBlank() || paidCents < 0) {
                return failure(HttpStatus.BAD_REQUEST, "transaction fields missing");
            }
            storeService.confirmProviderPaid(orderNo, "wechat_h5", paidCents, tradeNo, sha256(body));
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        } catch (Exception ex) {
            log.warn("wechat notify rejected: {}", ex.getMessage());
            return failure(HttpStatus.BAD_REQUEST, "notify rejected");
        }
    }

    private String decryptResource(String ciphertext, String nonce, String associatedData) throws Exception {
        byte[] key = properties.getApiV3Key().getBytes(StandardCharsets.UTF_8);
        if (key.length != 32) throw new IllegalStateException("invalid api v3 key");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        if (associatedData != null && !associatedData.isEmpty()) {
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        }
        return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
    }

    private static ResponseEntity<Map<String, String>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("code", "FAIL", "message", message));
    }

    private static boolean secureEquals(String expected, String actual) {
        return expected != null && actual != null && MessageDigest.isEqual(
                expected.trim().getBytes(StandardCharsets.UTF_8), actual.trim().getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) result.append(String.format(Locale.ROOT, "%02x", b));
            return result.toString();
        } catch (Exception ex) { return ""; }
    }
}
