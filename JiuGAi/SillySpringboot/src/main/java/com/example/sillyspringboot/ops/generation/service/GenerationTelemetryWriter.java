package com.example.sillyspringboot.ops.generation.service;

import com.example.sillyspringboot.ops.generation.entity.GenerationModelPricing;
import com.example.sillyspringboot.ops.generation.mapper.GenerationAttemptMapper;
import com.example.sillyspringboot.ops.generation.model.GenerationAttemptContext;
import com.example.sillyspringboot.ops.generation.model.GenerationAttemptEvent;
import com.example.sillyspringboot.ops.generation.model.GenerationAttemptRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class GenerationTelemetryWriter {

    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    private final GenerationAttemptMapper attemptMapper;
    private final GenerationModelPricingService pricingService;

    public GenerationTelemetryWriter(
            GenerationAttemptMapper attemptMapper,
            GenerationModelPricingService pricingService
    ) {
        this.attemptMapper = attemptMapper;
        this.pricingService = pricingService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(GenerationAttemptEvent event) {
        if (event == null || event.startedAt() == null || event.finishedAt() == null) {
            return;
        }

        GenerationAttemptRow row = toRow(event);
        if (event.conversationId() != null && event.conversationId() > 0) {
            GenerationAttemptContext context = attemptMapper.findContext(
                    event.conversationId(),
                    safe(event.clientMessageId(), 64)
            );
            if (context != null) {
                row.setGenerationTaskId(context.getGenerationTaskId());
                row.setCharacterId(context.getCharacterId());
            }
        }

        GenerationModelPricing pricing = pricingService.resolve(
                row.getProviderKey(),
                row.getModel(),
                row.getStartedAt()
        );
        applyPricing(row, pricing);
        attemptMapper.insert(row);
    }

    private static GenerationAttemptRow toRow(GenerationAttemptEvent event) {
        GenerationAttemptRow row = new GenerationAttemptRow();
        row.setConversationId(event.conversationId());
        row.setAttemptNo(Math.max(1, event.attemptNo()));
        row.setProviderKey(defaultText(event.providerKey(), "st_default", 80).toLowerCase(Locale.ROOT));
        row.setRouteKey(defaultText(event.routeKey(), "st_default", 80).toLowerCase(Locale.ROOT));
        row.setProviderSource(safe(event.providerSource(), 80));
        row.setModel(safe(event.model(), 255));
        row.setByok(event.byok());
        row.setFallback(event.fallback());
        row.setStartedAt(event.startedAt());
        row.setFirstTokenAt(event.firstTokenAt());
        row.setFinishedAt(event.finishedAt());
        row.setTtftMs(event.firstTokenAt() == null ? null : millisBetween(event.startedAt(), event.firstTokenAt()));
        row.setDurationMs(millisBetween(event.startedAt(), event.finishedAt()));
        row.setHttpStatus(event.httpStatus());
        row.setStatus(defaultText(event.status(), "FAILED", 24).toUpperCase(Locale.ROOT));
        row.setErrorCode(errorCode(event.errorCode()));
        row.setPromptTokens(nonNegative(event.promptTokens()));
        row.setCompletionTokens(nonNegative(event.completionTokens()));
        row.setPromptTokensEstimated(row.getPromptTokens() != null && event.promptTokensEstimated());
        row.setCompletionTokensEstimated(row.getCompletionTokens() != null && event.completionTokensEstimated());
        return row;
    }

    private static void applyPricing(GenerationAttemptRow row, GenerationModelPricing pricing) {
        if (pricing == null) {
            row.setCostEstimated(false);
            row.setCostPartial(false);
            return;
        }
        row.setPricingId(pricing.getId());
        row.setPricingVersion(pricing.getVersion());
        row.setCurrency(pricing.getCurrency());
        row.setInputUsdPerMillionTokens(pricing.getInputUsdPerMillionTokens());
        row.setOutputUsdPerMillionTokens(pricing.getOutputUsdPerMillionTokens());
        row.setInputCostUsd(tokenCost(row.getPromptTokens(), pricing.getInputUsdPerMillionTokens()));
        row.setOutputCostUsd(tokenCost(row.getCompletionTokens(), pricing.getOutputUsdPerMillionTokens()));
        row.setTotalCostUsd(addKnown(row.getInputCostUsd(), row.getOutputCostUsd()));
        row.setCostEstimated(Boolean.TRUE.equals(row.getPromptTokensEstimated())
                || Boolean.TRUE.equals(row.getCompletionTokensEstimated()));
        row.setCostPartial(row.getPromptTokens() == null || row.getCompletionTokens() == null);
    }

    private static BigDecimal tokenCost(Integer tokens, BigDecimal usdPerMillion) {
        if (tokens == null || usdPerMillion == null) {
            return null;
        }
        return usdPerMillion.multiply(BigDecimal.valueOf(tokens))
                .divide(ONE_MILLION, 10, RoundingMode.HALF_UP);
    }

    private static BigDecimal addKnown(BigDecimal left, BigDecimal right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.add(right).setScale(10, RoundingMode.HALF_UP);
    }

    private static int millisBetween(LocalDateTime start, LocalDateTime end) {
        long value = Math.max(0L, Duration.between(start, end).toMillis());
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private static Integer nonNegative(Integer value) {
        return value == null || value < 0 ? null : value;
    }

    private static String errorCode(String raw) {
        String value = safe(raw, 80).toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_:-]", "_");
        return value.isBlank() ? null : value;
    }

    private static String defaultText(String value, String fallback, int max) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return safe(normalized, max);
    }

    private static String safe(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }
}
