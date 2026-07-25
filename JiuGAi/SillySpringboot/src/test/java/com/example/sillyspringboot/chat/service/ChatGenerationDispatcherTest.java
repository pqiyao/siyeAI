package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.chat.config.AppChatProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ChatGenerationDispatcherTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesSubmissionContextAndDoesNotLeakItToNextTask() throws Exception {
        AppChatProperties properties = new AppChatProperties();
        properties.setGenerationWorkerThreads(1);
        properties.setGenerationQueueCapacity(4);
        ChatGenerationDispatcher dispatcher = new ChatGenerationDispatcher(properties);
        AtomicReference<String> firstTrace = new AtomicReference<>();
        AtomicReference<String> secondTrace = new AtomicReference<>("not-run");
        CountDownLatch firstDone = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);

        MDC.put("traceId", "trace-first");
        dispatcher.submit(() -> {
            firstTrace.set(MDC.get("traceId"));
            MDC.put("worker-only", "must-not-leak");
            firstDone.countDown();
        });
        assertThat(firstDone.await(3, TimeUnit.SECONDS)).isTrue();

        MDC.clear();
        dispatcher.submit(() -> {
            secondTrace.set(MDC.get("traceId"));
            assertThat(MDC.get("worker-only")).isNull();
            secondDone.countDown();
        });
        assertThat(secondDone.await(3, TimeUnit.SECONDS)).isTrue();

        assertThat(firstTrace.get()).isEqualTo("trace-first");
        assertThat(secondTrace.get()).isNull();
    }
}
