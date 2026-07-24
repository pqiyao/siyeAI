package com.example.sillyspringboot.conversation.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationMemoryRefreshLeaseManagerTest {

    @Test
    void lease_shouldUseTtlAndCompareOwnerTokenBeforeDelete() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ObjectProvider<StringRedisTemplate> provider = provider(redis);
        Duration ttl = Duration.ofSeconds(300);
        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(
                eq("conversation:memory:auto-refresh:10:20"),
                owner.capture(),
                eq(ttl)
        )).thenReturn(true);
        ConversationMemoryRefreshLeaseManager manager = new ConversationMemoryRefreshLeaseManager(provider);

        ConversationMemoryRefreshLeaseManager.LeaseAttempt attempt = manager.tryAcquire("10:20", ttl);
        assertThat(attempt.acquired()).isTrue();
        assertThat(attempt.localOnly()).isFalse();
        attempt.lease().close();
        attempt.lease().close();

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<DefaultRedisScript> script = ArgumentCaptor.forClass(DefaultRedisScript.class);
        verify(redis).execute(
                script.capture(),
                eq(List.of("conversation:memory:auto-refresh:10:20")),
                eq(owner.getValue())
        );
        assertThat(script.getValue().getScriptAsString())
                .contains("redis.call('get', KEYS[1]) == ARGV[1]")
                .contains("redis.call('del', KEYS[1])");
    }

    @Test
    void tryAcquire_shouldSkipSafelyWhenConfiguredRedisFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("offline"));
        ConversationMemoryRefreshLeaseManager manager =
                new ConversationMemoryRefreshLeaseManager(provider(redis));

        ConversationMemoryRefreshLeaseManager.LeaseAttempt attempt =
                manager.tryAcquire("10:20", Duration.ofSeconds(300));

        assertThat(attempt.status()).isEqualTo(ConversationMemoryRefreshLeaseManager.Status.UNAVAILABLE);
        assertThat(attempt.acquired()).isFalse();
    }

    @Test
    void tryAcquire_shouldUseLocalLeaseWhenRedisIsNotConfigured() {
        ConversationMemoryRefreshLeaseManager manager =
                new ConversationMemoryRefreshLeaseManager(provider(null));

        ConversationMemoryRefreshLeaseManager.LeaseAttempt attempt =
                manager.tryAcquire("10:20", Duration.ofSeconds(300));

        assertThat(attempt.acquired()).isTrue();
        assertThat(attempt.localOnly()).isTrue();
        attempt.lease().close();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<StringRedisTemplate> provider(StringRedisTemplate redis) {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redis);
        return provider;
    }
}
