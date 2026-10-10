package com.example.sillyspringboot.shared.error;

import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class GlobalExceptionHandlerAsyncTest {

    @Test
    void disconnectedSseClientIsHandledWithoutWritingAnotherResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertDoesNotThrow(() -> handler.handleAsyncClientDisconnect(
                new AsyncRequestNotUsableException("client disconnected")));
    }

    @Test
    void timedOutSseRequestIsHandledWithoutWritingAnotherResponse() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertDoesNotThrow(() -> handler.handleAsyncRequestTimeout(
                new AsyncRequestTimeoutException()));
    }
}
