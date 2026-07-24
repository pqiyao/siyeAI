package com.example.sillyspringboot.integration.sillytavern;

import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class StClientResponseParsingTest {

    private StClient client;
    private HttpServer server;
    private final AtomicReference<String> regexResponse = new AtomicReference<>("{\"mes\":\"clean\"}");
    private final AtomicInteger regexStatus = new AtomicInteger(200);
    private final AtomicReference<String> appendRequest = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/csrf-token", exchange -> respond(exchange, 200, "{\"token\":\"disabled\"}"));
        server.createContext(StApiPaths.RUNTIME_CHAT_APPLY_OUTPUT_REGEX,
                exchange -> respond(exchange, regexStatus.get(), regexResponse.get()));
        server.createContext(StApiPaths.RUNTIME_CHAT_APPEND, exchange -> {
            appendRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"ok\":true,\"mes\":\"\"}");
        });
        server.start();

        SillyTavernProperties properties = new SillyTavernProperties();
        properties.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        properties.setConnectTimeout(Duration.ofSeconds(1));
        properties.setReadTimeout(Duration.ofSeconds(1));
        client = new StClient(properties, null, null, null, mock(StRuntimeChatWriteCapture.class));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void parsesOpenAiStreamingContent() {
        StClient.ParsedChunk chunk = client.parseChunk(
                "{\"choices\":[{\"delta\":{\"content\":\"hello\"},\"finish_reason\":null}]}"
        );

        assertEquals("hello", chunk.delta());
        assertFalse(chunk.done());
    }

    @Test
    void parsesOpenAiNonStreamingContent() {
        StClient.ParsedChunk chunk = client.parseChunk(
                "{\"choices\":[{\"message\":{\"content\":\"hello\"},\"finish_reason\":\"stop\"}]}"
        );

        assertEquals("hello", chunk.delta());
        assertTrue(chunk.done());
    }

    @Test
    void parsesGeminiCandidateContent() {
        StClient.ParsedChunk chunk = client.parseChunk(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hello\"}]},\"finishReason\":\"STOP\"}]}"
        );

        assertEquals("hello", chunk.delta());
        assertTrue(chunk.done());
    }

    @Test
    void surfacesSanitizedUpstreamErrorsInsteadOfRenderingThemAsChatText() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> client.parseChunk("{\"error\":{\"message\":\"private-provider-detail\"}}")
        );

        assertEquals("upstream error", error.getMessage());
        assertFalse(error.getMessage().contains("private-provider-detail"));
    }

    @Test
    void outputRegexResponsePreservesAValidEmptyCanonicalString() {
        regexResponse.set("{\"mes\":\"\"}");

        assertEquals("", applyOutputRegex());
    }

    @Test
    void outputRegexResponsePreservesTextUntilTheServiceCanonicalizesIt() {
        regexResponse.set("{\"mes\":\"  clean reply  \"}");

        assertEquals("  clean reply  ", applyOutputRegex());
    }

    @Test
    void outputRegexResponseRejectsMissingNullAndNonTextualCanonicalValues() {
        for (String invalid : new String[]{"{}", "{\"mes\":null}", "{\"mes\":{}}"}) {
            regexResponse.set(invalid);
            assertThrows(StUnavailableException.class, this::applyOutputRegex);
        }
    }

    @Test
    void outputRegexResponseSurfacesHttpFailures() {
        regexStatus.set(500);
        regexResponse.set("{\"error\":\"failed\"}");

        assertThrows(StUnavailableException.class, this::applyOutputRegex);
    }

    @Test
    void finalizedEmptyAndWhitespaceCanonicalValuesReachTheRuntimeWriteUnchanged() throws Exception {
        DefaultStAdapter adapter = new DefaultStAdapter(client, null, null);
        ChatGenerateRequest request = runtimeRequest("root:501");

        adapter.appendAssistantMessage(request, "", true);
        JsonNode emptyBody = new ObjectMapper().readTree(appendRequest.get());
        assertEquals("", emptyBody.path("mes").textValue());
        assertTrue(emptyBody.path("output_regex_applied").booleanValue());

        adapter.appendAssistantMessage(request, "  canonical reply  ", true);
        JsonNode whitespaceBody = new ObjectMapper().readTree(appendRequest.get());
        assertEquals("  canonical reply  ", whitespaceBody.path("mes").textValue());
        assertTrue(whitespaceBody.path("output_regex_applied").booleanValue());
    }

    private String applyOutputRegex() {
        return client.runtimeChatApplyOutputRegex(
                "avatar.png",
                "chat.jsonl",
                "Bob",
                "Alice",
                "raw reply"
        );
    }

    private static ChatGenerateRequest runtimeRequest(String messageRef) {
        return new ChatGenerateRequest(
                42L,
                null,
                List.of(),
                "client-42",
                false,
                "sync",
                Set.of(),
                "Bob",
                "Alice",
                List.of(),
                "avatar.png",
                "chat.jsonl",
                messageRef,
                List.of(),
                null
        );
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = (body == null ? "" : body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
