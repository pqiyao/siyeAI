package com.example.sillyspringboot.humanchat.ws;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Map;

@Component
public class SocialChatWebSocketHandler extends TextWebSocketHandler {

    private final AppTokenService tokenService;
    private final SocialChatWsSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public SocialChatWebSocketHandler(
            AppTokenService tokenService,
            SocialChatWsSessionRegistry registry,
            ObjectMapper objectMapper
    ) {
        this.tokenService = tokenService;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long handshakeUserId = handshakeUserId(session);
        if (handshakeUserId != null && handshakeUserId > 0) {
            registry.bind(handshakeUserId, session);
        }
        send(session, "hello", Map.of(
                "authRequired", handshakeUserId == null,
                "serverTime", Instant.now().toString()
        ));
        if (handshakeUserId != null && handshakeUserId > 0) {
            send(session, "auth_ok", Map.of(
                    "userId", handshakeUserId,
                    "serverTime", Instant.now().toString()
            ));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> payload = parse(message == null ? "" : message.getPayload());
        String type = str(payload.get("type"));
        if ("auth".equals(type)) {
            handleAuth(session, str(payload.get("token")));
            return;
        }
        Long userId = registry.userId(session);
        if (userId == null) {
            send(session, "auth_failed", Map.of("message", "unauthorized"));
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("auth required"));
            return;
        }
        if ("ping".equals(type)) {
            send(session, "pong", Map.of("serverTime", Instant.now().toString()));
            return;
        }
        if ("query_online".equals(type)) {
            long peerId = longValue(payload.get("peerId"));
            send(session, "online_status", Map.of(
                    "userId", peerId,
                    "online", peerId > 0 && registry.isOnline(peerId)
            ));
            return;
        }
        send(session, "unsupported", Map.of("type", type));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        registry.remove(session);
        if (session != null && session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void handleAuth(WebSocketSession session, String token) throws Exception {
        try {
            AppUser user = tokenService.validateAndLoadUser(token);
            if (user == null || user.getId() == null) {
                throw new IllegalArgumentException("invalid user");
            }
            registry.bind(user.getId(), session);
            send(session, "auth_ok", Map.of(
                    "userId", user.getId(),
                    "serverTime", Instant.now().toString()
            ));
        } catch (Exception e) {
            send(session, "auth_failed", Map.of("message", "unauthorized"));
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("auth failed"));
        }
    }

    private Map<String, Object> parse(String raw) {
        try {
            return objectMapper.readValue(raw == null ? "" : raw, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void send(WebSocketSession session, String type, Object data) throws Exception {
        if (session == null || !session.isOpen()) {
            return;
        }
        String json = objectMapper.writeValueAsString(Map.of(
                "type", type,
                "data", data == null ? Map.of() : data,
                "serverTime", Instant.now().toString()
        ));
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        }
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String text = value == null ? "" : String.valueOf(value).trim();
            return text.isEmpty() ? 0L : Long.parseLong(text);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static Long handshakeUserId(WebSocketSession session) {
        Object value = session == null ? null : session.getAttributes().get(SocialChatHandshakeInterceptor.ATTR_USER_ID);
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            String text = value == null ? "" : String.valueOf(value).trim();
            return text.isEmpty() ? null : Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }
}
