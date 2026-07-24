package com.example.sillyspringboot.ops.generation.service;

import com.example.sillyspringboot.ops.generation.model.GenerationAttemptEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationTelemetryServiceTest {

    @Test
    void persistenceFailureNeverEscapesToChatCaller() throws Exception {
        GenerationTelemetryService service = new GenerationTelemetryService(
                event -> { throw new IllegalStateException("database unavailable"); },
                1,
                4
        );
        try {
            assertTrue(service.recordAsync(event(1, false)));
            await(() -> service.persistenceFailureCount() == 1L);
            assertEquals(1L, service.persistenceFailureCount());
            assertEquals(0L, service.droppedCount());
        } finally {
            service.close();
        }
    }

    @Test
    void fullQueueDropsTelemetryWithoutThrowing() throws Exception {
        CountDownLatch writerEntered = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        GenerationTelemetryService service = new GenerationTelemetryService(event -> {
            writerEntered.countDown();
            try {
                releaseWriter.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }, 1, 1);
        try {
            assertTrue(service.recordAsync(event(1, false)));
            assertTrue(writerEntered.await(1, TimeUnit.SECONDS));
            assertTrue(service.recordAsync(event(2, true)));
            assertFalse(service.recordAsync(event(3, true)));
            assertEquals(1L, service.droppedCount());
        } finally {
            releaseWriter.countDown();
            service.close();
        }
    }

    @Test
    void primaryAndFallbackAttemptsArePersistedIndependently() throws Exception {
        List<GenerationAttemptEvent> persisted = new CopyOnWriteArrayList<>();
        CountDownLatch written = new CountDownLatch(2);
        GenerationTelemetryService service = new GenerationTelemetryService(event -> {
            persisted.add(event);
            written.countDown();
        }, 1, 8);
        try {
            assertTrue(service.recordAsync(event(1, false)));
            assertTrue(service.recordAsync(event(2, true)));
            assertTrue(written.await(1, TimeUnit.SECONDS));
            assertEquals(2, persisted.size());
            assertEquals(1, persisted.get(0).attemptNo());
            assertFalse(persisted.get(0).fallback());
            assertEquals(2, persisted.get(1).attemptNo());
            assertTrue(persisted.get(1).fallback());
        } finally {
            service.close();
        }
    }

    private static GenerationAttemptEvent event(int attemptNo, boolean fallback) {
        LocalDateTime start = LocalDateTime.now();
        return new GenerationAttemptEvent(
                11L,
                "client-message",
                attemptNo,
                fallback ? "fallback_provider" : "primary_provider",
                "default_chat",
                "openai",
                "test-model",
                false,
                fallback,
                start,
                start.plusNanos(10_000_000L),
                start.plusNanos(20_000_000L),
                200,
                "SUCCESS",
                null,
                null,
                false,
                8,
                true
        );
    }

    private static void await(Check check) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!check.matches() && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertTrue(check.matches(), "condition did not become true before timeout");
    }

    @FunctionalInterface
    private interface Check {
        boolean matches();
    }
}
