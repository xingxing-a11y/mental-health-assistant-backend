package com.mindharbor.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.servlet.HandlerInterceptor;

/** Limits login attempts per client address before BCrypt is invoked. */
public class LoginRateLimitInterceptor implements HandlerInterceptor {

    private static final int MAX_ATTEMPTS = 10;
    private static final long WINDOW_SECONDS = 60;

    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws IOException {
        String clientAddress = request.getRemoteAddr();
        long now = Instant.now().getEpochSecond();
        AttemptWindow window = attempts.compute(clientAddress, (key, current) -> {
            if (current == null || now - current.startedAt >= WINDOW_SECONDS) {
                return new AttemptWindow(now, 1);
            }
            return new AttemptWindow(current.startedAt, current.count + 1);
        });

        if (window.count > MAX_ATTEMPTS) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(WINDOW_SECONDS));
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"code\":429,\"message\":\"登录请求过于频繁，请稍后再试\",\"data\":null}");
            return false;
        }

        return true;
    }

    private record AttemptWindow(long startedAt, int count) {
    }
}
