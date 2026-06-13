package com.example.sillyspringboot.shared.crypto;

import com.example.sillyspringboot.auth.config.AppAuthProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class SensitiveTextCrypto {

    private static final String PREFIX = "v1:";
    private static final int GCM_TAG_BITS = 128;
    private static final int NONCE_BYTES = 12;

    private final SecretKeySpec secretKeySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public SensitiveTextCrypto(AppAuthProperties appAuthProperties) {
        this.secretKeySpec = new SecretKeySpec(deriveKey(appAuthProperties.getSecret()), "AES");
    }

    public String encrypt(String plainText) {
        String text = plainText == null ? "" : plainText.trim();
        if (text.isBlank()) {
            return "";
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, packed, 0, nonce.length);
            System.arraycopy(encrypted, 0, packed, nonce.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (Exception ex) {
            throw new IllegalStateException("cannot encrypt sensitive text", ex);
        }
    }

    public String decrypt(String cipherText) {
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
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8).trim();
        } catch (Exception ex) {
            throw new IllegalStateException("cannot decrypt sensitive text", ex);
        }
    }

    private static byte[] deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(("h5-user-byok|" + (secret == null ? "" : secret.trim())).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("cannot derive crypto key", ex);
        }
    }
}
