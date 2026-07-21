package com.example.sillyspringboot.ops.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MockImageGenerationService {

    private final H5EntitlementService entitlementService;

    public MockImageGenerationService(H5EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @Transactional
    public Map<String, Object> generate(String clientUid, String prompt, int count) {
        String safePrompt = prompt == null ? "" : prompt.trim();
        if (safePrompt.isBlank()) {
            throw new IllegalArgumentException("prompt required");
        }
        int safeCount = Math.max(1, Math.min(4, count));
        entitlementService.guardImage(clientUid, safeCount);
        String imageUrl = buildImageDataUrl(safePrompt);

        Map<String, Object> image = new LinkedHashMap<>();
        image.put("url", imageUrl);
        image.put("prompt", safePrompt);
        image.put("width", 768);
        image.put("height", 1024);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", "mock");
        data.put("usedCount", 0);
        data.put("remainingCount", 0);
        data.put("images", List.of(image));
        data.put("message", "当前为 mock 生图链路");
        return data;
    }

    private String buildImageDataUrl(String prompt) {
        String snippet = prompt.length() > 40 ? prompt.substring(0, 40) + "..." : prompt;
        String escaped = escapeXml(snippet);
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" width="768" height="1024" viewBox="0 0 768 1024">
                  <defs>
                    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
                      <stop offset="0%" stop-color="#1c1d39"/>
                      <stop offset="55%" stop-color="#34215c"/>
                      <stop offset="100%" stop-color="#ff4fa2"/>
                    </linearGradient>
                  </defs>
                  <rect width="768" height="1024" fill="url(#bg)"/>
                  <circle cx="612" cy="214" r="140" fill="rgba(255,255,255,0.08)"/>
                  <circle cx="154" cy="792" r="200" fill="rgba(255,255,255,0.06)"/>
                  <rect x="56" y="640" width="656" height="256" rx="28" fill="rgba(8,10,28,0.58)" stroke="rgba(255,255,255,0.18)"/>
                  <text x="56" y="116" fill="#ffffff" font-size="34" font-family="Segoe UI, Microsoft YaHei, sans-serif">Mock Image</text>
                  <text x="56" y="698" fill="#ffffff" font-size="28" font-family="Segoe UI, Microsoft YaHei, sans-serif">Prompt</text>
                  <text x="56" y="744" fill="#f9d6ff" font-size="22" font-family="Segoe UI, Microsoft YaHei, sans-serif">__PROMPT__</text>
                  <text x="56" y="840" fill="#c4c8ff" font-size="18" font-family="Segoe UI, Microsoft YaHei, sans-serif">SillySpringboot image pipeline</text>
                </svg>
                """.replace("__PROMPT__", escaped);
        String base64 = Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + base64;
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
