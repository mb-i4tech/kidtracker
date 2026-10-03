package ru.mecotrade.kidtracker.security;

import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import static org.junit.jupiter.api.Assertions.*;
class CsrfHandshakeInterceptorTest {
    @Test void handshakeRequiresSessionTokenAndCopiesItForConnectValidation() {
        var interceptor = new CsrfHandshakeInterceptor();
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var attributes = new HashMap<String, Object>();
        assertFalse(interceptor.beforeHandshake(new ServletServerHttpRequest(request), new ServletServerHttpResponse(response), null, attributes));
        var repository = new HttpSessionCsrfTokenRepository();
        var token = repository.generateToken(request);
        repository.saveToken(token, request, response);
        assertTrue(interceptor.beforeHandshake(new ServletServerHttpRequest(request), new ServletServerHttpResponse(response), null, attributes));
        assertSame(token, attributes.get(StompSecurityInterceptor.CSRF_ATTRIBUTE));
    }
}
