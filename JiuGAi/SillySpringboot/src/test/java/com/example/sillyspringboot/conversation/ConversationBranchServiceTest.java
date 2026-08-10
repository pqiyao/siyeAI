package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.entity.AppMessageSegment;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.mapper.AppMessageSegmentMapper;
import com.example.sillyspringboot.conversation.entity.AppConversation;
import com.example.sillyspringboot.conversation.entity.AppConversationBranch;
import com.example.sillyspringboot.conversation.mapper.AppConversationBranchMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.service.ConversationBranchService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryCleanupService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationBranchServiceTest {

    @Test
    void selectingExistingOpeningReturnsToItsIsolatedHistory() {
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationBranchService service = service(conversationMapper, branchMapper, messageMapper);

        AppConversation conversation = conversation(1L, 10L, 20L);
        AppConversationBranch current = branch(20L, 1L, 10L, 0);
        AppConversationBranch alternate = branch(30L, 1L, 10L, 1);
        when(branchMapper.findByIdForConversation(1L, 20L)).thenReturn(current);
        when(branchMapper.listByConversationId(1L)).thenReturn(List.of(current, alternate));
        when(branchMapper.findByConversationIdAndOpeningVariantIndex(1L, 1)).thenReturn(alternate);
        when(branchMapper.findByIdForConversation(1L, 30L)).thenReturn(alternate);

        AppConversationBranch selected = service.selectOpeningBranch(
                conversation,
                1,
                List.of("first opening", "second opening")
        );

        assertThat(selected.getId()).isEqualTo(30L);
        verify(conversationMapper).setActiveBranchId(1L, 30L);
        verify(branchMapper).touch(30L);
        verify(messageMapper, never()).insert(any(AppMessage.class));
    }

    @Test
    void selectingNewOpeningCreatesOnlyThatUsersOpeningTimeline() {
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationBranchService service = service(conversationMapper, branchMapper, messageMapper);

        AppConversation conversation = conversation(1L, 10L, 20L);
        AppConversationBranch current = branch(20L, 1L, 10L, 0);
        when(branchMapper.findByIdForConversation(1L, 20L)).thenReturn(current);
        when(branchMapper.listByConversationId(1L)).thenReturn(List.of(current));
        when(branchMapper.findByConversationIdAndOpeningVariantIndex(1L, 1)).thenReturn(null);
        when(branchMapper.incrementMemorySourceRevision(1L, 30L)).thenReturn(1);
        doAnswer(invocation -> {
            AppConversationBranch inserted = invocation.getArgument(0);
            inserted.setId(30L);
            return null;
        }).when(branchMapper).insert(any(AppConversationBranch.class));
        AtomicLong messageIds = new AtomicLong(100L);
        doAnswer(invocation -> {
            AppMessage inserted = invocation.getArgument(0);
            inserted.setId(messageIds.getAndIncrement());
            return null;
        }).when(messageMapper).insert(any(AppMessage.class));

        AppConversationBranch selected = service.selectOpeningBranch(
                conversation,
                1,
                List.of("first opening", "second opening")
        );

        assertThat(selected.getId()).isEqualTo(30L);
        assertThat(selected.getConversationId()).isEqualTo(1L);
        assertThat(selected.getUserId()).isEqualTo(10L);
        assertThat(selected.getOpeningVariantIndex()).isEqualTo(1);
        verify(conversationMapper).setActiveBranchId(1L, 30L);
        verify(branchMapper).incrementMemorySourceRevision(1L, 30L);

        ArgumentCaptor<AppMessage> messageCaptor = ArgumentCaptor.forClass(AppMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(messageCaptor.capture());
        List<AppMessage> insertedMessages = messageCaptor.getAllValues();
        AppMessage root = insertedMessages.get(0);
        AppMessage hiddenCandidate = insertedMessages.get(1);

        assertThat(root.getUserId()).isEqualTo(10L);
        assertThat(root.getConversationId()).isEqualTo(1L);
        assertThat(root.getBranchId()).isEqualTo(30L);
        assertThat(root.getContent()).isEqualTo("second opening");
        assertThat(root.getSwipeIndex()).isEqualTo(1);
        assertThat(root.getStMessageRef()).isEqualTo("root:100");

        assertThat(hiddenCandidate.getUserId()).isEqualTo(10L);
        assertThat(hiddenCandidate.getConversationId()).isEqualTo(1L);
        assertThat(hiddenCandidate.getBranchId()).isEqualTo(30L);
        assertThat(hiddenCandidate.getParentMessageId()).isEqualTo(100L);
        assertThat(hiddenCandidate.getContent()).isEqualTo("first opening");
        assertThat(hiddenCandidate.getSwipeIndex()).isZero();
        assertThat(hiddenCandidate.getStMessageRef()).isEqualTo("root:100");
    }

    @Test
    void cannotSwitchToOpeningBranchOwnedByAnotherUser() {
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationBranchService service = service(conversationMapper, branchMapper, messageMapper);

        AppConversation conversation = conversation(1L, 10L, 20L);
        AppConversationBranch current = branch(20L, 1L, 10L, 0);
        AppConversationBranch otherUsersBranch = branch(30L, 1L, 99L, 1);
        when(branchMapper.findByIdForConversation(1L, 20L)).thenReturn(current);
        when(branchMapper.listByConversationId(1L)).thenReturn(List.of(current, otherUsersBranch));
        when(branchMapper.findByConversationIdAndOpeningVariantIndex(1L, 1)).thenReturn(otherUsersBranch);
        when(branchMapper.findByIdForConversation(1L, 30L)).thenReturn(otherUsersBranch);

        assertThatThrownBy(() -> service.selectOpeningBranch(
                conversation,
                1,
                List.of("first opening", "second opening")
        )).isInstanceOf(com.example.sillyspringboot.shared.error.BusinessException.class);

        verify(conversationMapper, never()).setActiveBranchId(1L, 30L);
        verify(messageMapper, never()).insert(any(AppMessage.class));
    }

    @Test
    void activeBranchOwnedByAnotherUserFallsBackToOwnedDefault() {
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationBranchService service = service(conversationMapper, branchMapper, messageMapper);

        AppConversation conversation = conversation(1L, 10L, 30L);
        AppConversationBranch otherUsersActive = branch(30L, 1L, 99L, 1);
        AppConversationBranch ownedDefault = branch(20L, 1L, 10L, 0);
        when(branchMapper.findByIdForConversation(1L, 30L)).thenReturn(otherUsersActive);
        when(branchMapper.findDefaultByConversationId(1L)).thenReturn(ownedDefault);

        AppConversationBranch active = service.requireActiveBranch(conversation);

        assertThat(active.getId()).isEqualTo(20L);
        assertThat(active.getUserId()).isEqualTo(10L);
        assertThat(conversation.getActiveBranchId()).isEqualTo(20L);
        verify(conversationMapper).setActiveBranchId(1L, 20L);
    }

    @Test
    void defaultBranchOwnedByAnotherUserIsRejected() {
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationBranchService service = service(conversationMapper, branchMapper, messageMapper);

        when(branchMapper.findDefaultByConversationId(1L)).thenReturn(branch(20L, 1L, 99L, 0));

        assertThatThrownBy(() -> service.ensureDefaultBranch(1L, 10L))
                .isInstanceOf(com.example.sillyspringboot.shared.error.BusinessException.class);
        verify(branchMapper, never()).insert(any(AppConversationBranch.class));
    }

    @Test
    void legacyBranchBindsToTheOpeningItAlreadyUses() {
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationBranchService service = service(conversationMapper, branchMapper, messageMapper);

        AppConversation conversation = conversation(1L, 10L, 20L);
        AppConversationBranch legacy = branch(20L, 1L, 10L, null);
        AppMessage opening = new AppMessage();
        opening.setContent("second opening");
        when(branchMapper.findByIdForConversation(1L, 20L)).thenReturn(legacy);
        when(branchMapper.listByConversationId(1L)).thenReturn(List.of(legacy));
        when(messageMapper.findOpeningByConversationBranch(1L, 20L)).thenReturn(opening);

        List<AppConversationBranch> reconciled = service.reconcileOpeningVariantBindings(
                conversation,
                List.of("first opening", "second opening")
        );

        assertThat(reconciled).hasSize(1);
        assertThat(reconciled.get(0).getOpeningVariantIndex()).isEqualTo(1);
        verify(branchMapper).setOpeningVariantIndex(20L, 1);
    }

    @Test
    void forkCopiesSegmentsForSelectedRootAndHiddenSwipeVariants() {
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppMessageSegmentMapper segmentMapper = mock(AppMessageSegmentMapper.class);
        ConversationBranchService service = new ConversationBranchService(
                conversationMapper, branchMapper, messageMapper, segmentMapper,
                mock(ConversationMemoryCleanupService.class));

        AppConversation conversation = conversation(1L, 10L, 20L);
        AppConversationBranch active = branch(20L, 1L, 10L, 0);
        when(branchMapper.findByIdForConversation(1L, 20L)).thenReturn(active);
        when(branchMapper.incrementMemorySourceRevision(1L, 30L)).thenReturn(1);
        doAnswer(invocation -> {
            AppConversationBranch inserted = invocation.getArgument(0);
            inserted.setId(30L);
            return null;
        }).when(branchMapper).insert(any(AppConversationBranch.class));

        AppMessage root = assistantMessage(50L, 20L, "root:50", 1, "【小夏】\n当前版本");
        AppMessage alternate = assistantMessage(51L, 20L, "root:50", 0, "【林澈】\n备选版本");
        when(messageMapper.findById(50L)).thenReturn(root);
        when(messageMapper.listByConversationBranchAsc(1L, 20L, 20000)).thenReturn(List.of(root, alternate));
        when(messageMapper.findByStMessageRefAndSwipeIndexAndBranch("root:50", 0, 20L)).thenReturn(alternate);
        when(segmentMapper.listByMessageId(50L)).thenReturn(List.of(segment(50L, 101L, "小夏", "当前版本")));
        when(segmentMapper.listByMessageId(51L)).thenReturn(List.of(segment(51L, 102L, "林澈", "备选版本")));

        AtomicLong ids = new AtomicLong(100L);
        doAnswer(invocation -> {
            AppMessage inserted = invocation.getArgument(0);
            inserted.setId(ids.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(AppMessage.class));

        AppConversationBranch fork = service.forkFromMessage(conversation, 50L, 0, "备选线");

        assertThat(fork.getId()).isEqualTo(30L);
        ArgumentCaptor<AppMessageSegment> segmentCaptor = ArgumentCaptor.forClass(AppMessageSegment.class);
        verify(segmentMapper, times(2)).insert(segmentCaptor.capture());
        List<AppMessageSegment> copied = segmentCaptor.getAllValues();
        assertThat(copied.get(0).getMessageId()).isEqualTo(100L);
        assertThat(copied.get(0).getSpeakerMemberId()).isEqualTo(102L);
        assertThat(copied.get(0).getSpeakerNameSnapshot()).isEqualTo("林澈");
        assertThat(copied.get(0).getContent()).isEqualTo("备选版本");
        assertThat(copied.get(1).getMessageId()).isEqualTo(101L);
        assertThat(copied.get(1).getSpeakerMemberId()).isEqualTo(101L);
        assertThat(copied.get(1).getSpeakerNameSnapshot()).isEqualTo("小夏");
        assertThat(copied.get(1).getContent()).isEqualTo("当前版本");
        verify(conversationMapper).setActiveBranchId(1L, 30L);
    }

    @Test
    void deletingBranchClearsItsMemoryAfterBranchIsSoftDeleted() {
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        AppConversationBranchMapper branchMapper = mock(AppConversationBranchMapper.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        ConversationMemoryCleanupService cleanupService = mock(ConversationMemoryCleanupService.class);
        ConversationBranchService service = new ConversationBranchService(
                conversationMapper,
                branchMapper,
                messageMapper,
                mock(AppMessageSegmentMapper.class),
                cleanupService
        );

        AppConversation conversation = conversation(1L, 10L, 30L);
        AppConversationBranch target = branch(30L, 1L, 10L, 1);
        target.setParentBranchId(20L);
        AppConversationBranch parent = branch(20L, 1L, 10L, 0);
        when(branchMapper.findByIdForConversation(1L, 30L)).thenReturn(target);
        when(branchMapper.findByIdForConversation(1L, 20L)).thenReturn(parent);
        when(branchMapper.softDelete(1L, 30L)).thenReturn(1);

        AppConversationBranch fallback = service.deleteBranch(conversation, 30L);

        assertThat(fallback.getId()).isEqualTo(20L);
        verify(branchMapper).softDelete(1L, 30L);
        verify(cleanupService).clearBranchMemory(1L, 30L, 10L);
        verify(conversationMapper).setActiveBranchId(1L, 20L);
    }

    private static AppConversation conversation(long id, long userId, long activeBranchId) {
        AppConversation conversation = new AppConversation();
        conversation.setId(id);
        conversation.setUserId(userId);
        conversation.setActiveBranchId(activeBranchId);
        return conversation;
    }

    private static ConversationBranchService service(
            AppConversationMapper conversationMapper,
            AppConversationBranchMapper branchMapper,
            AppMessageMapper messageMapper
    ) {
        return new ConversationBranchService(
                conversationMapper,
                branchMapper,
                messageMapper,
                mock(AppMessageSegmentMapper.class),
                mock(ConversationMemoryCleanupService.class)
        );
    }

    private static AppMessage assistantMessage(
            long id,
            long branchId,
            String ref,
            int swipeIndex,
            String content
    ) {
        AppMessage message = new AppMessage();
        message.setId(id);
        message.setUserId(10L);
        message.setConversationId(1L);
        message.setBranchId(branchId);
        message.setRole("assistant");
        message.setContent(content);
        message.setStMessageRef(ref);
        message.setSwipeIndex(swipeIndex);
        message.setStatus("SUCCESS");
        return message;
    }

    private static AppMessageSegment segment(long messageId, long memberId, String name, String content) {
        AppMessageSegment segment = new AppMessageSegment();
        segment.setMessageId(messageId);
        segment.setSegmentIndex(0);
        segment.setSegmentType("CHARACTER");
        segment.setSpeakerMemberId(memberId);
        segment.setSpeakerNameSnapshot(name);
        segment.setSpeakerAvatarSnapshot("/" + name + ".png");
        segment.setContent(content);
        segment.setStatus("SUCCESS");
        return segment;
    }

    private static AppConversationBranch branch(
            long id,
            long conversationId,
            long userId,
            Integer openingVariantIndex
    ) {
        AppConversationBranch branch = new AppConversationBranch();
        branch.setId(id);
        branch.setConversationId(conversationId);
        branch.setUserId(userId);
        branch.setOpeningVariantIndex(openingVariantIndex);
        branch.setDefaultBranch(id == 20L);
        return branch;
    }
}
