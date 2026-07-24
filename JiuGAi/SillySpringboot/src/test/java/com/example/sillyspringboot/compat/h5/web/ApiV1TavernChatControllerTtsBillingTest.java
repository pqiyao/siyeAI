package com.example.sillyspringboot.compat.h5.web;

import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.chat.service.AppChatService;
import com.example.sillyspringboot.chat.service.ChatAudioSpeechService;
import com.example.sillyspringboot.chat.service.ChatAudioTranscriptionService;
import com.example.sillyspringboot.chat.service.ChatAuditService;
import com.example.sillyspringboot.chat.service.ChatGenerationDispatcher;
import com.example.sillyspringboot.chat.service.MediaConcurrencyGate;
import com.example.sillyspringboot.chat.service.ChatSnapshotService;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.compat.h5.service.H5UserAiProviderService;
import com.example.sillyspringboot.compat.h5.service.H5VisitorTrialGuardService;
import com.example.sillyspringboot.compat.h5.web.dto.H5ChatPayload;
import com.example.sillyspringboot.conversation.service.AppConversationService;
import com.example.sillyspringboot.integration.sillytavern.StModelRoutingService;
import com.example.sillyspringboot.ops.service.AppFeatureSettingsService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiV1TavernChatControllerTtsBillingTest {

    @Mock H5ClientUidAuthService h5Auth;
    @Mock AppConversationService conversationService;
    @Mock AppChatService chatService;
    @Mock ChatGenerationDispatcher dispatcher;
    @Mock ChatAuditService auditService;
    @Mock ChatAudioTranscriptionService chatAudioTranscriptionService;
    @Mock ChatAudioSpeechService chatAudioSpeechService;
    @Mock MediaConcurrencyGate mediaConcurrencyGate;
    @Mock ChatSnapshotService snapshotService;
    @Mock AppMessageMapper messageMapper;
    @Mock H5EntitlementService entitlementService;
    @Mock H5VisitorTrialGuardService visitorTrialGuardService;
    @Mock AppFeatureSettingsService featureSettingsService;
    @Mock H5UserAiProviderService userAiProviderService;
    @Mock StModelRoutingService modelRoutingService;

    private ApiV1TavernChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ApiV1TavernChatController(
                h5Auth,
                conversationService,
                chatService,
                dispatcher,
                auditService,
                chatAudioTranscriptionService,
                chatAudioSpeechService,
                mediaConcurrencyGate,
                snapshotService,
                messageMapper,
                entitlementService,
                visitorTrialGuardService,
                featureSettingsService,
                userAiProviderService,
                modelRoutingService
        );
    }

    @Test
    void refundsReservedWalletChargeWhenUserResolutionFails() {
        H5ChatPayload payload = new H5ChatPayload();
        payload.setClientUid("client-1");
        payload.setContent("hello");
        H5EntitlementService.AccessTicket ticket = new H5EntitlementService.AccessTicket(
                7L,
                "client-1",
                false,
                0,
                H5EntitlementService.QuotaBucket.OFFICIAL_CHAT,
                null,
                "TTS",
                true,
                10,
                2,
                "tts-1"
        );
        when(h5Auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token-1");
        when(entitlementService.guardTts("client-1")).thenReturn(ticket);
        when(entitlementService.recordSuccessfulTts(ticket)).thenReturn(true);
        when(chatService.resolveUserId("token-1"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "token expired"));

        assertThatThrownBy(() -> controller.synthesizeSpeech(payload))
                .isInstanceOf(BusinessException.class)
                .hasMessage("token expired");

        InOrder billingOrder = inOrder(entitlementService);
        billingOrder.verify(entitlementService).recordSuccessfulTts(ticket);
        billingOrder.verify(entitlementService).refundWalletConsume(ticket);
        verify(chatAudioSpeechService, never()).synthesizeForUser(
                7L, "hello", null, null, null
        );
    }

    @Test
    void allSegmentsUseOneRequestIdAndLaterFailureDoesNotRefund() {
        H5EntitlementService.AccessTicket ticket = walletTicket("tts:7:stable");
        when(h5Auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token-1");
        when(entitlementService.guardTts("client-1", "tts_db_44_12345678")).thenReturn(ticket);
        when(entitlementService.recordSuccessfulTts(ticket)).thenReturn(true, false);
        when(chatService.resolveUserId("token-1")).thenReturn(7L);
        when(chatAudioSpeechService.synthesizeForUser(eq(7L), eq("first"), eq(null), eq(null), eq(null)))
                .thenReturn(new ChatAudioSpeechService.AudioSpeechResult(new byte[]{1}, "audio/mpeg", "tts-model", "alloy"));
        when(chatAudioSpeechService.synthesizeForUser(eq(7L), eq("second"), eq(null), eq(null), eq(null)))
                .thenThrow(new BusinessException(ErrorCode.UPSTREAM_ERROR, "upstream failed"));

        H5ChatPayload first = segmentPayload("first", 0, 2);
        H5ChatPayload second = segmentPayload("second", 1, 2);
        assertThat(controller.synthesizeSpeech(first).data()).containsEntry("modelName", "tts-model");
        assertThatThrownBy(() -> controller.synthesizeSpeech(second)).hasMessage("upstream failed");

        verify(entitlementService, times(2)).guardTts("client-1", "tts_db_44_12345678");
        verify(entitlementService, times(2)).recordSuccessfulTts(ticket);
        verify(entitlementService, never()).refundWalletConsume(ticket);
    }

    @Test
    void firstSegmentFailureRefundsOnce() {
        H5EntitlementService.AccessTicket ticket = walletTicket("tts:7:first");
        when(h5Auth.requireAuthenticatedTokenForClientUid("client-1")).thenReturn("token-1");
        when(entitlementService.guardTts("client-1", "tts_db_44_12345678")).thenReturn(ticket);
        when(entitlementService.recordSuccessfulTts(ticket)).thenReturn(true);
        when(chatService.resolveUserId("token-1")).thenReturn(7L);
        when(chatAudioSpeechService.synthesizeForUser(eq(7L), eq("first"), eq(null), eq(null), eq(null)))
                .thenThrow(new BusinessException(ErrorCode.UPSTREAM_ERROR, "upstream failed"));

        assertThatThrownBy(() -> controller.synthesizeSpeech(segmentPayload("first", 0, 2)))
                .hasMessage("upstream failed");

        verify(entitlementService).refundWalletConsume(ticket);
    }

    private static H5ChatPayload segmentPayload(String content, int index, int count) {
        H5ChatPayload payload = new H5ChatPayload();
        payload.setClientUid("client-1");
        payload.setContent(content);
        payload.setTtsRequestId("tts_db_44_12345678");
        payload.setTtsSegmentIndex(index);
        payload.setTtsSegmentCount(count);
        return payload;
    }

    private static H5EntitlementService.AccessTicket walletTicket(String bizRef) {
        return new H5EntitlementService.AccessTicket(
                7L, "client-1", false, 0, H5EntitlementService.QuotaBucket.OFFICIAL_CHAT,
                null, "TTS", true, 10, 2, bizRef
        );
    }
}
