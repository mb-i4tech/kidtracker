package ru.mecotrade.kidtracker.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.filter.OncePerRequestFilter;

/** Short, in-memory attempt windows, not persistent account lockouts. Never trusts proxy headers. */
public final class LoginAttemptFilter extends OncePerRequestFilter {
    static final int ACCOUNT_ATTEMPTS = 10, CLIENT_ATTEMPTS = 60, MAX_KEYS = 10000;
    static final long WINDOW_MS = 60000;
    private final Clock clock;
    private final Map<String, Window> accounts = new HashMap<>(), clients = new HashMap<>();
    public LoginAttemptFilter() { this(Clock.systemUTC()); }
    LoginAttemptFilter(Clock clock) { this.clock = clock; }

    synchronized long retryAfter(String account, String client) {
        long now = clock.millis();
        accounts.values().removeIf(w -> now - w.start >= WINDOW_MS);
        clients.values().removeIf(w -> now - w.start >= WINDOW_MS);
        // Bound key length too. Oversized identities cannot consume heap or password hashing CPU.
        if (account == null || account.length() > 256) return 60;
        account = account.trim().toLowerCase(Locale.ROOT);
        Window c = clients.get(client), a = accounts.get(account);
        if (c == null && clients.size() >= MAX_KEYS || a == null && accounts.size() >= MAX_KEYS) return 60;
        if (c == null) { c = new Window(now); clients.put(client, c); }
        if (a == null) { a = new Window(now); accounts.put(account, a); }
        // All submissions consume the client budget, including blocked account submissions.
        c.attempts++;
        if (c.attempts > CLIENT_ATTEMPTS) return remaining(c, now);
        if (a.attempts >= ACCOUNT_ATTEMPTS) return remaining(a, now);
        a.attempts++;
        return 0;
    }
    private long remaining(Window w, long now) { return Math.max(1, (WINDOW_MS - (now - w.start) + 999) / 1000); }
    private static final class Window {
        final long start; long attempts;
        Window(long start) { this.start = start; }
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && "/login".equals(request.getServletPath())) {
            long retry = retryAfter(request.getParameter("username"), request.getRemoteAddr());
            if (retry > 0) {
                response.setStatus(429);
                response.setHeader("Retry-After", Long.toString(retry));
                response.setHeader("Cache-Control", "no-store");
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write("<!doctype html><html lang=\"en\"><title>Sign-in temporarily limited</title>"
                        + "<h1>Too many sign-in attempts</h1><p>Please wait " + retry
                        + " seconds, then <a href=\"" + request.getContextPath() + "/login\">return to sign in</a>.</p></html>");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
