package com.example.sillyspringboot.admin.service;

import com.example.sillyspringboot.admin.mapper.AdminH5UserCleanupMapper;
import com.example.sillyspringboot.admin.mapper.AdminH5UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminH5UserLifecycleServiceTest {

    private AdminH5UserMapper adminH5UserMapper;
    private AdminH5UserCleanupMapper cleanupMapper;
    private ExternalCleanupTaskService externalCleanupTaskService;
    private PlatformTransactionManager transactionManager;
    private TransactionStatus transactionStatus;
    private AdminH5UserLifecycleService service;

    @BeforeEach
    void setUp() {
        adminH5UserMapper = mock(AdminH5UserMapper.class);
        cleanupMapper = mock(AdminH5UserCleanupMapper.class);
        externalCleanupTaskService = mock(ExternalCleanupTaskService.class);
        transactionManager = mock(PlatformTransactionManager.class);
        transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(cleanupMapper.listConversationStRefs(7L)).thenReturn(List.of(Map.of(
                "stAvatarUrl", "/characters/example.png",
                "stChatFileName", "chat.jsonl"
        )));
        when(cleanupMapper.listOwnedCharacterCleanupRows(7L)).thenReturn(List.of());
        when(cleanupMapper.listOwnedUploadRelativePaths(7L)).thenReturn(List.of());
        when(externalCleanupTaskService.enqueueUserDeletionTasks(anyLong(), anyList(), anyList(), anySet()))
                .thenReturn(List.of("cleanup-task-1"));
        when(externalCleanupTaskService.processImmediately(List.of("cleanup-task-1")))
                .thenReturn(List.of(new ExternalCleanupTaskService.CleanupAttempt(
                        "cleanup-task-1",
                        "ST_CHAT",
                        "COMPLETED",
                        "cleanup completed"
                )));
        service = new AdminH5UserLifecycleService(
                adminH5UserMapper,
                cleanupMapper,
                externalCleanupTaskService,
                transactionManager
        );
    }

    @Test
    void deletesExternalResourcesOnlyAfterDatabaseCommit() {
        service.deleteUserById(7L);

        InOrder order = inOrder(transactionManager, externalCleanupTaskService);
        order.verify(transactionManager).commit(transactionStatus);
        order.verify(externalCleanupTaskService).processImmediately(List.of("cleanup-task-1"));
    }

    @Test
    void databaseFailureNeverDeletesExternalResources() {
        when(cleanupMapper.deleteSupportTicketMessagesByUser(7L)).thenThrow(new IllegalStateException("db failed"));

        assertThatThrownBy(() -> service.deleteUserById(7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("db failed");

        verify(transactionManager).rollback(transactionStatus);
        verify(externalCleanupTaskService, never()).processImmediately(any());
    }

    @Test
    void externalCleanupFailureIsReportedWithoutMisreportingDatabaseDeletion() {
        when(adminH5UserMapper.findDetail(7L)).thenReturn(Map.of("id", 7L));
        when(externalCleanupTaskService.processImmediately(List.of("cleanup-task-1")))
                .thenReturn(List.of(new ExternalCleanupTaskService.CleanupAttempt(
                        "cleanup-task-1",
                        "ST_CHAT",
                        "RETRY",
                        "ST offline"
                )));

        Map<String, Object> result = service.deleteUsers(List.of(7L));

        assertThat(result.get("deleted")).isEqualTo(1);
        assertThat(result.get("failedCount")).isEqualTo(0);
        assertThat(result.get("cleanupWarningCount")).isEqualTo(1);
        assertThat((List<?>) result.get("cleanupWarnings")).hasSize(1);
    }

    @Test
    void singleUserDeletionAlsoReturnsExternalCleanupWarnings() {
        when(externalCleanupTaskService.processImmediately(List.of("cleanup-task-1")))
                .thenReturn(List.of(new ExternalCleanupTaskService.CleanupAttempt(
                        "cleanup-task-1",
                        "ST_CHAT",
                        "RETRY",
                        "ST offline"
                )));

        Map<String, Object> result = service.deleteUserById(7L);

        assertThat(result.get("deleted")).isEqualTo(true);
        assertThat(result.get("cleanupWarningCount")).isEqualTo(1);
        assertThat((List<?>) result.get("cleanupWarnings")).hasSize(1);
    }

    @Test
    void cleanupProcessorFailureCannotChangeCommittedDeletionIntoDatabaseFailure() {
        when(adminH5UserMapper.findDetail(7L)).thenReturn(Map.of("id", 7L));
        when(externalCleanupTaskService.processImmediately(List.of("cleanup-task-1")))
                .thenThrow(new IllegalStateException("cleanup database temporarily unavailable"));

        Map<String, Object> result = service.deleteUsers(List.of(7L));

        assertThat(result.get("deleted")).isEqualTo(1);
        assertThat(result.get("failedCount")).isEqualTo(0);
        assertThat(result.get("cleanupWarningCount")).isEqualTo(1);
        assertThat((List<?>) result.get("cleanupWarnings")).singleElement()
                .asString()
                .contains("remain queued");
    }
}
