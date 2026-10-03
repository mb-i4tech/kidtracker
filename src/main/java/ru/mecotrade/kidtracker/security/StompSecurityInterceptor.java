package ru.mecotrade.kidtracker.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;

/** Explicit inbound policy; broker queues are never accessible directly by clients. */
public class StompSecurityInterceptor implements ChannelInterceptor {
    public static final String CSRF_ATTRIBUTE = StompSecurityInterceptor.class.getName() + ".csrf";
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) return message;
        if (!(headers.getUser() instanceof Authentication auth) || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof UserPrincipal)) throw new AccessDeniedException("Authenticated session required");
        StompCommand command = headers.getCommand();
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            Object value = headers.getSessionAttributes() == null ? null : headers.getSessionAttributes().get(CSRF_ATTRIBUTE);
            if (!(value instanceof CsrfToken expected)) throw new AccessDeniedException("CSRF session required");
            String actual = headers.getFirstNativeHeader(expected.getHeaderName());
            if (!matchesMasked(expected.getToken(), actual)) throw new AccessDeniedException("Invalid CONNECT CSRF token");
        } else if (command == StompCommand.SUBSCRIBE) {
            String destination = headers.getDestination();
            if (!java.util.Set.of("/user/queue/report", "/user/queue/chat", "/user/queue/status").contains(destination == null ? "" : destination)) {
                throw new AccessDeniedException("Subscription destination denied");
            }
        } else if (command == StompCommand.SEND) {
            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            String prefix = "/user/" + principal.getUsername() + "/";
            String destination = headers.getDestination();
            if (destination == null || !destination.startsWith(prefix)
                    || !destination.substring(prefix.length()).matches("(report|status|chat/[^/]+/(before|after)/[0-9]+)")) {
                throw new AccessDeniedException("Message destination denied");
            }
        }
        return message;
    }
    // Spring Security's /api/csrf exposes a BREACH-masked token: random bytes || XOR(raw, random).
    static boolean matchesMasked(String expected, String supplied) {
        if (supplied == null) return false;
        try {
            byte[] raw = expected.getBytes(StandardCharsets.UTF_8);
            byte[] masked = java.util.Base64.getUrlDecoder().decode(supplied);
            if (masked.length != raw.length * 2) return false;
            byte[] decoded = new byte[raw.length];
            for (int i = 0; i < raw.length; i++) decoded[i] = (byte) (masked[i] ^ masked[i + raw.length]);
            return MessageDigest.isEqual(raw, decoded);
        } catch (IllegalArgumentException ex) { return false; }
    }
}
