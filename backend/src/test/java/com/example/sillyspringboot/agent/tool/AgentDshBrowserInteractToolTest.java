package com.example.sillyspringboot.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentDshBrowserInteractToolTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void carriesOpenSessionIntoFollowingAction() throws Exception {
        List<Map<String, Object>> requests = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/browser", exchange -> {
            Map<String, Object> request = objectMapper.readValue(
                    exchange.getRequestBody(), new TypeReference<>() { });
            requests.add(request);
            String body = objectMapper.writeValueAsString(Map.of(
                    "ok", true,
                    "result", Map.of("sessionId", "browser-test-session")
            ));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        AgentDshBrowserInteractTool tool = new AgentDshBrowserInteractTool(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/browser",
                "",
                objectMapper
        );
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("objective", "打开 http://127.0.0.1:8091 并提取页面");

        Map<String, Object> openResult = tool.interact(Map.of("inputJson", "{\"action\":\"open\"}"), task);
        task.put("completedSteps", List.of(Map.of(
                "toolName", "dsh.browser.interact",
                "outputJson", objectMapper.writeValueAsString(openResult)
        )));
        tool.interact(Map.of("inputJson", "{\"action\":\"extract\"}"), task);

        assertEquals("http://127.0.0.1:8091", requests.get(0).get("url"));
        assertEquals("browser-test-session", openResult.get("sessionId"));
        assertEquals("browser-test-session", requests.get(1).get("sessionId"));
    }

    @Test
    void mergesInputAndClickConstraintsIntoActions() throws Exception {
        List<Map<String, Object>> requests = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/browser", exchange -> {
            Map<String, Object> request = objectMapper.readValue(exchange.getRequestBody(), new TypeReference<>() { });
            requests.add(request);
            byte[] bytes = objectMapper.writeValueAsBytes(Map.of("ok", true, "result", Map.of("sessionId", "browser-test-session")));
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        AgentDshBrowserInteractTool tool = new AgentDshBrowserInteractTool(true,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/browser", "", objectMapper);
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("constraintsJson", "{\"browser\":{\"inputSelector\":\"#query\",\"inputValue\":\"safe text\",\"clickSelector\":\"#submit\"}}");
        task.put("completedSteps", List.of(Map.of("toolName", "dsh.browser.interact", "outputJson", "{\"sessionId\":\"browser-test-session\"}")));
        tool.interact(Map.of("inputJson", "{\"action\":\"input\"}"), task);
        tool.interact(Map.of("inputJson", "{\"action\":\"click\"}"), task);
        assertEquals("#query", requests.get(0).get("selector"));
        assertEquals("safe text", requests.get(0).get("value"));
        assertEquals("#submit", requests.get(1).get("selector"));
    }
}
