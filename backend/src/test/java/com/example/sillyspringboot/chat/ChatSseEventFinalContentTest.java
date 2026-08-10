package com.example.sillyspringboot.chat;

import com.example.sillyspringboot.chat.dto.ChatSseEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
        assertThat(event.segments()).isEmpty();
    }

    @Test
    void terminalStateCanCarryStructuredSpeakerSegments() {
        List<Map<String, Object>> segments = List.of(Map.of(
                "index", 0,
                "type", "CHARACTER",
                "speakerMemberId", 21L,
                "speakerName", "林夏",
                "content", "回来啦"
        ));

        ChatSseEvent event = ChatSseEvent.stateWithFinalContent(
                42L,
                "client-2",
                "SUCCESS",
                "【林夏】\n回来啦",
                segments
        );

        assertThat(event.type()).isEqualTo("state");
        assertThat(event.segments()).containsExactlyElementsOf(segments);
    }
}
