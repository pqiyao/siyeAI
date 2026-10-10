package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.RuoYiAdminAccessService;
import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.example.sillyspringboot.agent.service.AgentAdminQueryService;
import com.example.sillyspringboot.agent.service.AgentTenantService;
import com.example.sillyspringboot.agent.service.DeepSeekHarnessService;
import com.example.sillyspringboot.agent.service.AgentTaskService;
import com.example.sillyspringboot.agent.service.AgentPlanService;
import com.example.sillyspringboot.agent.service.PlatformBusinessQueryService;
import com.example.sillyspringboot.agent.service.PlatformExtendedQueryService;
import com.example.sillyspringboot.agent.tool.AgentPlatformQueryTool;
import com.example.sillyspringboot.admin.service.AdminJiugaiCharacterService;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.StWorldbookCatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AdminXiaoyeControllerTest {
    @Test
    @SuppressWarnings("unchecked")
    void platformSearchUsesUnifiedQueryToolForNewResources() {
        AgentAdminMapper mapper = mock(AgentAdminMapper.class);
        when(mapper.findXiaoyeConversation("session-query")).thenReturn(Map.of(
                "conversationId", "session-query", "adminId", 7L, "tenantKey", "platform", "status", "ACTIVE"));
        AgentPlatformQueryTool queryTool = mock(AgentPlatformQueryTool.class);
        when(queryTool.search(anyMap(), anyMap())).thenReturn(Map.of(
                "resource", "ai_routes", "pageNum", 1, "pageSize", 20, "total", 1,
                "rows", List.of(Map.of("routeKey", "chat.default"))));
        AdminXiaoyeController controller = new AdminXiaoyeController(
                mock(DeepSeekHarnessService.class), mock(AgentAdminQueryService.class),
                mock(AdminJiugaiCharacterService.class), mock(StAdapter.class),
                mock(StWorldbookCatalogService.class), new ObjectMapper(), mapper,
                mock(AgentTenantService.class), mock(AgentTaskService.class), mock(AgentPlanService.class),
                mock(PlatformBusinessQueryService.class), mock(PlatformExtendedQueryService.class), queryTool);

        Map<String, Object> response = controller.platformSearch("session-query", Map.of(
                "resource", "AI_ROUTES", "filters", Map.of("capability", "CHAT")), platformAdminRequest(7L));

        assertEquals(200, response.get("code"));
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        assertEquals("ai_routes", data.get("resource"));
        verify(queryTool).search(argThat(step -> String.valueOf(step.get("inputJson")).contains("ai_routes")),
                argThat(task -> Long.valueOf(7L).equals(task.get("createdBy"))
                        && "platform".equals(task.get("tenantKey"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void platformSearchQueuesAuthorizedResultForModelSummary() {
        DeepSeekHarnessService harness = mock(DeepSeekHarnessService.class);
        AgentAdminMapper mapper = mock(AgentAdminMapper.class);
        when(mapper.findXiaoyeConversation("session-summary")).thenReturn(Map.of(
                "conversationId", "session-summary", "adminId", 7L, "tenantKey", "platform", "status", "ACTIVE"));
        AgentPlatformQueryTool queryTool = mock(AgentPlatformQueryTool.class);
        when(queryTool.search(anyMap(), anyMap())).thenReturn(Map.of(
                "resource", "tasks", "total", 1,
                "rows", List.of(Map.of("status", "RUNNING", "title", "测试任务"))));
        AdminXiaoyeController controller = new AdminXiaoyeController(
                harness, mock(AgentAdminQueryService.class), mock(AdminJiugaiCharacterService.class),
                mock(StAdapter.class), mock(StWorldbookCatalogService.class), new ObjectMapper(), mapper,
                mock(AgentTenantService.class), mock(AgentTaskService.class), mock(AgentPlanService.class),
                mock(PlatformBusinessQueryService.class), mock(PlatformExtendedQueryService.class), queryTool);

        Map<String, Object> response = controller.platformSearch("session-summary", Map.of(
                "resource", "tasks", "question", "当前有哪些运行中的任务？"), platformAdminRequest(7L));

        assertEquals(200, response.get("code"));
        assertEquals(true, ((Map<String, Object>) response.get("data")).get("summaryQueued"));
        verify(harness).writeRpc(eq("session.prompt"), argThat(payload ->
                "session-summary".equals(payload.get("sessionId"))
                        && String.valueOf(payload.get("content")).contains("测试任务")
                        && String.valueOf(payload.get("content")).contains("当前有哪些运行中的任务")));
    }

    @Test
    void worldbookWriteRequiresExplicitConfirmation() {
        StAdapter stAdapter = mock(StAdapter.class);
        AdminXiaoyeController controller = controller(stAdapter, mock(AgentAdminMapper.class));

        Map<String, Object> result = controller.saveWorldbook(Map.of(
                "name", "吸血鬼设定",
                "entries", List.of(Map.of("key", "城堡", "content", "夜色中的古堡"))
        ), mock(HttpServletRequest.class));

        assertEquals(500, result.get("code"));
        verifyNoInteractions(stAdapter);
    }

    @Test
    void confirmedWorldbookWriteIsAudited() {
        StAdapter stAdapter = mock(StAdapter.class);
        AgentAdminMapper mapper = mock(AgentAdminMapper.class);
        when(mapper.insertConfigAudit(anyMap())).thenReturn(1);
        AdminXiaoyeController controller = controller(stAdapter, mapper);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("adminSession")).thenReturn(new RuoYiAdminAccessService.AdminSession(
                7L, "admin", "管理员", List.of("ADMIN"), List.of("agent:center:view")));

        Map<String, Object> result = controller.saveWorldbook(Map.of(
                "name", "吸血鬼设定",
                "confirmed", true,
                "entries", List.of(Map.of("key", "城堡", "content", "夜色中的古堡"))
        ), request);

        assertEquals(200, result.get("code"));
        verify(stAdapter).saveWorldbook(any());
        verify(mapper).insertConfigAudit(argThat(audit ->
                "WORLD_BOOK".equals(audit.get("configType"))
                        && "吸血鬼设定".equals(audit.get("configKey"))
                        && "ACTIVATE".equals(audit.get("action"))
                        && Long.valueOf(7L).equals(audit.get("operatorId"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void bootstrapLeavesSessionCapabilitiesEmptyUntilConversationExists() {
        DeepSeekHarnessService harness = mock(DeepSeekHarnessService.class);
        when(harness.status()).thenReturn(Map.of("state", "READY", "enabled", true));
        when(harness.readRpc("skill.list", Map.of("limit", 100))).thenReturn(Map.of("items", List.of(Map.of("name", "角色卡"))));
        when(harness.readRpc("subagent.list", Map.of("limit", 100))).thenReturn(Map.of("items", List.of(Map.of("name", "创作助手"))));
        when(harness.readRpc("llm.models", Map.of())).thenReturn(Map.of("items", List.of(Map.of("id", "deepseek-chat"))));
        when(harness.readRpc("llm.providers", Map.of())).thenReturn(Map.of("items", List.of(Map.of("id", "deepseek"))));
        when(harness.readRpc("workspace.list", Map.of())).thenReturn(Map.of("items", List.of(Map.of("path", "workspace"))));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("adminPermissions")).thenReturn(List.of("*:*:*"));

        Map<String, Object> result = controller(harness, mock(StAdapter.class), mock(AgentAdminMapper.class)).bootstrap(null, request);
        Map<String, Object> data = (Map<String, Object>) result.get("data");

        assertEquals(200, result.get("code"));
        assertEquals("READY", ((Map<String, Object>) data.get("dsh")).get("state"));
        assertEquals(List.of(), data.get("dshSkills"));
        assertEquals(List.of(), data.get("dshSubagents"));
        assertEquals("deepseek-chat", ((List<Map<String, Object>>) ((Map<String, Object>) data.get("dshModels")).get("items")).get(0).get("id"));
        assertEquals("deepseek", ((List<Map<String, Object>>) ((Map<String, Object>) data.get("dshProviders")).get("items")).get(0).get("id"));
        assertEquals("workspace", ((List<Map<String, Object>>) ((Map<String, Object>) data.get("dshWorkspace")).get("items")).get(0).get("path"));
        verify(harness).readRpc("llm.models", Map.of());
        verify(harness).readRpc("llm.providers", Map.of());
        verify(harness).readRpc("workspace.list", Map.of());
        verify(harness).status();
        verifyNoMoreInteractions(harness);
    }

    @Test
    @SuppressWarnings("unchecked")
    void bootstrapDoesNotQuerySessionCapabilitiesWithoutSession() {
        DeepSeekHarnessService harness = mock(DeepSeekHarnessService.class);
        when(harness.status()).thenReturn(Map.of("state", "READY", "enabled", true));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("adminPermissions")).thenReturn(List.of("*:*:*"));

        Map<String, Object> result = controller(harness, mock(StAdapter.class), mock(AgentAdminMapper.class)).bootstrap(null, request);
        Map<String, Object> data = (Map<String, Object>) result.get("data");

        assertEquals(200, result.get("code"));
        assertEquals(List.of(), data.get("dshSkills"));
        verify(harness, never()).readRpc(eq("skill.list"), anyMap());
    }

    @Test
    void createConversationPersistsPlatformOwnershipBinding() {
        DeepSeekHarnessService harness = mock(DeepSeekHarnessService.class);
        AgentAdminMapper mapper = mock(AgentAdminMapper.class);
        when(harness.createSession("standard")).thenReturn(Map.of(
                "result", Map.of("value", Map.of("sessionId", "xiaoye-session-1"))));
        HttpServletRequest request = platformAdminRequest(7L);

        Map<String, Object> result = controller(harness, mock(StAdapter.class), mapper)
                .createConversation(Map.of("preset", "standard"), request);

        assertEquals(200, result.get("code"));
        verify(mapper).insertXiaoyeConversation(argThat(binding ->
                "xiaoye-session-1".equals(binding.get("sessionId"))
                        && Long.valueOf(7L).equals(binding.get("adminId"))
                        && "standard".equals(binding.get("preset"))
                && "ACTIVE".equals(binding.get("status"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void conversationCapabilitiesUseTheCurrentSessionScope() {
        DeepSeekHarnessService harness = mock(DeepSeekHarnessService.class);
        AgentAdminMapper mapper = mock(AgentAdminMapper.class);
        when(mapper.findXiaoyeConversation("xiaoye-session-1")).thenReturn(Map.of(
                "sessionId", "xiaoye-session-1", "adminId", 7L, "status", "ACTIVE"));
        when(harness.readRpc("skill.list", Map.of("sessionId", "xiaoye-session-1")))
                .thenReturn(Map.of("result", Map.of("value", Map.of("skills", List.of(Map.of("name", "角色创作"))))));
        when(harness.readRpc("subagent.list", Map.of("parentSessionId", "xiaoye-session-1")))
                .thenReturn(Map.of("result", Map.of("value", Map.of("entries", List.of(Map.of("id", "child-1", "kind", "child"))))));

        AdminXiaoyeController controller = controller(harness, mock(StAdapter.class), mapper);
        Map<String, Object> skills = controller.conversationSkills("xiaoye-session-1", platformAdminRequest(7L));
        Map<String, Object> subagents = controller.conversationSubagents("xiaoye-session-1", platformAdminRequest(7L));

        assertEquals(200, skills.get("code"));
        assertEquals(200, subagents.get("code"));
        verify(harness).readRpc("skill.list", Map.of("sessionId", "xiaoye-session-1"));
        verify(harness).readRpc("subagent.list", Map.of("parentSessionId", "xiaoye-session-1"));
    }

    @Test
    void conversationHistoryRejectsAnotherAdministrator() {
        DeepSeekHarnessService harness = mock(DeepSeekHarnessService.class);
        AgentAdminMapper mapper = mock(AgentAdminMapper.class);
        when(mapper.findXiaoyeConversation("xiaoye-session-2")).thenReturn(Map.of(
                "sessionId", "xiaoye-session-2", "adminId", 8L, "tenantKey", "tenant-a", "status", "ACTIVE"));

        Map<String, Object> result = controller(harness, mock(StAdapter.class), mapper)
                .messages("xiaoye-session-2", standardAdminRequest(7L));

        assertEquals(500, result.get("code"));
        verify(harness, never()).readRpc(eq("session.history"), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void conversationListFiltersDshItemsToPlatformBindings() {
        DeepSeekHarnessService harness = mock(DeepSeekHarnessService.class);
        AgentAdminMapper mapper = mock(AgentAdminMapper.class);
        when(mapper.listXiaoyeConversations(7L, null, true)).thenReturn(List.of(Map.of("sessionId", "allowed-session")));
        when(harness.readRpc("session.list", Map.of("limit", 100))).thenReturn(Map.of(
                "result", Map.of("value", Map.of("items", List.of(
                        Map.of("sessionId", "allowed-session", "title", "可见会话"),
                        Map.of("sessionId", "foreign-session", "title", "不可见会话"))))));

        Map<String, Object> result = controller(harness, mock(StAdapter.class), mapper)
                .conversations(null, platformAdminRequest(7L));
        Map<String, Object> value = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) result.get("data"))
                .get("result")).get("value");
        List<Map<String, Object>> items = (List<Map<String, Object>>) value.get("items");

        assertEquals(200, result.get("code"));
        assertEquals(1, items.size());
        assertEquals("allowed-session", items.get(0).get("sessionId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void conversationListFallsBackToPersistedBindingsWhenDshIsUnavailable() {
        DeepSeekHarnessService harness = mock(DeepSeekHarnessService.class);
        AgentAdminMapper mapper = mock(AgentAdminMapper.class);
        when(mapper.listXiaoyeConversations(7L, null, true)).thenReturn(List.of(Map.of(
                "sessionId", "persisted-session", "updatedAt", 123L)));
        when(harness.readRpc("session.list", Map.of("limit", 100)))
                .thenThrow(new IllegalStateException("DSH unavailable"));

        Map<String, Object> result = controller(harness, mock(StAdapter.class), mapper)
                .conversations(null, platformAdminRequest(7L));
        List<Map<String, Object>> items = (List<Map<String, Object>>) ((Map<String, Object>) ((Map<String, Object>) result.get("data"))
                .get("result")).get("value");

        assertEquals(200, result.get("code"));
        assertEquals(1, items.size());
        assertEquals("persisted-session", items.get(0).get("sessionId"));
        assertEquals(true, items.get(0).get("missingFromDsh"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void greetingsAndCapabilityQuestionsStayInConversationWithoutCreatingTasks() {
        DeepSeekHarnessService harness = mock(DeepSeekHarnessService.class);
        AgentAdminMapper mapper = mock(AgentAdminMapper.class);
        AgentTaskService taskService = mock(AgentTaskService.class);
        AgentPlanService planService = mock(AgentPlanService.class);
        when(mapper.findXiaoyeConversation(anyString())).thenAnswer(invocation -> Map.of(
                "sessionId", invocation.getArgument(0), "adminId", 7L, "status", "ACTIVE"));
        AdminXiaoyeController controller = new AdminXiaoyeController(
                harness, mock(AgentAdminQueryService.class), mock(AdminJiugaiCharacterService.class),
                mock(StAdapter.class), mock(StWorldbookCatalogService.class), new ObjectMapper(), mapper,
                mock(AgentTenantService.class), taskService, planService,
                mock(PlatformBusinessQueryService.class), mock(PlatformExtendedQueryService.class),
                mock(AgentPlatformQueryTool.class));

        Map<String, Object> greetingResult = controller.sendMessage("task-session",
                Map.of("message", "你好", "mode", "TASK_EXECUTION"), platformAdminRequest(7L));
        Map<String, Object> capabilityResult = controller.sendMessage("engineering-session",
                Map.of("message", "这个模式能干什么？", "mode", "ENGINEERING"), platformAdminRequest(7L));

        assertEquals(200, greetingResult.get("code"));
        assertEquals(false, ((Map<String, Object>) greetingResult.get("data")).get("taskCreated"));
        assertEquals(false, ((Map<String, Object>) capabilityResult.get("data")).get("taskCreated"));
        verifyNoInteractions(taskService, planService);
        verify(harness, times(2)).writeRpc(eq("session.prompt"), anyMap());
        verify(harness, times(2)).writeRpc(eq("session.prompt"), argThat(payload -> {
            String content = String.valueOf(payload.get("content"));
            return content.contains("小叶")
                    && content.contains("四叶酒馆的专属智能助手")
                    && content.contains("不要自称‘通用编程助手’");
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    void actionableReadOnlyTaskIsAutoApprovedAfterPlanRiskCheck() {
        DeepSeekHarnessService harness = mock(DeepSeekHarnessService.class);
        AgentAdminMapper mapper = mock(AgentAdminMapper.class);
        AgentTaskService taskService = mock(AgentTaskService.class);
        AgentPlanService planService = mock(AgentPlanService.class);
        when(mapper.findXiaoyeConversation("task-session")).thenReturn(Map.of(
                "sessionId", "task-session", "adminId", 7L, "tenantKey", "platform", "status", "ACTIVE"));
        when(taskService.createAdminTask(anyMap(), eq(7L))).thenReturn(Map.of("id", 41L, "status", "DRAFT"));
        when(planService.generate(41L, 7L)).thenReturn(Map.of(
                "task", Map.of("id", 41L, "status", "WAITING_APPROVAL"),
                "plan", Map.of("id", 101L, "status", "WAITING_APPROVAL"),
                "steps", List.of(Map.of("id", 201L, "status", "READY")),
                "approval", Map.of("id", 301L, "taskId", 41L, "status", "PENDING")));
        when(planService.approveReadOnlyPlan(41L, 7L)).thenReturn(Map.of(
                "task", Map.of("id", 41L, "status", "READY"),
                "steps", List.of(Map.of("id", 201L, "status", "READY"))));
        AdminXiaoyeController controller = new AdminXiaoyeController(
                harness, mock(AgentAdminQueryService.class), mock(AdminJiugaiCharacterService.class),
                mock(StAdapter.class), mock(StWorldbookCatalogService.class), new ObjectMapper(), mapper,
                mock(AgentTenantService.class), taskService, planService,
                mock(PlatformBusinessQueryService.class), mock(PlatformExtendedQueryService.class),
                mock(AgentPlatformQueryTool.class));

        Map<String, Object> result = controller.sendMessage("task-session",
                Map.of("message", "执行只读检查", "mode", "TASK_EXECUTION"), platformAdminRequest(7L));
        Map<String, Object> data = (Map<String, Object>) result.get("data");

        assertEquals(200, result.get("code"));
        assertEquals(true, data.get("taskCreated"));
        assertEquals(false, data.get("approvalRequired"));
        assertEquals("READY", ((Map<String, Object>) data.get("task")).get("status"));
        verify(taskService).createAdminTask(argThat(body -> "READ_ONLY".equals(body.get("riskLevel"))), eq(7L));
        verify(planService).approveReadOnlyPlan(41L, 7L);
        verify(harness).writeRpc(eq("session.prompt"), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void engineeringWriteTaskKeepsItsPlanApprovalVisible() {
        DeepSeekHarnessService harness = mock(DeepSeekHarnessService.class);
        AgentAdminMapper mapper = mock(AgentAdminMapper.class);
        AgentTaskService taskService = mock(AgentTaskService.class);
        AgentPlanService planService = mock(AgentPlanService.class);
        when(mapper.findXiaoyeConversation("engineering-session")).thenReturn(Map.of(
                "sessionId", "engineering-session", "adminId", 7L, "tenantKey", "platform", "status", "ACTIVE"));
        when(taskService.createAdminTask(anyMap(), eq(7L))).thenReturn(Map.of("id", 42L, "status", "DRAFT"));
        Map<String, Object> approval = Map.of("id", 302L, "taskId", 42L, "status", "PENDING");
        when(planService.generate(42L, 7L)).thenReturn(Map.of(
                "task", Map.of("id", 42L, "status", "WAITING_APPROVAL"),
                "plan", Map.of("id", 102L, "status", "WAITING_APPROVAL"),
                "steps", List.of(Map.of("id", 202L, "status", "READY")),
                "approval", approval));
        AdminXiaoyeController controller = new AdminXiaoyeController(
                harness, mock(AgentAdminQueryService.class), mock(AdminJiugaiCharacterService.class),
                mock(StAdapter.class), mock(StWorldbookCatalogService.class), new ObjectMapper(), mapper,
                mock(AgentTenantService.class), taskService, planService,
                mock(PlatformBusinessQueryService.class), mock(PlatformExtendedQueryService.class),
                mock(AgentPlatformQueryTool.class));

        Map<String, Object> result = controller.sendMessage("engineering-session",
                Map.of("message", "修改测试文件", "mode", "ENGINEERING"), platformAdminRequest(7L));
        Map<String, Object> data = (Map<String, Object>) result.get("data");

        assertEquals(200, result.get("code"));
        assertEquals(true, data.get("taskCreated"));
        assertEquals(true, data.get("approvalRequired"));
        assertEquals(302L, ((Map<String, Object>) data.get("approval")).get("id"));
        verify(taskService).createAdminTask(argThat(body -> "WRITE_REVERSIBLE".equals(body.get("riskLevel"))), eq(7L));
        verify(planService, never()).approveReadOnlyPlan(anyLong(), anyLong());
        verify(harness).writeRpc(eq("session.prompt"), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    void nativeDshModeForwardsTheTurnVerbatimWithoutBusinessPromptPollution() {
        DeepSeekHarnessService harness = mock(DeepSeekHarnessService.class);
        AgentAdminMapper mapper = mock(AgentAdminMapper.class);
        when(mapper.findXiaoyeConversation("native-session")).thenReturn(Map.of(
                "sessionId", "native-session", "adminId", 7L, "status", "ACTIVE"));
        AdminXiaoyeController controller = controller(harness, mock(StAdapter.class), mapper);

        Map<String, Object> result = controller.sendMessage("native-session", Map.of(
                "message", "请检查当前工作区并总结发现的问题", "mode", "DSH_NATIVE"), platformAdminRequest(7L));

        assertEquals(200, result.get("code"));
        verify(harness).writeRpc(eq("session.prompt"), argThat(payload -> {
            assertEquals("native-session", payload.get("sessionId"));
            assertEquals("queue", payload.get("mode"));
            List<Map<String, Object>> content = (List<Map<String, Object>>) payload.get("content");
            return content.size() == 1
                    && "text".equals(content.get(0).get("type"))
                    && "请检查当前工作区并总结发现的问题".equals(content.get(0).get("text"));
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportConversationRequiresBindingBeforeProxyingDshExport() {
        DeepSeekHarnessService harness = mock(DeepSeekHarnessService.class);
        AgentAdminMapper mapper = mock(AgentAdminMapper.class);
        when(mapper.findXiaoyeConversation("export-session")).thenReturn(Map.of(
                "sessionId", "export-session", "adminId", 7L, "status", "ACTIVE"));
        HttpResponse<byte[]> dshResponse = mock(HttpResponse.class);
        when(dshResponse.statusCode()).thenReturn(200);
        when(dshResponse.body()).thenReturn("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(dshResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(
                Map.of("Content-Type", List.of("application/json")), (name, value) -> true));
        when(harness.exportSession("export-session")).thenReturn(dshResponse);

        ResponseEntity<byte[]> result = controller(harness, mock(StAdapter.class), mapper)
                .exportConversation("export-session", platformAdminRequest(7L));

        assertEquals(200, result.getStatusCode().value());
        assertEquals("application/json", result.getHeaders().getContentType().toString());
        verify(harness).exportSession("export-session");
    }

    private static HttpServletRequest platformAdminRequest(long adminId) {
        HttpServletRequest request = standardAdminRequest(adminId);
        when(request.getAttribute("adminPermissions")).thenReturn(List.of("*:*:*"));
        return request;
    }

    private static HttpServletRequest standardAdminRequest(long adminId) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("adminSession")).thenReturn(new RuoYiAdminAccessService.AdminSession(
                adminId, "admin-" + adminId, "管理员", List.of("ADMIN"), List.of("agent:center:view")));
        when(request.getAttribute("adminPermissions")).thenReturn(List.of("agent:center:view"));
        return request;
    }

    private static AdminXiaoyeController controller(StAdapter stAdapter, AgentAdminMapper mapper) {
        return controller(mock(DeepSeekHarnessService.class), stAdapter, mapper);
    }

    private static AdminXiaoyeController controller(DeepSeekHarnessService harness, StAdapter stAdapter, AgentAdminMapper mapper) {
        return new AdminXiaoyeController(
                harness,
                mock(AgentAdminQueryService.class),
                mock(AdminJiugaiCharacterService.class),
                stAdapter,
                mock(StWorldbookCatalogService.class),
                new ObjectMapper(),
                mapper,
                mock(AgentTenantService.class));
    }
}
