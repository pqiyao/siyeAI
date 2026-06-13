package com.example.sillyspringboot.humanchat.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SocialChatWsSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SocialChatWsSessionRegistry.class);

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final Map<String, Long> userBySession = new ConcurrentHashMap<>();

    public SocialChatWsSessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void bind(long userId, WebSocketSession session) {
        if (session == null) {
            return;
        }
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        userBySession.put(session.getId(), userId);
    }

    public Long userId(WebSocketSession session) {
        return session == null ? null : userBySession.get(session.getId());
    }

    public void remove(WebSocketSession session) {
        if (session == null) {
            return;
        }
        Long userId = userBySession.remove(session.getId());
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUser.remove(userId, sessions);
        }
    }

    public boolean isOnline(long userId) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return false;
        }
        sessions.removeIf(session -> session == null || !session.isOpen());
        if (sessions.isEmpty()) {
            sessionsByUser.remove(userId, sessions);
            return false;
        }
        return true;
    }

    public int sendToUser(long userId, String type, Object data) {
        return sendResultToUser(userId, type, data).sentCount();
    }

    public SocialChatRealtimeNotifier.SendResult sendResultToUser(long userId, String type, Object data) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            log.debug("social chat realtime skip: userId={}, type={}, reason=no_session", userId, type);
            return new SocialChatRealtimeNotifier.SendResult(userId, type, false, 0, 0, 0);
        }
        boolean online = true;
        Map<String, Object> event = Map.of(
                "type", type,
                "data", data == null ? Map.of() : data,
                "serverTime", Instant.now().toString()
        );
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("social chat realtime encode failed: userId={}, type={}", userId, type, e);
            return new SocialChatRealtimeNotifier.SendResult(userId, type, online, 0, 0, sessions.size());
        }
        int sent = 0;
        List<WebSocketSession> stale = new ArrayList<>();
        for (WebSocketSession session : sessions) {
            if (session == null || !session.isOpen()) {
                stale.add(session);
                continue;
            }
            try {
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                        sent++;
                    }
                }
            } catch (IOException | IllegalStateException e) {
                log.debug("social chat realtime send failed: userId={}, type={}, sessionId={}", userId, type, session.getId(), e);
                stale.add(session);
            }
        }
        stale.forEach(this::remove);
        log.debug(
                "social chat realtime sent: userId={}, type={}, sent={}, stale={}, sessions={}",
                userId,
                type,
                sent,
                stale.size(),
                sessions.size()
        );
        return new SocialChatRealtimeNotifier.SendResult(userId, type, online, sent, stale.size(), sessions.size());
    }
}
