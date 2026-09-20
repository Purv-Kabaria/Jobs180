package com.companytracker.integration;

import com.companytracker.domain.Company;
import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.CompanyRepository;
import com.companytracker.repo.UserAccountRepository;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyLifecycleIT extends AbstractIntegrationTest {

    @Autowired UserAccountRepository userAccountRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyService companyService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void softDeleteRestoreAndHardDeleteFlow() {
        UserAccount user = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, "life@test.com", "password123", UserRole.USER);
        authenticate(user);

        Company created = companyService.create("Lifecycle Co", "https://ex.com", "Tech", "SF", null,
                UUID.randomUUID().toString());
        assertFalse(created.isDeleted());

        companyService.softDelete(created.getId(), UUID.randomUUID().toString());
        assertTrue(companyRepository.findById(created.getId()).orElseThrow().isDeleted());
        assertThrows(AppException.class, () -> companyService.getActive(created.getId()));

        companyService.restore(created.getId(), UUID.randomUUID().toString());
        assertFalse(companyRepository.findById(created.getId()).orElseThrow().isDeleted());

        companyService.softDelete(created.getId(), UUID.randomUUID().toString());
        companyService.hardDelete(created.getId(), UUID.randomUUID().toString());
        assertTrue(companyRepository.findById(created.getId()).isEmpty());

        SecurityContextHolder.clearContext();
    }

    @Test
    void optimisticLockOnUpdate() {
        UserAccount user = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, "lock@test.com", "password123", UserRole.USER);
        authenticate(user);
        Company created = companyService.create("Lock Co", null, null, null, null, UUID.randomUUID().toString());
        Long stale = created.getVersion() == null ? 0L : created.getVersion() - 1;
        if (stale < 0) {
            // force conflict by updating once then using old version
            companyService.update(created.getId(), "Lock Co 2", null, null, null, null, created.getVersion());
            AppException ex = assertThrows(AppException.class,
                    () -> companyService.update(created.getId(), "Lock Co 3", null, null, null, null, created.getVersion()));
            assertEquals("OPTIMISTIC_LOCK", ex.getCode());
        } else {
            AppException ex = assertThrows(AppException.class,
                    () -> companyService.update(created.getId(), "Lock Co X", null, null, null, null, stale));
            assertEquals("OPTIMISTIC_LOCK", ex.getCode());
        }
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UserAccount user) {
        TrackerUserDetails details = new TrackerUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
