package com.example.sillyspringboot.ops.service;

public interface ImageGenerationConcurrencyGate {

    Lease acquire(long userId);

    RequestLease claimRequest(long userId, String requestId);

    interface Lease extends AutoCloseable {
        @Override
        void close();
    }

    interface RequestLease extends AutoCloseable {
        void markSucceeded();

        @Override
        void close();
    }
}
