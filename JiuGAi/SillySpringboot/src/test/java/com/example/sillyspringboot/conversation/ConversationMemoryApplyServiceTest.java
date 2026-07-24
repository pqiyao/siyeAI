package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.config.MemoryLlmProperties;
import com.example.sillyspringboot.conversation.dto.ConversationMemoryRefreshSnapshot;
import com.example.sillyspringboot.conversation.dto.ExtractedMemoryEntry;
import com.example.sillyspringboot.conversation.dto.StructuredMemoryExtraction;
import com.example.sillyspringboot.conversation.entity.AppConversationBranch;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.mapper.AppConversationBranchMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryEntryMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.ConversationMemoryApplyService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryCapacityService;
import com.example.sillyspringboot.conversation.service.ConversationMemorySanitizer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversationMemoryApplyServiceTest {

    @Test
    void sourceChangedDuringLlmReturnsStaleWithoutEntryWrites() {
        Harness harness = harness(11L, 4L, 7L);

        ConversationMemoryApplyService.ApplyStatus status = harness.service.applyStructured(
                snapshot(10L, 4L, 7L),
                extraction("identity_name")
        );

        assertThat(status).isEqualTo(ConversationMemoryApplyService.ApplyStatus.STALE);
        verifyNoInteractions(harness.entryMapper, harness.capacityService);
        verify(harness.memoryMapper, never()).updateRefreshStateWithRevision(
                any(Long.class), any(Long.class), any(), any(Integer.class), any(Integer.class), any(Integer.class),
                any(), any(Integer.class), any(), any(Long.class), any(Long.class), any(Long.class)
        );
    }

    @Test
    void manualOperationDuringLlmInvalidatesStructuredAndRollupPaths() {
        Harness structured = harness(10L, 5L, 7L);
        assertThat(structured.service.applyStructured(snapshot(10L, 4L, 7L), extraction("identity_name")))
                .isEqualTo(ConversationMemoryApplyService.ApplyStatus.STALE);
        verifyNoInteractions(structured.entryMapper, structured.capacityService);

        Harness rollup = harness(10L, 5L, 7L);
        assertThat(rollup.service.applyRollup(snapshot(10L, 4L, 7L), "旧摘要", 1))
                .isEqualTo(ConversationMemoryApplyService.ApplyStatus.STALE);
        verifyNoInteractions(rollup.entryMapper, rollup.capacityService);
    }

    @Test
    void partialEntryFailureDoesNotAdvanceMemoryState() {
        Harness harness = harness(10L, 4L, 7L);
        when(harness.entryMapper.listAllIncludingDeletedForUpdate(100L, 200L)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("write failed"))
                .when(harness.entryMapper).upsert(any());

        assertThatThrownBy(() -> harness.service.applyStructured(
                snapshot(10L, 4L, 7L),
                extraction("identity_name")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("write failed");

        verify(harness.capacityService, never()).enforceAfterRefresh(100L, 200L);
        verify(harness.memoryMapper, never()).updateRefreshStateWithRevision(
                any(Long.class), any(Long.class), any(), any(Integer.class), any(Integer.class), any(Integer.class),
                any(), any(Integer.class), any(), any(Long.class), any(Long.class), any(Long.class)
        );
        assertThat(transactionalApplyMethod()).isTrue();
    }

    private static boolean transactionalApplyMethod() {
        try {
            return ConversationMemoryApplyService.class
                    .getMethod(
                            "applyStructured",
                            ConversationMemoryRefreshSnapshot.class,
                            StructuredMemoryExtraction.class
                    )
                    .isAnnotationPresent(Transactional.class);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }

    private static Harness harness(long sourceRevision, long manualRevision, long memoryRevision) {
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        AppConversationMemoryEntryMapper entryMapper = mock(AppConversationMemoryEntryMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationMemoryCapacityService capacityService = mock(ConversationMemoryCapacityService.class);
        MemoryLlmProperties properties = new MemoryLlmProperties();

        AppConversationBranch branch = new AppConversationBranch();
        branch.setId(200L);
        branch.setConversationId(100L);
        branch.setMemorySourceRevision(sourceRevision);
        AppConversationMemory memory = new AppConversationMemory();
        memory.setConversationId(100L);
        memory.setBranchId(200L);
        memory.setManualRevision(manualRevision);
        memory.setMemoryRevision(memoryRevision);
        when(branchMapper.findByIdForConversationForUpdate(100L, 200L)).thenReturn(branch);
        when(memoryMapper.findByConversationBranchIdForUpdate(100L, 200L)).thenReturn(memory);

        ConversationMemoryApplyService service = new ConversationMemoryApplyService(
                branchMapper,
                memoryMapper,
                entryMapper,
                messageMapper,
                new ConversationMemorySanitizer(properties),
                capacityService
        );
        return new Harness(service, memoryMapper, entryMapper, capacityService);
    }

    private static ConversationMemoryRefreshSnapshot snapshot(
            long sourceRevision,
            long manualRevision,
            long memoryRevision
    ) {
        return new ConversationMemoryRefreshSnapshot(
                100L,
                200L,
                sourceRevision,
                manualRevision,
                memoryRevision,
                1,
                "",
                0,
                List.of(new ConversationMemoryRefreshSnapshot.MessageSnapshot(
                        300L, "user", "SUCCESS", "以后叫我阿曜", null
                )),
                List.of()
        );
    }

    private static StructuredMemoryExtraction extraction(String key) {
        return new StructuredMemoryExtraction(
                "用户希望被称为阿曜",
                List.of(new ExtractedMemoryEntry(
                        key,
                        "identity",
                        "用户称呼",
                        "用户希望角色称呼他为阿曜。",
                        List.of("阿曜", "称呼"),
                        List.of(),
                        200,
                        "before_char",
                        true,
                        false,
                        true,
                        BigDecimal.valueOf(0.95),
                        List.of()
                )),
                List.of()
        );
    }

    private record Harness(
            ConversationMemoryApplyService service,
            AppConversationMemoryMapper memoryMapper,
            AppConversationMemoryEntryMapper entryMapper,
            ConversationMemoryCapacityService capacityService
    ) {
    }
}
