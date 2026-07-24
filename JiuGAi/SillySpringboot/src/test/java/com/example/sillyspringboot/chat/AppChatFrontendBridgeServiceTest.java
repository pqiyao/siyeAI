package com.example.sillyspringboot.chat;

import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.chat.service.AppChatFrontendBridgeService;
import com.example.sillyspringboot.integration.sillytavern.StStreamControl;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AppChatFrontendBridgeServiceTest {

    @Test
    void tokenIsRejectedWhileBridgeIsDisabled() {
        AppChatProperties properties = new AppChatProperties();
        String token = "bridge-A1!f99c9fba744d2432b93823ef9415d";
        properties.getCompatibility().setFrontendBridgeToken(token);
        AppChatFrontendBridgeService service = new AppChatFrontendBridgeService(properties);

        assertThat(service.validToken(token)).isFalse();

        properties.getCompatibility().setFrontendBridgeEnabled(true);
        assertThat(service.validToken(token)).isTrue();
    }

    @Test
    void pollNext_shouldSerializeDispatchedFrontendJobs() throws Exception {
        AppChatProperties properties = new AppChatProperties();
        properties.getCompatibility().setFrontendBridgeEnabled(true);
        properties.getCompatibility().setFrontendBridgePollWaitSeconds(1);
        properties.getCompatibility().setFrontendBridgeRequestTimeoutSeconds(5);

        AppChatFrontendBridgeService service = new AppChatFrontendBridgeService(properties);
        service.heartbeat("worker-a");

        List<ChatGenerateChunk> chunks = new ArrayList<>();
        CompletableFuture<Void> firstCall = CompletableFuture.runAsync(() ->
                service.streamGenerate(request(1L), chunks::add, new StStreamControl()));
        AppChatFrontendBridgeService.BridgeJobPayload first = service.pollNext("worker-a", 1000);
        assertThat(first).isNotNull();
        assertThat(first.conversationId()).isEqualTo(1L);

        CompletableFuture<Void> secondCall = CompletableFuture.runAsync(() ->
                service.streamGenerate(request(2L), chunks::add, new StStreamControl()));
        AppChatFrontendBridgeService.BridgeJobPayload blocked = service.pollNext("worker-b", 250);
        assertThat(blocked).isNull();

        service.complete(first.jobId(), new AppChatFrontendBridgeService.BridgeCompletion(
                first.jobId(),
                "m1",
                "first",
                "done",
                "{}"
        ));
        firstCall.get(1, TimeUnit.SECONDS);

        AppChatFrontendBridgeService.BridgeJobPayload second = service.pollNext("worker-b", 1000);
        assertThat(second).isNotNull();
        assertThat(second.conversationId()).isEqualTo(2L);

        service.complete(second.jobId(), new AppChatFrontendBridgeService.BridgeCompletion(
                second.jobId(),
                "m2",
                "second",
                "done",
                "{}"
        ));
        secondCall.get(1, TimeUnit.SECONDS);
    }

    private static ChatGenerateRequest request(long conversationId) {
        return new ChatGenerateRequest(
                conversationId,
                "hello",
                List.of(),
                "client-" + conversationId,
                true,
                "normal",
                Set.of(),
                "User",
                "Char",
                List.of(),
                "Char.png",
                "chat-" + conversationId,
                "root:" + conversationId,
                List.of("world"),
                null
        );
    }
}
