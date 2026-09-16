package com.companytracker.service;

import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.security.CurrentUserService;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.web.error.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnershipGuardTest {

    @Mock CurrentUserService currentUserService;
    @InjectMocks OwnershipGuard ownershipGuard;

    @Test
    void nonOwnerGetsNotFound() {
        UserAccount owner = user(2L);
        TrackerUserDetails current = new TrackerUserDetails(user(1L));
        when(currentUserService.requireUser()).thenReturn(current);

        AppException ex = assertThrows(AppException.class, () -> ownershipGuard.requireActiveOwner(owner));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void adminCanAccessOthers() {
        UserAccount owner = user(2L);
        UserAccount adminEntity = user(1L);
        adminEntity.setRole(UserRole.ADMIN);
        when(currentUserService.requireUser()).thenReturn(new TrackerUserDetails(adminEntity));
        assertDoesNotThrow(() -> ownershipGuard.requireActiveOwner(owner));
    }

    private UserAccount user(Long id) {
        UserAccount user = new UserAccount();
        user.setEmail(id + "@example.com");
        user.setPasswordHash("x");
        user.setRole(UserRole.USER);
        user.setEnabled(true);
        user.setSecurityStamp(UUID.randomUUID());
        try {
            var field = UserAccount.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return user;
    }
}
