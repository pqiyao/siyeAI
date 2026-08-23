package com.example.sillyspringboot.agent.executor;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.example.sillyspringboot.agent.tool.AgentBuiltinToolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentWorkerServiceTest {

    private AgentAdminMapper mapper;
    private AgentBuiltinToolExecutor toolExecutor;
    private AgentWorkerService worker;

    @BeforeEach
    void setUp() {
        mapper = mock(AgentAdminMapper.class);
        toolExecutor = mock(AgentBuiltinToolExecutor.class);
        worker = new AgentWorkerService(mapper, toolExecutor);

        when(mapper.insertEvent(anyMap())).thenReturn(1);
        when(mapper.taskRuntimeExceeded(anyLong())).thenReturn(0);
        when(mapper.taskBudgetExceeded(anyLong())).thenReturn(0);
        when(mapper.finalizeCancelledTask(anyLong())).thenReturn(0);
        when(mapper.findToolByKey(anyString())).thenReturn(Map.of(
                "toolKey", "planner.analyze",
                "enabled", 1,
                "approvalRequired", 0
        ));
        when(mapper.findTask(anyLong())).thenReturn(Map.of(
                "tokenUsage", 0L,
                "costAmount", BigDecimal.ZERO
        ));
        doAnswer(invocation -> {
            Map<String, Object> call = invocation.getArgument(0);
            call.put("id", 91L);
            return 1;
        }).when(mapper).insertToolCall(anyMap());
    }

    @Test
    void successfulStepPromotesNextStepWithoutCancellingPendingSteps() {
        Map<String, Object> candidate = candidate();
        when(mapper.listReadySteps(5)).thenReturn(List.of(candidate));
        when(mapper.claimStep(eq(11L), anyString(), any())).thenReturn(1);
        when(mapper.updateTaskStatus(eq(7L), anyList(), eq("RUNNING"), eq(false), eq(0), eq(false)))
                .thenReturn(1);
        when(mapper.listSteps(7L)).thenReturn(List.of());
        when(toolExecutor.execute(eq(candidate), anyMap())).thenReturn(
                new AgentBuiltinToolExecutor.ToolResult("{\"ok\":true}", "done")
        );
        when(mapper.completeStep(eq(11L), anyString(), anyString(), eq("done"))).thenReturn(1);
        when(mapper.findNextBlockedStep(7L)).thenReturn(Map.of("stepId", 12L));
        when(mapper.promoteStep(12L)).thenReturn(1);

        worker.dispatchReadySteps();

        verify(mapper).promoteStep(12L);
        verify(mapper).tryCompleteTask(7L);
        verify(mapper, never()).cancelPendingSteps(7L);
    }

    @Test
    void failedStepDoesNotCancelBlockedStepsBeforeManualRetry() {
        Map<String, Object> candidate = candidate();
        when(mapper.listReadySteps(5)).thenReturn(List.of(candidate));
        when(mapper.claimStep(eq(11L), anyString(), any())).thenReturn(1);
        when(mapper.updateTaskStatus(eq(7L), anyList(), eq("RUNNING"), eq(false), eq(0), eq(false)))
                .thenReturn(1);
        when(mapper.listSteps(7L)).thenReturn(List.of());
        when(toolExecutor.execute(eq(candidate), anyMap())).thenThrow(new IllegalStateException("tool failed"));

        worker.dispatchReadySteps();

        verify(mapper).failStep(eq(11L), anyString(), eq("TOOL_EXECUTION_FAILED"), eq("tool failed"));
        verify(mapper).failTask(7L, "TOOL_EXECUTION_FAILED", "tool failed");
        verify(mapper, never()).cancelPendingSteps(7L);
    }

    private static Map<String, Object> candidate() {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("stepId", 11L);
        candidate.put("taskId", 7L);
        candidate.put("stepName", "分析任务");
        candidate.put("toolName", "planner.analyze");
        candidate.put("taskObjective", "验证 Agent Worker");
        candidate.put("attemptCount", 0);
        candidate.put("tokenUsage", 0L);
        candidate.put("costAmount", BigDecimal.ZERO);
        return candidate;
    }
}
