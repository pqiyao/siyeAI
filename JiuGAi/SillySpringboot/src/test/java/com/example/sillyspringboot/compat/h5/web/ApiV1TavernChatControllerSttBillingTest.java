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
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiV1TavernChatControllerSttBillingTest {

    private static final String CLIENT_UID = "client-1";
    private static final String REQUEST_ID = "stt_task_12345678";

    @Mock H5ClientUidAuthService h5Auth;
    @Mock AppConversationService conversationService;
    @Mock AppChatService chatService;
    @Mock ChatGenerationDispatcher dispatcher;
    @Mock ChatAuditService auditService;
    @Mock ChatAudioTranscriptionService transcriptionService;
    @Mock ChatAudioSpeechService speechService;
    @Mock MediaConcurrencyGate mediaConcurrencyGate;
    @Mock ChatSnapshotService snapshotService;
    @Mock AppMessageMapper messageMapper;
    @Mock H5EntitlementService entitlementService;
    @Mock H5VisitorTrialGuardService visitorTrialGuardService;
    @Mock AppFeatureSettingsService featureSettingsService;
    @Mock H5UserAiProviderService userAiProviderService;
    @Mock StModelRoutingService modelRoutingService;
    @Mock UserTtsVoiceService userTtsVoiceService;
    @Mock MediaConcurrencyGate.Lease lease;

    private ApiV1TavernChatController controller;
    private MockMultipartFile audio;
    private H5EntitlementService.AccessTicket ticket;

    @BeforeEach
    void setUp() {
        controller = new ApiV1TavernChatController(
                h5Auth,
                conversationService,
                chatService,
                dispatcher,
                auditService,
                transcriptionService,
                speechService,
                mediaConcurrencyGate,
                snapshotService,
                messageMapper,
                entitlementService,
                visitorTrialGuardService,
                featureSettingsService,
                userAiProviderService,
                modelRoutingService,
                userTtsVoiceService
        );
        audio = new MockMultipartFile("file", "voice.wav", "audio/wav", new byte[]{1, 2, 3});
        ticket = new H5EntitlementService.AccessTicket(
                7L, CLIENT_UID, false, 0, H5EntitlementService.QuotaBucket.OFFICIAL_CHAT,
                null, "STT", true, 8, 1, "stt:7:task"
        );
        when(h5Auth.requireAuthenticatedTokenForClientUid(CLIENT_UID)).thenReturn("token-1");
        when(chatService.resolveUserId("token-1")).thenReturn(7L);
        when(entitlementService.guardStt(CLIENT_UID, REQUEST_ID)).thenReturn(ticket);
        when(entitlementService.reserveSttCharge(ticket)).thenReturn(true);
    }

    @Test
    void successfulTranscriptionKeepsSingleReservedCharge() {
        when(mediaConcurrencyGate.acquire(MediaConcurrencyGate.Capability.STT, 7L, REQUEST_ID)).thenReturn(lease);
        when(transcriptionService.transcribeForUser(7L, audio)).thenReturn(
                new ChatAudioTranscriptionService.AudioTranscriptionResult("hello", "whisper-1", null)
        );

        assertThat(controller.transcribeAudio(audio, CLIENT_UID, REQUEST_ID).data())
                .containsEntry("text", "hello")
                .containsEntry("modelName", "whisper-1");

        verify(entitlementService, never()).refundSttCharge(ticket);
        verify(lease).close();
    }

    @Test
    void transcriptionFailureRefundsNewCharge() {
        when(mediaConcurrencyGate.acquire(MediaConcurrencyGate.Capability.STT, 7L, REQUEST_ID)).thenReturn(lease);
        when(transcriptionService.transcribeForUser(7L, audio))
                .thenThrow(new BusinessException(ErrorCode.UPSTREAM_ERROR, "stt failed"));

        assertThatThrownBy(() -> controller.transcribeAudio(audio, CLIENT_UID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("stt failed");

        verify(entitlementService).refundSttCharge(ticket);
        verify(lease).close();
    }

    @Test
    void concurrencyRejectionRefundsNewChargeBeforeProviderCall() {
        when(mediaConcurrencyGate.acquire(MediaConcurrencyGate.Capability.STT, 7L, REQUEST_ID))
                .thenThrow(new BusinessException(ErrorCode.SERVICE_BUSY, "stt busy"));

        assertThatThrownBy(() -> controller.transcribeAudio(audio, CLIENT_UID, REQUEST_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("stt busy");

        verify(entitlementService).refundSttCharge(ticket);
        verify(transcriptionService, never()).transcribeForUser(7L, audio);
    }
}
