package com.companytracker.security;

import com.companytracker.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock FilterChain filterChain;

    RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        AppProperties props = new AppProperties(
                "local", 1024, 3600, true, "", "UTC",
                new AppProperties.S3("http://x", "b", "k", "s", "us-east-1", true),
                new AppProperties.RateLimit(2, 2, 2, 2, 2)
        );
        filter = new RateLimitFilter(redisTemplate, props, new ObjectMapper());
    }

    @Test
    void allowsUnderLimit() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/dashboard");
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, filterChain);
        verify(filterChain).doFilter(req, res);
    }

    @Test
    void rejectsApiOverLimitWith429() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(3L);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/me");
        req.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, filterChain);
        assertEquals(429, res.getStatus());
        verify(filterChain, never()).doFilter(any(), any());
    }
}
