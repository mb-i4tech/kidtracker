package ru.mecotrade.kidtracker.security;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class LoginAttemptFilterTest {
    @Test void accountAndClientBudgetsCannotBeBypassedByRotatingOtherKey() {
        LoginAttemptFilter f = new LoginAttemptFilter();
        for (int i=0;i<10;i++) assertEquals(0, f.retryAfter("Account", "client"+i));
        assertTrue(f.retryAfter("ACCOUNT", "different") > 0);
        f = new LoginAttemptFilter();
        for (int i=0;i<60;i++) assertEquals(0, f.retryAfter("account"+i, "client"));
        assertTrue(f.retryAfter("fresh", "client") > 0);
    }
    @Test void windowExpiresWithoutPasswordOrAccountMutation() {
        class MutableClock extends Clock {
            long now; public ZoneId getZone(){return ZoneOffset.UTC;}
            public Clock withZone(ZoneId z){return this;} public Instant instant(){return Instant.ofEpochMilli(now);}
        }
        MutableClock clock = new MutableClock(); LoginAttemptFilter f = new LoginAttemptFilter(clock);
        for (int i=0;i<10;i++) f.retryAfter("a", "c");
        assertEquals(60, f.retryAfter("a", "c"));
        clock.now = 60000; assertEquals(0, f.retryAfter("a", "c"));
    }
    @Test void forwardedHeadersDoNotResetClientAndGetLoginIsUnaffected() throws Exception {
        LoginAttemptFilter f = new LoginAttemptFilter();
        for (int i=0;i<61;i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/login"); req.setServletPath("/login");
            req.addParameter("username", "user"+i); req.addHeader("X-Forwarded-For", "192.0.2."+i);
            MockHttpServletResponse res = new MockHttpServletResponse();
            f.doFilter(req,res,new MockFilterChain());
            assertEquals(i==60?429:200,res.getStatus());
            if(i==60) {assertEquals("60",res.getHeader("Retry-After"));assertTrue(res.getContentAsString().contains("/login"));}
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(new MockHttpServletRequest("GET", "/login"),res,new MockFilterChain());
        assertEquals(200,res.getStatus());
    }
    @Test void capacityFailsClosedAndOversizeIdentityIsRejected() {
        LoginAttemptFilter f = new LoginAttemptFilter();
        for(int i=0;i<LoginAttemptFilter.MAX_KEYS;i++) assertEquals(0,f.retryAfter("a"+i,"c"+i));
        assertTrue(f.retryAfter("new","new")>0);
        assertTrue(f.retryAfter("x".repeat(257),"c")>0);
    }
}
