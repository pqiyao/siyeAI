package com.example.sillyspringboot;

import com.example.sillyspringboot.config.AppProperties;
import com.example.sillyspringboot.config.ApiRateLimitProperties;
import com.example.sillyspringboot.config.ExternalCleanupProperties;
import com.example.sillyspringboot.config.H5SecurityEventProperties;
import com.example.sillyspringboot.config.GenerationRetentionProperties;
import com.example.sillyspringboot.config.LegacyFlywayProperties;
import com.example.sillyspringboot.auth.config.AppAuthProperties;
import com.example.sillyspringboot.auth.config.TelegramProperties;
import com.example.sillyspringboot.billing.config.AlipayWapPaymentProperties;
import com.example.sillyspringboot.billing.config.EpayPaymentProperties;
import com.example.sillyspringboot.billing.config.MockPaymentProperties;
import com.example.sillyspringboot.billing.config.PaymentSecretsProperties;
import com.example.sillyspringboot.billing.config.TelegramStarsPaymentProperties;
import com.example.sillyspringboot.billing.config.WechatH5PaymentProperties;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.admin.config.RuoYiAdminProperties;
import com.example.sillyspringboot.ops.config.AppImageGenerationProperties;
import com.example.sillyspringboot.ai.config.AiRoutingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
    AppProperties.class,
    ApiRateLimitProperties.class,
    ExternalCleanupProperties.class,
    H5SecurityEventProperties.class,
    GenerationRetentionProperties.class,
    LegacyFlywayProperties.class,
    AppAuthProperties.class,
    TelegramProperties.class,
    TelegramStarsPaymentProperties.class,
    MockPaymentProperties.class,
    WechatH5PaymentProperties.class,
    AlipayWapPaymentProperties.class,
    EpayPaymentProperties.class,
    PaymentSecretsProperties.class,
    MemoryLlmProperties.class,
    RuoYiAdminProperties.class,
    AppImageGenerationProperties.class,
    AiRoutingProperties.class
})
public class SillySpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.run(SillySpringbootApplication.class, args);
    }

}
