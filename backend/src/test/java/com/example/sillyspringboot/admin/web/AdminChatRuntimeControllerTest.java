package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.mapper.AdminChatRuntimeMapper;
import com.example.sillyspringboot.chat.entity.AppGenerationTask;
import com.example.sillyspringboot.chat.mapper.AppGenerationTaskMapper;
import com.example.sillyspringboot.chat.service.ChatRuntimeClusterService;
import com.example.sillyspringboot.ops.service.OperationalStatsService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminChatRuntimeControllerTest {

    @Test
    void cancelsOnlyTheSelectedTaskAndRecordsTheOperator() {
        Fixture fixture = new Fixture();
        AppGenerationTask task = activeTask(41L, 9L, "GENERATING");
        when(fixture.taskMapper.findById(41L)).thenReturn(task);
        when(fixture.taskMapper.updateStatus(eq(41L), eq("STOPPED"), eq("ADMIN_CANCELLED"),
                contains("ops-admin"), isNull(), eq(499))).thenReturn(1);
        when(fixture.clusterService.requestCancellation(41L))
                .thenReturn(new ChatRuntimeClusterService.CancellationSignal(true, true));
        when(fixture.adminMapper.stopAssistantMessageForTask(41L)).thenReturn(1);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("adminUsername", "ops-admin");
        Map<String, Object> result = fixture.controller.cancel(41L, request);

        assertThat(result.get("code")).isEqualTo(200);
        assertThat(result.get("signalled")).isEqualTo(true);
        assertThat(result.get("messagesStopped")).isEqualTo(1);
        verify(fixture.clusterService).requestCancellation(41L);
        verify(fixture.operationalStatsService).recordGenerationTaskStatus(41L, "STOPPED");
    }

    @Test
    void doesNotCancelAnotherTaskFromTheSameConversation() {
        Fixture fixture = new Fixture();
        AppGenerationTask selected = activeTask(51L, 15L, "QUEUED");
        when(fixture.taskMapper.findById(51L)).thenReturn(selected);
        when(fixture.taskMapper.updateStatus(eq(51L), anyString(), any(), any(), any(), any()))
                .thenReturn(1);
        when(fixture.clusterService.requestCancellation(51L))
                .thenReturn(new ChatRuntimeClusterService.CancellationSignal(false, true));

        Map<String, Object> result = fixture.controller.cancel(51L, new MockHttpServletRequest());

        assertThat(result.get("code")).isEqualTo(200);
        assertThat(result.get("signalled")).isEqualTo(true);
        assertThat(result.get("localSignalled")).isEqualTo(false);
        assertThat(result.get("distributed")).isEqualTo(true);
    }

    @Test
    void reportsAStateRaceInsteadOfCancellingAfterTheTaskEnded() {
        Fixture fixture = new Fixture();
        AppGenerationTask task = activeTask(61L, 19L, "GENERATING");
        when(fixture.taskMapper.findById(61L)).thenReturn(task);
        when(fixture.taskMapper.updateStatus(eq(61L), anyString(), any(), any(), any(), any()))
                .thenReturn(0);
        Map<String, Object> result = fixture.controller.cancel(61L, new MockHttpServletRequest());

        assertThat(result.get("code")).isEqualTo(500);
        verify(fixture.clusterService, never()).requestCancellation(anyLong());
    }

    @Test
    void hardDeletesTerminalTasksAndTheirOperationalRecords() {
        Fixture fixture = new Fixture();
        List<Long> ids = List.of(71L, 72L);
        when(fixture.adminMapper.findTaskStatuses(ids)).thenReturn(List.of(
                Map.of("id", 71L, "status", "SUCCESS"),
                Map.of("id", 72L, "status", "FAILED")
        ));
        when(fixture.adminMapper.deleteAttemptsByTaskIds(ids)).thenReturn(3);
        when(fixture.adminMapper.deleteStatEventsByTaskIds(ids)).thenReturn(2);
        when(fixture.adminMapper.hardDeleteTasks(ids)).thenReturn(2);

        Map<String, Object> result = fixture.controller.hardDelete(
                new AdminChatRuntimeController.DeleteRequest(ids)
        );

        assertThat(result.get("code")).isEqualTo(200);
        assertThat(result.get("deleted")).isEqualTo(2);
        assertThat(result.get("attemptsDeleted")).isEqualTo(3);
        assertThat(result.get("statsDeleted")).isEqualTo(2);
    }

    @Test
    void refusesToHardDeleteActiveTasks() {
        Fixture fixture = new Fixture();
        List<Long> ids = List.of(81L);
        when(fixture.adminMapper.findTaskStatuses(ids)).thenReturn(List.of(
                Map.of("id", 81L, "status", "GENERATING")
        ));

        Map<String, Object> result = fixture.controller.hardDelete(
                new AdminChatRuntimeController.DeleteRequest(ids)
        );

        assertThat(result.get("code")).isEqualTo(500);
        assertThat(result.get("msg")).asString().contains("不能删除");
        verify(fixture.adminMapper, never()).hardDeleteTasks(anyList());
        verify(fixture.adminMapper, never()).deleteAttemptsByTaskIds(anyList());
    }

    private static AppGenerationTask activeTask(long taskId, long conversationId, String status) {
        AppGenerationTask task = new AppGenerationTask();
        task.setId(taskId);
        task.setConversationId(conversationId);
        task.setStatus(status);
        return task;
    }

    private static final class Fixture {
        private final AdminChatRuntimeMapper adminMapper = mock(AdminChatRuntimeMapper.class);
        private final AppGenerationTaskMapper taskMapper = mock(AppGenerationTaskMapper.class);
        private final OperationalStatsService operationalStatsService = mock(OperationalStatsService.class);
        private final ChatRuntimeClusterService clusterService = mock(ChatRuntimeClusterService.class);
        private final AdminChatRuntimeController controller = new AdminChatRuntimeController(
                adminMapper, taskMapper, operationalStatsService, clusterService
        );
    }
}
