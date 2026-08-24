package com.example.sillyspringboot.agent.tool;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** DSH evaluation bridge backed by persisted deterministic Agent facts. */
@Component
public class AgentDshEvaluateTool {
    private final AgentEvaluationTool evaluationTool;

    public AgentDshEvaluateTool(AgentEvaluationTool evaluationTool) {
        this.evaluationTool = evaluationTool;
    }

    public Map<String, Object> evaluate(Map<String, Object> task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operationId", "dsh-op-" + UUID.randomUUID());
        result.put("capability", "dsh.evaluate.run");
        result.put("status", "SUCCEEDED");
        result.put("evaluation", evaluationTool.evaluate(task));
        return result;
    }
}
