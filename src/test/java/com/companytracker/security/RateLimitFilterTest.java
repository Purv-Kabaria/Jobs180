package com.companytracker.security;

import com.companytracker.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock FilterChain filterChain;

    RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                "local", 1024, 3600, true, "", "UTC",
                new AppProperties.S3("http://x", "b", "k", "s", "us-east-1", true),
                new AppProperties.RateLimit(2, 2, 2, 2, 2)
        );
        filter = new RateLimitFilter(redisTemplate, props, new ObjectMapper());
    }

    @SuppressWarnings("unchecked")
    private void windowReturns(long count, long ttlMs) {
        when(redisTemplate.execute(eq(RateLimitFilter.WINDOW_SCRIPT), anyList(), anyString()))
                .thenReturn((List) List.of(count, ttlMs));
    }

    @Test
    void allowsUnderLimit() throws Exception {
        windowReturns(1L, 60_000L);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/dashboard");
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, filterChain);
        verify(filterChain).doFilter(req, res);
    }

    @Test
    void rejectsApiOverLimitWith429AndExactRetryAfter() throws Exception {
        windowReturns(3L, 12_400L);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/me");
        req.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, filterChain);
        assertEquals(429, res.getStatus());
        assertEquals("13", res.getHeader("Retry-After"));
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void ignoresClientSuppliedForwardedForHeader() throws Exception {
        windowReturns(1L, 60_000L);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/dashboard");
        req.setRemoteAddr("10.0.0.3");
        req.addHeader("X-Forwarded-For", "1.2.3.4");
        filter.doFilter(req, new MockHttpServletResponse(), filterChain);
        verify(redisTemplate).execute(eq(RateLimitFilter.WINDOW_SCRIPT), eq(List.of("ct:rl:ip:10.0.0.3")), anyString());
    }

    @Test
    void failsOpenWhenRedisIsDown() throws Exception {
        when(redisTemplate.execute(eq(RateLimitFilter.WINDOW_SCRIPT), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/me");
        req.setRemoteAddr("10.0.0.4");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, filterChain);
        verify(filterChain).doFilter(req, res);
    }
}
