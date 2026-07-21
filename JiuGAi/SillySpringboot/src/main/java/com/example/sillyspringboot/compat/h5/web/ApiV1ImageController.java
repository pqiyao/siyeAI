package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.ops.service.MockImageGenerationService;
import com.example.sillyspringboot.ops.service.ImageGenerationFacade;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/image")
public class ApiV1ImageController {

    private final MockImageGenerationService mockImageGenerationService;
    private final ImageGenerationFacade imageGenerationFacade;

    public ApiV1ImageController(
            MockImageGenerationService mockImageGenerationService,
            ImageGenerationFacade imageGenerationFacade
    ) {
        this.mockImageGenerationService = mockImageGenerationService;
        this.imageGenerationFacade = imageGenerationFacade;
    }

    @PostMapping("/generate")
    public ApiV1Result<Map<String, Object>> generate(
            @RequestBody(required = false) Map<String, Object> payload,
            HttpServletRequest request
    ) {
        Map<String, Object> safePayload = payload == null ? new LinkedHashMap<>() : new LinkedHashMap<>(payload);
        String clientUid = str(safePayload.get("clientUid"));
        String prompt = str(safePayload.get("prompt"));
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "prompt 不能为空");
        }
        safePayload.put("_requestOrigin", resolveRequestOrigin(request));
        return ApiV1Result.ok(imageGenerationFacade.generate(clientUid, safePayload));
    }

    @PostMapping("/generate/mock")
    public ApiV1Result<Map<String, Object>> mockGenerate(@RequestBody(required = false) Map<String, Object> payload) {
        String clientUid = payload == null ? null : str(payload.get("clientUid"));
        String prompt = payload == null ? null : str(payload.get("prompt"));
        int count = intVal(payload == null ? null : payload.get("count"), 1);
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "prompt 不能为空");
        }
        return ApiV1Result.ok(mockImageGenerationService.generate(clientUid, prompt, count));
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static int intVal(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String resolveRequestOrigin(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwardedOrigin = parseForwardedOrigin(request.getHeader("Forwarded"));
        if (forwardedOrigin != null && !forwardedOrigin.isBlank()) {
            return forwardedOrigin;
        }
        String proxyOrigin = buildOrigin(
                request.getHeader("X-Forwarded-Proto"),
                request.getHeader("X-Forwarded-Host"),
                request.getHeader("X-Forwarded-Port")
        );
        if (proxyOrigin != null && !proxyOrigin.isBlank()) {
            return proxyOrigin;
        }
        return buildOrigin(request.getScheme(), request.getServerName(), String.valueOf(request.getServerPort()));
    }

    private static String parseForwardedOrigin(String forwarded) {
        String firstHop = firstHeaderValue(forwarded);
        if (firstHop == null || firstHop.isBlank()) {
            return "";
        }
        String proto = "";
        String host = "";
        String[] parts = firstHop.split(";");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = str(kv[0]);
            if (key == null || key.isBlank()) {
                continue;
            }
            key = key.toLowerCase();
            String value = stripQuotes(str(kv[1]));
            if ("proto".equals(key)) {
                proto = value;
            } else if ("host".equals(key)) {
                host = value;
            }
        }
        return buildOrigin(proto, host, null);
    }

    private static String buildOrigin(String scheme, String host, String port) {
        String safeScheme = normalizeScheme(scheme);
        String safeHost = firstHeaderValue(host);
        if (safeScheme.isBlank() || safeHost == null || safeHost.isBlank()) {
            return "";
        }
        try {
            URI hostUri = URI.create(safeScheme + "://" + safeHost.trim());
            String resolvedHost = str(hostUri.getHost());
            int resolvedPort = normalizePort(hostUri.getPort(), safeScheme);
            int explicitPort = parsePort(port);
            if (explicitPort > 0) {
                resolvedPort = normalizePort(explicitPort, safeScheme);
            }
            if (resolvedHost == null || resolvedHost.isBlank()) {
                return "";
            }
            boolean defaultPort = ("http".equalsIgnoreCase(safeScheme) && resolvedPort == 80)
                    || ("https".equalsIgnoreCase(safeScheme) && resolvedPort == 443);
            return safeScheme + "://" + resolvedHost + (defaultPort || resolvedPort <= 0 ? "" : ":" + resolvedPort);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String normalizeScheme(String scheme) {
        String safeScheme = str(scheme);
        if (safeScheme == null || safeScheme.isBlank()) {
            return "";
        }
        safeScheme = safeScheme.toLowerCase();
        if ("http".equals(safeScheme) || "https".equals(safeScheme)) {
            return safeScheme;
        }
        return "";
    }

    private static String firstHeaderValue(String raw) {
        if (raw == null) {
            return "";
        }
        String[] values = raw.split(",", 2);
        return str(values.length > 0 ? values[0] : raw);
    }

    private static String stripQuotes(String value) {
        String safe = str(value);
        if (safe == null || safe.length() < 2) {
            return safe;
        }
        if ((safe.startsWith("\"") && safe.endsWith("\"")) || (safe.startsWith("'") && safe.endsWith("'"))) {
            return safe.substring(1, safe.length() - 1).trim();
        }
        return safe;
    }

    private static int parsePort(String value) {
        String safe = str(value);
        if (safe == null || safe.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(safe);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static int normalizePort(int port, String scheme) {
        if (port > 0) {
            return port;
        }
        if ("http".equalsIgnoreCase(scheme)) {
            return 80;
        }
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return -1;
    }
}
