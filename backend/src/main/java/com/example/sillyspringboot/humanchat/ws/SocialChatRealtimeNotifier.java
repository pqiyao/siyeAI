package com.example.sillyspringboot.humanchat.ws;

import org.springframework.stereotype.Component;

@Component
public class SocialChatRealtimeNotifier {

    private final SocialChatWsSessionRegistry registry;

    public SocialChatRealtimeNotifier(SocialChatWsSessionRegistry registry) {
        this.registry = registry;
    }

    public boolean isOnline(long userId) {
        return registry.isOnline(userId);
    }

    public int sendToUser(long userId, String type, Object data) {
        return sendResultToUser(userId, type, data).sentCount();
    }

    public SendResult sendResultToUser(long userId, String type, Object data) {
        return registry.sendResultToUser(userId, type, data);
    }

    public record SendResult(
            long userId,
            String type,
            boolean online,
            int sentCount,
            int staleCount,
            int sessionCount
    ) {
        public String deliveryStatus() {
            if (sentCount > 0 && staleCount > 0) {
                return "partial";
            }
            if (sentCount > 0) {
                return "success";
            }
            return online ? "failed" : "offline";
        }
    }
}
