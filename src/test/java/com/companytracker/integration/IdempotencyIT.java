package com.companytracker.integration;

import com.companytracker.domain.Company;
import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.UserAccountRepository;
import com.companytracker.security.IdempotencyService;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.service.CompanyService;
import com.companytracker.support.AbstractIntegrationTest;
import com.companytracker.support.TestDataFactory;
import com.companytracker.web.error.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyIT extends AbstractIntegrationTest {

    @Autowired UserAccountRepository userAccountRepository;
    @Autowired CompanyService companyService;
    @Autowired IdempotencyService idempotencyService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void sameKeySamePayloadReplaysSameCompany() {
        UserAccount user = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, "idem@test.com", "password123", UserRole.USER);
        authenticate(user);
        String key = "create-co-" + UUID.randomUUID();

        Company first = companyService.create("Idem Co", null, null, null, null, key);
        Company second = companyService.create("Idem Co", null, null, null, null, key);

        assertEquals(first.getId(), second.getId());
        SecurityContextHolder.clearContext();
    }

    @Test
    void sameKeyDifferentPayloadConflicts() {
        UserAccount user = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, "idem2@test.com", "password123", UserRole.USER);
        authenticate(user);
        String key = "create-co-conflict-" + UUID.randomUUID();

        companyService.create("Alpha", null, null, null, null, key);
        AppException ex = assertThrows(AppException.class,
                () -> companyService.create("Beta", null, null, null, null, key));
        assertEquals("IDEMPOTENCY_CONFLICT", ex.getCode());
        SecurityContextHolder.clearContext();
    }

    /**
     * Ten clients retry the same create at the same instant. Exactly one company may exist
     * afterwards: every request that succeeds must return that one id, and the rest may only
     * fail with the explicit in-flight conflict, never by creating a second row.
     */
    @Test
    void concurrentRetriesWithSameKeyCreateExactlyOneCompany() throws Exception {
        UserAccount user = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, "idem3@test.com", "password123", UserRole.USER);
        String key = "create-co-race-" + UUID.randomUUID();
        int clients = 10;
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        CountDownLatch start = new CountDownLatch(1);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        Set<String> otherErrors = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < clients; i++) {
            pool.submit(() -> {
                authenticate(user);
                try {
                    start.await();
                    ids.add(companyService.create("Race Co", null, null, null, null, key).getId());
                } catch (AppException e) {
                    if (!"IDEMPOTENCY_IN_FLIGHT".equals(e.getCode())) {
                        otherErrors.add(e.getCode());
                    }
                } catch (Exception e) {
                    otherErrors.add(e.toString());
                } finally {
                    SecurityContextHolder.clearContext();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertTrue(otherErrors.isEmpty(), "unexpected errors: " + otherErrors);
        assertEquals(1, ids.size(), "same key must never create two companies");
    }

    private void authenticate(UserAccount user) {
        TrackerUserDetails details = new TrackerUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
