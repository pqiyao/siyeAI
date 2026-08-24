package com.example.sillyspringboot.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** DSH-facing DevOps inspection bridge; it never executes build or deployment commands. */
@Component
public class AgentDshDevopsInspectTool {
    private final AgentSystemInspectionTool inspectionTool;

    public AgentDshDevopsInspectTool(AgentSystemInspectionTool inspectionTool) {
        this.inspectionTool = inspectionTool;
    }

    public Map<String, Object> inspect(Map<String, Object> step, Map<String, Object> task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operationId", "dsh-op-" + UUID.randomUUID());
        result.put("capability", "dsh.devops.inspect");
        result.put("status", "SUCCEEDED");
        result.put("inspection", inspectionTool.inspect(step, task));
        return result;
    }
}
