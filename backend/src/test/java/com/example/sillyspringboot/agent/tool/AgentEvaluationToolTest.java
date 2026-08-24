package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentEvaluationToolTest {

    @Test
    void countsDegradedSuccessfulStepsInEvaluation() {
        AgentAdminMapper mapper = mock(AgentAdminMapper.class);
        when(mapper.listSteps(9L)).thenReturn(List.of(Map.of(
                "id", 1L,
                "status", "SUCCEEDED",
                "outputJson", "{\"degraded\":true}"
        )));
        when(mapper.listArtifacts(9L)).thenReturn(List.of());
        when(mapper.summarizeToolUsage(9L)).thenReturn(Map.of("unpricedCalls", 0L));
        AgentEvaluationTool tool = new AgentEvaluationTool(mapper, new ObjectMapper());

        Map<String, Object> result = tool.evaluate(Map.of("taskId", 9L, "status", "SUCCEEDED"));

        assertEquals(1L, result.get("degradedSteps"));
        assertEquals(0L, result.get("degradedArtifacts"));
    }
}
