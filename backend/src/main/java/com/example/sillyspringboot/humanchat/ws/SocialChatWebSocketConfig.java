package com.example.sillyspringboot.humanchat.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class SocialChatWebSocketConfig implements WebSocketConfigurer {

    private final SocialChatWebSocketHandler handler;
    private final SocialChatHandshakeInterceptor handshakeInterceptor;

    public SocialChatWebSocketConfig(
            SocialChatWebSocketHandler handler,
            SocialChatHandshakeInterceptor handshakeInterceptor
    ) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/social")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns(handshakeInterceptor.allowedOriginPatterns());
    }
}
