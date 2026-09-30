package com.boxy.boxy.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window rate limit, per client IP, on the two endpoints a caller can hit without ever
 * being authenticated: {@code POST /auth/login} (credential stuffing / password guessing) and
 * {@code POST /auth/refresh}. This is a coarse, in-memory defense layered on top of the
 * per-account lockout in {@code AuthService} (which is what actually stops a single account
 * being brute-forced regardless of source IP) — it does not survive an app restart or scale
 * across instances, which is fine for a defense-in-depth check, not the only one.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int LOGIN_LIMIT_PER_WINDOW = 5;
    private static final int REFRESH_LIMIT_PER_WINDOW = 20;
    private static final long WINDOW_MILLIS = 60_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(long windowStartMillis, AtomicInteger count) {}

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean isLogin = "POST".equalsIgnoreCase(request.getMethod()) && path.endsWith("/auth/login");
        boolean isRefresh = "POST".equalsIgnoreCase(request.getMethod()) && path.endsWith("/auth/refresh");

        if (!isLogin && !isRefresh) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = (isLogin ? "login:" : "refresh:") + request.getRemoteAddr();
        int limit = isLogin ? LOGIN_LIMIT_PER_WINDOW : REFRESH_LIMIT_PER_WINDOW;

        if (isOverLimit(key, limit)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Demasiados intentos. Intenta de nuevo en un minuto.\",\"status\":429}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isOverLimit(String key, int limit) {
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStartMillis() >= WINDOW_MILLIS) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return window.count().get() > limit;
    }
}
