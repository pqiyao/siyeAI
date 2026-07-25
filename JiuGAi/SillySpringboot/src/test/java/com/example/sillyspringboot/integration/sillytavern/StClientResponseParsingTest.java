package com.example.sillyspringboot.integration.sillytavern;

import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.StCharacterImportRequest;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    private final AtomicReference<String> exportRequest = new AtomicReference<>();
    private final AtomicReference<String> exportMethod = new AtomicReference<>();
    private final AtomicReference<String> exportContentType = new AtomicReference<>();
    private final AtomicReference<byte[]> importRequest = new AtomicReference<>();
    private final AtomicReference<String> importMethod = new AtomicReference<>();
    private final AtomicReference<String> importContentType = new AtomicReference<>();
    private final byte[] exportedCharacterPng = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x01, 0x02, (byte) 0xff
    };

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
        server.createContext(StApiPaths.CHARACTERS_EXPORT, exchange -> {
            exportMethod.set(exchange.getRequestMethod());
            exportContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            exportRequest.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "image/png", exportedCharacterPng);
        });
        server.createContext(StApiPaths.CHARACTERS_IMPORT, exchange -> {
            importMethod.set(exchange.getRequestMethod());
            importContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            importRequest.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 200, "{\"file_name\":\"system_copy_1_uuid\"}");
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

    @Test
    void characterPngExportUsesNativeStEndpointAndPreservesBinaryPayload() throws Exception {
        byte[] exported = client.exportCharacterPng("source-avatar.png");

        assertArrayEquals(exportedCharacterPng, exported);
        assertEquals("POST", exportMethod.get());
        assertTrue(exportContentType.get().startsWith("application/json"));
        JsonNode request = new ObjectMapper().readTree(exportRequest.get());
        assertEquals("png", request.path("format").textValue());
        assertEquals("source-avatar.png", request.path("avatar_url").textValue());
    }

    @Test
    void characterPngImportUsesNativeStMultipartContractAndPreservesExportedBytes() {
        Object result = client.importCharacterPng(
                exportedCharacterPng,
                "source-avatar.png",
                new StCharacterImportRequest("png", "system_copy_1_uuid.png")
        );

        assertEquals("POST", importMethod.get());
        assertTrue(importContentType.get().startsWith("multipart/form-data;boundary="));
        String multipart = new String(importRequest.get(), StandardCharsets.ISO_8859_1);
        assertTrue(multipart.contains("name=\"file_type\""));
        assertTrue(multipart.contains("name=\"preserved_name\""));
        assertTrue(multipart.contains("system_copy_1_uuid.png"));
        assertTrue(multipart.contains("name=\"avatar\""));
        assertTrue(multipart.contains("filename=\"source-avatar.png\""));
        assertTrue(multipart.contains(new String(exportedCharacterPng, StandardCharsets.ISO_8859_1)));
        assertEquals("system_copy_1_uuid.png", ((Map<?, ?>) result).get("file_name"));
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
        respond(exchange, status, "application/json; charset=utf-8", bytes);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
