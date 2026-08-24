package com.example.sillyspringboot.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** DSH-facing Browser bridge; the existing whitelist and redaction rules remain authoritative. */
@Component
public class AgentDshBrowserTool {
    private final AgentBrowserOpenTool browserOpenTool;
    private final ObjectMapper objectMapper;

    public AgentDshBrowserTool(AgentBrowserOpenTool browserOpenTool, ObjectMapper objectMapper) {
        this.browserOpenTool = browserOpenTool;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> open(Map<String, Object> step) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operationId", "dsh-op-" + UUID.randomUUID());
        result.put("capability", "dsh.browser.open");
        try {
            result.put("browserResponse", browserOpenTool.read(step));
            result.put("status", "SUCCEEDED");
        } catch (RuntimeException ex) {
            result.put("status", "FAILED");
            result.put("error", ex.getMessage());
            throw ex;
        }
        return result;
    }
}
