package com.example.sillyspringboot.chat;

import com.example.sillyspringboot.chat.dto.ChatSseEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSseEventFinalContentTest {

    @Test
    void terminalStateCarriesCanonicalFinalContentWithoutChangingTheEventType() {
        ChatSseEvent event = ChatSseEvent.stateWithFinalContent(
                42L,
                "client-1",
                "SUCCESS",
                "visible reply"
        );

        assertThat(event.type()).isEqualTo("state");
        assertThat(event.state()).isEqualTo("SUCCESS");
        assertThat(event.done()).isTrue();
        assertThat(event.finalContent()).isEqualTo("visible reply");
    }
}
