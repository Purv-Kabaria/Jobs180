package com.companytracker.security;

import com.companytracker.config.AppProperties;
import com.companytracker.web.error.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    IdempotencyService service;
    ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        AppProperties props = new AppProperties(
                "s3", 1024, 86400, true, "", "UTC",
                new AppProperties.S3("http://localhost:9000", "bucket", "k", "s", "us-east-1", true),
                new AppProperties.RateLimit(5, 3, 60, 10, 120)
        );
        service = new IdempotencyService(redisTemplate, mapper, props);
    }

    @Test
    void executesWhenNoExistingRecord() {
        AtomicInteger calls = new AtomicInteger();
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        Map<?, ?> result = service.execute("user:1", "key-1", Map.of("a", 1), () -> {
            calls.incrementAndGet();
            return Map.of("id", 10L, "ok", true);
        });

        assertEquals(1, calls.get());
        assertEquals(10L, ((Number) result.get("id")).longValue());
        verify(valueOps).set(anyString(), anyString(), any());
    }

    @Test
    void conflictsOnDifferentPayload() throws Exception {
        IdempotencyService.IdempotencyRecord record =
                new IdempotencyService.IdempotencyRecord("deadbeef", 1L, Map.of("id", 1));
        when(valueOps.get(anyString())).thenReturn(mapper.writeValueAsString(record));

        AppException ex = assertThrows(AppException.class, () ->
                service.execute("user:1", "key-1", Map.of("a", 2), () -> Map.of("id", 2)));
        assertEquals("IDEMPOTENCY_CONFLICT", ex.getCode());
    }

    @Test
    void replaysCachedMapBody() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String hash;
        // compute hash the same way service does for payload {"a":1}
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] bytes = mapper.writeValueAsBytes(Map.of("a", 1));
        hash = java.util.HexFormat.of().formatHex(digest.digest(bytes));

        IdempotencyService.IdempotencyRecord record =
                new IdempotencyService.IdempotencyRecord(hash, 10L, Map.of("id", 10, "ok", true));
        when(valueOps.get(anyString())).thenReturn(mapper.writeValueAsString(record));

        Map<?, ?> result = service.execute("user:1", "key-1", Map.of("a", 1), () -> {
            calls.incrementAndGet();
            return Map.of("id", 99);
        });

        assertEquals(0, calls.get());
        assertEquals(10, ((Number) result.get("id")).intValue());
    }

    @Test
    void releasesOnlyItsOwnLockToken() {
        when(valueOps.get(anyString())).thenReturn(null);
        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        when(valueOps.setIfAbsent(anyString(), token.capture(), any())).thenReturn(true);

        service.execute("user:1", "key-2", Map.of("a", 1), () -> Map.of("id", 5L));

        verify(redisTemplate).execute(
                eq(IdempotencyService.RELEASE_LOCK),
                eq(List.of("ct:idem:lock:user:1:key-2")),
                eq(token.getValue()));
    }

    @Test
    void releasesLockEvenWhenActionFails() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        assertThrows(IllegalStateException.class, () ->
                service.execute("user:1", "key-3", Map.of("a", 1), () -> {
                    throw new IllegalStateException("db down");
                }));

        verify(redisTemplate).execute(eq(IdempotencyService.RELEASE_LOCK), eq(List.of("ct:idem:lock:user:1:key-3")), anyString());
    }

    @Test
    void payloadHashIgnoresMapKeyOrder() {
        AtomicInteger calls = new AtomicInteger();
        java.util.Map<String, Object> ab = new java.util.LinkedHashMap<>();
        ab.put("a", 1);
        ab.put("b", 2);
        java.util.Map<String, Object> ba = new java.util.LinkedHashMap<>();
        ba.put("b", 2);
        ba.put("a", 1);

        when(valueOps.get(anyString())).thenReturn(null);
        when(valueOps.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        service.execute("user:1", "key-4", ab, () -> Map.of("id", 7L));
        verify(valueOps).set(anyString(), stored.capture(), any());

        when(valueOps.get(anyString())).thenReturn(stored.getValue());
        Map<?, ?> replay = service.execute("user:1", "key-4", ba, () -> {
            calls.incrementAndGet();
            return Map.of("id", 8L);
        });
        assertEquals(0, calls.get());
        assertEquals(7L, ((Number) replay.get("id")).longValue());
    }
}
