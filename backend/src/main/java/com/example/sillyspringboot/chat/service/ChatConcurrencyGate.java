package com.example.sillyspringboot.chat.service;

/**
 * 并发闸门抽象：支持“单用户 1、全局 8”等强约束。
 */
public interface ChatConcurrencyGate {

    Lease acquire(long userId);

    interface Lease extends AutoCloseable {
        @Override
        void close();
    }
}
