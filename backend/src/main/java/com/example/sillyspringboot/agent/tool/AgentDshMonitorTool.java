package com.example.sillyspringboot.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** DSH monitoring bridge backed by the existing read-only health snapshot. */
@Component
public class AgentDshMonitorTool {
    private final AgentSystemInspectionTool inspectionTool;

    public AgentDshMonitorTool(AgentSystemInspectionTool inspectionTool) {
        this.inspectionTool = inspectionTool;
    }

    public Map<String, Object> snapshot(Map<String, Object> step, Map<String, Object> task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operationId", "dsh-op-" + UUID.randomUUID());
        result.put("capability", "dsh.monitor.snapshot");
        result.put("status", "SUCCEEDED");
        result.put("snapshot", inspectionTool.inspect(step, task));
        return result;
    }
}
