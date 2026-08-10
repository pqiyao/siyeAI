package com.example.sillyspringboot.auth.service;

import com.example.sillyspringboot.auth.config.AppAuthProperties;
import com.example.sillyspringboot.auth.config.PasswordResetProperties;
import com.example.sillyspringboot.auth.dto.H5PasswordResetConfirmRequest;
import com.example.sillyspringboot.auth.entity.AppPasswordResetToken;
import com.example.sillyspringboot.auth.entity.AppUserIdentity;
import com.example.sillyspringboot.auth.mapper.AppPasswordResetTokenMapper;
import com.example.sillyspringboot.auth.mapper.AppUserIdentityMapper;
import com.example.sillyspringboot.auth.mapper.AppUserSessionMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class H5PasswordResetConfirmationService {

    private static final String IDENTITY_H5_ACCOUNT = "h5_account";

    private final PasswordResetProperties properties;
    private final AppAuthProperties authProperties;
    private final AppUserIdentityMapper identityMapper;
    private final AppPasswordResetTokenMapper resetTokenMapper;
    private final AppUserSessionMapper sessionMapper;
    private final PasswordEncoder passwordEncoder;

    public H5PasswordResetConfirmationService(
            PasswordResetProperties properties,
            AppAuthProperties authProperties,
            AppUserIdentityMapper identityMapper,
            AppPasswordResetTokenMapper resetTokenMapper,
            AppUserSessionMapper sessionMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.authProperties = authProperties;
        this.identityMapper = identityMapper;
        this.resetTokenMapper = resetTokenMapper;
        this.sessionMapper = sessionMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public boolean confirm(H5PasswordResetConfirmRequest request) {
        String requestId = request.getRequestId() == null ? "" : request.getRequestId().trim();
        String accountKey = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase(Locale.ROOT);
        String accountHash = hashAccountKey(accountKey);
        AppPasswordResetToken token = resetTokenMapper.findByRequestIdForUpdate(requestId);
        int maxAttempts = (int) Math.max(3, Math.min(10, properties.getMaxAttempts()));
        if (token == null
                || token.getConsumedAt() != null
                || token.getExpiresAt() == null
                || !token.getExpiresAt().isAfter(LocalDateTime.now())
                || token.getAttemptCount() == null
                || token.getAttemptCount() >= maxAttempts
                || !accountHash.equals(token.getAccountKey())) {
            return false;
        }

        String expectedHash = hashCode(requestId, request.getCode());
        if (!MessageDigest.isEqual(
                token.getCodeHash().getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII))) {
            resetTokenMapper.incrementAttemptCount(token.getId(), maxAttempts);
            return false;
        }

        AppUserIdentity identity = identityMapper.findByTypeAndKey(IDENTITY_H5_ACCOUNT, accountKey);
        if (token.getUserId() == null || identity == null || !token.getUserId().equals(identity.getUserId())) {
            resetTokenMapper.markConsumed(token.getId());
            return false;
        }

        identity.setCredentialHash(passwordEncoder.encode(request.getNewPassword()));
        identity.setVerified(Boolean.TRUE);
        identityMapper.updateById(identity);
        resetTokenMapper.markConsumed(token.getId());
        resetTokenMapper.invalidateActiveByUserId(token.getUserId());
        sessionMapper.revokeActiveByUserId(token.getUserId());
        return true;
    }

    private String hashCode(String requestId, String code) {
        String source = authProperties.getSecret() + ':' + requestId + ':' + String.valueOf(code);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >>> 4) & 0x0f, 16));
                hex.append(Character.forDigit(value & 0x0f, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String hashAccountKey(String accountKey) {
        String source = authProperties.getSecret() + ":password-reset-account:" + accountKey;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >>> 4) & 0x0f, 16));
                hex.append(Character.forDigit(value & 0x0f, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
