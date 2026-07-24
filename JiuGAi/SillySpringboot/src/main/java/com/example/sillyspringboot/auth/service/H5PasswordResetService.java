package com.example.sillyspringboot.auth.service;

import com.example.sillyspringboot.auth.config.AppAuthProperties;
import com.example.sillyspringboot.auth.config.PasswordResetProperties;
import com.example.sillyspringboot.auth.dto.H5PasswordResetRequest;
import com.example.sillyspringboot.auth.dto.H5PasswordResetRequestResponse;
import com.example.sillyspringboot.auth.entity.AppPasswordResetToken;
import com.example.sillyspringboot.auth.entity.AppPasswordResetThrottle;
import com.example.sillyspringboot.auth.entity.AppUserIdentity;
import com.example.sillyspringboot.auth.mapper.AppPasswordResetTokenMapper;
import com.example.sillyspringboot.auth.mapper.AppUserIdentityMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class H5PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(H5PasswordResetService.class);
    private static final String IDENTITY_H5_ACCOUNT = "h5_account";
    private static final String INVALID_CODE_MESSAGE = "验证码无效或已过期";

    private final PasswordResetProperties properties;
    private final AppAuthProperties authProperties;
    private final AppUserIdentityMapper identityMapper;
    private final AppPasswordResetTokenMapper resetTokenMapper;
    private final H5PasswordResetConfirmationService confirmationService;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final Executor mailExecutor;
    private final SecureRandom secureRandom = new SecureRandom();

    public H5PasswordResetService(
            PasswordResetProperties properties,
            AppAuthProperties authProperties,
            AppUserIdentityMapper identityMapper,
            AppPasswordResetTokenMapper resetTokenMapper,
            H5PasswordResetConfirmationService confirmationService,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Qualifier("passwordResetMailExecutor") Executor mailExecutor
    ) {
        this.properties = properties;
        this.authProperties = authProperties;
        this.identityMapper = identityMapper;
        this.resetTokenMapper = resetTokenMapper;
        this.confirmationService = confirmationService;
        this.mailSenderProvider = mailSenderProvider;
        this.mailExecutor = mailExecutor;
    }

    @Transactional
    public H5PasswordResetRequestResponse requestReset(H5PasswordResetRequest request) {
        String accountKey = normalizeEmail(request.getEmail());
        long ttlSeconds = clamp(properties.getTokenTtlSeconds(), 300, 1800);
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (!properties.isEnabled() || mailSender == null || properties.getFrom() == null || properties.getFrom().isBlank()) {
            return new H5PasswordResetRequestResponse("", false, 0, 0);
        }

        long cooldownSeconds = clamp(properties.getRequestCooldownSeconds(), 30, 300);
        String accountHash = hashAccountKey(accountKey);
        LocalDateTime now = LocalDateTime.now();
        String requestId = UUID.randomUUID().toString();
        resetTokenMapper.deleteThrottleBefore(now.minusDays(1));
        boolean throttleCreated = resetTokenMapper.insertThrottleIfAbsent(accountHash, requestId) > 0;
        AppPasswordResetThrottle throttle = resetTokenMapper.findThrottleForUpdate(accountHash);
        if (throttle == null) {
            throw new IllegalStateException("password reset throttle row missing");
        }
        if (!throttleCreated && throttle.getRequestedAt() != null) {
            long remainingSeconds = java.time.Duration.between(now, throttle.getRequestedAt().plusSeconds(cooldownSeconds)).getSeconds();
            if (remainingSeconds > 0) {
                return new H5PasswordResetRequestResponse(throttle.getRequestId(), true, ttlSeconds, remainingSeconds);
            }
        }
        if (!throttleCreated) {
            resetTokenMapper.updateThrottle(accountHash, requestId);
        }

        AppUserIdentity identity = identityMapper.findByTypeAndKey(IDENTITY_H5_ACCOUNT, accountKey);
        boolean deliverable = identity != null && identity.getUserId() != null && Boolean.TRUE.equals(identity.getVerified());
        String code = String.format(Locale.ROOT, "%08d", 10_000_000 + secureRandom.nextInt(90_000_000));
        AppPasswordResetToken token = new AppPasswordResetToken();
        token.setRequestId(requestId);
        token.setUserId(deliverable ? identity.getUserId() : null);
        token.setAccountKey(accountHash);
        token.setCodeHash(hashCode(requestId, code));
        token.setExpiresAt(now.plusSeconds(ttlSeconds));

        resetTokenMapper.deleteExpiredBefore(now.minusDays(1));
        if (deliverable) {
            resetTokenMapper.invalidateActiveByUserId(identity.getUserId());
        }
        resetTokenMapper.insert(token);

        if (deliverable) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        mailExecutor.execute(() -> {
                            try {
                                sendResetMail(mailSender, accountKey, code, ttlSeconds);
                            } catch (RuntimeException ex) {
                                log.error("password reset email delivery failed requestId={}", requestId, ex);
                            }
                        });
                    } catch (RuntimeException ex) {
                        log.error("password reset email task rejected requestId={}", requestId, ex);
                    }
                }
            });
        }
        return new H5PasswordResetRequestResponse(requestId, true, ttlSeconds, cooldownSeconds);
    }

    public void confirmReset(com.example.sillyspringboot.auth.dto.H5PasswordResetConfirmRequest request) {
        if (!confirmationService.confirm(request)) {
            throw invalidCode();
        }
    }

    private void sendResetMail(JavaMailSender mailSender, String email, String code, long ttlSeconds) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getFrom().trim());
        message.setTo(email);
        message.setSubject("四叶酒馆密码重置 / Password reset");
        message.setText(
                "你的密码重置验证码是：" + code + "\n"
                        + "验证码将在 " + Math.max(1, ttlSeconds / 60) + " 分钟后失效，请勿转发。\n\n"
                        + "Your password reset code is: " + code + "\n"
                        + "This code expires soon. Do not share it with anyone."
        );
        mailSender.send(message);
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
        return sha256(authProperties.getSecret() + ":password-reset-account:" + accountKey);
    }

    private static String sha256(String source) {
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

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static BusinessException invalidCode() {
        return new BusinessException(ErrorCode.VALIDATION_FAILED, INVALID_CODE_MESSAGE);
    }
}
