package com.example.sillyspringboot.billing.service.provider;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class PaymentPublicKeySupport {

    private PaymentPublicKeySupport() {}

    public static PublicKey loadPublicKey(String pem, String path) {
        try {
            String value = pem == null ? "" : pem.trim();
            if (value.isBlank() && path != null && !path.isBlank()) {
                value = Files.readString(Path.of(path.trim()), StandardCharsets.UTF_8).trim();
            }
            if (value.contains("BEGIN CERTIFICATE")) {
                byte[] der = decodePem(value, "CERTIFICATE");
                return CertificateFactory.getInstance("X.509")
                        .generateCertificate(new java.io.ByteArrayInputStream(der)).getPublicKey();
            }
            byte[] der = value.contains("BEGIN PUBLIC KEY")
                    ? decodePem(value, "PUBLIC KEY")
                    : Base64.getDecoder().decode(value.replaceAll("\\s+", ""));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new IllegalStateException("cannot load payment platform public key", ex);
        }
    }

    public static boolean verify(String content, String signature, PublicKey publicKey) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifier.update(content.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (Exception ex) {
            return false;
        }
    }

    private static byte[] decodePem(String value, String type) {
        String body = value
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(body);
    }
}
