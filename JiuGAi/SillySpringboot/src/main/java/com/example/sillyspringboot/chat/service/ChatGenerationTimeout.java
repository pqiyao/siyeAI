package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.integration.sillytavern.StStreamControl;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatGenerationTimeout implements AutoCloseable {

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "chat-generation-timeout");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean timedOut = new AtomicBoolean(false);
    private final ScheduledFuture<?> future;

    private ChatGenerationTimeout(StStreamControl control, int timeoutSeconds) {
        this.future = EXECUTOR.schedule(() -> {
            if (closed.get()) {
                return;
            }
            timedOut.set(true);
            control.cancel();
        }, timeoutSeconds, TimeUnit.SECONDS);
    }

    public static ChatGenerationTimeout start(StStreamControl control, int timeoutSeconds) {
        return new ChatGenerationTimeout(control, timeoutSeconds);
    }

    public boolean isTimedOut() {
        return timedOut.get();
    }

    @Override
    public void close() {
        closed.set(true);
        future.cancel(false);
    }
}
