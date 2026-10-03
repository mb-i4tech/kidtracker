package ru.mecotrade.kidtracker.security;

import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

public class CsrfHandshakeInterceptor implements HandshakeInterceptor {
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servlet)) return false;
        CsrfToken token = new HttpSessionCsrfTokenRepository().loadToken(servlet.getServletRequest());
        if (token == null) return false;
        attributes.put(StompSecurityInterceptor.CSRF_ATTRIBUTE, token);
        return true;
    }
    @Override public void afterHandshake(ServerHttpRequest req, ServerHttpResponse res,
            WebSocketHandler handler, Exception ex) { }
}
