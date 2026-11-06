package com.companytracker.security;

import com.companytracker.config.AppProperties;
import com.companytracker.web.error.AppException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Replays the first result for a repeated {@code Idempotency-Key} instead of running the write again.
 *
 * <p>Flow: look up the stored record; if absent, take a short Redis lock so two concurrent requests
 * with the same key cannot both run the action; run it; store {payload hash, created id}; release
 * the lock. A retry with the same key and the same body gets the original resource back. The same
 * key with a different body is a client bug and returns 409.
 *
 * <p>Callers usually run inside a {@code @Transactional} service method. The record and the lock
 * release are therefore deferred to {@code afterCompletion}: the record is stored only once the
 * row is committed, and dropped if the transaction rolls back. Storing it earlier would let a
 * concurrent retry replay an id whose row is not visible yet (a false 404), or point at a row
 * that a later rollback removed.
 *
 * <p>The lock value is a random token and is released with a compare-and-delete script. A plain
 * DEL is unsafe: if the action outlives the lock TTL, a second request can take the lock, and the
 * first request's DEL would then remove the second request's lock.
 *
 * <p>Known limit: the record is written to Redis after the database commit. If the process dies in
 * that gap, a retry runs the action again. Closing that gap fully means storing the key in Postgres
 * in the same transaction as the write; it is not done here because the gap is milliseconds wide
 * and the API also has per-resource unique constraints for the cases that matter most.
 */
@Service
public class IdempotencyService {

    static final RedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    /**
     * Used only to fingerprint request bodies. Map.of() iteration order is randomised per JVM, so
     * without sorted keys the same body could hash differently on another replica or after a
     * restart, and an honest retry would be rejected as a payload mismatch.
     */
    private final ObjectMapper canonicalMapper;
    private final AppProperties properties;

    public IdempotencyService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, AppProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.canonicalMapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
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

        String lockToken = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, Duration.ofSeconds(30));
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

        boolean deferred = false;
        try {
            existing = redisTemplate.opsForValue().get(recordKey);
            if (existing != null) {
                return replay(existing, payloadHash, reloadById, action);
            }
            T result = action.get();
            Long id = extractId(result);
            String recordJson = write(new IdempotencyRecord(payloadHash, id, result instanceof Map ? result : null));
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        try {
                            if (status == STATUS_COMMITTED) {
                                storeRecord(recordKey, recordJson);
                            }
                        } finally {
                            releaseLock(lockKey, lockToken);
                        }
                    }
                });
                deferred = true;
            } else {
                storeRecord(recordKey, recordJson);
            }
            return result;
        } finally {
            if (!deferred) {
                releaseLock(lockKey, lockToken);
            }
        }
    }

    private void storeRecord(String recordKey, String recordJson) {
        redisTemplate.opsForValue().set(
                recordKey,
                recordJson,
                Duration.ofSeconds(Math.max(1, properties.idempotencyTtlSeconds())));
    }

    private void releaseLock(String lockKey, String lockToken) {
        redisTemplate.execute(RELEASE_LOCK, List.of(lockKey), lockToken);
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
            String json = canonicalMapper.writeValueAsString(payload == null ? Map.of() : payload);
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
