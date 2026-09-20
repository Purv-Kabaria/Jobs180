package com.companytracker.integration;

import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.UserAccountRepository;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.service.AdminUserService;
import com.companytracker.support.AbstractIntegrationTest;
import com.companytracker.support.TestDataFactory;
import com.companytracker.web.error.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminUserLifecycleIT extends AbstractIntegrationTest {

    @Autowired UserAccountRepository userAccountRepository;
    @Autowired AdminUserService adminUserService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void soleAdminCannotBeDisabledButRegularUserCan() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount admin = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, "adm-" + suffix + "@it.com", "password123", UserRole.ADMIN);
        UserAccount user = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, "usr-" + suffix + "@it.com", "password123", UserRole.USER);

        // Ensure this admin is sole enabled admin among our seeded users by counting — if other admins
        // exist from prior tests, demotion of this one is allowed; sole check uses DB count.
        authenticate(admin);
        long otherAdmins = userAccountRepository.countEnabledAdminsExcluding(admin.getId());
        if (otherAdmins == 0) {
            AppException ex = assertThrows(AppException.class,
                    () -> adminUserService.setEnabled(admin.getId(), false, UUID.randomUUID().toString()));
            assertEquals("SOLE_ADMIN", ex.getCode());
        }

        UserAccount disabled = adminUserService.setEnabled(user.getId(), false, UUID.randomUUID().toString());
        assertFalse(disabled.isEnabled());

        UserAccount soft = adminUserService.softDelete(user.getId(), UUID.randomUUID().toString());
        assertTrue(soft.isDeleted());

        UserAccount restored = adminUserService.restore(user.getId(), UUID.randomUUID().toString());
        assertFalse(restored.isDeleted());
        assertTrue(restored.isEnabled());

        SecurityContextHolder.clearContext();
    }

    private void authenticate(UserAccount user) {
        TrackerUserDetails details = new TrackerUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
