package com.example.sillyspringboot.config;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPreferenceBodyLimitFilterTest {

    @Test
    void rejectsOversizedChunkedBodyBeforeTheController() throws Exception {
        MockHttpServletRequest request = preferenceRequest();
        request.setContent(new byte[ChatPreferenceBodyLimitFilter.MAX_BODY_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);
        FilterChain chain = (wrapped, ignored) -> invoked.set(true);

        new ChatPreferenceBodyLimitFilter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("设置内容过大");
        assertThat(invoked).isFalse();
    }

    @Test
    void replaysAnAcceptedBodyWithoutChangingIt() throws Exception {
        byte[] body = "{\"expectedRevision\":0,\"bubble\":null}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = preferenceRequest();
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<byte[]> seen = new AtomicReference<>();
        FilterChain chain = (ServletRequest wrapped, ServletResponse ignored) ->
                seen.set(wrapped.getInputStream().readAllBytes());

        new ChatPreferenceBodyLimitFilter().doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seen.get()).isEqualTo(body);
    }

    private static MockHttpServletRequest preferenceRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "PUT",
                "/api/v1/app/me/chat-preferences"
        );
        request.setContentType("application/json");
        return request;
    }
}
