package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.entity.AppConversationStBinding;
import com.example.sillyspringboot.conversation.mapper.AppConversationMapper;
import com.example.sillyspringboot.conversation.mapper.AppConversationStBindingMapper;
import com.example.sillyspringboot.conversation.service.ConversationBranchService;
import com.example.sillyspringboot.integration.sillytavern.SillyTavernProperties;
import com.example.sillyspringboot.integration.sillytavern.StAdapter;
import com.example.sillyspringboot.integration.sillytavern.dto.StChatSaveRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSnapshotServiceTest {

    @Test
    void generationSnapshotExcludesOnlyTheCurrentQueuedUserMessage() {
        long conversationId = 10L;
        long branchId = 20L;
        AppConversationStBindingMapper bindingMapper = mock(AppConversationStBindingMapper.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        ConversationBranchService branchService = mock(ConversationBranchService.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        StAdapter stAdapter = mock(StAdapter.class);

        AppConversationStBinding binding = new AppConversationStBinding();
        binding.setConversationId(conversationId);
        binding.setStAvatarUrl("avatar.png");
        binding.setStChatFileName("chat.jsonl");
        when(bindingMapper.findByConversationId(conversationId)).thenReturn(binding);
        when(stAdapter.getChatSnapshot(any())).thenReturn(List.of(Map.of(
                "chat_metadata", Map.of(),
                "user_name", "User",
                "character_name", "Character"
        )));

        AppMessage olderQueued = message(101L, "user", "older queued", "QUEUED");
        AppMessage currentQueued = message(102L, "user", "current queued", "QUEUED");
        AppMessage assistant = message(103L, "assistant", "previous reply", "SUCCESS");
        when(messageMapper.listRecentByConversationBranchAsc(conversationId, branchId, 800))
                .thenReturn(List.of(olderQueued, currentQueued, assistant));

        ChatSnapshotService service = new ChatSnapshotService(
                bindingMapper,
                conversationMapper,
                branchService,
                messageMapper,
                stAdapter,
                new SillyTavernProperties()
        );
        service.saveSnapshotFromDb(conversationId, branchId, 800, "  root:102  ");

        ArgumentCaptor<StChatSaveRequest> captor = ArgumentCaptor.forClass(StChatSaveRequest.class);
        verify(stAdapter).saveChatSnapshot(captor.capture());
        verify(stAdapter, times(1)).getChatSnapshot(any());
        List<Map<String, Object>> savedMessages = captor.getValue().chat().subList(1, captor.getValue().chat().size());
        assertThat(savedMessages).extracting(message -> message.get("mes"))
                .containsExactly("older queued", "previous reply");
        assertThat(savedMessages).extracting(ChatSnapshotServiceTest::messageRef)
                .containsExactly("root:101", "root:103");
    }

    @Test
    void unchangedSnapshotSkipsSave() {
        long conversationId = 11L;
        long branchId = 21L;
        AppConversationStBindingMapper bindingMapper = mock(AppConversationStBindingMapper.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        ConversationBranchService branchService = mock(ConversationBranchService.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        StAdapter stAdapter = mock(StAdapter.class);

        AppConversationStBinding binding = new AppConversationStBinding();
        binding.setConversationId(conversationId);
        binding.setStAvatarUrl("avatar.png");
        binding.setStChatFileName("chat.jsonl");
        when(bindingMapper.findByConversationId(conversationId)).thenReturn(binding);

        Map<String, Object> header = Map.of(
                "chat_metadata", Map.of(),
                "user_name", "User",
                "character_name", "Character"
        );
        Map<String, Object> existingUser = Map.of(
                "is_user", true,
                "mes", "same message",
                "extra", Map.of("message_ref", "root:201")
        );
        when(stAdapter.getChatSnapshot(any())).thenReturn(List.of(header, existingUser));
        when(messageMapper.listRecentByConversationBranchAsc(conversationId, branchId, 800))
                .thenReturn(List.of(message(201L, "user", "same message", "SUCCESS")));

        ChatSnapshotService service = new ChatSnapshotService(
                bindingMapper,
                conversationMapper,
                branchService,
                messageMapper,
                stAdapter,
                new SillyTavernProperties()
        );
        service.saveSnapshotFromDb(conversationId, branchId, 800);

        verify(stAdapter, times(1)).getChatSnapshot(any());
        verify(stAdapter, never()).saveChatSnapshot(any());
    }

    @Test
    void successfulEmptyCanonicalAssistantRemainsInTheStSnapshot() {
        long conversationId = 12L;
        long branchId = 22L;
        AppConversationStBindingMapper bindingMapper = mock(AppConversationStBindingMapper.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        ConversationBranchService branchService = mock(ConversationBranchService.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        StAdapter stAdapter = mock(StAdapter.class);

        AppConversationStBinding binding = new AppConversationStBinding();
        binding.setConversationId(conversationId);
        binding.setStAvatarUrl("avatar.png");
        binding.setStChatFileName("chat.jsonl");
        when(bindingMapper.findByConversationId(conversationId)).thenReturn(binding);
        when(stAdapter.getChatSnapshot(any())).thenReturn(List.of(Map.of(
                "chat_metadata", Map.of(),
                "user_name", "User",
                "character_name", "Character"
        )));

        AppMessage successfulEmpty = message(301L, "assistant", "", "SUCCESS");
        successfulEmpty.setSwipeIndex(0);
        AppMessage stoppedEmpty = message(302L, "assistant", "", "STOPPED");
        when(messageMapper.listRecentByConversationBranchAsc(conversationId, branchId, 800))
                .thenReturn(List.of(successfulEmpty, stoppedEmpty));

        ChatSnapshotService service = new ChatSnapshotService(
                bindingMapper,
                conversationMapper,
                branchService,
                messageMapper,
                stAdapter,
                new SillyTavernProperties()
        );
        service.saveSnapshotFromDb(conversationId, branchId, 800);

        ArgumentCaptor<StChatSaveRequest> captor = ArgumentCaptor.forClass(StChatSaveRequest.class);
        verify(stAdapter).saveChatSnapshot(captor.capture());
        List<Map<String, Object>> savedMessages = captor.getValue().chat().subList(1, captor.getValue().chat().size());
        assertThat(savedMessages).hasSize(1);
        assertThat(savedMessages.get(0).get("mes")).isEqualTo("");
        assertThat(savedMessages.get(0).get("swipes")).isEqualTo(List.of(""));
        assertThat(messageRef(savedMessages.get(0))).isEqualTo("root:301");
    }

    @Test
    void continuationRowsAreFoldedIntoTheAnchorCurrentSwipe() {
        long conversationId = 13L;
        long branchId = 23L;
        AppConversationStBindingMapper bindingMapper = mock(AppConversationStBindingMapper.class);
        AppConversationMapper conversationMapper = mock(AppConversationMapper.class);
        ConversationBranchService branchService = mock(ConversationBranchService.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        StAdapter stAdapter = mock(StAdapter.class);

        AppConversationStBinding binding = new AppConversationStBinding();
        binding.setConversationId(conversationId);
        binding.setStAvatarUrl("avatar.png");
        binding.setStChatFileName("chat.jsonl");
        when(bindingMapper.findByConversationId(conversationId)).thenReturn(binding);
        when(stAdapter.getChatSnapshot(any())).thenReturn(List.of(Map.of(
                "chat_metadata", Map.of(),
                "user_name", "User",
                "character_name", "Character"
        )));

        AppMessage anchor = message(401L, "assistant", "Hello", "SUCCESS");
        anchor.setSwipeIndex(0);
        AppMessage continuation = message(402L, "assistant", " world", "SUCCESS");
        continuation.setMessageKind("CONTINUATION");
        continuation.setContinueFromMessageId(401L);
        continuation.setSwipeIndex(0);
        AppMessage secondContinuation = message(403L, "assistant", "!", "SUCCESS");
        secondContinuation.setMessageKind("CONTINUATION");
        secondContinuation.setContinueFromMessageId(402L);
        secondContinuation.setSwipeIndex(0);
        when(messageMapper.listRecentByConversationBranchAsc(conversationId, branchId, 800))
                .thenReturn(List.of(anchor, continuation, secondContinuation));

        ChatSnapshotService service = new ChatSnapshotService(
                bindingMapper,
                conversationMapper,
                branchService,
                messageMapper,
                stAdapter,
                new SillyTavernProperties()
        );
        service.saveSnapshotFromDb(conversationId, branchId, 800);

        ArgumentCaptor<StChatSaveRequest> captor = ArgumentCaptor.forClass(StChatSaveRequest.class);
        verify(stAdapter).saveChatSnapshot(captor.capture());
        List<Map<String, Object>> savedMessages = captor.getValue().chat().subList(1, captor.getValue().chat().size());
        assertThat(savedMessages).hasSize(1);
        assertThat(savedMessages.get(0).get("mes")).isEqualTo("Hello world!");
        assertThat(savedMessages.get(0).get("swipes")).isEqualTo(List.of("Hello world!"));
        assertThat(messageRef(savedMessages.get(0))).isEqualTo("root:401");
    }

    private static AppMessage message(long id, String role, String content, String status) {
        AppMessage message = new AppMessage();
        message.setId(id);
        message.setRole(role);
        message.setContent(content);
        message.setStatus(status);
        return message;
    }

    private static String messageRef(Map<String, Object> message) {
        Object rawExtra = message.get("extra");
        if (!(rawExtra instanceof Map<?, ?> extra)) {
            return "";
        }
        Object rawRef = extra.get("message_ref");
        return rawRef == null ? "" : String.valueOf(rawRef);
    }
}
