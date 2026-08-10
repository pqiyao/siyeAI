package com.example.sillyspringboot.chat;

import com.example.sillyspringboot.chat.dto.AppChatRegenerateRequest;
import com.example.sillyspringboot.chat.service.AppChatService;
import com.example.sillyspringboot.chat.service.ChatAuditService;
import com.example.sillyspringboot.chat.service.ChatGenerationDispatcher;
import com.example.sillyspringboot.chat.service.ChatSnapshotService;
import com.example.sillyspringboot.chat.web.AppChatController;
import com.example.sillyspringboot.compat.h5.web.H5UploadService;
import com.example.sillyspringboot.integration.sillytavern.StStreamControl;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppChatControllerRegenerateCancellationTest {

    @Test
    void successfulRegenerateMarksTheGeneratedMessageAsHiddenVariantOnly() {
        Fixture fixture = fixture();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<ChatGenerateChunk> onChunk = invocation.getArgument(2);
            onChunk.accept(new ChatGenerateChunk(7L, "regen-client", 0, "replacement", true, null, null));
            return null;
        }).when(fixture.chatService).streamRegenerate(
                eq(fixture.request),
                eq("token"),
                any(),
                any(StStreamControl.class)
        );
        when(fixture.chatService.normalizeAssistantOutput(7L, "replacement", "token"))
                .thenReturn(new AppChatService.AssistantOutputNormalization("replacement", false));
        when(fixture.chatService.finalizeEnsembleOutput(7L, 101L, "replacement", "token"))
                .thenReturn("replacement");
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(fixture.dispatcher).submit(any(Runnable.class));

        fixture.controller.regenerateStream(fixture.request, "Bearer token");

        verify(fixture.auditService).stageFinalAssistantContent(101L, "replacement", "unknown");
        verify(fixture.chatService).promoteRegenerateVariant(
                7L, 42L, 101L, "token", true, false
        );
        verify(fixture.auditService).onHiddenVariantSuccess(101L, 201L, "replacement", "unknown");
        verify(fixture.auditService, never()).onSuccess(101L, 201L, "replacement", "unknown");
    }

    @Test
    void partialRegenerateCancellationKeepsTheOriginalAssistantMessage() {
        Fixture fixture = fixture();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<ChatGenerateChunk> onChunk = invocation.getArgument(2);
            StStreamControl control = invocation.getArgument(3);
            onChunk.accept(new ChatGenerateChunk(7L, "regen-client", 0, "partial raw", false, null, null));
            control.cancel();
            return null;
        }).when(fixture.chatService).streamRegenerate(
                eq(fixture.request),
                eq("token"),
                any(),
                any(StStreamControl.class)
        );
        when(fixture.chatService.normalizeAssistantOutput(7L, "partial raw", "token"))
                .thenReturn(new AppChatService.AssistantOutputNormalization("partial canonical", true));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(fixture.dispatcher).submit(any(Runnable.class));

        fixture.controller.regenerateStream(fixture.request, "Bearer token");

        verify(fixture.chatService).getAssistantMessageContent(7L, 42L, "token");
        verify(fixture.auditService).onStopped(101L, 201L, "", "unknown");
        verify(fixture.snapshotService).saveSnapshotFromDb(7L, 800);
        verify(fixture.auditService, never()).stageFinalAssistantContent(anyLong(), anyString(), anyString());
        verify(fixture.chatService, never()).promoteRegenerateVariant(
                anyLong(), anyLong(), anyLong(), anyString(), anyBoolean(), anyBoolean()
        );
    }

    @Test
    void queuedRegenerateCancellationAlsoRestoresTheOriginalAssistantMessage() {
        Fixture fixture = fixture();
        AtomicReference<Runnable> submitted = new AtomicReference<>();
        doAnswer(invocation -> {
            submitted.set(invocation.getArgument(0));
            return null;
        }).when(fixture.dispatcher).submit(any(Runnable.class));

        fixture.controller.regenerateStream(fixture.request, "Bearer token");

        ArgumentCaptor<StStreamControl> controlCaptor = ArgumentCaptor.forClass(StStreamControl.class);
        verify(fixture.chatService).registerControl(eq(7L), controlCaptor.capture());
        controlCaptor.getValue().cancel();
        submitted.get().run();

        verify(fixture.chatService).getAssistantMessageContent(7L, 42L, "token");
        verify(fixture.auditService).onStopped(101L, 201L, "", "unknown");
        verify(fixture.snapshotService).saveSnapshotFromDb(7L, 800);
        verify(fixture.chatService, never()).acquireLease(anyString());
        verify(fixture.chatService, never()).promoteRegenerateVariant(
                anyLong(), anyLong(), anyLong(), anyString(), anyBoolean(), anyBoolean()
        );
    }

    private static Fixture fixture() {
        AppChatService chatService = mock(AppChatService.class);
        ChatGenerationDispatcher dispatcher = mock(ChatGenerationDispatcher.class);
        ChatAuditService auditService = mock(ChatAuditService.class);
        ChatSnapshotService snapshotService = mock(ChatSnapshotService.class);
        AppChatController controller = new AppChatController(
                chatService,
                dispatcher,
                auditService,
                snapshotService,
                mock(H5UploadService.class)
        );

        AppChatRegenerateRequest request = new AppChatRegenerateRequest();
        request.setConversationId(7L);
        request.setTargetMessageId("42");
        request.setClientMessageId("regen-client");
        ChatAuditService.AuditContext audit = new ChatAuditService.AuditContext(9L, 0L, 101L, 201L);

        when(chatService.sseTimeoutMillis()).thenReturn(30_000L);
        when(chatService.maxQueueWaitSeconds()).thenReturn(2);
        when(chatService.generationTimeoutSeconds()).thenReturn(30);
        when(chatService.acquireLease("token")).thenReturn(() -> { });
        when(chatService.getAssistantMessageContent(7L, 42L, "token")).thenReturn("original reply");
        when(auditService.onQueued(
                eq(7L),
                eq(""),
                eq("regen-client"),
                eq("token"),
                anyString(),
                eq("REGEN_STREAM")
        )).thenReturn(audit);

        return new Fixture(controller, chatService, dispatcher, auditService, snapshotService, request);
    }

    private record Fixture(
            AppChatController controller,
            AppChatService chatService,
            ChatGenerationDispatcher dispatcher,
            ChatAuditService auditService,
            ChatSnapshotService snapshotService,
            AppChatRegenerateRequest request
    ) { }
}
