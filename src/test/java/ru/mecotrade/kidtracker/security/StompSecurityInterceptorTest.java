package ru.mecotrade.kidtracker.security;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import ru.mecotrade.kidtracker.dao.model.UserInfo;
import static org.junit.jupiter.api.Assertions.*;
class StompSecurityInterceptorTest {
    private final StompSecurityInterceptor interceptor = new StompSecurityInterceptor();
    private final CsrfToken raw = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-session-token");
    StompHeaderAccessor headers(StompCommand command) {
        StompHeaderAccessor h = StompHeaderAccessor.create(command);
        h.setLeaveMutable(true);
        h.setUser(new UsernamePasswordAuthenticationToken(new UserPrincipal(
                UserInfo.builder().id(1L).username("owner").build(), List.of()), null, List.of()));
        h.setSessionAttributes(Map.of(StompSecurityInterceptor.CSRF_ATTRIBUTE, raw));
        return h;
    }
    void send(StompHeaderAccessor h) { interceptor.preSend(MessageBuilder.createMessage(new byte[0], h.getMessageHeaders()), null); }
    String masked() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        new XorCsrfTokenRequestAttributeHandler().handle(request, new MockHttpServletResponse(), () -> raw);
        return ((CsrfToken) request.getAttribute(CsrfToken.class.getName())).getToken();
    }
    @Test void connectNeedsAuthenticatedSessionAndCorrectCsrf() {
        StompHeaderAccessor h = headers(StompCommand.CONNECT);
        assertThrows(AccessDeniedException.class, () -> send(h));
        h.setNativeHeader(raw.getHeaderName(), "wrong");
        assertThrows(AccessDeniedException.class, () -> send(h));
        h.setNativeHeader(raw.getHeaderName(), masked());
        assertDoesNotThrow(() -> send(h));
        h.setUser(null);
        assertThrows(AccessDeniedException.class, () -> send(h));
    }
    @Test void anotherSessionsTokenCannotConnect() {
        StompHeaderAccessor h = headers(StompCommand.CONNECT);
        h.setNativeHeader(raw.getHeaderName(), masked());
        h.setSessionAttributes(Map.of(StompSecurityInterceptor.CSRF_ATTRIBUTE,
                new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "other-session-token")));
        assertThrows(AccessDeniedException.class, () -> send(h));
    }
    @Test void privateQueuesOnlyAndNoBrokerSpoofing() {
        StompHeaderAccessor h = headers(StompCommand.SUBSCRIBE);
        for (String destination : List.of("/queue/report-user123", "/queue/report", "/user/other/queue/report")) {
            h.setDestination(destination); assertThrows(AccessDeniedException.class, () -> send(h));
        }
        h.setDestination("/user/queue/report"); assertDoesNotThrow(() -> send(h));
        StompHeaderAccessor send = headers(StompCommand.SEND);
        send.setDestination("/queue/report"); assertThrows(AccessDeniedException.class, () -> send(send));
        send.setDestination("/user/other/report"); assertThrows(AccessDeniedException.class, () -> send(send));
        send.setDestination("/user/owner/report"); assertDoesNotThrow(() -> send(send));
    }
}
