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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private void authenticate(UserAccount user) {
        TrackerUserDetails details = new TrackerUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
