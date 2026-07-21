package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.chat.config.AppChatProperties;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 阶段 4：生成执行域（隔离线程池 + 有界队列）。
 * <p>
 * 强约束：高峰期不雪崩，不允许无界线程/无界排队。
 */
public class ChatGenerationDispatcher {

    private final ThreadPoolExecutor executor;

    public ChatGenerationDispatcher(AppChatProperties props) {
        int threads = props.getGenerationWorkerThreads();
        int capacity = props.getGenerationQueueCapacity();

        this.executor = new ThreadPoolExecutor(
                threads,
                threads,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity),
                new NamedThreadFactory("chat-gen-"),
                (r, e) -> {
                    throw new RejectedExecutionException("queue full");
                }
        );
        this.executor.allowCoreThreadTimeOut(true);
    }

    public void submit(Runnable task) {
        executor.execute(task);
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger seq = new AtomicInteger(1);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(prefix + seq.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}

