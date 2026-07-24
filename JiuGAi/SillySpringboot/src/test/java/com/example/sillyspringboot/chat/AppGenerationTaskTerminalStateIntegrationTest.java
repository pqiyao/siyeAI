package com.example.sillyspringboot.chat;

import com.example.sillyspringboot.chat.entity.AppGenerationTask;
import com.example.sillyspringboot.chat.mapper.AppGenerationTaskMapper;
import com.example.sillyspringboot.chat.service.StaleGenerationTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AppGenerationTaskTerminalStateIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppGenerationTaskMapper taskMapper;

    @Autowired
    private StaleGenerationTaskService staleGenerationTaskService;

    @Test
    void terminalTaskCannotBeOverwrittenByALateWorkerCompletion() {
        long suffix = Math.abs(System.nanoTime());
        long telegramId = 8_000_000_000L + (suffix % 900_000_000L);
        jdbcTemplate.update(
                "INSERT INTO app_user (telegram_user_id, username) VALUES (?, ?)",
                telegramId,
                "runtime-test-" + suffix
        );
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE telegram_user_id = ?",
                Long.class,
                telegramId
        );
        jdbcTemplate.update(
                "INSERT INTO app_conversation (user_id, character_id, title) VALUES (?, ?, ?)",
                userId,
                1L,
                "runtime terminal test"
        );
        Long conversationId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_conversation WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                userId
        );

        AppGenerationTask task = new AppGenerationTask();
        task.setUserId(userId);
        task.setConversationId(conversationId);
        task.setRequestType("generate");
        task.setClientMessageId("terminal-" + suffix);
        task.setStatus("QUEUED");
        taskMapper.insert(task);

        assertThat(taskMapper.updateStatus(
                task.getId(), "STOPPED", "ADMIN_CANCELLED", "cancelled by admin test", null, 499
        )).isEqualTo(1);
        assertThat(taskMapper.updateStatus(
                task.getId(), "SUCCESS", null, null, null, 200
        )).isZero();

        AppGenerationTask stored = taskMapper.findById(task.getId());
        assertThat(stored.getStatus()).isEqualTo("STOPPED");
        assertThat(stored.getErrorCode()).isEqualTo("ADMIN_CANCELLED");
        assertThat(stored.getDurationMs()).isNotNull().isGreaterThanOrEqualTo(0);
    }

    @Test
    void staleQueuedTaskIsFailedAndReflectedInOperationalStatistics() {
        long suffix = Math.abs(System.nanoTime());
        long telegramId = 7_000_000_000L + (suffix % 900_000_000L);
        jdbcTemplate.update(
                "INSERT INTO app_user (telegram_user_id, username) VALUES (?, ?)",
                telegramId,
                "stale-runtime-test-" + suffix
        );
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_user WHERE telegram_user_id = ?",
                Long.class,
                telegramId
        );
        jdbcTemplate.update(
                "INSERT INTO app_conversation (user_id, character_id, title) VALUES (?, ?, ?)",
                userId,
                1L,
                "stale runtime test"
        );
        Long conversationId = jdbcTemplate.queryForObject(
                "SELECT id FROM app_conversation WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                userId
        );

        AppGenerationTask task = new AppGenerationTask();
        task.setUserId(userId);
        task.setConversationId(conversationId);
        task.setRequestType("generate");
        task.setChannel("CHAT_STREAM");
        task.setClientMessageId("stale-" + suffix);
        task.setStatus("QUEUED");
        task.setQueuedAt(LocalDateTime.now().minusHours(1));
        taskMapper.insert(task);

        staleGenerationTaskService.scheduledReconcile();

        AppGenerationTask stored = taskMapper.findById(task.getId());
        assertThat(stored.getStatus()).isEqualTo("FAILED");
        assertThat(stored.getErrorCode()).isEqualTo("STALE_GENERATION");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM app_generation_stat_event WHERE task_id = ?",
                String.class,
                task.getId()
        )).isEqualTo("FAILED");
    }
}
