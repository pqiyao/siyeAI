package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.service.AppChatService;
import com.example.sillyspringboot.compat.h5.mapper.AppConversationArchiveMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5StAssetUrls;
import com.example.sillyspringboot.compat.h5.service.H5TavernSessionService;
import com.example.sillyspringboot.conversation.dto.ConversationDetailDto;
import com.example.sillyspringboot.conversation.dto.ConversationInboxItemDto;
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiV1TavernInboxControllerSessionDeleteTest {

    @Test
    void deletesOnlyOwnedInactiveSession() {
        Fixture fixture = fixture();
        when(fixture.conversationService.getDetail(11L, "token"))
                .thenReturn(new ConversationDetailDto(11L, 22L, "旧故事", null));
        when(fixture.conversationService.findConversationIdByH5CharacterForSessionCleanup("client", 22L, "token"))
                .thenReturn(12L);

        fixture.controller.deleteOneSession(payload());

        verify(fixture.sessionService).archiveHideWipeAndDetach(7L, 11L);
        verify(fixture.conversationService, never()).activateH5Session(
                "client", 22L, 12L, "token");
    }

    @Test
    void deletingActiveSessionSwitchesToAnotherVisibleStoryFirst() {
        Fixture fixture = fixture();
        when(fixture.conversationService.getDetail(11L, "token"))
                .thenReturn(new ConversationDetailDto(11L, 22L, "当前故事", null));
        when(fixture.conversationService.findConversationIdByH5CharacterForSessionCleanup("client", 22L, "token"))
                .thenReturn(11L);
        when(fixture.conversationService.listInboxForUser("token", 200))
                .thenReturn(List.of(inboxItem(12L, 22L, "备用故事")));

        fixture.controller.deleteOneSession(payload());

        verify(fixture.conversationService).activateH5Session("client", 22L, 12L, "token");
        verify(fixture.sessionService).archiveHideWipeAndDetach(7L, 11L);
    }

    @Test
    void deletingLastActiveSessionDetachesItWithoutRecreatingOrRestarting() {
        Fixture fixture = fixture();
        when(fixture.conversationService.getDetail(11L, "token"))
                .thenReturn(new ConversationDetailDto(11L, 22L, "唯一故事", null));
        when(fixture.conversationService.findConversationIdByH5CharacterForSessionCleanup("client", 22L, "token"))
                .thenReturn(11L);
        when(fixture.conversationService.listInboxForUser("token", 200)).thenReturn(List.of());

        fixture.controller.deleteOneSession(payload());

        verify(fixture.conversationService, never()).activateH5Session(
                org.mockito.ArgumentMatchers.anyString(), anyLong(), anyLong(),
                org.mockito.ArgumentMatchers.anyString());
        verify(fixture.sessionService).archiveHideWipeAndDetach(7L, 11L);
        verify(fixture.sessionService, never()).restartFresh(7L, 11L);
    }

    @Test
    void legacyCharacterDeleteArchivesAndDetachesInsteadOfRestartingActiveStory() {
        Fixture fixture = fixture();
        when(fixture.conversationService.findConversationIdByH5CharacterForSessionCleanup(
                "client", 22L, "token")).thenReturn(11L);

        fixture.controller.delete(null, Map.of("clientUid", "client", "characterId", 22L));

        verify(fixture.sessionService).archiveHideWipeAndDetach(7L, 11L);
        verify(fixture.sessionService, never()).restartFresh(7L, 11L);
        verify(fixture.archiveMapper, never()).deleteByUserAndConversation(7L, 11L);
    }

    private static Map<String, Object> payload() {
        return Map.of("clientUid", "client", "characterId", 22L, "conversationId", 11L);
    }

    private static ConversationInboxItemDto inboxItem(long conversationId, long characterId, String title) {
        LocalDateTime now = LocalDateTime.now();
        return new ConversationInboxItemDto(
                conversationId, characterId, title, now, "角色", "", "", "assistant", "回复", now);
    }

    private static Fixture fixture() {
        H5ClientUidAuthService auth = mock(H5ClientUidAuthService.class);
        AppConversationService conversationService = mock(AppConversationService.class);
        AppTokenService tokenService = mock(AppTokenService.class);
        AppConversationArchiveMapper archiveMapper = mock(AppConversationArchiveMapper.class);
        H5TavernSessionService sessionService = mock(H5TavernSessionService.class);
        when(auth.requireAuthenticatedTokenForClientUid("client")).thenReturn("token");
        AppUser user = new AppUser();
        user.setId(7L);
        when(tokenService.validateAndLoadUser("token")).thenReturn(user);
        ApiV1TavernInboxController controller = new ApiV1TavernInboxController(
                auth,
                conversationService,
                mock(AppMessageMapper.class),
                tokenService,
                archiveMapper,
                sessionService,
                mock(H5StAssetUrls.class),
                mock(AppConversationMemoryService.class),
                mock(AppChatService.class)
        );
        return new Fixture(controller, conversationService, archiveMapper, sessionService);
    }

    private record Fixture(
            ApiV1TavernInboxController controller,
            AppConversationService conversationService,
            AppConversationArchiveMapper archiveMapper,
            H5TavernSessionService sessionService
    ) {
    }
}
