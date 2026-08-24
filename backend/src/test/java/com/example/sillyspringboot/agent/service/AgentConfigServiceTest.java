package com.example.sillyspringboot.agent.service;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AgentConfigServiceTest {

    @Test
    void rejectsWorkflowCycleBeforePersisting() {
        AgentAdminMapper mapper = mock(AgentAdminMapper.class);
        AgentConfigService service = new AgentConfigService(mapper, new ObjectMapper());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("workflowKey", "cycle-check");
        body.put("displayName", "Cycle check");
        body.put("taskType", "GENERAL");
        body.put("allowedRolesJson", "[\"Analyst\"]");
        body.put("allowedToolsJson", "[\"planner.analyze\"]");
        body.put("maxSteps", 2);
        body.put("maxRuntimeSeconds", 60);
        body.put("maxTokens", 1000);
        body.put("maxCost", "1");
        body.put("failurePolicy", "STOP");
        body.put("stepsJson", "["
                + "{\"key\":\"a\",\"agentRole\":\"Analyst\",\"toolName\":\"planner.analyze\",\"dependsOn\":[\"b\"]},"
                + "{\"key\":\"b\",\"agentRole\":\"Analyst\",\"toolName\":\"planner.analyze\",\"dependsOn\":[\"a\"]}"
                + "]");

        assertThrows(IllegalArgumentException.class, () -> service.createWorkflow(body, 7L));
        verifyNoInteractions(mapper);
    }
}
