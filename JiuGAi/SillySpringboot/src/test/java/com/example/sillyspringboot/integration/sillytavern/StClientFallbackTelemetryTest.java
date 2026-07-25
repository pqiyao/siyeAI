package com.example.sillyspringboot.integration.sillytavern;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.sillyspringboot.ai.model.AiCapability;
import com.example.sillyspringboot.ai.model.AiProtocol;
import com.example.sillyspringboot.ai.service.AiRoutingService;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatMessage;
import com.example.sillyspringboot.integration.sillytavern.dto.UserModelOverride;
import com.example.sillyspringboot.ops.generation.model.GenerationAttemptEvent;
import com.example.sillyspringboot.ops.generation.service.GenerationTelemetryService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class StClientFallbackTelemetryTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void failedPrimaryAndSuccessfulFallbackProduceTwoSanitizedAttempts() throws Exception {
        String upstreamErrorBody = "temporary unavailable secret-value-must-not-be-stored";
        AtomicInteger upstreamAttempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/csrf-token", exchange -> respond(exchange, 200, "{\"token\":\"disabled\"}"));
        server.createContext(StApiPaths.RUNTIME_CHAT_GENERATE, exchange -> {
            if (upstreamAttempts.incrementAndGet() == 1) {
                respond(exchange, 503, upstreamErrorBody);
                return;
            }
            String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\n"
                    + "data: [DONE]\n\n";
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            respond(exchange, 200, sse);
        });
        server.start();

        StModelRoutingService routing = mock(StModelRoutingService.class);
        when(routing.resolveForScene(StModelRoutingService.DEFAULT_SCENE)).thenReturn(new StModelRoutingService.ResolvedRoute(
                StModelRoutingService.DEFAULT_SCENE,
                "Default chat",
                List.of(
                        provider("primary", "model-primary", "primary-secret"),
                        provider("fallback", "model-fallback", "fallback-secret")
                )
        ));
        GenerationTelemetryService telemetry = mock(GenerationTelemetryService.class);
        when(telemetry.recordAsync(any())).thenReturn(true);

        SillyTavernProperties properties = new SillyTavernProperties();
        properties.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(3));
        StClient client = new StClient(properties, null, routing, null, null, telemetry);

        List<ChatGenerateChunk> chunks = new ArrayList<>();
        Logger stClientLogger = (Logger) LoggerFactory.getLogger(StClient.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        stClientLogger.addAppender(logAppender);
        try {
            client.streamRuntimeChatGenerate(request(), chunks::add, new StStreamControl());
        } finally {
            stClientLogger.detachAppender(logAppender);
            logAppender.stop();
        }

        ArgumentCaptor<GenerationAttemptEvent> events = ArgumentCaptor.forClass(GenerationAttemptEvent.class);
        verify(telemetry, times(2)).recordAsync(events.capture());
        List<GenerationAttemptEvent> captured = events.getAllValues();
        GenerationAttemptEvent primary = captured.get(0);
        GenerationAttemptEvent fallback = captured.get(1);

        assertEquals(2, upstreamAttempts.get());
        assertEquals("primary", primary.providerKey());
        assertEquals(1, primary.attemptNo());
        assertFalse(primary.fallback());
        assertEquals("FAILED", primary.status());
        assertEquals(503, primary.httpStatus());
        assertEquals("HTTP_503", primary.errorCode());

        assertEquals("fallback", fallback.providerKey());
        assertEquals(2, fallback.attemptNo());
        assertTrue(fallback.fallback());
        assertEquals("SUCCESS", fallback.status());
        assertEquals(200, fallback.httpStatus());
        assertNotNull(fallback.firstTokenAt());
        assertNotNull(fallback.completionTokens());
        assertTrue(fallback.completionTokensEstimated());
        assertTrue(chunks.stream().anyMatch(chunk -> "hello".equals(chunk.delta())));

        ArgumentCaptor<String> failureReason = ArgumentCaptor.forClass(String.class);
        verify(routing).recordFailure(eq("primary"), failureReason.capture());
        assertEquals(
                "st runtime generate http 503 responseBytes=" + upstreamErrorBody.getBytes(StandardCharsets.UTF_8).length,
                failureReason.getValue()
        );
        List<String> logMessages = logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertTrue(logMessages.stream().noneMatch(message -> message.contains(upstreamErrorBody)));
        assertTrue(logMessages.stream().anyMatch(message ->
                message.contains("providerKey=primary")
                        && message.contains("routeKey=" + StModelRoutingService.DEFAULT_SCENE)
                        && message.contains("status=503")
                        && message.contains("responseBytes=" + upstreamErrorBody.getBytes(StandardCharsets.UTF_8).length)
        ));
    }

    @Test
    void runtimeTerminalHttpFailuresDoNotFallbackOrCountCircuitFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger currentStatus = new AtomicInteger();
        startRuntimeServer(exchange -> {
            attempts.incrementAndGet();
            respond(exchange, currentStatus.get(), "terminal failure");
        });
        StModelRoutingService routing = mock(StModelRoutingService.class);
        when(routing.resolveForScene(StModelRoutingService.DEFAULT_SCENE)).thenReturn(new StModelRoutingService.ResolvedRoute(
                StModelRoutingService.DEFAULT_SCENE,
                "Default",
                List.of(provider("primary", "model-a", "key-a"), provider("fallback", "model-b", "key-b"))));
        StClient client = client(routing, null, null);

        for (int status : List.of(400, 401, 403, 404, 413, 415, 422)) {
            currentStatus.set(status);
            int before = attempts.get();
            assertThrows(StUnavailableException.class,
                    () -> client.streamRuntimeChatGenerate(request(), ignored -> {}, new StStreamControl()));
            assertEquals(before + 1, attempts.get(), "status " + status + " must not call fallback provider");
        }

        verify(routing, never()).recordFailure(any(), any());
        verify(routing, never()).recordSuccess("fallback");
    }

    @Test
    void runtimeRetryableHttpFailuresFallbackAndCountCircuitFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger currentStatus = new AtomicInteger();
        startRuntimeServer(exchange -> {
            int attempt = attempts.incrementAndGet();
            if ((attempt & 1) == 1) {
                respond(exchange, currentStatus.get(), "retryable failure");
                return;
            }
            respond(exchange, 200, "data: {\"choices\":[{\"delta\":{\"content\":\"runtime-ok\"}}]}\n\ndata: [DONE]\n\n");
        });
        StModelRoutingService routing = mock(StModelRoutingService.class);
        when(routing.resolveForScene(StModelRoutingService.DEFAULT_SCENE)).thenReturn(new StModelRoutingService.ResolvedRoute(
                StModelRoutingService.DEFAULT_SCENE,
                "Default",
                List.of(provider("primary", "model-a", "key-a"), provider("fallback", "model-b", "key-b"))));
        StClient client = client(routing, null, null);

        for (int status : List.of(408, 425, 429, 503)) {
            currentStatus.set(status);
            int before = attempts.get();
            List<ChatGenerateChunk> chunks = new ArrayList<>();
            client.streamRuntimeChatGenerate(request(), chunks::add, new StStreamControl());
            assertEquals(before + 2, attempts.get(), "status " + status + " must call exactly one fallback provider");
            assertTrue(chunks.stream().anyMatch(chunk -> "runtime-ok".equals(chunk.delta())));
        }

        verify(routing, times(4)).recordFailure(eq("primary"), any());
        verify(routing, times(4)).recordSuccess("fallback");
    }

    @Test
    void runtimeEmptyResponseFallsBackBeforeFirstToken() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startRuntimeServer(exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 200, "");
                return;
            }
            respond(exchange, 200, "data: {\"choices\":[{\"delta\":{\"content\":\"runtime-recovered\"}}]}\n\ndata: [DONE]\n\n");
        });
        StModelRoutingService routing = mock(StModelRoutingService.class);
        when(routing.resolveForScene(StModelRoutingService.DEFAULT_SCENE)).thenReturn(new StModelRoutingService.ResolvedRoute(
                StModelRoutingService.DEFAULT_SCENE,
                "Default",
                List.of(provider("primary", "model-a", "key-a"), provider("fallback", "model-b", "key-b"))));
        List<ChatGenerateChunk> chunks = new ArrayList<>();

        client(routing, null, null).streamRuntimeChatGenerate(request(), chunks::add, new StStreamControl());

        assertEquals(2, attempts.get());
        assertTrue(chunks.stream().anyMatch(chunk -> "runtime-recovered".equals(chunk.delta())));
        verify(routing).recordFailure(eq("primary"), eq("st runtime generate empty response"));
        verify(routing).recordSuccess("fallback");
    }

    @Test
    void runtimeReadTimeoutFallsBackBeforeFirstToken() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startRuntimeServer(exchange -> {
            if (attempts.incrementAndGet() == 1) {
                try {
                    Thread.sleep(350L);
                    respond(exchange, 200, "late response");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (IOException ignored) {
                    // The timed-out client is expected to close the first exchange.
                }
                return;
            }
            respond(exchange, 200, "data: {\"choices\":[{\"delta\":{\"content\":\"timeout-recovered\"}}]}\n\ndata: [DONE]\n\n");
        });
        StModelRoutingService routing = mock(StModelRoutingService.class);
        when(routing.resolveForScene(StModelRoutingService.DEFAULT_SCENE)).thenReturn(new StModelRoutingService.ResolvedRoute(
                StModelRoutingService.DEFAULT_SCENE,
                "Default",
                List.of(provider("primary", "model-a", "key-a"), provider("fallback", "model-b", "key-b"))));
        List<ChatGenerateChunk> chunks = new ArrayList<>();

        client(routing, null, null, Duration.ofMillis(150))
                .streamRuntimeChatGenerate(request(), chunks::add, new StStreamControl());

        assertEquals(2, attempts.get());
        assertTrue(chunks.stream().anyMatch(chunk -> "timeout-recovered".equals(chunk.delta())));
        verify(routing).recordFailure(eq("primary"), eq("st runtime generate transport failure"));
        verify(routing).recordSuccess("fallback");
    }

    @Test
    void directGenerateHttpErrorDoesNotExposeResponseBodyInExceptionChain() throws Exception {
        String upstreamErrorBody = "private-direct-provider-detail";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/csrf-token", exchange -> respond(exchange, 200, "{\"token\":\"disabled\"}"));
        server.createContext(StApiPaths.SETTINGS_GET, exchange -> respond(
                exchange,
                200,
                "{\"settings\":\"{\\\"chat_completion_source\\\":\\\"openai\\\",\\\"openai_model\\\":\\\"test-model\\\"}\"}"
        ));
        server.createContext(
                StApiPaths.CHAT_COMPLETIONS_GENERATE,
                exchange -> respond(exchange, 502, upstreamErrorBody)
        );
        server.start();

        SillyTavernProperties properties = new SillyTavernProperties();
        properties.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(3));
        OpenRouterGenerationSettingsService settings = mock(OpenRouterGenerationSettingsService.class);
        when(settings.resolveForRuntime()).thenReturn(new OpenRouterGenerationSettingsService.ResolvedSettings(
                "test-model",
                0.85d,
                256,
                -1d,
                -999d,
                -999d,
                List.of()
        ));
        StModelRoutingService routing = mock(StModelRoutingService.class);
        StClient client = new StClient(properties, settings, routing, null, null);

        StUnavailableException error = assertThrows(
                StUnavailableException.class,
                () -> client.streamChatCompletionsGenerate(request(), ignored -> {}, new StStreamControl())
        );

        List<String> exceptionMessages = throwableMessages(error);
        assertTrue(exceptionMessages.stream().noneMatch(message -> message.contains(upstreamErrorBody)));
        assertTrue(exceptionMessages.stream().anyMatch(message -> message.equals(
                "st generate http 502 responseBytes=" + upstreamErrorBody.getBytes(StandardCharsets.UTF_8).length
        )));
    }

    @Test
    void directGenerateFallsBackBeforeFirstToken() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startDirectServer(exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 503, "primary unavailable");
                return;
            }
            respond(exchange, 200, "data: {\"choices\":[{\"delta\":{\"content\":\"direct-ok\"}}]}\n\ndata: [DONE]\n\n");
        });
        StModelRoutingService routing = mock(StModelRoutingService.class);
        when(routing.resolveForScene(StModelRoutingService.DEFAULT_SCENE)).thenReturn(new StModelRoutingService.ResolvedRoute(
                StModelRoutingService.DEFAULT_SCENE,
                "Default",
                List.of(provider("primary", "model-a", "key-a"), provider("fallback", "model-b", "key-b"))));

        List<ChatGenerateChunk> chunks = new ArrayList<>();
        directClient(routing, null).streamChatCompletionsGenerate(request(), chunks::add, new StStreamControl());

        assertEquals(2, attempts.get());
        assertTrue(chunks.stream().anyMatch(chunk -> "direct-ok".equals(chunk.delta())));
        verify(routing).recordFailure(eq("primary"), any());
        verify(routing).recordSuccess("fallback");
    }

    @Test
    void directTerminalHttpFailuresDoNotFallbackOrCountCircuitFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger fallbackProviderAttempts = new AtomicInteger();
        AtomicInteger currentStatus = new AtomicInteger();
        startDirectServer(exchange -> {
            attempts.incrementAndGet();
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (requestBody.contains("model-b")) {
                fallbackProviderAttempts.incrementAndGet();
            }
            respond(exchange, currentStatus.get(), "terminal failure");
        });
        StModelRoutingService routing = mock(StModelRoutingService.class);
        when(routing.resolveForScene(StModelRoutingService.DEFAULT_SCENE)).thenReturn(new StModelRoutingService.ResolvedRoute(
                StModelRoutingService.DEFAULT_SCENE,
                "Default",
                List.of(provider("primary", "model-a", "key-a"), provider("fallback", "model-b", "key-b"))));
        StClient client = directClient(routing, null);

        for (int status : List.of(400, 401, 403, 404, 413, 415, 422)) {
            currentStatus.set(status);
            int before = attempts.get();
            assertThrows(StUnavailableException.class,
                    () -> client.streamChatCompletionsGenerate(request(), ignored -> {}, new StStreamControl()));
            int expectedAttempts = status == 400 ? 2 : 1;
            assertEquals(before + expectedAttempts, attempts.get(),
                    "status " + status + " must not exceed its same-provider compatibility retry");
        }

        assertEquals(0, fallbackProviderAttempts.get());
        verify(routing, never()).recordFailure(any(), any());
        verify(routing, never()).recordSuccess("fallback");
    }

    @Test
    void directRetryableHttpFailuresFallbackAndCountCircuitFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger currentStatus = new AtomicInteger();
        startDirectServer(exchange -> {
            int attempt = attempts.incrementAndGet();
            if ((attempt & 1) == 1) {
                respond(exchange, currentStatus.get(), "retryable failure");
                return;
            }
            respond(exchange, 200, "data: {\"choices\":[{\"delta\":{\"content\":\"direct-ok\"}}]}\n\ndata: [DONE]\n\n");
        });
        StModelRoutingService routing = mock(StModelRoutingService.class);
        when(routing.resolveForScene(StModelRoutingService.DEFAULT_SCENE)).thenReturn(new StModelRoutingService.ResolvedRoute(
                StModelRoutingService.DEFAULT_SCENE,
                "Default",
                List.of(provider("primary", "model-a", "key-a"), provider("fallback", "model-b", "key-b"))));
        StClient client = directClient(routing, null);

        for (int status : List.of(408, 425, 429, 503)) {
            currentStatus.set(status);
            int before = attempts.get();
            List<ChatGenerateChunk> chunks = new ArrayList<>();
            client.streamChatCompletionsGenerate(request(), chunks::add, new StStreamControl());
            assertEquals(before + 2, attempts.get(), "status " + status + " must call exactly one fallback provider");
            assertTrue(chunks.stream().anyMatch(chunk -> "direct-ok".equals(chunk.delta())));
        }

        verify(routing, times(4)).recordFailure(eq("primary"), any());
        verify(routing, times(4)).recordSuccess("fallback");
    }

    @Test
    void directEmptyResponseFallsBackBeforeFirstToken() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startDirectServer(exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 200, "");
                return;
            }
            respond(exchange, 200, "data: {\"choices\":[{\"delta\":{\"content\":\"direct-recovered\"}}]}\n\ndata: [DONE]\n\n");
        });
        StModelRoutingService routing = mock(StModelRoutingService.class);
        when(routing.resolveForScene(StModelRoutingService.DEFAULT_SCENE)).thenReturn(new StModelRoutingService.ResolvedRoute(
                StModelRoutingService.DEFAULT_SCENE,
                "Default",
                List.of(provider("primary", "model-a", "key-a"), provider("fallback", "model-b", "key-b"))));
        List<ChatGenerateChunk> chunks = new ArrayList<>();

        directClient(routing, null).streamChatCompletionsGenerate(request(), chunks::add, new StStreamControl());

        assertEquals(2, attempts.get());
        assertTrue(chunks.stream().anyMatch(chunk -> "direct-recovered".equals(chunk.delta())));
        verify(routing).recordFailure(eq("primary"), eq("st generate empty response"));
        verify(routing).recordSuccess("fallback");
    }

    @Test
    void directGenerateDoesNotSwitchAfterFirstToken() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startDirectServer(exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 200, "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"
                    + "data: {\"error\":{\"message\":\"late failure\"}}\n\n");
        });
        StModelRoutingService routing = mock(StModelRoutingService.class);
        when(routing.resolveForScene(StModelRoutingService.DEFAULT_SCENE)).thenReturn(new StModelRoutingService.ResolvedRoute(
                StModelRoutingService.DEFAULT_SCENE,
                "Default",
                List.of(provider("first", "model-a", "key-a"), provider("second", "model-b", "key-b"))));
        List<ChatGenerateChunk> chunks = new ArrayList<>();

        assertThrows(StUnavailableException.class,
                () -> directClient(routing, null).streamChatCompletionsGenerate(request(), chunks::add, new StStreamControl()));

        assertEquals(1, attempts.get());
        assertTrue(chunks.stream().anyMatch(chunk -> "partial".equals(chunk.delta())));
        verify(routing, never()).recordSuccess("second");
    }

    @Test
    void directByokNeverResolvesOfficialOrLegacyRoutes() throws Exception {
        startDirectServer(exchange -> respond(
                exchange,
                200,
                "data: {\"choices\":[{\"delta\":{\"content\":\"byok-direct\"}}]}\n\ndata: [DONE]\n\n"
        ));
        StModelRoutingService legacy = mock(StModelRoutingService.class);
        AiRoutingService v2 = mock(AiRoutingService.class);
        UserModelOverride override = new UserModelOverride(
                "custom", "byok-model", "", "", "", "", "", "", "", "",
                "", "", "", "", "user-secret", "https://byok.example/v1");
        List<ChatGenerateChunk> chunks = new ArrayList<>();

        directClient(legacy, v2).streamChatCompletionsGenerate(request(override), chunks::add, new StStreamControl());

        assertTrue(chunks.stream().anyMatch(chunk -> "byok-direct".equals(chunk.delta())));
        verify(legacy, never()).resolveForScene(any());
        verify(v2, never()).resolve(any());
    }

    @Test
    void canaryV2FailureFallsBackToLegacyBeforeFirstToken() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/csrf-token", exchange -> respond(exchange, 200, "{\"token\":\"disabled\"}"));
        server.createContext(StApiPaths.RUNTIME_CHAT_GENERATE, exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 503, "v2 unavailable");
                return;
            }
            respond(exchange, 200, "data: {\"choices\":[{\"delta\":{\"content\":\"legacy-ok\"}}]}\n\ndata: [DONE]\n\n");
        });
        server.start();

        StModelRoutingService legacy = mock(StModelRoutingService.class);
        when(legacy.resolveForScene(StModelRoutingService.DEFAULT_SCENE)).thenReturn(new StModelRoutingService.ResolvedRoute(
                StModelRoutingService.DEFAULT_SCENE, "Default", List.of(provider("legacy", "legacy-model", "legacy-key"))));
        AiRoutingService v2 = mock(AiRoutingService.class);
        when(v2.resolve(AiCapability.CHAT)).thenReturn(List.of(v2Provider(101L, "v2", "v2-model")));
        when(v2.shouldUseChatV2(77L)).thenReturn(true);

        StClient client = client(legacy, null, v2);
        List<ChatGenerateChunk> chunks = new ArrayList<>();
        client.streamRuntimeChatGenerate(request(), chunks::add, new StStreamControl());

        assertEquals(2, attempts.get());
        assertTrue(chunks.stream().anyMatch(chunk -> "legacy-ok".equals(chunk.delta())));
        verify(v2).recordFailure(eq(101L), any());
        verify(legacy).recordSuccess("legacy");
    }

    @Test
    void visionTransientFailureFallsBackOnlyWithinVisionRoute() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startDirectServer(exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 503, "vision unavailable");
                return;
            }
            respond(exchange, 200, "data: {\"choices\":[{\"delta\":{\"content\":\"vision-ok\"}}]}\n\ndata: [DONE]\n\n");
        });
        StModelRoutingService legacy = mock(StModelRoutingService.class);
        AiRoutingService v2 = mock(AiRoutingService.class);
        when(v2.isCapabilityEnabled(AiCapability.VISION)).thenReturn(true);
        when(v2.resolve(AiCapability.VISION)).thenReturn(List.of(
                v2Provider(201L, "vision-a", "vision-a-model", AiCapability.VISION),
                v2Provider(202L, "vision-b", "vision-b-model", AiCapability.VISION)
        ));

        List<ChatGenerateChunk> chunks = new ArrayList<>();
        directClient(legacy, v2).streamChatCompletionsGenerate(visionRequest(null), chunks::add, new StStreamControl());

        assertEquals(2, attempts.get());
        assertTrue(chunks.stream().anyMatch(chunk -> "vision-ok".equals(chunk.delta())));
        verify(v2).recordFailure(eq(201L), any());
        verify(v2).recordSuccess(202L);
        verify(legacy, never()).resolveForScene(any());
    }

    @Test
    void visionConfigurationFailureDoesNotFallback() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startDirectServer(exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 401, "invalid vision key");
        });
        StModelRoutingService legacy = mock(StModelRoutingService.class);
        AiRoutingService v2 = mock(AiRoutingService.class);
        when(v2.isCapabilityEnabled(AiCapability.VISION)).thenReturn(true);
        when(v2.resolve(AiCapability.VISION)).thenReturn(List.of(
                v2Provider(211L, "vision-a", "vision-a-model", AiCapability.VISION),
                v2Provider(212L, "vision-b", "vision-b-model", AiCapability.VISION)
        ));

        assertThrows(StUnavailableException.class, () -> directClient(legacy, v2)
                .streamChatCompletionsGenerate(visionRequest(null), ignored -> {}, new StStreamControl()));

        assertEquals(1, attempts.get());
        verify(v2).recordConfigurationError(eq(211L), any());
        verify(v2, never()).recordFailure(anyLong(), any());
        verify(v2, never()).recordSuccess(212L);
    }

    @Test
    void visionPartialFailedAttemptIsDiscardedBeforeFallback() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startDirectServer(exchange -> {
            if (attempts.incrementAndGet() == 1) {
                respond(exchange, 200,
                        "data: {\"choices\":[{\"delta\":{\"content\":\"discard-me\"}}]}\n\n"
                                + "data: {\"error\":{\"message\":\"late vision failure\"}}\n\n");
                return;
            }
            respond(exchange, 200,
                    "data: {\"choices\":[{\"delta\":{\"content\":\"clean-summary\"}}]}\n\ndata: [DONE]\n\n");
        });
        StModelRoutingService legacy = mock(StModelRoutingService.class);
        AiRoutingService v2 = mock(AiRoutingService.class);
        when(v2.isCapabilityEnabled(AiCapability.VISION)).thenReturn(true);
        when(v2.resolve(AiCapability.VISION)).thenReturn(List.of(
                v2Provider(221L, "vision-a", "vision-a-model", AiCapability.VISION),
                v2Provider(222L, "vision-b", "vision-b-model", AiCapability.VISION)
        ));
        List<ChatGenerateChunk> chunks = new ArrayList<>();

        directClient(legacy, v2).streamChatCompletionsGenerate(
                visionRequest(null), chunks::add, new StStreamControl());

        assertEquals(2, attempts.get());
        assertFalse(chunks.stream().anyMatch(chunk -> "discard-me".equals(chunk.delta())));
        assertTrue(chunks.stream().anyMatch(chunk -> "clean-summary".equals(chunk.delta())));
    }

    @Test
    void visionByokNeverResolvesOfficialVisionRoute() throws Exception {
        startDirectServer(exchange -> respond(
                exchange, 200,
                "data: {\"choices\":[{\"delta\":{\"content\":\"byok-vision\"}}]}\n\ndata: [DONE]\n\n"
        ));
        StModelRoutingService legacy = mock(StModelRoutingService.class);
        AiRoutingService v2 = mock(AiRoutingService.class);
        UserModelOverride override = new UserModelOverride(
                "custom", "text-model", "vision-model", "", "", "", "", "", "", "",
                "", "", "", "", "user-secret", "https://byok.example/v1");

        directClient(legacy, v2).streamChatCompletionsGenerate(
                visionRequest(override), ignored -> {}, new StStreamControl());

        verify(v2, never()).resolve(AiCapability.VISION);
        verify(legacy, never()).resolveForScene(any());
    }

    @Test
    void fullRolloutV2FailureNeverFallsBackToLegacy() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startRuntimeServer(exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 503, "v2 unavailable");
        });
        StModelRoutingService legacy = mock(StModelRoutingService.class);
        when(legacy.resolveForScene(StModelRoutingService.DEFAULT_SCENE)).thenReturn(new StModelRoutingService.ResolvedRoute(
                StModelRoutingService.DEFAULT_SCENE, "Default",
                List.of(provider("legacy", "legacy-model", "legacy-key"))));
        AiRoutingService v2 = mock(AiRoutingService.class);
        when(v2.resolve(AiCapability.CHAT)).thenReturn(List.of(v2Provider(101L, "v2", "v2-model")));
        when(v2.shouldUseChatV2(77L)).thenReturn(true);
        when(v2.isChatFullyRolledOut()).thenReturn(true);

        assertThrows(StUnavailableException.class,
                () -> client(legacy, null, v2).streamRuntimeChatGenerate(request(), ignored -> {}, new StStreamControl()));

        assertEquals(1, attempts.get());
        verify(v2).recordFailure(eq(101L), any());
        verify(legacy, never()).recordSuccess("legacy");
    }

    @Test
    void canaryV2TerminalFailureDoesNotFallbackToLegacyOrCountCircuitFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startRuntimeServer(exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 401, "invalid key");
        });
        StModelRoutingService legacy = mock(StModelRoutingService.class);
        when(legacy.resolveForScene(StModelRoutingService.DEFAULT_SCENE)).thenReturn(new StModelRoutingService.ResolvedRoute(
                StModelRoutingService.DEFAULT_SCENE,
                "Default",
                List.of(provider("legacy", "legacy-model", "legacy-key"))));
        AiRoutingService v2 = mock(AiRoutingService.class);
        when(v2.resolve(AiCapability.CHAT)).thenReturn(List.of(v2Provider(101L, "v2", "v2-model")));
        when(v2.shouldUseChatV2(77L)).thenReturn(true);

        assertThrows(StUnavailableException.class,
                () -> client(legacy, null, v2).streamRuntimeChatGenerate(request(), ignored -> {}, new StStreamControl()));

        assertEquals(1, attempts.get());
        verify(v2, never()).recordFailure(eq(101L), any());
        verify(v2).recordConfigurationError(eq(101L), any());
        verify(legacy, never()).recordFailure(any(), any());
        verify(legacy, never()).recordSuccess("legacy");
    }

    @Test
    void emittedFirstTokenPreventsSwitchingToAnotherProvider() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/csrf-token", exchange -> respond(exchange, 200, "{\"token\":\"disabled\"}"));
        server.createContext(StApiPaths.RUNTIME_CHAT_GENERATE, exchange -> {
            attempts.incrementAndGet();
            String stream = "data: {\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}\n\n"
                    + "data: {\"error\":{\"message\":\"late failure\"}}\n\n";
            respond(exchange, 200, stream);
        });
        server.start();

        StModelRoutingService legacy = mock(StModelRoutingService.class);
        when(legacy.resolveForScene(StModelRoutingService.DEFAULT_SCENE)).thenReturn(new StModelRoutingService.ResolvedRoute(
                StModelRoutingService.DEFAULT_SCENE,
                "Default",
                List.of(provider("first", "model-1", "key-1"), provider("second", "model-2", "key-2"))));
        List<ChatGenerateChunk> chunks = new ArrayList<>();
        assertThrows(StUnavailableException.class,
                () -> client(legacy, null, null).streamRuntimeChatGenerate(request(), chunks::add, new StStreamControl()));

        assertEquals(1, attempts.get());
        assertTrue(chunks.stream().anyMatch(chunk -> "partial".equals(chunk.delta())));
        verify(legacy, never()).recordSuccess("second");
    }

    @Test
    void byokRequestNeverResolvesOfficialOrLegacyRoutes() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/csrf-token", exchange -> respond(exchange, 200, "{\"token\":\"disabled\"}"));
        server.createContext(StApiPaths.RUNTIME_CHAT_GENERATE,
                exchange -> respond(exchange, 200, "data: {\"choices\":[{\"delta\":{\"content\":\"byok-ok\"}}]}\n\ndata: [DONE]\n\n"));
        server.start();

        StModelRoutingService legacy = mock(StModelRoutingService.class);
        AiRoutingService v2 = mock(AiRoutingService.class);
        UserModelOverride override = new UserModelOverride(
                "custom", "byok-model", "", "", "", "", "", "", "", "",
                "", "", "", "", "user-secret", "https://byok.example/v1");
        List<ChatGenerateChunk> chunks = new ArrayList<>();
        client(legacy, null, v2).streamRuntimeChatGenerate(request(override), chunks::add, new StStreamControl());

        assertTrue(chunks.stream().anyMatch(chunk -> "byok-ok".equals(chunk.delta())));
        verify(legacy, never()).resolveForScene(any());
        verify(v2, never()).resolve(any());
    }

    @Test
    void failedByokRequestNeverFallsBackToOfficialOrLegacyRoutes() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startRuntimeServer(exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 503, "byok unavailable");
        });
        StModelRoutingService legacy = mock(StModelRoutingService.class);
        AiRoutingService v2 = mock(AiRoutingService.class);
        UserModelOverride override = new UserModelOverride(
                "custom", "byok-model", "", "", "", "", "", "", "", "",
                "", "", "", "", "user-secret", "https://byok.example/v1");

        assertThrows(StUnavailableException.class,
                () -> client(legacy, null, v2).streamRuntimeChatGenerate(
                        request(override), ignored -> {}, new StStreamControl()));

        assertEquals(1, attempts.get());
        verify(legacy, never()).resolveForScene(any());
        verify(v2, never()).resolve(any());
        verify(v2, never()).recordFailure(eq(101L), any());
    }

    private StClient client(
            StModelRoutingService legacy,
            GenerationTelemetryService telemetry,
            AiRoutingService v2
    ) {
        return client(legacy, telemetry, v2, Duration.ofSeconds(3));
    }

    private StClient client(
            StModelRoutingService legacy,
            GenerationTelemetryService telemetry,
            AiRoutingService v2,
            Duration readTimeout
    ) {
        SillyTavernProperties properties = new SillyTavernProperties();
        properties.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(readTimeout);
        return new StClient(properties, null, legacy, null, null, telemetry, v2);
    }

    private StClient directClient(StModelRoutingService legacy, AiRoutingService v2) {
        SillyTavernProperties properties = new SillyTavernProperties();
        properties.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(3));
        OpenRouterGenerationSettingsService settings = mock(OpenRouterGenerationSettingsService.class);
        when(settings.resolveForRuntime()).thenReturn(new OpenRouterGenerationSettingsService.ResolvedSettings(
                "test-model", 0.85d, 256, -1d, -999d, -999d, List.of()));
        return new StClient(properties, settings, legacy, null, null, null, v2);
    }

    private void startRuntimeServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(runnable -> {
            Thread worker = new Thread(runnable, "st-client-test-http");
            worker.setDaemon(true);
            return worker;
        }));
        server.createContext("/csrf-token", exchange -> respond(exchange, 200, "{\"token\":\"disabled\"}"));
        server.createContext(StApiPaths.RUNTIME_CHAT_GENERATE, handler::handle);
        server.start();
    }

    private void startDirectServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/csrf-token", exchange -> respond(exchange, 200, "{\"token\":\"disabled\"}"));
        server.createContext(StApiPaths.SETTINGS_GET, exchange -> respond(
                exchange,
                200,
                "{\"settings\":\"{\\\"chat_completion_source\\\":\\\"openai\\\",\\\"openai_model\\\":\\\"test-model\\\"}\"}"
        ));
        server.createContext(StApiPaths.CHAT_COMPLETIONS_GENERATE, handler::handle);
        server.start();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static AiRoutingService.ResolvedProvider v2Provider(long id, String key, String model) {
        return v2Provider(id, key, model, AiCapability.CHAT);
    }

    private static AiRoutingService.ResolvedProvider v2Provider(
            long id,
            String key,
            String model,
            AiCapability capability
    ) {
        return new AiRoutingService.ResolvedProvider(
                id, key, key, "custom", "https://v2.example/v1", "v2-key",
                capability, AiProtocol.forCapability(capability), model, "", 1);
    }

    private static StModelRoutingService.ResolvedProvider provider(String key, String model, String secret) {
        return new StModelRoutingService.ResolvedProvider(
                key,
                key,
                "openai",
                model,
                "https://example.invalid/v1",
                secret,
                ""
        );
    }

    private static ChatGenerateRequest request() {
        return request(null);
    }

    private static ChatGenerateRequest request(UserModelOverride override) {
        return new ChatGenerateRequest(
                77L,
                "hello",
                List.of(),
                "client-message-1",
                true,
                "normal",
                Set.of(),
                "User",
                "Character",
                List.of(),
                "character.png",
                "chat-file",
                "root:client-message-1",
                List.of(),
                override
        );
    }

    private static ChatGenerateRequest visionRequest(UserModelOverride override) {
        return new ChatGenerateRequest(
                77L,
                "",
                List.of(ChatMessage.multimodalUser("describe", List.of("data:image/png;base64,AA=="))),
                "vision-message-1",
                true,
                "vision_summary",
                Set.of(),
                "",
                "",
                List.of(),
                "",
                "",
                "",
                List.of(),
                override,
                null,
                null,
                AiCapability.VISION
        );
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static List<String> throwableMessages(Throwable error) {
        List<String> messages = new ArrayList<>();
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor.getMessage() != null) {
                messages.add(cursor.getMessage());
            }
            if (cursor.getCause() == cursor) {
                break;
            }
            cursor = cursor.getCause();
        }
        return messages;
    }
}
