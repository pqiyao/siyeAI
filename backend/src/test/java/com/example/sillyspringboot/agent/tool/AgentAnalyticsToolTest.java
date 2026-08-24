package com.example.sillyspringboot.agent.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAnalyticsToolTest {

    @Test
    void proposesProviderReviewForLowSuccessRateWithoutMakingItExecutable() {
        List<Map<String, Object>> actions = AgentAnalyticsTool.proposedActions(
                List.of("模型生成成功率低于 95%：0.8"), 0.8d, 0L);

        assertEquals("REVIEW_PROVIDER_HEALTH", actions.get(0).get("actionKey"));
        assertEquals("PROPOSED", actions.get(0).get("status"));
        assertFalse((Boolean) actions.get(0).get("executable"));
        assertFalse((Boolean) actions.get(0).get("approvalRequired"));
    }

    @Test
    void marksPricingConfigurationAsApprovalRequired() {
        List<Map<String, Object>> actions = AgentAnalyticsTool.proposedActions(
                List.of("存在未计价生成尝试：3"), 0.99d, 3L);

        Map<String, Object> pricing = actions.stream()
                .filter(item -> "CONFIGURE_MODEL_PRICING".equals(item.get("actionKey")))
                .findFirst().orElseThrow();
        assertTrue((Boolean) pricing.get("approvalRequired"));
        assertFalse((Boolean) pricing.get("executable"));
    }

    @Test
    void emitsObservationProposalWhenNoThresholdSignalExists() {
        List<Map<String, Object>> actions = AgentAnalyticsTool.proposedActions(List.of(), 0.99d, 0L);

        assertEquals(1, actions.size());
        assertEquals("CONTINUE_OBSERVATION", actions.get(0).get("actionKey"));
    }
}
