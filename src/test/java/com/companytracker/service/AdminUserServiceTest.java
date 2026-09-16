package com.companytracker.service;

import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.UserAccountRepository;
import com.companytracker.security.CurrentUserService;
import com.companytracker.security.IdempotencyService;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.web.error.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserAccountRepository userAccountRepository;
    @Mock CurrentUserService currentUserService;
    @Mock AuthService authService;
    @Mock CascadeDeleteService cascadeDeleteService;
    @Mock IdempotencyService idempotencyService;

    AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(
                userAccountRepository, currentUserService, authService, cascadeDeleteService, idempotencyService);
        TrackerUserDetails admin = new TrackerUserDetails(adminUser(1L, "admin@example.com", UserRole.ADMIN));
        when(currentUserService.requireUser()).thenReturn(admin);
        when(idempotencyService.execute(anyString(), anyString(), any(), ArgumentMatchers.<Supplier<UserAccount>>any()))
                .thenAnswer(inv -> {
                    Supplier<?> supplier = inv.getArgument(3);
                    return supplier.get();
                });
        org.mockito.Mockito.lenient().when(idempotencyService.execute(anyString(), anyString(), any(),
                ArgumentMatchers.<Supplier<UserAccount>>any(), ArgumentMatchers.<Function<Long, UserAccount>>any()))
                .thenAnswer(inv -> {
                    Supplier<?> supplier = inv.getArgument(3);
                    return supplier.get();
                });
    }

    @Test
    void refusesDemotingSoleAdmin() {
        UserAccount sole = adminUser(1L, "admin@example.com", UserRole.ADMIN);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(sole));
        when(userAccountRepository.countEnabledAdminsExcluding(1L)).thenReturn(0L);

        AppException ex = assertThrows(AppException.class,
                () -> adminUserService.changeRole(1L, UserRole.USER, UUID.randomUUID().toString()));
        assertEquals("SOLE_ADMIN", ex.getCode());
    }

    @Test
    void allowsDemoteWhenAnotherAdminExists() {
        UserAccount admin = adminUser(1L, "admin@example.com", UserRole.ADMIN);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userAccountRepository.countEnabledAdminsExcluding(1L)).thenReturn(1L);

        UserAccount result = adminUserService.changeRole(1L, UserRole.USER, UUID.randomUUID().toString());
        assertEquals(UserRole.USER, result.getRole());
    }

    private UserAccount adminUser(Long id, String email, UserRole role) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRole(role);
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
