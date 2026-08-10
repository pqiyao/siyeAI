package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.service.AppChatService;
import com.example.sillyspringboot.chat.service.ChatAudioSpeechService;
import com.example.sillyspringboot.chat.service.ChatAudioTranscriptionService;
import com.example.sillyspringboot.chat.service.ChatAuditService;
import com.example.sillyspringboot.chat.service.ChatGenerationDispatcher;
import com.example.sillyspringboot.chat.service.ChatSnapshotService;
import com.example.sillyspringboot.chat.service.MediaConcurrencyGate;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.compat.h5.service.H5VisitorTrialGuardService;
import com.example.sillyspringboot.compat.h5.web.dto.H5ChatPayload;
import com.example.sillyspringboot.conversation.dto.ConversationDetailDto;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.integration.sillytavern.StModelRoutingService;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.EntitlementPolicyService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.ops.service.UserTtsVoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiV1TavernReplySuggestionsBillingTest {

    private final H5ClientUidAuthService auth = mock(H5ClientUidAuthService.class);
    private final AppConversationService conversations = mock(AppConversationService.class);
    private final AppChatService chat = mock(AppChatService.class);
    private final H5EntitlementService entitlement = mock(H5EntitlementService.class);
    private final H5VisitorTrialGuardService visitorGuard = mock(H5VisitorTrialGuardService.class);
    private final H5EntitlementService.AccessTicket ticket = mock(H5EntitlementService.AccessTicket.class);
    private ApiV1TavernChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ApiV1TavernChatController(
                auth,
                conversations,
                chat,
                mock(ChatGenerationDispatcher.class),
                mock(ChatAuditService.class),
                mock(ChatAudioTranscriptionService.class),
                mock(ChatAudioSpeechService.class),
                mock(MediaConcurrencyGate.class),
                mock(ChatSnapshotService.class),
                mock(AppMessageMapper.class),
                entitlement,
                visitorGuard,
                mock(AppFeatureSettingsService.class),
                mock(H5UserAiProviderService.class),
                mock(StModelRoutingService.class),
                mock(UserTtsVoiceService.class)
        );
        when(auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token");
        when(conversations.findDetailByH5Character("client-1", 7L, "token"))
                .thenReturn(new ConversationDetailDto(99L, 7L, "chat", null));
        when(entitlement.guardChat(
                "client-1", 7L, EntitlementPolicyService.ChatQuotaAction.GENERATE,
                "suggest-request-1", 99L, null)).thenReturn(ticket);
    }

    @Test
    void successfulSuggestionsCompleteTheReservedTicket() {
        when(chat.suggestReplies(99L, "token", "draft")).thenReturn(List.of("reply"));

        ApiV1Result<java.util.Map<String, Object>> result = controller.replySuggestions(payload());

        assertEquals(List.of("reply"), result.data().get("suggestions"));
        verify(entitlement).recordSuccessfulChat(ticket, true);
    }

    @Test
    void failedSuggestionsRefundTheReservedTicket() {
        RuntimeException failure = new RuntimeException("upstream failed");
        when(chat.suggestReplies(99L, "token", "draft")).thenThrow(failure);

        assertThrows(RuntimeException.class, () -> controller.replySuggestions(payload()));

        verify(entitlement).refundFailedChat(ticket, false);
    }

    private static H5ChatPayload payload() {
        H5ChatPayload payload = new H5ChatPayload();
        payload.setCharacterId(7L);
        payload.setClientUid("client-1");
        payload.setContent("draft");
        payload.setGenerationRequestId("suggest-request-1");
        return payload;
    }
}
