package ru.mecotrade.kidtracker.security;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Fixed windows keyed only by authenticated user, shared by both confirmation APIs. */
@Component
public class TokenAttemptLimiter {
    static final int MAX_USERS = 10000;
    static final int ATTEMPTS = 5;
    static final long WINDOW_MILLIS = 300000;
    private final Clock clock;
    private final Map<Long, Window> windows = new HashMap<>();
    public TokenAttemptLimiter() { this(Clock.systemUTC()); }
    TokenAttemptLimiter(Clock clock) { this.clock = clock; }

    public synchronized void check(Long userId) {
        long now = clock.millis();
        windows.entrySet().removeIf(e -> now - e.getValue().start >= WINDOW_MILLIS);
        Window window = windows.get(userId);
        if (window == null) {
            // Fail closed rather than evicting active windows and allowing budget resets.
            if (windows.size() >= MAX_USERS) throw limited(300);
            window = new Window(now);
            windows.put(userId, window);
        }
        if (window.attempts >= ATTEMPTS) {
            throw limited(Math.max(1, (WINDOW_MILLIS - (now - window.start) + 999) / 1000));
        }
        window.attempts++;
    }
    private ResponseStatusException limited(long seconds) {
        return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Confirmation attempt limit reached") {
            @Override public HttpHeaders getHeaders() {
                HttpHeaders headers = new HttpHeaders();
                headers.set(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
                return headers;
            }
        };
    }
    private static class Window {
        final long start;
        int attempts;
        Window(long start) { this.start = start; }
    }
}
