package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

public class InMemoryImageGenerationConcurrencyGate implements ImageGenerationConcurrencyGate {

    private final AppImageGenerationSettingsService settingsService;
    private final AtomicInteger global = new AtomicInteger(0);
    private final ConcurrentHashMap<Long, AtomicInteger> perUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RequestState> requests = new ConcurrentHashMap<>();

    public InMemoryImageGenerationConcurrencyGate(AppImageGenerationSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public Lease acquire(long userId) {
        int globalLimit = Math.max(1, settingsService.getSettings().getGlobalConcurrentLimit());
        int perUserLimit = Math.max(1, settingsService.getSettings().getPerUserConcurrentLimit());
        AtomicInteger userCounter = perUser.computeIfAbsent(userId, key -> new AtomicInteger(0));
        if (userCounter.incrementAndGet() > perUserLimit) {
            userCounter.decrementAndGet();
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "当前已有生图任务进行中，请稍后再试");
        }
        if (global.incrementAndGet() > globalLimit) {
            global.decrementAndGet();
            userCounter.decrementAndGet();
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "生图引擎繁忙，请稍后再试");
        }
        AtomicBoolean closed = new AtomicBoolean(false);
        return () -> {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                global.decrementAndGet();
            } finally {
                userCounter.decrementAndGet();
            }
        };
    }

    @Override
    public RequestLease claimRequest(long userId, String requestId) {
        long now = System.currentTimeMillis();
        String key = userId + ":" + requestId;
        String token = UUID.randomUUID().toString();
        RequestState state = requests.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAt() <= now) {
                return new RequestState("RUNNING", token, now + 5 * 60_000L);
            }
            return current;
        });
        if (!token.equals(state.token())) {
            if ("DONE".equals(state.status())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "该生图请求已经完成，请勿重复提交");
            }
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "该生图请求正在处理中，请稍后查看结果");
        }
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicBoolean succeeded = new AtomicBoolean(false);
        return new RequestLease() {
            @Override
            public void markSucceeded() {
                if (succeeded.compareAndSet(false, true)) {
                    requests.computeIfPresent(key, (ignored, current) -> token.equals(current.token())
                            ? new RequestState("DONE", token, System.currentTimeMillis() + 24 * 60 * 60_000L)
                            : current);
                }
            }

            @Override
            public void close() {
                if (closed.compareAndSet(false, true) && !succeeded.get()) {
                    requests.computeIfPresent(key, (ignored, current) -> token.equals(current.token()) ? null : current);
                }
            }
        };
    }

    private record RequestState(String status, String token, long expiresAt) {}
}
