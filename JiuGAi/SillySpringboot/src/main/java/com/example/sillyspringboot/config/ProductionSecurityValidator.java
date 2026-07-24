package com.example.sillyspringboot.config;

import com.example.sillyspringboot.admin.config.RuoYiAdminProperties;
import com.example.sillyspringboot.auth.config.AppAuthProperties;
import com.example.sillyspringboot.auth.config.PasswordResetProperties;
import com.example.sillyspringboot.billing.config.EpayPaymentProperties;
import com.example.sillyspringboot.billing.config.MockPaymentProperties;
import com.example.sillyspringboot.billing.config.PaymentSecretsProperties;
import com.example.sillyspringboot.billing.config.TelegramStarsPaymentProperties;
import com.example.sillyspringboot.chat.config.AppChatProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ProductionSecurityValidator implements InitializingBean {

    private static final int MIN_SECRET_BYTES = 32;

    private final AppProperties appProperties;
    private final AppAuthProperties authProperties;
    private final RuoYiAdminProperties adminProperties;
    private final AppChatProperties chatProperties;
    private final MockPaymentProperties mockPaymentProperties;
    private final TelegramStarsPaymentProperties telegramStarsPaymentProperties;
    private final EpayPaymentProperties epayPaymentProperties;
    private final PaymentSecretsProperties paymentSecretsProperties;
    private final PasswordResetProperties passwordResetProperties;
    private final Environment environment;

    public ProductionSecurityValidator(
            AppProperties appProperties,
            AppAuthProperties authProperties,
            RuoYiAdminProperties adminProperties,
            AppChatProperties chatProperties,
            MockPaymentProperties mockPaymentProperties,
            TelegramStarsPaymentProperties telegramStarsPaymentProperties,
            EpayPaymentProperties epayPaymentProperties,
            PaymentSecretsProperties paymentSecretsProperties,
            PasswordResetProperties passwordResetProperties,
            Environment environment
    ) {
        this.appProperties = appProperties;
        this.authProperties = authProperties;
        this.adminProperties = adminProperties;
        this.chatProperties = chatProperties;
        this.mockPaymentProperties = mockPaymentProperties;
        this.telegramStarsPaymentProperties = telegramStarsPaymentProperties;
        this.epayPaymentProperties = epayPaymentProperties;
        this.paymentSecretsProperties = paymentSecretsProperties;
        this.passwordResetProperties = passwordResetProperties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    public void validate() {
        List<String> violations = new ArrayList<>();
        validatePasswordResetConfiguration(violations);
        AppChatProperties.Compatibility compatibility = chatProperties.getCompatibility();
        if (compatibility.isFrontendBridgeEnabled()) {
            validateSecret("app.chat.compatibility.frontend-bridge-token", compatibility.getFrontendBridgeToken(), violations);
        }
        validateMinimumSecretLength("app.ruoyi-admin.jwt-secret", adminProperties.getJwtSecret(), violations);

        if (!isProduction(appProperties.getEnvironment())) {
            failIfAny(violations);
            return;
        }

        validateSecret("app.auth.secret", authProperties.getSecret(), violations);
        validateSecret("app.ruoyi-admin.jwt-secret", adminProperties.getJwtSecret(), violations);
        if (safe(authProperties.getSecret()).equals(safe(adminProperties.getJwtSecret()))) {
            violations.add("APP_AUTH_SECRET and APP_RUOYI_JWT_SECRET must be different");
        }
        validateAdminPassword(adminProperties.getEncodedPassword(), violations);

        if (safe(adminProperties.getUsername()).isBlank()) {
            violations.add("APP_RUOYI_ADMIN_USERNAME must be configured");
        }
        if (adminProperties.getJwtExpireHours() < 1 || adminProperties.getJwtExpireHours() > 24) {
            violations.add("APP_RUOYI_JWT_EXPIRE_HOURS must be between 1 and 24 in production");
        }
        if (!adminProperties.isCaptchaEnabled()) {
            violations.add("APP_RUOYI_CAPTCHA_ENABLED must be true in production");
        }
        if (adminProperties.getLoginMaxAttempts() < 1 || adminProperties.getLoginMaxAttempts() > 10) {
            violations.add("APP_RUOYI_LOGIN_MAX_ATTEMPTS must be between 1 and 10 in production");
        }
        if (adminProperties.getCaptchaTtlSeconds() < 30 || adminProperties.getCaptchaTtlSeconds() > 600) {
            violations.add("APP_RUOYI_CAPTCHA_TTL_SECONDS must be between 30 and 600 in production");
        }
        if (adminProperties.getLoginWindowSeconds() < 30 || adminProperties.getLoginWindowSeconds() > 3600) {
            violations.add("APP_RUOYI_LOGIN_WINDOW_SECONDS must be between 30 and 3600 in production");
        }
        if (adminProperties.getLoginBlockSeconds() < 60 || adminProperties.getLoginBlockSeconds() > 86_400) {
            violations.add("APP_RUOYI_LOGIN_BLOCK_SECONDS must be between 60 and 86400 in production");
        }
        if (authProperties.getTokenTtlSeconds() < 300 || authProperties.getTokenTtlSeconds() > 604_800) {
            violations.add("APP_TOKEN_TTL_SECONDS must be between 300 and 604800 in production");
        }
        if (mockPaymentProperties.isEnabled() || mockPaymentProperties.isAllowInProd()) {
            violations.add("Mock payment must remain disabled in production");
        }
        if (telegramStarsPaymentProperties.isEnabled()
                && safe(telegramStarsPaymentProperties.getWebhookSecret()).isBlank()) {
            violations.add("APP_PAYMENT_TELEGRAM_STARS_WEBHOOK_SECRET is required when Telegram Stars payment is enabled");
        }
        if (epayPaymentProperties.isEnabled()) {
            if (safe(epayPaymentProperties.getPid()).isBlank() || safe(epayPaymentProperties.getKey()).isBlank()) {
                violations.add("APP_PAYMENT_EPAY_PID and APP_PAYMENT_EPAY_KEY are required when epay is enabled");
            }
        }
        validateSecret(
                "app.payment.secrets-master-key (APP_PAYMENT_SECRETS_MASTER_KEY)",
                paymentSecretsProperties.getSecretsMasterKey(),
                violations
        );
        if (compatibility.isFrontendBridgeEnabled()
                && safe(compatibility.getFrontendBridgeToken()).equals(safe(adminProperties.getJwtSecret()))) {
            violations.add("The frontend bridge token and admin JWT secret must be different");
        }

        failIfAny(violations);
    }

    private void validatePasswordResetConfiguration(List<String> violations) {
        if (!passwordResetProperties.isEnabled()) {
            return;
        }
        if (safe(passwordResetProperties.getFrom()).isBlank()) {
            violations.add("APP_AUTH_PASSWORD_RESET_FROM is required when password reset is enabled");
        }
        if (safe(environment.getProperty("spring.mail.host")).isBlank()) {
            violations.add("SPRING_MAIL_HOST is required when password reset is enabled");
        }
        Integer port = environment.getProperty("spring.mail.port", Integer.class, 587);
        if (port == null || port < 1 || port > 65_535) {
            violations.add("SPRING_MAIL_PORT must be between 1 and 65535");
        }
        boolean smtpAuth = environment.getProperty("spring.mail.properties.mail.smtp.auth", Boolean.class, true);
        if (smtpAuth && (safe(environment.getProperty("spring.mail.username")).isBlank()
                || safe(environment.getProperty("spring.mail.password")).isBlank())) {
            violations.add("SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD are required when SMTP authentication is enabled");
        }
    }

    private static void validateSecret(String name, String value, List<String> violations) {
        String secret = safe(value);
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES
                || looksLikePlaceholder(secret)
                || secret.chars().distinct().count() < 8) {
            violations.add(name + " must be a non-placeholder secret of at least 32 UTF-8 bytes");
        }
    }

    private static void validateMinimumSecretLength(String name, String value, List<String> violations) {
        if (safe(value).getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            violations.add(name + " must contain at least 32 UTF-8 bytes");
        }
    }

    private static void validateAdminPassword(String value, List<String> violations) {
        String password = safe(value);
        if (password.startsWith("{")) {
            boolean strongHash = password.startsWith("{bcrypt}")
                    || password.startsWith("{argon2}")
                    || password.startsWith("{scrypt}")
                    || password.startsWith("{pbkdf2}");
            if (!strongHash || password.length() < 40 || looksLikePlaceholder(password)) {
                violations.add("APP_RUOYI_ADMIN_PASSWORD must use a supported strong password hash");
            }
            return;
        }

        int characterGroups = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) characterGroups++;
        if (password.chars().anyMatch(Character::isUpperCase)) characterGroups++;
        if (password.chars().anyMatch(Character::isDigit)) characterGroups++;
        if (password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) characterGroups++;
        if (password.length() < 12 || characterGroups < 3 || looksLikePlaceholder(password)) {
            violations.add("APP_RUOYI_ADMIN_PASSWORD must contain at least 12 characters from at least 3 character groups");
        }
    }

    private static boolean looksLikePlaceholder(String value) {
        String lower = safe(value).toLowerCase(Locale.ROOT);
        return lower.isBlank()
                || lower.contains("change-this")
                || lower.contains("replace_with")
                || lower.contains("replace-with")
                || lower.contains("example")
                || lower.contains("dev-change")
                || lower.contains("admin123")
                || lower.contains("password")
                || lower.contains("请改")
                || lower.contains("自己的");
    }

    private static boolean isProduction(String environment) {
        String normalized = safe(environment).toLowerCase(Locale.ROOT);
        return "prod".equals(normalized) || "production".equals(normalized);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static void failIfAny(List<String> violations) {
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Unsafe application configuration: " + String.join("; ", violations));
        }
    }
}
