package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.ops.config.AppImageGenerationProperties;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NovelAiImageGenerationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsV4RequestAndExtractsImageFromZip() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        byte[] zip = zipWithImage();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ai/generate-image", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            send(exchange, 200, zip);
        });
        server.start();

        AppImageGenerationProperties properties = configuredProperties();
        AppImageGenerationProperties.NovelAi config = properties.getNovelAi();
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        config.setSeed(123456L);
        config.setSteps(31);
        config.setScale(7.5d);
        config.setVarietyBoost(true);

        NovelAiImageGenerationService service = new NovelAiImageGenerationService(properties, objectMapper);
        Map<String, Object> result = service.generate("client", Map.of(
                "prompt", "1girl, red hair",
                "userPrompt", "red hair",
                "negativePrompt", "low quality",
                "aspectRatio", "landscape",
                "referencePolicy", "balanced"
        ));

        assertThat(authorization.get()).isEqualTo("Bearer test-token");
        JsonNode body = objectMapper.readTree(requestBody.get());
        assertThat(body.path("action").asText()).isEqualTo("generate");
        assertThat(body.path("input").asText()).isEqualTo("1girl, red hair");
        assertThat(body.path("model").asText()).isEqualTo("nai-diffusion-4-5-full");
        JsonNode parameters = body.path("parameters");
        assertThat(parameters.path("width").asInt()).isEqualTo(1216);
        assertThat(parameters.path("height").asInt()).isEqualTo(832);
        assertThat(parameters.path("seed").asLong()).isEqualTo(123456L);
        assertThat(parameters.path("steps").asInt()).isEqualTo(31);
        assertThat(parameters.path("scale").asDouble()).isEqualTo(7.5d);
        assertThat(parameters.path("negative_prompt").asText()).isEqualTo("low quality");
        assertThat(parameters.path("v4_prompt").path("caption").path("base_caption").asText())
                .isEqualTo("1girl, red hair");
        assertThat(parameters.path("v4_negative_prompt").path("caption").path("base_caption").asText())
                .isEqualTo("low quality");
        assertThat(parameters.path("skip_cfg_above_sigma").asDouble()).isEqualTo(58.0d);

        assertThat(result).containsEntry("providerSource", "novelai")
                .containsEntry("modelName", "nai-diffusion-4-5-full");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> images = (List<Map<String, Object>>) result.get("images");
        assertThat(images).hasSize(1);
        assertThat(images.get(0).get("url").toString()).startsWith("data:image/png;base64,");
        assertThat(images.get(0)).containsEntry("width", 1216).containsEntry("height", 832);
    }

    @Test
    void missingTokenFailsBeforeNetworkCall() {
        AppImageGenerationProperties properties = new AppImageGenerationProperties();
        NovelAiImageGenerationService service = new NovelAiImageGenerationService(properties, objectMapper);

        assertThatThrownBy(() -> service.generate("client", Map.of("prompt", "test")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未配置 Token");
    }

    @Test
    void referenceOnlyStrongModeIsRejectedWithoutFallback() {
        AppImageGenerationProperties properties = configuredProperties();
        NovelAiImageGenerationService service = new NovelAiImageGenerationService(properties, objectMapper);

        assertThatThrownBy(() -> service.generate("client", Map.of(
                "prompt", "test",
                "referencePolicy", "reference_only"
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("强一致性");
    }

    private AppImageGenerationProperties configuredProperties() {
        AppImageGenerationProperties properties = new AppImageGenerationProperties();
        properties.getNovelAi().setToken("test-token");
        properties.getNovelAi().setRequestTimeout(Duration.ofSeconds(5));
        return properties;
    }

    private static byte[] zipWithImage() throws IOException {
        byte[] png = new byte[] {
                (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 0
        };
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("image_0.png"));
            zip.write(png);
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
