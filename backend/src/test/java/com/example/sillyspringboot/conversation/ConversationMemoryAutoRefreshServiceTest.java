package com.example.sillyspringboot.conversation;

import com.example.sillyspringboot.chat.mapper.AppMessageMapper;
import com.example.sillyspringboot.conversation.entity.AppConversationMemory;
import com.example.sillyspringboot.conversation.mapper.AppConversationMemoryMapper;
import com.example.sillyspringboot.conversation.service.AppConversationMemoryService;
import com.example.sillyspringboot.conversation.service.ConversationMemoryAutoRefreshService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ConversationMemoryAutoRefreshServiceTest {

    @Test
    void shouldRefresh_shouldRequireVisibleMessagesAndTwentyNewMessages() {
        long conversationId = 123L;
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        ConversationMemoryAutoRefreshService service = service(messageMapper, memoryMapper);

        when(messageMapper.countMemorySourceByConversationId(conversationId)).thenReturn(5);
        assertThat(service.shouldRefresh(conversationId)).isFalse();

        when(messageMapper.countMemorySourceByConversationId(conversationId)).thenReturn(19);
        when(memoryMapper.findByConversationId(conversationId)).thenReturn(null);
        assertThat(service.shouldRefresh(conversationId)).isFalse();

        when(messageMapper.countMemorySourceByConversationId(conversationId)).thenReturn(20);
        assertThat(service.shouldRefresh(conversationId)).isTrue();
    }

    @Test
    void shouldRefresh_shouldRequireThirtyMinutesSinceLastRefresh() {
        long conversationId = 124L;
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        ConversationMemoryAutoRefreshService service = service(messageMapper, memoryMapper);

        when(messageMapper.countMemorySourceByConversationId(conversationId)).thenReturn(46);
        when(memoryMapper.findByConversationId(conversationId)).thenReturn(memory(26, LocalDateTime.now().minusMinutes(10)));
        assertThat(service.shouldRefresh(conversationId)).isFalse();

        when(memoryMapper.findByConversationId(conversationId)).thenReturn(memory(26, LocalDateTime.now().minusMinutes(31)));
        assertThat(service.shouldRefresh(conversationId)).isTrue();
    }

    @Test
    void shouldRefresh_shouldSkipWhenLessThanTwentyNewMessagesSinceLastRefresh() {
        long conversationId = 125L;
        AppMessageMapper messageMapper = mock(AppMessageMapper.class);
        AppConversationMemoryMapper memoryMapper = mock(AppConversationMemoryMapper.class);
        ConversationMemoryAutoRefreshService service = service(messageMapper, memoryMapper);

        when(messageMapper.countMemorySourceByConversationId(conversationId)).thenReturn(45);
        when(memoryMapper.findByConversationId(conversationId)).thenReturn(memory(26, LocalDateTime.now().minusMinutes(31)));

        assertThat(service.shouldRefresh(conversationId)).isFalse();
    }

    private static ConversationMemoryAutoRefreshService service(
            AppMessageMapper messageMapper,
            AppConversationMemoryMapper memoryMapper
    ) {
        return new ConversationMemoryAutoRefreshService(
                messageMapper,
                memoryMapper,
                mock(AppConversationMemoryService.class)
        );
    }

    private static AppConversationMemory memory(int lastRefreshedMessageCount, LocalDateTime updatedAt) {
        AppConversationMemory memory = new AppConversationMemory();
        memory.setLastRefreshedMessageCount(lastRefreshedMessageCount);
        memory.setUpdatedAt(updatedAt);
        return memory;
    }
}
