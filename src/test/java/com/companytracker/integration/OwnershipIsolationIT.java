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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OwnershipIsolationIT extends AbstractIntegrationTest {

    @Autowired UserAccountRepository userAccountRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyService companyService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void userCannotReadAnotherUsersCompany() {
        UserAccount alice = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, "alice@iso.com", "password123", UserRole.USER);
        UserAccount bob = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, "bob@iso.com", "password123", UserRole.USER);
        Company alices = TestDataFactory.persistCompany(companyRepository, alice, "Alice Corp");

        authenticate(bob);
        AppException ex = assertThrows(AppException.class, () -> companyService.getActive(alices.getId()));
        assertEquals("NOT_FOUND", ex.getCode());
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCanReadAnyCompany() {
        UserAccount admin = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, "admin@iso.com", "password123", UserRole.ADMIN);
        UserAccount user = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, "user@iso.com", "password123", UserRole.USER);
        Company owned = TestDataFactory.persistCompany(companyRepository, user, "User Corp");

        authenticate(admin);
        Company loaded = companyService.getActive(owned.getId());
        assertEquals("User Corp", loaded.getName());
        SecurityContextHolder.clearContext();
    }

    private void authenticate(UserAccount user) {
        TrackerUserDetails details = new TrackerUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
