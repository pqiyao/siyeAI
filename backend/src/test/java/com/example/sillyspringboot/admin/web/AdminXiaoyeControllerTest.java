package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.RuoYiAdminAccessService;
import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.example.sillyspringboot.agent.service.AgentAdminQueryService;
import com.example.sillyspringboot.agent.service.AgentTenantService;
import com.example.sillyspringboot.agent.service.DeepSeekHarnessService;
import com.example.sillyspringboot.admin.service.AdminJiugaiCharacterService;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.StWorldbookCatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AdminXiaoyeControllerTest {
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

    private static AdminXiaoyeController controller(StAdapter stAdapter, AgentAdminMapper mapper) {
        return new AdminXiaoyeController(
                mock(DeepSeekHarnessService.class),
                mock(AgentAdminQueryService.class),
                mock(AdminJiugaiCharacterService.class),
                stAdapter,
                mock(StWorldbookCatalogService.class),
                new ObjectMapper(),
                mapper,
                mock(AgentTenantService.class));
    }
}
