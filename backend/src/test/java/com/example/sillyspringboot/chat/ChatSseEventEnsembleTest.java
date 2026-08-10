package com.example.sillyspringboot.chat;

import com.example.sillyspringboot.chat.dto.ChatSseEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSseEventEnsembleTest {

    @Test
    void terminalStateCarriesCanonicalContentAndStructuredSegmentsTogether() {
        List<Map<String, Object>> segments = List.of(
                Map.of(
                        "index", 0,
                        "type", "CHARACTER",
                        "speakerMemberId", 11L,
                        "speakerName", "小夏",
                        "speakerAvatarUrl", "/avatars/xia.png",
                        "content", "你好。"
                )
        );

        ChatSseEvent event = ChatSseEvent.stateWithFinalContent(7L, "client-1", "SUCCESS",
                "【小夏】\n你好。", segments);

        assertThat(event.done()).isTrue();
        assertThat(event.finalContent()).isEqualTo("【小夏】\n你好。");
        assertThat(event.segments()).isEqualTo(segments);
        assertThat(event.segments().get(0))
                .containsEntry("speakerMemberId", 11L)
                .containsEntry("content", "你好。");
    }

    @Test
    void nullSegmentsAreNormalizedToAnEmptyStableCollection() {
        ChatSseEvent event = ChatSseEvent.stateWithFinalContent(7L, "client-1", "SUCCESS",
                "普通单角色回复", null);

        assertThat(event.finalContent()).isEqualTo("普通单角色回复");
        assertThat(event.segments()).isEmpty();
    }
}
