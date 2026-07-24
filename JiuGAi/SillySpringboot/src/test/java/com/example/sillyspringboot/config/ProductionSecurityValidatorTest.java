package com.example.sillyspringboot.config;

import com.example.sillyspringboot.admin.config.RuoYiAdminProperties;
import com.example.sillyspringboot.auth.config.AppAuthProperties;
import com.example.sillyspringboot.auth.config.PasswordResetProperties;
import com.example.sillyspringboot.billing.config.EpayPaymentProperties;
import com.example.sillyspringboot.billing.config.MockPaymentProperties;
import com.example.sillyspringboot.billing.config.PaymentSecretsProperties;
import com.example.sillyspringboot.billing.config.TelegramStarsPaymentProperties;
import com.example.sillyspringboot.chat.config.AppChatProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityValidatorTest {

    @Test
    void productionAcceptsIndependentStrongSecretsAndDisabledMockPayment() {
        SecurityFixture fixture = productionFixture();

        assertThatCode(fixture.validator()::validate).doesNotThrowAnyException();
    }

    @Test
    void enabledFrontendBridgeAlwaysRequiresStrongToken() {
        SecurityFixture fixture = productionFixture();
        fixture.appProperties().setEnvironment("dev");
        fixture.chatProperties().getCompatibility().setFrontendBridgeEnabled(true);
        fixture.chatProperties().getCompatibility().setFrontendBridgeToken("short-token");

        assertThatThrownBy(fixture.validator()::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frontend-bridge-token");
    }

    @Test
    void developmentRejectsShortAdminJwtSecretAtStartup() {
        SecurityFixture fixture = productionFixture();
        fixture.appProperties().setEnvironment("dev");
        fixture.adminProperties().setJwtSecret("short");

        assertThatThrownBy(fixture.validator()::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.ruoyi-admin.jwt-secret")
                .hasMessageContaining("at least 32 UTF-8 bytes");
    }

    @Test
    void productionRejectsMockPaymentEvenWhenLegacyOverrideIsSet() {
        SecurityFixture fixture = productionFixture();
        fixture.mockPaymentProperties().setAllowInProd(true);

        assertThatThrownBy(fixture.validator()::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Mock payment must remain disabled");
    }

    @Test
    void productionRejectsWeakAdminDefaults() {
        SecurityFixture fixture = productionFixture();
        fixture.adminProperties().setEncodedPassword("{noop}admin123");
        fixture.adminProperties().setJwtExpireHours(720);
        fixture.adminProperties().setCaptchaEnabled(false);

        assertThatThrownBy(fixture.validator()::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_RUOYI_ADMIN_PASSWORD")
                .hasMessageContaining("APP_RUOYI_JWT_EXPIRE_HOURS")
                .hasMessageContaining("APP_RUOYI_CAPTCHA_ENABLED");
    }

    @Test
    void enabledPasswordResetRequiresCompleteSmtpConfiguration() {
        SecurityFixture fixture = productionFixture();
        fixture.passwordResetProperties().setEnabled(true);
        fixture.passwordResetProperties().setFrom("noreply@example.test");

        assertThatThrownBy(fixture.validator()::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_MAIL_HOST")
                .hasMessageContaining("SPRING_MAIL_USERNAME");
    }

    @Test
    void enabledPasswordResetAcceptsCompleteAuthenticatedSmtpConfiguration() {
        SecurityFixture fixture = productionFixture();
        fixture.passwordResetProperties().setEnabled(true);
        fixture.passwordResetProperties().setFrom("noreply@example.test");
        fixture.environment()
                .withProperty("spring.mail.host", "smtp.example.test")
                .withProperty("spring.mail.port", "587")
                .withProperty("spring.mail.username", "mailer")
                .withProperty("spring.mail.password", "smtp-secret")
                .withProperty("spring.mail.properties.mail.smtp.auth", "true");

        assertThatCode(fixture.validator()::validate).doesNotThrowAnyException();
    }

    @Test
    void productionRejectsEnabledTelegramStarsWithoutWebhookSecret() {
        SecurityFixture fixture = productionFixture();
        fixture.telegramStarsPaymentProperties().setEnabled(true);
        fixture.telegramStarsPaymentProperties().setWebhookSecret("");

        assertThatThrownBy(fixture.validator()::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TELEGRAM_STARS_WEBHOOK_SECRET");
    }

    @Test
    void productionRejectsEnabledEpayWithoutPidKey() {
        SecurityFixture fixture = productionFixture();
        fixture.epayPaymentProperties().setEnabled(true);

        assertThatThrownBy(fixture.validator()::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EPAY_PID");
    }

    @Test
    void productionRejectsMissingPaymentSecretsMasterKey() {
        SecurityFixture fixture = productionFixture();
        fixture.paymentSecretsProperties().setSecretsMasterKey("");

        assertThatThrownBy(fixture.validator()::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_PAYMENT_SECRETS_MASTER_KEY");
    }

    private static SecurityFixture productionFixture() {
        AppProperties app = new AppProperties();
        app.setEnvironment("production");

        AppAuthProperties auth = new AppAuthProperties();
        auth.setSecret("A1!app-9fb431c8a2d74ef1850be477b640d203");
        auth.setTokenTtlSeconds(604_800);

        RuoYiAdminProperties admin = new RuoYiAdminProperties();
        admin.setUsername("admin");
        admin.setEncodedPassword("Strong!AdminKey42");
        admin.setJwtSecret("B2@jwt-3913b7d6d25c48d481e6f4a5450e0f70");
        admin.setJwtExpireHours(8);
        admin.setCaptchaEnabled(true);

        AppChatProperties chat = new AppChatProperties();
        MockPaymentProperties mockPayment = new MockPaymentProperties();
        mockPayment.setEnabled(false);
        mockPayment.setAllowInProd(false);
        TelegramStarsPaymentProperties telegramStars = new TelegramStarsPaymentProperties();
        EpayPaymentProperties epay = new EpayPaymentProperties();
        PaymentSecretsProperties paymentSecrets = new PaymentSecretsProperties();
        paymentSecrets.setSecretsMasterKey("C3#pay-7e2a91f04b6d48c3a9f1e6d2058b3c71");
        PasswordResetProperties passwordReset = new PasswordResetProperties();
        MockEnvironment environment = new MockEnvironment();

        ProductionSecurityValidator validator = new ProductionSecurityValidator(
                app,
                auth,
                admin,
                chat,
                mockPayment,
                telegramStars,
                epay,
                paymentSecrets,
                passwordReset,
                environment
        );
        return new SecurityFixture(
                app,
                admin,
                chat,
                mockPayment,
                telegramStars,
                epay,
                paymentSecrets,
                passwordReset,
                environment,
                validator
        );
    }

    private record SecurityFixture(
            AppProperties appProperties,
            RuoYiAdminProperties adminProperties,
            AppChatProperties chatProperties,
            MockPaymentProperties mockPaymentProperties,
            TelegramStarsPaymentProperties telegramStarsPaymentProperties,
            EpayPaymentProperties epayPaymentProperties,
            PaymentSecretsProperties paymentSecretsProperties,
            PasswordResetProperties passwordResetProperties,
            MockEnvironment environment,
            ProductionSecurityValidator validator
    ) {
    }
}
