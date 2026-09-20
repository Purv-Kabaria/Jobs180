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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceEdgeTest {

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
        when(currentUserService.requireUser()).thenReturn(new TrackerUserDetails(user(99L, "admin@x.com", UserRole.ADMIN)));
        lenient().when(idempotencyService.execute(anyString(), anyString(), any(),
                        ArgumentMatchers.<Supplier<Object>>any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
        lenient().when(idempotencyService.execute(anyString(), anyString(), any(),
                        ArgumentMatchers.<Supplier<Object>>any(),
                        ArgumentMatchers.<Function<Long, Object>>any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
    }

    @Test
    void refusesDisablingSoleAdmin() {
        UserAccount sole = user(1L, "a@x.com", UserRole.ADMIN);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(sole));
        when(userAccountRepository.countEnabledAdminsExcluding(1L)).thenReturn(0L);
        AppException ex = assertThrows(AppException.class,
                () -> adminUserService.setEnabled(1L, false, UUID.randomUUID().toString()));
        assertEquals("SOLE_ADMIN", ex.getCode());
    }

    @Test
    void refusesSoftDeletingSoleAdmin() {
        UserAccount sole = user(1L, "a@x.com", UserRole.ADMIN);
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(sole));
        when(userAccountRepository.countEnabledAdminsExcluding(1L)).thenReturn(0L);
        AppException ex = assertThrows(AppException.class,
                () -> adminUserService.softDelete(1L, UUID.randomUUID().toString()));
        assertEquals("SOLE_ADMIN", ex.getCode());
    }

    @Test
    void softDeleteCascadesAndRevokes() {
        UserAccount target = user(2L, "u@x.com", UserRole.USER);
        when(userAccountRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userAccountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserAccount result = adminUserService.softDelete(2L, UUID.randomUUID().toString());
        assertEquals(true, result.isDeleted());
        assertEquals(false, result.isEnabled());
        verify(cascadeDeleteService).softDeleteAllForUser(any(), any());
        verify(authService).rotateStampAndRevoke(any());
    }

    @Test
    void hardDeletePurges() {
        UserAccount target = user(2L, "u@x.com", UserRole.USER);
        target.softDelete(UUID.randomUUID());
        when(userAccountRepository.findById(2L)).thenReturn(Optional.of(target));

        adminUserService.hardDelete(2L, UUID.randomUUID().toString());
        verify(cascadeDeleteService).hardDeleteAllForUser(target);
        verify(userAccountRepository).delete(target);
    }

    private UserAccount user(Long id, String email, UserRole role) {
        UserAccount u = new UserAccount();
        u.setEmail(email);
        u.setPasswordHash("h");
        u.setRole(role);
        u.setEnabled(true);
        u.setSecurityStamp(UUID.randomUUID());
        try {
            var f = UserAccount.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return u;
    }
}
