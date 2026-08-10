package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.chat.entity.AppMessage;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.service.AppChatService;
import com.example.sillyspringboot.chat.service.ChatSnapshotService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.conversation.dto.ConversationDetailDto;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryAutoRefreshService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiV1TavernMessageOpsControllerEnsembleTest {

    @Test
    void swipeReturnsSegmentsFromTheActivatedTargetVariant() {
        H5ClientUidAuthService auth = mock(H5ClientUidAuthService.class);
        AppTokenService tokenService = mock(AppTokenService.class);
        AppConversationService conversationService = mock(AppConversationService.class);
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppChatService chatService = mock(AppChatService.class);
        ChatSnapshotService snapshotService = mock(ChatSnapshotService.class);
        ConversationMemoryAutoRefreshService memoryRefresh = mock(ConversationMemoryAutoRefreshService.class);
        ApiV1TavernMessageOpsController controller = new ApiV1TavernMessageOpsController(
                auth, tokenService, conversationService, messageMapper, chatService, snapshotService, memoryRefresh);

        AppUser user = new AppUser();
        user.setId(7L);
        AppMessage current = assistant(10L, 5L, "旧回复", 0);
        AppMessage target = assistant(11L, 5L, "【顾言】\n新回复", 1);
        AppMessage activated = assistant(10L, 5L, "【顾言】\n新回复", 1);
        List<Map<String, Object>> targetSegments = List.of(Map.of(
                "index", 0,
                "type", "CHARACTER",
                "speakerMemberId", 102L,
                "speakerName", "顾言",
                "speakerAvatarUrl", "/avatars/guyan.png",
                "content", "新回复"
        ));

        when(auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token");
        when(conversationService.findDetailByH5Character("client-1", 99L, "token"))
                .thenReturn(new ConversationDetailDto(3L, 99L, "多人故事", null));
        when(chatService.requireActiveBranchId(3L, "token")).thenReturn(5L);
        when(tokenService.validateAndLoadUser("token")).thenReturn(user);
        when(messageMapper.findById(10L)).thenReturn(current, activated);
        when(messageMapper.listByStMessageRefAndBranch("root:10", 5L))
                .thenReturn(List.of(current, target), List.of(activated, target));
        when(messageMapper.findByStMessageRefAndSwipeIndexAndBranch("root:10", 1, 5L))
                .thenReturn(target);
        when(chatService.messageSegments(10L)).thenReturn(targetSegments);

        ApiV1Result<Map<String, Object>> result = controller.swipe(Map.of(
                "characterId", 99L,
                "clientUid", "client-1",
                "messageId", "db_10",
                "delta", 1
        ));

        assertThat(result.data()).containsEntry("text", "【顾言】\n新回复");
        assertThat(result.data().get("segments")).isEqualTo(targetSegments);
        verify(chatService).activateEnsembleVariantInCurrentTransaction(
                99L, 11L, 10L, "【顾言】\n新回复", 3L);
        verify(chatService).messageSegments(10L);
    }

    private static AppMessage assistant(long id, long branchId, String content, int swipeIndex) {
        AppMessage message = new AppMessage();
        message.setId(id);
        message.setConversationId(3L);
        message.setBranchId(branchId);
        message.setRole("assistant");
        message.setContent(content);
        message.setStatus("SUCCESS");
        message.setStMessageRef("root:10");
        message.setSwipeIndex(swipeIndex);
        return message;
    }
}
