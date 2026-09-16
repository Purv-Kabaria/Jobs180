package com.companytracker.security;

import com.companytracker.config.AppProperties;
import com.companytracker.web.error.AppException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public IdempotencyService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, AppProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public <T> T execute(String scope, String key, Object payload, Supplier<T> action) {
        return execute(scope, key, payload, action, id -> action.get());
    }

    public <T> T execute(String scope, String key, Object payload, Supplier<T> action, Function<Long, T> reloadById) {
        if (key == null || key.isBlank()) {
            return action.get();
        }
        String recordKey = "ct:idem:" + scope + ":" + key;
        String lockKey = "ct:idem:lock:" + scope + ":" + key;
        String payloadHash = hash(payload);

        String existing = redisTemplate.opsForValue().get(recordKey);
        if (existing != null) {
            return replay(existing, payloadHash, reloadById, action);
        }

        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(30));
        if (Boolean.FALSE.equals(locked)) {
            for (int i = 0; i < 20; i++) {
                sleep(100);
                existing = redisTemplate.opsForValue().get(recordKey);
                if (existing != null) {
                    return replay(existing, payloadHash, reloadById, action);
                }
            }
            throw AppException.conflict("IDEMPOTENCY_IN_FLIGHT", "Request is already being processed");
        }

        try {
            existing = redisTemplate.opsForValue().get(recordKey);
            if (existing != null) {
                return replay(existing, payloadHash, reloadById, action);
            }
            T result = action.get();
            Long id = extractId(result);
            IdempotencyRecord record = new IdempotencyRecord(payloadHash, id, result instanceof Map ? result : null);
            redisTemplate.opsForValue().set(
                    recordKey,
                    write(record),
                    Duration.ofSeconds(properties.idempotencyTtlSeconds())
            );
            return result;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T replay(String json, String payloadHash, Function<Long, T> reloadById, Supplier<T> action) {
        IdempotencyRecord record = read(json);
        if (!record.payloadHash().equals(payloadHash)) {
            throw AppException.conflict("IDEMPOTENCY_CONFLICT", "Idempotency key reused with different payload");
        }
        if (record.mapBody() instanceof Map<?, ?> map) {
            return (T) map;
        }
        if (record.id() != null) {
            return reloadById.apply(record.id());
        }
        return action.get();
    }

    private Long extractId(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof Map<?, ?> map && map.get("id") instanceof Number n) {
            return n.longValue();
        }
        try {
            Method m = result.getClass().getMethod("getId");
            Object id = m.invoke(result);
            if (id instanceof Long l) {
                return l;
            }
            if (id instanceof Number n) {
                return n.longValue();
            }
        } catch (ReflectiveOperationException ignored) {
            // no id
        }
        return null;
    }

    private String hash(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash idempotency payload", e);
        }
    }

    private String write(IdempotencyRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private IdempotencyRecord read(String json) {
        try {
            return objectMapper.readValue(json, IdempotencyRecord.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void sleep(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record IdempotencyRecord(String payloadHash, Long id, Object mapBody) {}
}
