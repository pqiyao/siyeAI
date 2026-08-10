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
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.integration.sillytavern.StModelRoutingService;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.ops.service.UserTtsVoiceService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ApiV1TavernRegenerateCancellationBillingTest {

    private final H5EntitlementService entitlement = mock(H5EntitlementService.class);
    private final H5EntitlementService.AccessTicket ticket = mock(H5EntitlementService.AccessTicket.class);
    private final ApiV1TavernChatController controller = controller(entitlement);

    @Test
    void blockingRegenerateCancellationRefundsDiscardedPartialContent() {
        controller.settleFinalizedChat(
                ApiV1TavernChatController.StreamKind.REGENERATE, ticket, true, true, false, false);

        verify(entitlement).refundDiscardedChat(ticket);
        verify(entitlement, never()).settleBlockingChat(ticket, true, true, false);
    }

    @Test
    void streamingRegenerateCancellationRefundsDiscardedPartialContent() {
        controller.settleFinalizedChat(
                ApiV1TavernChatController.StreamKind.REGENERATE, ticket, true, true, false, true);

        verify(entitlement).refundDiscardedChat(ticket);
        verify(entitlement, never()).settleStreamingChat(ticket, true, false);
    }

    @Test
    void ordinaryStreamingCancellationKeepsExistingPartialSettlementPolicy() {
        controller.settleFinalizedChat(
                ApiV1TavernChatController.StreamKind.GENERATE, ticket, true, true, true, true);

        verify(entitlement).settleStreamingChat(ticket, true, true);
        verify(entitlement, never()).refundDiscardedChat(ticket);
    }

    private static ApiV1TavernChatController controller(H5EntitlementService entitlement) {
        return new ApiV1TavernChatController(
                mock(H5ClientUidAuthService.class),
                mock(AppConversationService.class),
                mock(AppChatService.class),
                mock(ChatGenerationDispatcher.class),
                mock(ChatAuditService.class),
                mock(ChatAudioTranscriptionService.class),
                mock(ChatAudioSpeechService.class),
                mock(MediaConcurrencyGate.class),
                mock(ChatSnapshotService.class),
                mock(AppMessageMapper.class),
                entitlement,
                mock(H5VisitorTrialGuardService.class),
                mock(AppFeatureSettingsService.class),
                mock(H5UserAiProviderService.class),
                mock(StModelRoutingService.class),
                mock(UserTtsVoiceService.class)
        );
    }
}
