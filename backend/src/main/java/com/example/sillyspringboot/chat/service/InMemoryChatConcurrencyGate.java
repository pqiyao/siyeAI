package com.example.sillyspringboot.chat.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单机内存版并发闸门：用于测试环境或无 Redis 场景。
 */
public class InMemoryChatConcurrencyGate implements ChatConcurrencyGate {

    private final Semaphore global;
    private final int perUserLimit;
    private final ConcurrentHashMap<Long, Semaphore> perUser = new ConcurrentHashMap<>();

    public InMemoryChatConcurrencyGate(int globalLimit, int perUserLimit) {
        this.global = new Semaphore(globalLimit);
        this.perUserLimit = perUserLimit;
    }

    @Override
    public Lease acquire(long userId) {
        Semaphore userSem = perUser.computeIfAbsent(userId, k -> new Semaphore(perUserLimit));
        if (!userSem.tryAcquire()) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "当前有生成进行中，请稍后重试");
        }
        if (!global.tryAcquire()) {
            userSem.release();
            throw new BusinessException(ErrorCode.SERVICE_BUSY, "系统繁忙，请稍后重试");
        }
        return () -> {
            try {
                global.release();
            } finally {
                userSem.release();
            }
        };
    }
}

