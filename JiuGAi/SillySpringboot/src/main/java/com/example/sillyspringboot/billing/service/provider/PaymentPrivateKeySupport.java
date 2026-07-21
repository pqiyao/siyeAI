package com.example.sillyspringboot.billing.service.provider;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

final class PaymentPrivateKeySupport {

    private PaymentPrivateKeySupport() {
    }

    static PrivateKey loadPrivateKey(String inlinePem, String filePath) {
        String pem = readPem(inlinePem, filePath);
        if (pem.isBlank()) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "支付私钥未配置");
        }
        try {
            String sanitized = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(sanitized);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "支付私钥解析失败");
        }
    }

    static String sign(String content, PrivateKey privateKey, String algorithm) {
        try {
            Signature signature = Signature.getInstance(algorithm);
            signature.initSign(privateKey);
            signature.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "支付签名失败");
        }
    }

    private static String readPem(String inlinePem, String filePath) {
        if (inlinePem != null && !inlinePem.isBlank()) {
            return inlinePem.trim();
        }
        if (filePath == null || filePath.isBlank()) {
            return "";
        }
        try {
            return Files.readString(Path.of(filePath.trim()), StandardCharsets.UTF_8).trim();
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.UPSTREAM_ERROR, "支付私钥文件读取失败");
        }
    }
}
