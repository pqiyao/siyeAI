package com.example.sillyspringboot.chat;

import com.example.sillyspringboot.chat.config.AppChatProperties;
import com.example.sillyspringboot.chat.service.AppChatFrontendBridgeService;
import com.example.sillyspringboot.integration.sillytavern.StStreamControl;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateChunk;
import com.example.sillyspringboot.integration.sillytavern.dto.ChatGenerateRequest;
import com.example.sillyspringboot.integration.sillytavern.dto.UserModelOverride;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppChatFrontendBridgeServiceTest {

    @Test
    void cancelledDispatchedJobRemainsVisibleToLateWorkerPoll() throws Exception {
        AppChatProperties properties = new AppChatProperties();
        properties.getCompatibility().setFrontendBridgeEnabled(true);
        properties.getCompatibility().setFrontendBridgeRequestTimeoutSeconds(5);
        AppChatFrontendBridgeService service = new AppChatFrontendBridgeService(properties);
        service.heartbeat("worker-a");

        StStreamControl control = new StStreamControl();
        CompletableFuture<Void> stream = CompletableFuture.runAsync(() ->
                service.streamGenerate(request(99L), chunk -> { }, control));
        AppChatFrontendBridgeService.BridgeJobPayload job = service.pollNext("worker-a", 1000);
        assertThat(job).isNotNull();

        control.cancel();
        stream.get(2, TimeUnit.SECONDS);

        assertThat(service.status().activeJobs()).isZero();
        assertThat(service.isJobCancelled(job.jobId())).isTrue();
    }

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
        assertThat(first.runtimePresetBundle()).contains("temperature");
        assertThat(first.tailSystemPrompt()).isEqualTo("remember this");
        assertThat(first.userPersona()).isEqualTo("Profile persona");

        CompletableFuture<Void> secondCall = CompletableFuture.runAsync(() ->
                service.streamGenerate(request(2L), chunks::add, new StStreamControl()));
        service.heartbeat("worker-b");
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

    @Test
    void failureAfterWorkerDispatchIsMarkedAsUnsafeForRuntimeFallback() throws Exception {
        AppChatProperties properties = new AppChatProperties();
        properties.getCompatibility().setFrontendBridgeEnabled(true);
        properties.getCompatibility().setFrontendBridgeRequestTimeoutSeconds(5);

        AppChatFrontendBridgeService service = new AppChatFrontendBridgeService(properties);
        service.heartbeat("worker-a");

        CompletableFuture<Throwable> call = CompletableFuture.supplyAsync(() -> {
            try {
                service.streamGenerate(request(3L), chunk -> { }, new StStreamControl());
                return null;
            } catch (Throwable ex) {
                return ex;
            }
        });
        AppChatFrontendBridgeService.BridgeJobPayload job = service.pollNext("worker-a", 1000);
        assertThat(job).isNotNull();
        assertThat(service.fail(job.jobId(), "worker failed after Generate started")).isTrue();

        Throwable failure = call.get(1, TimeUnit.SECONDS);
        assertThatThrownBy(() -> {
            if (failure != null) {
                throw failure;
            }
        })
                .isInstanceOf(AppChatFrontendBridgeService.FrontendBridgeGenerationException.class)
                .satisfies(ex -> assertThat(
                        ((AppChatFrontendBridgeService.FrontendBridgeGenerationException) ex).wasDispatched()
                ).isTrue());
    }

    @Test
    void concurrentWorkersCanOnlyDispatchOneJob() throws Exception {
        AppChatProperties properties = new AppChatProperties();
        properties.getCompatibility().setFrontendBridgeEnabled(true);
        properties.getCompatibility().setFrontendBridgePollWaitSeconds(1);
        properties.getCompatibility().setFrontendBridgeRequestTimeoutSeconds(5);
        AppChatFrontendBridgeService service = new AppChatFrontendBridgeService(properties);
        service.heartbeat("worker-a");
        service.heartbeat("worker-b");

        CompletableFuture<Void> firstCall = CompletableFuture.runAsync(() ->
                service.streamGenerate(request(10L), chunk -> { }, new StStreamControl()));
        CompletableFuture<Void> secondCall = CompletableFuture.runAsync(() ->
                service.streamGenerate(request(11L), chunk -> { }, new StStreamControl()));
        waitForQueuedJobs(service, 2);

        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<AppChatFrontendBridgeService.BridgeJobPayload> firstPoll =
                CompletableFuture.supplyAsync(() -> awaitAndPoll(service, start, "worker-a"));
        CompletableFuture<AppChatFrontendBridgeService.BridgeJobPayload> secondPoll =
                CompletableFuture.supplyAsync(() -> awaitAndPoll(service, start, "worker-b"));
        start.countDown();

        List<AppChatFrontendBridgeService.BridgeJobPayload> dispatched = java.util.stream.Stream.of(
                firstPoll.get(2, TimeUnit.SECONDS),
                secondPoll.get(2, TimeUnit.SECONDS)
        ).filter(java.util.Objects::nonNull).toList();
        assertThat(dispatched).hasSize(1);
        assertThat(service.status().activeJobs()).isEqualTo(1);
        assertThat(service.status().queuedJobs()).isEqualTo(1);

        AppChatFrontendBridgeService.BridgeJobPayload active = dispatched.get(0);
        service.complete(active.jobId(), new AppChatFrontendBridgeService.BridgeCompletion(
                active.jobId(), "m-active", "done", "done", "{}"));
        AppChatFrontendBridgeService.BridgeJobPayload remaining = service.pollNext("worker-a", 1000);
        assertThat(remaining).isNotNull();
        service.complete(remaining.jobId(), new AppChatFrontendBridgeService.BridgeCompletion(
                remaining.jobId(), "m-remaining", "done", "done", "{}"));
        CompletableFuture.allOf(firstCall, secondCall).get(2, TimeUnit.SECONDS);
    }

    @Test
    void workerMustReportReadyPageAndModelConnection() {
        AppChatProperties properties = new AppChatProperties();
        properties.getCompatibility().setFrontendBridgeEnabled(true);
        AppChatFrontendBridgeService service = new AppChatFrontendBridgeService(properties);

        service.heartbeat("worker-a", new AppChatFrontendBridgeService.WorkerHeartbeat(
                true, false, false, false, "", List.of("third-party/st-h5-frontend-bridge")));
        assertThat(service.hasOnlineWorker()).isFalse();
        assertThat(service.status().onlineWorkers()).isEqualTo(1);
        assertThat(service.status().readyWorkers()).isZero();

        service.heartbeat("worker-a", new AppChatFrontendBridgeService.WorkerHeartbeat(
                true, true, true, true, "job-1", List.of("third-party/st-h5-frontend-bridge")));
        assertThat(service.hasOnlineWorker()).isTrue();
        assertThat(service.status().readyWorkers()).isEqualTo(1);
        assertThat(service.status().workers().get(0)).containsEntry("activeJobId", "job-1");
    }

    @Test
    void busyWorkerRemainsOnlineButCannotReceiveAnotherJob() throws Exception {
        AppChatProperties properties = new AppChatProperties();
        properties.getCompatibility().setFrontendBridgeEnabled(true);
        properties.getCompatibility().setFrontendBridgePollWaitSeconds(1);
        properties.getCompatibility().setFrontendBridgeRequestTimeoutSeconds(5);
        AppChatFrontendBridgeService service = new AppChatFrontendBridgeService(properties);
        service.heartbeat("worker-a", new AppChatFrontendBridgeService.WorkerHeartbeat(
                true, true, true, true, "previous-job", List.of()));

        CompletableFuture<Void> call = CompletableFuture.runAsync(() ->
                service.streamGenerate(request(12L), chunk -> { }, new StStreamControl()));
        waitForQueuedJobs(service, 1);
        assertThat(service.hasOnlineWorker()).isTrue();
        assertThat(service.pollNext("worker-a", 250)).isNull();

        service.heartbeat("worker-a", new AppChatFrontendBridgeService.WorkerHeartbeat(
                true, true, false, false, "", List.of()));
        AppChatFrontendBridgeService.BridgeJobPayload job = service.pollNext("worker-a", 1000);
        assertThat(job).isNotNull();
        service.complete(job.jobId(), new AppChatFrontendBridgeService.BridgeCompletion(
                job.jobId(), "m12", "done", "done", "{}"));
        call.get(1, TimeUnit.SECONDS);
    }

    @Test
    void byokOverrideIsDeliveredOnlyInsideTheJobAndRedactedFromDiagnostics() throws Exception {
        AppChatProperties properties = new AppChatProperties();
        properties.getCompatibility().setFrontendBridgeEnabled(true);
        properties.getCompatibility().setFrontendBridgeRequestTimeoutSeconds(5);
        AppChatFrontendBridgeService service = new AppChatFrontendBridgeService(properties);
        service.heartbeat("worker-byok");
        String secret = "sk-user-secret-that-must-never-be-logged";

        CompletableFuture<Void> call = CompletableFuture.runAsync(() ->
                service.streamGenerate(request(20L, userOverride("openrouter", secret)),
                        chunk -> { }, new StStreamControl()));
        AppChatFrontendBridgeService.BridgeJobPayload job = service.pollNext("worker-byok", 1000);

        assertThat(job).isNotNull();
        assertThat(job.modelOverride()).isNotNull();
        assertThat(job.modelOverride().providerSource()).isEqualTo("openrouter");
        assertThat(job.modelOverride().modelName()).isEqualTo("openai/gpt-4o-mini");
        assertThat(job.modelOverride().apiKey()).isEqualTo(secret);
        assertThat(job.modelOverride().reverseProxy()).isEqualTo("https://openrouter.ai/api/v1");
        assertThat(job.toString()).doesNotContain(secret).contains("[REDACTED]");
        assertThat(job.toString()).doesNotContain("https://openrouter.ai/api/v1");
        assertThat(service.status().toString()).doesNotContain(secret);

        service.complete(job.jobId(), new AppChatFrontendBridgeService.BridgeCompletion(
                job.jobId(), "m20", "done", "done", "{}"));
        call.get(1, TimeUnit.SECONDS);
        assertThat(service.status().toString()).doesNotContain(secret);
    }

    @Test
    void unsupportedByokProviderCannotBeSerializedForFrontendExecution() {
        assertThat(AppChatFrontendBridgeService.BridgeModelOverride.supports(
                userOverride("unsupported-provider", "secret"))).isFalse();
    }

    @Test
    void partialUpdatesAreStreamedInOrderAndCompletionOnlyEmitsTheRemainingSuffix() throws Exception {
        AppChatProperties properties = new AppChatProperties();
        properties.getCompatibility().setFrontendBridgeEnabled(true);
        properties.getCompatibility().setFrontendBridgeRequestTimeoutSeconds(5);
        AppChatFrontendBridgeService service = new AppChatFrontendBridgeService(properties);
        service.heartbeat("worker-stream");
        List<ChatGenerateChunk> chunks = java.util.Collections.synchronizedList(new ArrayList<>());

        CompletableFuture<Void> call = CompletableFuture.runAsync(() ->
                service.streamGenerate(request(30L), chunks::add, new StStreamControl()));
        AppChatFrontendBridgeService.BridgeJobPayload job = service.pollNext("worker-stream", 1000);
        assertThat(job).isNotNull();

        assertThat(service.publishPartial(new AppChatFrontendBridgeService.BridgePartial(
                job.jobId(), 0, "m30", "Hello "))).isTrue();
        assertThat(service.publishPartial(new AppChatFrontendBridgeService.BridgePartial(
                job.jobId(), 0, "m30", "Hello "))).isTrue();
        assertThat(service.publishPartial(new AppChatFrontendBridgeService.BridgePartial(
                job.jobId(), 0, "m30", "different duplicate"))).isFalse();
        assertThat(service.publishPartial(new AppChatFrontendBridgeService.BridgePartial(
                job.jobId(), 2, "m30", "out of order"))).isFalse();
        assertThat(service.publishPartial(new AppChatFrontendBridgeService.BridgePartial(
                job.jobId(), 1, "m30", "world"))).isTrue();
        waitForChunks(chunks, 2);
        assertThat(chunks.stream().map(ChatGenerateChunk::delta).toList())
                .containsExactly("Hello ", "world");

        service.complete(job.jobId(), new AppChatFrontendBridgeService.BridgeCompletion(
                job.jobId(), "m30", "Hello world!", "done", "{}"));
        call.get(1, TimeUnit.SECONDS);
        assertThat(chunks.stream().map(ChatGenerateChunk::delta).toList())
                .containsExactly("Hello ", "world", "!", "");
        assertThat(chunks.get(chunks.size() - 1).done()).isTrue();
    }

    private static AppChatFrontendBridgeService.BridgeJobPayload awaitAndPoll(
            AppChatFrontendBridgeService service, CountDownLatch start, String workerId) {
        try {
            start.await(1, TimeUnit.SECONDS);
            return service.pollNext(workerId, 300);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private static void waitForQueuedJobs(AppChatFrontendBridgeService service, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (service.status().queuedJobs() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(service.status().queuedJobs()).isEqualTo(expected);
    }

    private static void waitForChunks(List<ChatGenerateChunk> chunks, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (chunks.size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(chunks).hasSizeGreaterThanOrEqualTo(expected);
    }

    private static ChatGenerateRequest request(long conversationId) {
        return request(conversationId, null);
    }

    private static ChatGenerateRequest request(long conversationId, UserModelOverride override) {
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
                override,
                "remember this",
                "{\"generation\":{\"temperature\":0.4}}",
                null,
                null,
                "Profile persona"
        );
    }

    private static UserModelOverride userOverride(String provider, String apiKey) {
        return new UserModelOverride(
                provider, "openai/gpt-4o-mini", "", "", "", "", "",
                "", "", "", "", "", "", "", apiKey, "");
    }
}
