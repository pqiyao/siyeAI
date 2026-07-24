package com.example.sillyspringboot.ai.service;

import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AiProviderCatalogService {

    public record ProviderDefinition(
            String value,
            String label,
            String defaultBaseUrl,
            boolean customBaseUrl,
            Set<AiCapability> capabilities
    ) {}

    private static final List<ProviderDefinition> DEFINITIONS = List.of(
            definition("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1", false,
                    AiCapability.CHAT, AiCapability.IMAGE, AiCapability.TTS, AiCapability.STT),
            definition("openai", "OpenAI", "https://api.openai.com/v1", false,
                    AiCapability.CHAT, AiCapability.IMAGE, AiCapability.TTS, AiCapability.STT),
            definition("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", false,
                    AiCapability.CHAT, AiCapability.IMAGE),
            definition("deepseek", "DeepSeek", "https://api.deepseek.com/v1", false, AiCapability.CHAT),
            definition("groq", "Groq", "https://api.groq.com/openai/v1", false,
                    AiCapability.CHAT, AiCapability.STT),
            definition("mistralai", "Mistral", "https://api.mistral.ai/v1", false, AiCapability.CHAT),
            definition("moonshot", "Moonshot", "https://api.moonshot.cn/v1", false, AiCapability.CHAT),
            definition("xai", "xAI", "https://api.x.ai/v1", false, AiCapability.CHAT, AiCapability.IMAGE),
            definition("fireworks", "Fireworks", "https://api.fireworks.ai/inference/v1", false,
                    AiCapability.CHAT, AiCapability.IMAGE),
            definition("custom", "自定义 OpenAI 兼容", "", true,
                    AiCapability.CHAT, AiCapability.IMAGE, AiCapability.TTS, AiCapability.STT)
    );

    public List<Map<String, Object>> publicCatalog() {
        return DEFINITIONS.stream().map(item -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("value", item.value());
            value.put("label", item.label());
            value.put("defaultBaseUrl", item.defaultBaseUrl());
            value.put("customBaseUrl", item.customBaseUrl());
            value.put("capabilities", item.capabilities().stream().map(Enum::name).toList());
            return value;
        }).toList();
    }

    public ProviderDefinition require(String rawVendor) {
        String vendor = safe(rawVendor).toLowerCase(Locale.ROOT);
        return DEFINITIONS.stream()
                .filter(item -> item.value().equals(vendor))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "不支持的供应商"));
    }

    public String normalizeBaseUrl(String vendor, String rawBaseUrl) {
        ProviderDefinition definition = require(vendor);
        String value = safe(rawBaseUrl);
        if (value.isBlank()) {
            value = definition.defaultBaseUrl();
        }
        if (value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "API 地址不能为空");
        }
        value = stripKnownEndpoint(stripTrailingSlash(value));
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
                throw new IllegalArgumentException("invalid provider url");
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "API 地址必须是有效的 http/https 基础地址");
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.endsWith("/v1") ? value : value + "/v1";
    }

    private static String stripKnownEndpoint(String raw) {
        String value = raw;
        String lower = value.toLowerCase(Locale.ROOT);
        for (String suffix : List.of(
                "/chat/completions", "/images/generations", "/audio/speech",
                "/audio/transcriptions", "/models"
        )) {
            if (lower.endsWith(suffix)) {
                value = stripTrailingSlash(value.substring(0, value.length() - suffix.length()));
                break;
            }
        }
        return value;
    }

    private static String stripTrailingSlash(String raw) {
        String value = safe(raw);
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static ProviderDefinition definition(
            String value,
            String label,
            String baseUrl,
            boolean custom,
            AiCapability... capabilities
    ) {
        return new ProviderDefinition(value, label, baseUrl, custom, Set.of(capabilities));
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
