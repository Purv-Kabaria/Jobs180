package com.companytracker.integration;

import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.UserAccountRepository;
import com.companytracker.service.AuthService;
import com.companytracker.support.AbstractIntegrationTest;
import com.companytracker.web.error.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthSignupIT extends AbstractIntegrationTest {

    @Autowired AuthService authService;
    @Autowired UserAccountRepository userAccountRepository;

    @Test
    void firstSignupIsAdminWhenDbEmptyOtherwiseUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long before = userAccountRepository.count();
        UserAccount first = authService.signup(
                "first-" + suffix + "@auth.it", "password123", "k-first-" + suffix, "127.0.0.1");
        UserAccount second = authService.signup(
                "second-" + suffix + "@auth.it", "password123", "k-second-" + suffix, "127.0.0.1");

        if (before == 0) {
            assertEquals(UserRole.ADMIN, first.getRole());
        } else {
            assertEquals(UserRole.USER, first.getRole());
        }
        assertEquals(UserRole.USER, second.getRole());
        assertTrue(userAccountRepository.findByEmailIgnoreCase(first.getEmail()).isPresent());
    }

    @Test
    void duplicateEmailRejected() {
        String email = "dup-" + UUID.randomUUID() + "@auth.it";
        authService.signup(email, "password123", UUID.randomUUID().toString(), "127.0.0.1");
        AppException ex = assertThrows(AppException.class,
                () -> authService.signup(email.toUpperCase(), "password123",
                        UUID.randomUUID().toString(), "127.0.0.1"));
        assertEquals("EMAIL_TAKEN", ex.getCode());
    }
}
