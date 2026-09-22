package com.texto.emailplatform.auth.security;

import com.texto.emailplatform.common.api.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

/** IP-based protection for unauthenticated credential and tenant-provisioning endpoints. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Map<String, LocalCounter> LOCAL = new ConcurrentHashMap<>();

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final boolean production;

    public AuthenticationRateLimitFilter(StringRedisTemplate redis, ObjectMapper objectMapper, Environment environment) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.production = environment.acceptsProfiles("prod");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!production) return true;
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;

        String path = request.getRequestURI();
        return !(path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/admin/auth/login"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = path.endsWith("/register") ? 5 : 10;
        String bucket = Long.toString(Instant.now().getEpochSecond() / WINDOW.toSeconds());
        String key = "auth-rate:" + path + ":" + request.getRemoteAddr() + ":" + bucket;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) redis.expire(key, WINDOW.plusSeconds(30));
            if (count != null && count > limit) {
                reject(response, HttpStatus.TOO_MANY_REQUESTS.value(), "AUTH_RATE_LIMITED", "Too many authentication attempts");
                return;
            }
        } catch (RuntimeException unavailable) {
            if (production) {
                reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "AUTH_RATE_LIMIT_UNAVAILABLE", "Authentication is temporarily unavailable");
                return;
            }
            LocalCounter counter = LOCAL.compute(key, (ignored, current) -> current == null ? new LocalCounter() : current);
            if (counter.count.incrementAndGet() > limit) {
                reject(response, HttpStatus.TOO_MANY_REQUESTS.value(), "AUTH_RATE_LIMITED", "Too many authentication attempts");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(code, message));
    }

    private static final class LocalCounter {
        private final AtomicInteger count = new AtomicInteger();
    }
}
