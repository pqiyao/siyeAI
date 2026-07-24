package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.entity.AppConversationMemoryEntry;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.service.ConversationMemoryCapacityService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationMemoryCapacityServiceTest {

    private static final long CONVERSATION_ID = 9001L;
    private static final long BRANCH_ID = 101L;

    private SqlSession sqlSession;
    private AppConversationMemoryEntryMapper entryMapper;
    private MemoryLlmProperties properties;
    private ConversationMemoryCapacityService service;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:memory_capacity_" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE app_conversation_memory_entry (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    conversation_id BIGINT NOT NULL,
                    branch_id BIGINT NOT NULL,
                    entry_key VARCHAR(128) NOT NULL,
                    memory_type VARCHAR(32) NOT NULL,
                    title VARCHAR(255) NULL,
                    content TEXT NOT NULL,
                    keywords_json TEXT NOT NULL,
                    secondary_keywords_json TEXT NULL,
                    priority INT NOT NULL DEFAULT 100,
                    position VARCHAR(32) NOT NULL DEFAULT 'before_char',
                    constant_injection BOOLEAN NOT NULL DEFAULT FALSE,
                    selective BOOLEAN NOT NULL DEFAULT FALSE,
                    enabled BOOLEAN NOT NULL DEFAULT TRUE,
                    manual_disabled BOOLEAN NOT NULL DEFAULT FALSE,
                    manual_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                    manual_pinned BOOLEAN NOT NULL DEFAULT FALSE,
                    retired_reason VARCHAR(32) NULL,
                    retired_at TIMESTAMP NULL,
                    confidence DECIMAL(5,2) NULL,
                    source_message_from_id BIGINT NULL,
                    source_message_to_id BIGINT NULL,
                    last_activated_at TIMESTAMP NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted_at TIMESTAMP NULL DEFAULT NULL,
                    CONSTRAINT uk_memory_entry_key UNIQUE (conversation_id, branch_id, entry_key)
                )
                """);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/conversation/AppConversationMemoryEntryMapper.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        assertThat(factory).isNotNull();

        sqlSession = factory.openSession(true);
        entryMapper = sqlSession.getMapper(AppConversationMemoryEntryMapper.class);
        properties = new MemoryLlmProperties();
        properties.setMaxEnabledEntries(80);
        properties.setMaxConstantEntries(12);
        properties.setMaxArchivedEntries(40);
        service = new ConversationMemoryCapacityService(entryMapper, properties);
    }

    @AfterEach
    void tearDown() {
        if (sqlSession != null) {
            sqlSession.close();
        }
    }

    @Test
    void enforceAfterRefresh_shouldKeepBest80Of81EnabledEntries() {
        AppConversationMemoryEntry lowest = insert(BRANCH_ID, "lowest_event", "event", 40, false, 1L);
        for (int i = 0; i < 80; i++) {
            insert(BRANCH_ID, "identity_" + i, "identity", 100 + (i % 100), false, 10L + i);
        }

        ConversationMemoryCapacityService.CapacityResult result =
                service.enforceAfterRefresh(CONVERSATION_ID, BRANCH_ID);

        assertThat(result.enabledCount()).isEqualTo(80);
        assertThat(result.archivedCount()).isEqualTo(1);
        assertThat(result.capacityArchivedCount()).isEqualTo(1);
        assertThat(findById(lowest.getId()))
                .satisfies(entry -> {
                    assertThat(entry.isEnabled()).isFalse();
                    assertThat(entry.getRetiredReason()).isEqualTo(ConversationMemoryCapacityService.RETIRED_CAPACITY);
                    assertThat(entry.getRetiredAt()).isNotNull();
                    assertThat(entry.getDeletedAt()).isNotNull();
                });
    }

    @Test
    void enforceAfterRefresh_shouldDowngradeConstantsAfterBest12WithoutDisablingThem() {
        AppConversationMemoryEntry lowest = null;
        for (int i = 0; i < 13; i++) {
            AppConversationMemoryEntry entry = insert(
                    BRANCH_ID,
                    "constant_" + i,
                    "identity",
                    80 + i,
                    true,
                    100L + i
            );
            if (i == 0) {
                lowest = entry;
            }
        }

        ConversationMemoryCapacityService.CapacityResult result =
                service.enforceAfterRefresh(CONVERSATION_ID, BRANCH_ID);

        assertThat(result.enabledCount()).isEqualTo(13);
        assertThat(result.constantCount()).isEqualTo(12);
        assertThat(lowest).isNotNull();
        assertThat(findById(lowest.getId()))
                .satisfies(entry -> {
                    assertThat(entry.isEnabled()).isTrue();
                    assertThat(entry.isConstantInjection()).isFalse();
                    assertThat(entry.getRetiredAt()).isNull();
                });
    }

    @Test
    void enforceAfterRefresh_shouldArchiveLowerValueDuplicateFirst() {
        AppConversationMemoryEntry winner = insertWithContent(
                BRANCH_ID,
                "relationship_current",
                "relationship",
                "用户与角色已经确认是相互信任的恋人关系。",
                200,
                false,
                300L
        );
        AppConversationMemoryEntry duplicate = insertWithContent(
                BRANCH_ID,
                "relationship_old",
                "relationship",
                "用户与角色已经确认是相互信任的恋人关系。",
                80,
                false,
                200L
        );

        ConversationMemoryCapacityService.CapacityResult result =
                service.enforceAfterRefresh(CONVERSATION_ID, BRANCH_ID);

        assertThat(result.duplicateArchivedCount()).isEqualTo(1);
        assertThat(findById(winner.getId()).isEnabled()).isTrue();
        assertThat(findById(duplicate.getId()).getRetiredReason())
                .isEqualTo(ConversationMemoryCapacityService.RETIRED_DUPLICATE);
    }

    @Test
    void enforceAfterRefresh_shouldKeepOnly40AutomaticArchivesAndPreserveManualRows() {
        properties.setMaxEnabledEntries(1);
        AppConversationMemoryEntry pinned = insert(BRANCH_ID, "pinned", "event", 40, false, 1L);
        assertThat(entryMapper.setManualEnabledById(
                pinned.getId(), CONVERSATION_ID, BRANCH_ID, true, false
        )).isEqualTo(1);

        for (int i = 0; i < 45; i++) {
            insert(BRANCH_ID, "auto_" + i, "identity", 100 + (i % 100), false, 20L + i);
        }
        AppConversationMemoryEntry manualDisabled = insert(
                BRANCH_ID, "manual_disabled", "event", 40, false, 2L
        );
        assertThat(entryMapper.setManualEnabledById(
                manualDisabled.getId(), CONVERSATION_ID, BRANCH_ID, false, true
        )).isEqualTo(1);
        AppConversationMemoryEntry manualDeleted = insert(
                BRANCH_ID, "manual_deleted", "event", 40, false, 3L
        );
        assertThat(entryMapper.softDeleteManualById(
                manualDeleted.getId(), CONVERSATION_ID, BRANCH_ID
        )).isEqualTo(1);

        ConversationMemoryCapacityService.CapacityResult result =
                service.enforceAfterRefresh(CONVERSATION_ID, BRANCH_ID);

        assertThat(result.enabledCount()).isEqualTo(1);
        assertThat(result.archivedCount()).isEqualTo(40);
        assertThat(result.physicallyDeletedCount()).isEqualTo(5);
        assertThat(findById(pinned.getId()))
                .satisfies(entry -> {
                    assertThat(entry.isEnabled()).isTrue();
                    assertThat(entry.isManualPinned()).isTrue();
                });
        assertThat(findById(manualDisabled.getId()).isManualDisabled()).isTrue();
        assertThat(findById(manualDeleted.getId()).isManualDeleted()).isTrue();
        assertThat(entryMapper.listPanelByConversationBranchId(CONVERSATION_ID, BRANCH_ID)).hasSize(42);
    }

    @Test
    void setManualEnabledWithCapacity_shouldEvictOrdinaryEntryAndPinRestoredArchive() {
        properties.setMaxEnabledEntries(3);
        AppConversationMemoryEntry target = insert(BRANCH_ID, "target", "event", 40, false, 1L);
        insert(BRANCH_ID, "identity_a", "identity", 200, false, 20L);
        insert(BRANCH_ID, "identity_b", "identity", 180, false, 21L);
        insert(BRANCH_ID, "identity_c", "identity", 160, false, 22L);
        service.enforceAfterRefresh(CONVERSATION_ID, BRANCH_ID);
        assertThat(findById(target.getId()).getRetiredReason())
                .isEqualTo(ConversationMemoryCapacityService.RETIRED_CAPACITY);

        ConversationMemoryCapacityService.CapacityResult result = service.setManualEnabledWithCapacity(
                target.getId(), CONVERSATION_ID, BRANCH_ID, true
        );

        assertThat(result.enabledCount()).isEqualTo(3);
        assertThat(result.capacityArchivedCount()).isEqualTo(1);
        assertThat(findById(target.getId()))
                .satisfies(entry -> {
                    assertThat(entry.isEnabled()).isTrue();
                    assertThat(entry.isManualPinned()).isTrue();
                    assertThat(entry.getRetiredReason()).isNull();
                    assertThat(entry.getRetiredAt()).isNull();
                    assertThat(entry.getDeletedAt()).isNull();
                });
    }

    @Test
    void setManualEnabledWithCapacity_shouldRejectWhenEveryActiveEntryIsPinned() {
        properties.setMaxEnabledEntries(2);
        AppConversationMemoryEntry target = insert(BRANCH_ID, "target", "event", 40, false, 1L);
        AppConversationMemoryEntry first = insert(BRANCH_ID, "first", "identity", 200, false, 20L);
        AppConversationMemoryEntry second = insert(BRANCH_ID, "second", "identity", 180, false, 21L);
        service.enforceAfterRefresh(CONVERSATION_ID, BRANCH_ID);
        assertThat(entryMapper.setManualEnabledById(
                first.getId(), CONVERSATION_ID, BRANCH_ID, true, false
        )).isEqualTo(1);
        assertThat(entryMapper.setManualEnabledById(
                second.getId(), CONVERSATION_ID, BRANCH_ID, true, false
        )).isEqualTo(1);

        assertThatThrownBy(() -> service.setManualEnabledWithCapacity(
                target.getId(), CONVERSATION_ID, BRANCH_ID, true
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(findById(target.getId()).isEnabled()).isFalse();
    }

    @Test
    void enforceAfterRefresh_shouldArchiveSystemDisabledEntriesAsSuperseded() {
        for (int i = 0; i < 45; i++) {
            AppConversationMemoryEntry disabled = insert(
                    BRANCH_ID, "superseded_" + i, "preference", 120, false, 30L + i
            );
            entryMapper.disableByKeyForBranch(CONVERSATION_ID, BRANCH_ID, disabled.getEntryKey());
        }

        ConversationMemoryCapacityService.CapacityResult result =
                service.enforceAfterRefresh(CONVERSATION_ID, BRANCH_ID);

        assertThat(result.supersededArchivedCount()).isEqualTo(45);
        assertThat(result.archivedCount()).isEqualTo(40);
        assertThat(result.physicallyDeletedCount()).isEqualTo(5);
        assertThat(entryMapper.listPanelByConversationBranchId(CONVERSATION_ID, BRANCH_ID))
                .hasSize(40)
                .allSatisfy(entry -> {
                    assertThat(entry.getRetiredReason())
                            .isEqualTo(ConversationMemoryCapacityService.RETIRED_SUPERSEDED);
                    assertThat(entry.getRetiredAt()).isNotNull();
                    assertThat(entry.getDeletedAt()).isNotNull();
                });
    }

    @Test
    void setManualEnabledWithCapacity_shouldRestoreSupersededArchive() {
        AppConversationMemoryEntry target = insert(
                BRANCH_ID, "superseded_restore", "preference", 120, false, 30L
        );
        assertThat(entryMapper.setManualEnabledById(
                target.getId(), CONVERSATION_ID, BRANCH_ID, false, false
        )).isEqualTo(1);
        service.enforceAfterRefresh(CONVERSATION_ID, BRANCH_ID);
        assertThat(findById(target.getId()).getRetiredReason())
                .isEqualTo(ConversationMemoryCapacityService.RETIRED_SUPERSEDED);

        service.setManualEnabledWithCapacity(target.getId(), CONVERSATION_ID, BRANCH_ID, true);

        assertThat(findById(target.getId()))
                .satisfies(entry -> {
                    assertThat(entry.isEnabled()).isTrue();
                    assertThat(entry.isManualPinned()).isTrue();
                    assertThat(entry.getRetiredReason()).isNull();
                    assertThat(entry.getRetiredAt()).isNull();
                    assertThat(entry.getDeletedAt()).isNull();
                });
    }

    @Test
    void enforceAfterRefresh_shouldNotTouchAnotherBranch() {
        properties.setMaxEnabledEntries(3);
        for (int i = 0; i < 4; i++) {
            insert(BRANCH_ID, "branch_a_" + i, "event", 100 + i, false, 10L + i);
            insert(202L, "branch_b_" + i, "event", 100 + i, false, 10L + i);
        }

        service.enforceAfterRefresh(CONVERSATION_ID, BRANCH_ID);

        assertThat(entryMapper.countEnabledByConversationBranchId(CONVERSATION_ID, BRANCH_ID)).isEqualTo(3);
        assertThat(entryMapper.countEnabledByConversationBranchId(CONVERSATION_ID, 202L)).isEqualTo(4);
        assertThat(entryMapper.listPanelByConversationBranchId(CONVERSATION_ID, 202L))
                .allSatisfy(entry -> {
                    assertThat(entry.getRetiredAt()).isNull();
                    assertThat(entry.getDeletedAt()).isNull();
                });
    }

    @Test
    void historyChange_shouldInvalidatePinnedGeneratedEntryAndAllowReExtraction() {
        AppConversationMemoryEntry entry = insert(BRANCH_ID, "history_fact", "identity", 200, true, 50L);
        properties.setMaxEnabledEntries(1);
        AppConversationMemoryEntry archived = insert(
                BRANCH_ID, "history_archived", "event", 10, false, 40L
        );
        service.enforceAfterRefresh(CONVERSATION_ID, BRANCH_ID);
        assertThat(findById(archived.getId()).getRetiredReason())
                .isEqualTo(ConversationMemoryCapacityService.RETIRED_CAPACITY);
        assertThat(entryMapper.setManualEnabledById(
                entry.getId(), CONVERSATION_ID, BRANCH_ID, true, false
        )).isEqualTo(1);

        AppConversationMemoryEntry modelUpdate = memoryEntry(
                BRANCH_ID,
                "history_fact",
                "identity",
                "模型重新评估但没有显式替代这条用户固定记忆。",
                160,
                true,
                55L
        );
        modelUpdate.setEnabled(false);
        entryMapper.upsert(modelUpdate);
        assertThat(findById(entry.getId()))
                .satisfies(preserved -> {
                    assertThat(preserved.isEnabled()).isTrue();
                    assertThat(preserved.isManualPinned()).isTrue();
                });

        assertThat(entryMapper.softDeleteGeneratedByConversationBranchId(CONVERSATION_ID, BRANCH_ID)).isEqualTo(2);
        assertThat(findById(entry.getId()))
                .satisfies(invalidated -> {
                    assertThat(invalidated.isManualPinned()).isFalse();
                    assertThat(invalidated.getRetiredReason()).isEqualTo("HISTORY_CHANGED");
                    assertThat(invalidated.getDeletedAt()).isNotNull();
                });
        assertThat(findById(archived.getId()))
                .satisfies(invalidated -> {
                    assertThat(invalidated.getRetiredReason()).isEqualTo("HISTORY_CHANGED");
                    assertThat(invalidated.getRetiredAt()).isNotNull();
                    assertThat(invalidated.getDeletedAt()).isNotNull();
                });
        assertThat(entryMapper.listPanelByConversationBranchId(CONVERSATION_ID, BRANCH_ID)).isEmpty();

        AppConversationMemoryEntry refreshed = memoryEntry(
                BRANCH_ID,
                "history_fact",
                "identity",
                "重新整理后确认的身份事实。",
                180,
                true,
                60L
        );
        entryMapper.upsert(refreshed);

        assertThat(findById(entry.getId()))
                .satisfies(restored -> {
                    assertThat(restored.isEnabled()).isTrue();
                    assertThat(restored.isManualPinned()).isFalse();
                    assertThat(restored.getRetiredReason()).isNull();
                    assertThat(restored.getRetiredAt()).isNull();
                    assertThat(restored.getDeletedAt()).isNull();
                });
    }

    private AppConversationMemoryEntry insert(
            long branchId,
            String entryKey,
            String memoryType,
            int priority,
            boolean constant,
            long sourceMessageToId
    ) {
        String content = UUID.nameUUIDFromBytes(
                (branchId + ":" + entryKey).getBytes(StandardCharsets.UTF_8)
        ).toString();
        return insertWithContent(
                branchId,
                entryKey,
                memoryType,
                content,
                priority,
                constant,
                sourceMessageToId
        );
    }

    private AppConversationMemoryEntry insertWithContent(
            long branchId,
            String entryKey,
            String memoryType,
            String content,
            int priority,
            boolean constant,
            long sourceMessageToId
    ) {
        AppConversationMemoryEntry entry = memoryEntry(
                branchId,
                entryKey,
                memoryType,
                content,
                priority,
                constant,
                sourceMessageToId
        );
        entryMapper.upsert(entry);
        assertThat(entry.getId()).isNotNull();
        return entry;
    }

    private static AppConversationMemoryEntry memoryEntry(
            long branchId,
            String entryKey,
            String memoryType,
            String content,
            int priority,
            boolean constant,
            long sourceMessageToId
    ) {
        AppConversationMemoryEntry entry = new AppConversationMemoryEntry();
        entry.setConversationId(CONVERSATION_ID);
        entry.setBranchId(branchId);
        entry.setEntryKey(entryKey);
        entry.setMemoryType(memoryType);
        entry.setTitle(entryKey);
        entry.setContent(content);
        entry.setKeywordsJson("[]");
        entry.setSecondaryKeywordsJson("[]");
        entry.setPriority(priority);
        entry.setPosition("before_char");
        entry.setConstantInjection(constant);
        entry.setSelective(false);
        entry.setEnabled(true);
        entry.setManualDisabled(false);
        entry.setManualDeleted(false);
        entry.setManualPinned(false);
        entry.setConfidence(new BigDecimal("0.90"));
        entry.setSourceMessageToId(sourceMessageToId);
        return entry;
    }

    private AppConversationMemoryEntry findById(long id) {
        List<AppConversationMemoryEntry> rows = entryMapper.listCapacityEntriesForUpdate(CONVERSATION_ID, BRANCH_ID);
        return rows.stream()
                .filter(entry -> entry.getId() != null && entry.getId() == id)
                .findFirst()
                .orElseThrow();
    }
}
