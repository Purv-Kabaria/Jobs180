package com.companytracker.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SessionRevocationService {

    public static final String PRINCIPAL_NAME_INDEX =
            FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final StringRedisTemplate redisTemplate;

    public SessionRevocationService(
            FindByIndexNameSessionRepository<? extends Session> sessionRepository,
            StringRedisTemplate redisTemplate) {
        this.sessionRepository = sessionRepository;
        this.redisTemplate = redisTemplate;
    }

    public void revokeAllSessionsForEmail(String email) {
        Map<String, ? extends Session> sessions = sessionRepository.findByIndexNameAndIndexValue(
                PRINCIPAL_NAME_INDEX, email);
        if (sessions != null) {
            sessions.keySet().forEach(sessionRepository::deleteById);
        }
        // also clear any idempotency/cache keys tied to user email if needed later
    }

    public void revokeAllSessionsForUserId(Long userId, String email) {
        revokeAllSessionsForEmail(email);
        redisTemplate.delete("ct:cache:dashboard:" + userId);
    }
}
