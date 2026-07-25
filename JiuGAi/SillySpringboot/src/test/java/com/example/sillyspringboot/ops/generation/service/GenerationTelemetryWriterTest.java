package com.example.sillyspringboot.ops.generation.service;

import com.example.sillyspringboot.ops.generation.entity.GenerationModelPricing;
import com.example.sillyspringboot.ops.generation.mapper.GenerationAttemptMapper;
import com.example.sillyspringboot.ops.generation.mapper.GenerationModelPricingMapper;
import com.example.sillyspringboot.ops.generation.model.GenerationAttemptContext;
import com.example.sillyspringboot.ops.generation.model.GenerationAttemptEvent;
import com.example.sillyspringboot.ops.generation.model.GenerationAttemptRow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationTelemetryWriterTest {

    @Test
    void freezesMatchedPriceAndMarksCompletionOnlyCostAsPartialEstimate() {
        GenerationAttemptMapper attemptMapper = mock(GenerationAttemptMapper.class);
        GenerationAttemptContext context = new GenerationAttemptContext();
        context.setGenerationTaskId(91L);
        context.setCharacterId(42L);
        when(attemptMapper.findContext(77L, "client-message")).thenReturn(context);

        GenerationModelPricingMapper pricingMapper = mock(GenerationModelPricingMapper.class);
        GenerationModelPricing pricing = pricing();
        when(pricingMapper.listEffective(anyString(), any(LocalDateTime.class))).thenReturn(List.of(pricing));
        GenerationTelemetryWriter writer = new GenerationTelemetryWriter(
                attemptMapper,
                new GenerationModelPricingService(pricingMapper)
        );

        LocalDateTime start = LocalDateTime.of(2026, 7, 13, 23, 0);
        writer.persist(new GenerationAttemptEvent(
                77L,
                "client-message",
                "trace-77",
                1,
                "openrouter_primary",
                "default_chat",
                "openrouter",
                "openai/gpt-4.1-mini",
                false,
                false,
                start,
                start.plusNanos(100_000_000L),
                start.plusNanos(500_000_000L),
                200,
                "SUCCESS",
                null,
                null,
                null,
                false,
                10,
                true
        ));

        ArgumentCaptor<GenerationAttemptRow> rows = ArgumentCaptor.forClass(GenerationAttemptRow.class);
        verify(attemptMapper).insert(rows.capture());
        GenerationAttemptRow row = rows.getValue();
        assertEquals(91L, row.getGenerationTaskId());
        assertEquals(42L, row.getCharacterId());
        assertEquals("client-message", row.getRequestId());
        assertEquals("trace-77", row.getTraceId());
        assertEquals(7L, row.getPricingId());
        assertEquals("2026-07", row.getPricingVersion());
        assertNull(row.getPromptTokens());
        assertEquals(10, row.getCompletionTokens());
        assertNull(row.getInputCostUsd());
        assertEquals(new BigDecimal("0.0000400000"), row.getOutputCostUsd());
        assertEquals(row.getOutputCostUsd(), row.getTotalCostUsd());
        assertTrue(row.getCostEstimated());
        assertTrue(row.getCostPartial());
    }

    @Test
    void sanitizesAttemptErrorBeforeDatabaseInsert() {
        GenerationAttemptMapper attemptMapper = mock(GenerationAttemptMapper.class);
        GenerationModelPricingMapper pricingMapper = mock(GenerationModelPricingMapper.class);
        GenerationTelemetryWriter writer = new GenerationTelemetryWriter(
                attemptMapper, new GenerationModelPricingService(pricingMapper));
        LocalDateTime start = LocalDateTime.of(2026, 7, 25, 10, 0);

        writer.persist(new GenerationAttemptEvent(
                null, "vision-request", "trace-safe", 1, "provider", "vision.default", "openai",
                "vision-model", false, false, start, null, start.plusSeconds(1), 401, "FAILED",
                "UPSTREAM_ERROR", "Authorization: Bearer sk-1234567890abcdef", null, false, null, false));

        ArgumentCaptor<GenerationAttemptRow> rows = ArgumentCaptor.forClass(GenerationAttemptRow.class);
        verify(attemptMapper).insert(rows.capture());
        assertEquals("vision-request", rows.getValue().getRequestId());
        assertEquals("trace-safe", rows.getValue().getTraceId());
        assertEquals("Authorization: ***", rows.getValue().getErrorMessage());
    }

    private static GenerationModelPricing pricing() {
        GenerationModelPricing pricing = new GenerationModelPricing();
        pricing.setId(7L);
        pricing.setProviderKey("openrouter_primary");
        pricing.setModelPattern("openai/gpt-*");
        pricing.setVersion("2026-07");
        pricing.setCurrency("USD");
        pricing.setInputUsdPerMillionTokens(new BigDecimal("2"));
        pricing.setOutputUsdPerMillionTokens(new BigDecimal("4"));
        pricing.setEffectiveFrom(LocalDateTime.of(2026, 7, 1, 0, 0));
        pricing.setEnabled(true);
        return pricing;
    }
}
