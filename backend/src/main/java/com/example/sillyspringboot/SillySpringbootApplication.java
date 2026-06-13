package com.example.sillyspringboot;

import com.example.sillyspringboot.config.AppProperties;
import com.example.sillyspringboot.config.ApiRateLimitProperties;
import com.example.sillyspringboot.config.LegacyFlywayProperties;
import com.example.sillyspringboot.config.SocialUploadRateLimitProperties;
import com.example.sillyspringboot.config.WebSocketSecurityProperties;
import com.example.sillyspringboot.auth.config.AppAuthProperties;
import com.example.sillyspringboot.auth.config.TelegramProperties;
import com.example.sillyspringboot.billing.config.AlipayWapPaymentProperties;
import com.example.sillyspringboot.billing.config.MockPaymentProperties;
import com.example.sillyspringboot.billing.config.TelegramStarsPaymentProperties;
import com.example.sillyspringboot.billing.config.WechatH5PaymentProperties;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.admin.config.RuoYiAdminProperties;
import com.example.sillyspringboot.ops.config.AppImageGenerationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    AppProperties.class,
    ApiRateLimitProperties.class,
    SocialUploadRateLimitProperties.class,
    WebSocketSecurityProperties.class,
    LegacyFlywayProperties.class,
    AppAuthProperties.class,
    TelegramProperties.class,
    TelegramStarsPaymentProperties.class,
    MockPaymentProperties.class,
    WechatH5PaymentProperties.class,
    AlipayWapPaymentProperties.class,
    MemoryLlmProperties.class,
    RuoYiAdminProperties.class,
    AppImageGenerationProperties.class
})
public class SillySpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.run(SillySpringbootApplication.class, args);
    }

}
