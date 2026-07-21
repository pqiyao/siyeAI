package com.example.sillyspringboot.billing.web;

import com.example.sillyspringboot.billing.service.TelegramStarsWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/telegram/stars")
public class TelegramStarsWebhookController {

    private final TelegramStarsWebhookService webhookService;

    public TelegramStarsWebhookController(TelegramStarsWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(
            @RequestHeader(name = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody(required = false) Map<String, Object> update
    ) {
        webhookService.handleWebhook(secretToken, update);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
