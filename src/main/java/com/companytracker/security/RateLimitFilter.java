package com.companytracker.security;

import com.companytracker.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Fixed-window rate limiter backed by Redis.
 *
 * <p>Each window is one Lua call: INCR the counter and, only when the key is new, set its TTL.
 * Doing INCR and EXPIRE as two separate round trips has a failure window: if the process dies
 * between them the key never expires and that client is locked out forever. The script makes the
 * pair atomic, and also returns the remaining TTL so {@code Retry-After} is exact instead of a
 * hard-coded 60.
 *
 * <p>Fixed windows allow a burst of up to 2x the limit across a window boundary. That is an
 * accepted trade-off here: the limits exist to stop credential stuffing and runaway clients, not
 * to meter usage, and the fixed window costs one key and one round trip per check.
 *
 * <p>If Redis is unreachable the filter fails open and logs a warning. Rate limiting is a
 * protective layer, not a correctness one, so losing it should not take the whole app down.
 *
 * <p>The client IP comes from {@link HttpServletRequest#getRemoteAddr()}. With
 * {@code server.forward-headers-strategy=native}, Tomcat only rewrites it from
 * {@code X-Forwarded-For} when the immediate peer is a trusted internal proxy, so a client cannot
 * dodge per-IP limits by sending a fake header.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    static final RedisScript<List> WINDOW_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "local ttl = redis.call('PTTL', KEYS[1]) "
                    + "if ttl < 0 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) ttl = tonumber(ARGV[1]) end "
                    + "return {c, ttl}",
            List.class);

    static final long WINDOW_MS = 60_000L;

    private final StringRedisTemplate redisTemplate;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(StringRedisTemplate redisTemplate, AppProperties properties, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String ip = clientIp(request);

        long retry = check("ct:rl:ip:" + ip, properties.rateLimit().ipPerMin());
        if (retry > 0) {
            reject(request, response, retry);
            return;
        }

        if ("POST".equalsIgnoreCase(method) && (path.equals("/login") || path.equals("/api/v1/auth/login"))) {
            retry = check("ct:rl:login:ip:" + ip, properties.rateLimit().loginPerMin());
            if (retry > 0) {
                reject(request, response, retry);
                return;
            }
        }
        if ("POST".equalsIgnoreCase(method) && (path.equals("/signup") || path.equals("/api/v1/auth/signup"))) {
            retry = check("ct:rl:signup:ip:" + ip, properties.rateLimit().signupPerMin());
            if (retry > 0) {
                reject(request, response, retry);
                return;
            }
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TrackerUserDetails details) {
            if (isWrite(method) && !path.startsWith("/actuator")) {
                retry = check("ct:rl:write:" + details.getId(), properties.rateLimit().writePerMin());
                if (retry > 0) {
                    reject(request, response, retry);
                    return;
                }
            }
            if (isWrite(method) && (path.contains("/resumes") || path.contains("/resources"))
                    && request.getContentType() != null
                    && request.getContentType().startsWith("multipart/")) {
                retry = check("ct:rl:upload:" + details.getId(), properties.rateLimit().uploadPerMin());
                if (retry > 0) {
                    reject(request, response, retry);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isWrite(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    /**
     * @return 0 when the request is allowed, otherwise the seconds until the window resets.
     */
    long check(String key, int limitPerMin) {
        List<?> result;
        try {
            result = redisTemplate.execute(WINDOW_SCRIPT, List.of(key), String.valueOf(WINDOW_MS));
        } catch (RuntimeException e) {
            log.warn("rate_limit.redis_unavailable key={} error={}", key, e.toString());
            return 0;
        }
        if (result == null || result.size() < 2) {
            return 0;
        }
        long count = ((Number) result.get(0)).longValue();
        long ttlMs = ((Number) result.get(1)).longValue();
        if (count <= limitPerMin) {
            return 0;
        }
        return Math.max(1, (ttlMs + 999) / 1000);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        if (request.getRequestURI().startsWith("/api/")) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "code", "RATE_LIMITED",
                    "message", "Too many requests. Try again later."
            ));
        } else {
            response.sendRedirect("/login?error=rate_limited");
        }
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
