package ru.mecotrade.kidtracker.security;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class TokenAttemptLimiterTest {
    static class MutableClock extends Clock {
        long now;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
    }
    @Test void budgetExpiresAndIsIndependentPerUser() {
        MutableClock clock = new MutableClock(); TokenAttemptLimiter limiter = new TokenAttemptLimiter(clock);
        for (int i = 0; i < 5; i++) limiter.check(1L);
        assertThrows(ResponseStatusException.class, () -> limiter.check(1L));
        assertDoesNotThrow(() -> limiter.check(2L));
        clock.now = 300000;
        assertDoesNotThrow(() -> limiter.check(1L));
    }
    @Test void boundedCapacityFailsClosedAndExpiredEntriesAreReclaimed() {
        MutableClock clock = new MutableClock(); TokenAttemptLimiter limiter = new TokenAttemptLimiter(clock);
        for (long i = 0; i < 10000; i++) limiter.check(i);
        assertThrows(ResponseStatusException.class, () -> limiter.check(10000L));
        clock.now = 300000;
        assertDoesNotThrow(() -> limiter.check(10000L));
    }
}
