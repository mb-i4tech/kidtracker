package ru.mecotrade.kidtracker.security;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.mecotrade.kidtracker.dao.model.*;
import javax.servlet.FilterChain;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class UserDeviceFilterTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private UserInfo login() {
        UserInfo user = UserInfo.builder().id(1L).username("owner").password("stored-hash")
                .kids(Collections.singletonList(KidInfo.builder().device(DeviceInfo.of("1234567890")).build())).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal(user, Collections.emptyList()), null, Collections.emptyList()));
        return user;
    }
    @Test void ownedDeviceIsAllowedWithoutMutatingPersistedPassword() throws Exception {
        UserInfo user = login(); FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/device/1234567890/status");
        MockHttpServletResponse response = new MockHttpServletResponse();
        new UserDeviceFilter().doFilter(request, response, chain);
        verify(chain).doFilter(request, response); assertEquals("stored-hash", user.getPassword());
    }
    @Test void foreignDeviceIsRejectedAlsoBehindContextPath() {
        login(); FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/tracker/api/device/9999999999/status");
        request.setContextPath("/tracker");
        assertThrows(InsufficientAuthenticationException.class,
                () -> new UserDeviceFilter().doFilter(request, new MockHttpServletResponse(), chain));
        verifyNoInteractions(chain);
    }
}
