package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryImageGenerationConcurrencyGate implements ImageGenerationConcurrencyGate {

    private final AppImageGenerationSettingsService settingsService;
    private final AtomicInteger global = new AtomicInteger(0);
    private final ConcurrentHashMap<Long, AtomicInteger> perUser = new ConcurrentHashMap<>();

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
}
