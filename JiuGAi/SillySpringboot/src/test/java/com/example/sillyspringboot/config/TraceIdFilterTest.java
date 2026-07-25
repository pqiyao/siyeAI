package com.example.sillyspringboot.config;

import com.example.sillyspringboot.shared.error.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void acceptsSafeTraceIdAndClearsRequestContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceIdFilter.TRACE_ID_HEADER, "client.trace-01:part");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> observed = new AtomicReference<>();

        new TraceIdFilter().doFilter(request, response,
                (req, res) -> observed.set(MDC.get(GlobalExceptionHandler.MDC_TRACE_ID)));

        assertThat(observed.get()).isEqualTo("client.trace-01:part");
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(observed.get());
        assertThat(MDC.get(GlobalExceptionHandler.MDC_TRACE_ID)).isNull();
    }

    @Test
    void replacesUnsafeOrOversizedTraceIdWithServerUuid() throws Exception {
        for (String supplied : new String[]{"bad trace\nvalue", "x".repeat(65)}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(TraceIdFilter.TRACE_ID_HEADER, supplied);
            MockHttpServletResponse response = new MockHttpServletResponse();

            new TraceIdFilter().doFilter(request, response, (req, res) -> { });

            assertThatCodeIsUuid(response.getHeader(TraceIdFilter.TRACE_ID_HEADER));
        }
    }

    private static void assertThatCodeIsUuid(String value) {
        assertThat(value).isNotBlank().hasSizeLessThanOrEqualTo(64);
        assertThat(UUID.fromString(value)).isNotNull();
    }
}
