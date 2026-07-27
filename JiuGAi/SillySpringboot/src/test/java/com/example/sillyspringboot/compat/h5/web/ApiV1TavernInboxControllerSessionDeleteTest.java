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
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

        verify(fixture.sessionService).archiveHideAndWipe(7L, 11L);
        verify(fixture.archiveMapper).upsert(7L, 11L);
    }

    @Test
    void refusesToDeleteActiveSession() {
        Fixture fixture = fixture();
        when(fixture.conversationService.getDetail(11L, "token"))
                .thenReturn(new ConversationDetailDto(11L, 22L, "当前故事", null));
        when(fixture.conversationService.findConversationIdByH5CharacterForSessionCleanup("client", 22L, "token"))
                .thenReturn(11L);

        assertThatThrownBy(() -> fixture.controller.deleteOneSession(payload()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先切换到其他故事");

        verify(fixture.sessionService, never()).archiveHideAndWipe(7L, 11L);
        verify(fixture.archiveMapper, never()).upsert(7L, 11L);
    }

    private static Map<String, Object> payload() {
        return Map.of("clientUid", "client", "characterId", 22L, "conversationId", 11L);
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
