package com.companytracker.security;

import com.companytracker.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

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

        if (!allow("ct:rl:ip:" + ip, properties.rateLimit().ipPerMin())) {
            reject(request, response);
            return;
        }

        if ("POST".equalsIgnoreCase(method) && (path.equals("/login") || path.equals("/api/v1/auth/login"))) {
            if (!allow("ct:rl:login:ip:" + ip, properties.rateLimit().loginPerMin())) {
                reject(request, response);
                return;
            }
        }
        if ("POST".equalsIgnoreCase(method) && (path.equals("/signup") || path.equals("/api/v1/auth/signup"))) {
            if (!allow("ct:rl:signup:ip:" + ip, properties.rateLimit().signupPerMin())) {
                reject(request, response);
                return;
            }
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TrackerUserDetails details) {
            if (isWrite(method) && !path.startsWith("/actuator")) {
                if (!allow("ct:rl:write:" + details.getId(), properties.rateLimit().writePerMin())) {
                    reject(request, response);
                    return;
                }
            }
            if (isWrite(method) && (path.contains("/resumes") || path.contains("/resources"))
                    && request.getContentType() != null
                    && request.getContentType().startsWith("multipart/")) {
                if (!allow("ct:rl:upload:" + details.getId(), properties.rateLimit().uploadPerMin())) {
                    reject(request, response);
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

    private boolean allow(String key, int limitPerMin) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        return count == null || count <= limitPerMin;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
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
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
