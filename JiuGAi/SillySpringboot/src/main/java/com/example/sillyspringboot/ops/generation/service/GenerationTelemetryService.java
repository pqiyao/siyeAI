package com.example.sillyspringboot.ops.generation.service;

import com.example.sillyspringboot.ops.generation.model.GenerationAttemptEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class GenerationTelemetryService {

    private static final Logger log = LoggerFactory.getLogger(GenerationTelemetryService.class);
    private static final int DEFAULT_QUEUE_CAPACITY = 1024;

    private final AttemptSink sink;
    private final ThreadPoolExecutor executor;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong persistenceFailures = new AtomicLong();

    @Autowired
    public GenerationTelemetryService(GenerationTelemetryWriter writer) {
        this(writer::persist, 1, DEFAULT_QUEUE_CAPACITY);
    }

    GenerationTelemetryService(AttemptSink sink, int workers, int queueCapacity) {
        this.sink = sink;
        AtomicLong threadSequence = new AtomicLong();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "generation-telemetry-" + threadSequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(
                Math.max(1, workers),
                Math.max(1, workers),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, queueCapacity)),
                factory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public boolean recordAsync(GenerationAttemptEvent event) {
        if (event == null) {
            return false;
        }
        try {
            executor.execute(() -> persistWithoutImpact(event));
            return true;
        } catch (Throwable failure) {
            try {
                logAtPowersOfTwo(dropped.incrementAndGet(), "生成观测队列已满或关闭，已丢弃观测事件", failure);
            } catch (Throwable ignored) {
                // Even logging is not allowed to leak an observability failure into chat.
            }
            return false;
        }
    }

    public long droppedCount() {
        return dropped.get();
    }

    public long persistenceFailureCount() {
        return persistenceFailures.get();
    }

    private void persistWithoutImpact(GenerationAttemptEvent event) {
        try {
            sink.persist(event);
        } catch (Throwable failure) {
            logAtPowersOfTwo(
                    persistenceFailures.incrementAndGet(),
                    "生成观测写入失败，聊天结果不受影响",
                    failure
            );
        }
    }

    private static void logAtPowersOfTwo(long count, String message, Throwable failure) {
        if ((count & (count - 1L)) == 0L) {
            log.warn("{} count={} cause={}", message, count, failure == null ? "unknown" : failure.getClass().getSimpleName());
        }
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }

    @FunctionalInterface
    interface AttemptSink {
        void persist(GenerationAttemptEvent event);
    }
}
