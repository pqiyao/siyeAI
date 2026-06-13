package com.example.sillyspringboot.ops.service;

public interface ImageGenerationConcurrencyGate {

    Lease acquire(long userId);

    interface Lease extends AutoCloseable {
        @Override
        void close();
    }
}
