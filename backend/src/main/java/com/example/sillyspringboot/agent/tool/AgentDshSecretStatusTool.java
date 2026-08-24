package com.example.sillyspringboot.agent.tool;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import java.time.LocalDateTime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Reports credential readiness without exposing secret values to DSH or Agent events. */
@Component
public class AgentDshSecretStatusTool {
    private final String apiToken;
    private final String webSearchToken;
    private final String mcpToken;
    private final String codeRunnerImage;
    private final AgentAdminMapper mapper;

    public AgentDshSecretStatusTool(
            @Value("${app.agent.api.auth-token:}") String apiToken,
            @Value("${app.agent.web-search.auth-token:}") String webSearchToken,
            @Value("${app.agent.mcp.auth-token:}") String mcpToken,
            @Value("${app.agent.code-runner.image:}") String codeRunnerImage,
            AgentAdminMapper mapper
    ) {
        this.apiToken = apiToken == null ? "" : apiToken.trim();
        this.webSearchToken = webSearchToken == null ? "" : webSearchToken.trim();
        this.mcpToken = mcpToken == null ? "" : mcpToken.trim();
        this.codeRunnerImage = codeRunnerImage == null ? "" : codeRunnerImage.trim();
        this.mapper = mapper;
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operationId", "dsh-op-" + UUID.randomUUID());
        result.put("capability", "dsh.secret.status");
        result.put("status", "SUCCEEDED");
        result.put("credentialsConfigured", Map.of(
                "api", !apiToken.isBlank(),
                "webSearch", !webSearchToken.isBlank(),
                "mcp", !mcpToken.isBlank(),
                "codeRunner", !codeRunnerImage.isBlank()
        ));
        result.put("secretValuesExposed", false);
        result.put("mode", "environment-injected-readiness-only");
        return result;
    }

    public Map<String, Object> lease(Map<String, Object> step, Map<String, Object> task) {
        Map<String, Object> input = step != null && step.get("inputJson") instanceof Map<?, ?> values ? copy(values) : Map.of();
        String provider = text(input.get("provider"));
        if (provider.isBlank() || provider.length() > 80) throw new IllegalArgumentException("secret lease provider 无效");
        String tenant = text(task == null ? null : task.getOrDefault("tenantKey", "platform"));
        String leaseId = "lease-" + UUID.randomUUID();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("leaseId", leaseId); row.put("tenantKey", tenant); row.put("provider", provider); row.put("expiresAt", expiresAt);
        mapper.insertSecretLease(row);
        return Map.of("operationId", "dsh-op-" + UUID.randomUUID(), "capability", "dsh.secret.lease", "status", "SUCCEEDED", "leaseId", leaseId, "provider", provider, "expiresAt", expiresAt.toString(), "secretValuesExposed", false);
    }

    private static Map<String, Object> copy(Map<?, ?> values) { Map<String, Object> result = new LinkedHashMap<>(); values.forEach((key, value) -> result.put(String.valueOf(key), value)); return result; }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
