package com.example.sillyspringboot.billing.service;

import com.example.sillyspringboot.billing.config.PaymentSecretsProperties;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PaymentChannelSecretService {

    private static final String PREFIX = "v1:";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;

    private final PaymentSecretsProperties secretsProperties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public PaymentChannelSecretService(PaymentSecretsProperties secretsProperties, ObjectMapper objectMapper) {
        this.secretsProperties = secretsProperties;
        this.objectMapper = objectMapper;
    }

    public String encryptConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return "";
        }
        try {
            return encrypt(objectMapper.writeValueAsString(config));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("cannot serialize payment channel secrets", ex);
        }
    }

    public Map<String, Object> decryptConfig(String cipherText) {
        String plain = decrypt(cipherText);
        if (plain == null || plain.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(plain, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("cannot parse payment channel secrets", ex);
        }
    }

    public Map<String, Object> maskSecrets(Map<String, Object> secrets) {
        Map<String, Object> masked = new LinkedHashMap<>();
        if (secrets == null) {
            return masked;
        }
        for (Map.Entry<String, Object> entry : secrets.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null) {
                continue;
            }
            if ("key".equalsIgnoreCase(key) || key.toLowerCase().contains("secret") || key.toLowerCase().contains("private")) {
                masked.put(key, maskValue(String.valueOf(value == null ? "" : value)));
            } else {
                masked.put(key, value == null ? "" : value);
            }
        }
        return masked;
    }

    private String encrypt(String plainText) {
        String text = plainText == null ? "" : plainText.trim();
        if (text.isBlank()) {
            return "";
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, packed, 0, nonce.length);
            System.arraycopy(encrypted, 0, packed, nonce.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("cannot encrypt payment secrets", ex);
        }
    }

    private String decrypt(String cipherText) {
        String text = cipherText == null ? "" : cipherText.trim();
        if (text.isBlank()) {
            return "";
        }
        try {
            String raw = text.startsWith(PREFIX) ? text.substring(PREFIX.length()) : text;
            byte[] packed = Base64.getDecoder().decode(raw);
            if (packed.length <= NONCE_BYTES) {
                return "";
            }
            byte[] nonce = Arrays.copyOfRange(packed, 0, NONCE_BYTES);
            byte[] encrypted = Arrays.copyOfRange(packed, NONCE_BYTES, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8).trim();
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("cannot decrypt payment secrets", ex);
        }
    }

    private SecretKeySpec secretKey() {
        String master = secretsProperties.getSecretsMasterKey();
        if (master == null || master.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "app.payment.secrets-master-key is required to encrypt payment channel secrets");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(("payment-channel|" + master.trim()).getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(key, "AES");
        } catch (Exception ex) {
            throw new IllegalStateException("cannot derive payment secrets key", ex);
        }
    }

    private static String maskValue(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            return "";
        }
        if (text.length() <= 4) {
            return "****";
        }
        return text.substring(0, 2) + "****" + text.substring(text.length() - 2);
    }
}
