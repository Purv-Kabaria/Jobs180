package com.companytracker.service;

import com.companytracker.config.AppProperties;
import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.AppLockRepository;
import com.companytracker.repo.UserAccountRepository;
import com.companytracker.security.IdempotencyService;
import com.companytracker.security.SessionRevocationService;
import com.companytracker.web.error.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

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
class AuthServiceTest {

    @Mock UserAccountRepository userAccountRepository;
    @Mock AppLockRepository appLockRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock IdempotencyService idempotencyService;
    @Mock SessionRevocationService sessionRevocationService;

    AuthService authService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                "local", 1024, 3600, true, "", "UTC",
                new AppProperties.S3("http://x", "b", "k", "s", "us-east-1", true),
                new AppProperties.RateLimit(5, 3, 60, 10, 120)
        );
        authService = new AuthService(
                userAccountRepository, appLockRepository, passwordEncoder, props,
                idempotencyService, sessionRevocationService);
        lenient().when(idempotencyService.execute(anyString(), anyString(), any(),
                        ArgumentMatchers.<Supplier<UserAccount>>any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
        lenient().when(idempotencyService.execute(anyString(), anyString(), any(),
                        ArgumentMatchers.<Supplier<UserAccount>>any(),
                        ArgumentMatchers.<Function<Long, UserAccount>>any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
    }

    @Test
    void firstUserBecomesAdmin() {
        when(userAccountRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.empty());
        when(userAccountRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userAccountRepository.save(any())).thenAnswer(inv -> {
            UserAccount u = inv.getArgument(0);
            setId(u, 1L);
            return u;
        });

        UserAccount user = authService.signup("A@B.com", "password123", "k1", "127.0.0.1");
        assertEquals(UserRole.ADMIN, user.getRole());
        assertEquals("a@b.com", user.getEmail());
        verify(appLockRepository).lockFirstAdmin();
    }

    @Test
    void secondUserIsRegularUser() {
        when(userAccountRepository.findByEmailIgnoreCase("u@b.com")).thenReturn(Optional.empty());
        when(userAccountRepository.count()).thenReturn(1L);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userAccountRepository.save(any())).thenAnswer(inv -> {
            UserAccount u = inv.getArgument(0);
            setId(u, 2L);
            return u;
        });

        UserAccount user = authService.signup("u@b.com", "password123", "k2", "127.0.0.1");
        assertEquals(UserRole.USER, user.getRole());
    }

    @Test
    void rejectsDuplicateEmail() {
        when(userAccountRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.of(new UserAccount()));
        AppException ex = assertThrows(AppException.class,
                () -> authService.signup("a@b.com", "password123", "k", "1.1.1.1"));
        assertEquals("EMAIL_TAKEN", ex.getCode());
    }

    @Test
    void rejectsShortPassword() {
        when(userAccountRepository.findByEmailIgnoreCase("a@b.com")).thenReturn(Optional.empty());
        AppException ex = assertThrows(AppException.class,
                () -> authService.signup("a@b.com", "short", "k", "1.1.1.1"));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void rejectsWhenSignupsDisabled() {
        AppProperties props = new AppProperties(
                "local", 1024, 3600, false, "", "UTC",
                new AppProperties.S3("http://x", "b", "k", "s", "us-east-1", true),
                new AppProperties.RateLimit(5, 3, 60, 10, 120)
        );
        authService = new AuthService(
                userAccountRepository, appLockRepository, passwordEncoder, props,
                idempotencyService, sessionRevocationService);
        AppException ex = assertThrows(AppException.class,
                () -> authService.signup("a@b.com", "password123", "k", "1.1.1.1"));
        assertEquals("SIGNUPS_DISABLED", ex.getCode());
    }

    @Test
    void rotateStampRevokesSessions() {
        UserAccount user = new UserAccount();
        setId(user, 9L);
        user.setEmail("x@y.com");
        user.setSecurityStamp(UUID.randomUUID());
        when(userAccountRepository.save(user)).thenReturn(user);

        UUID before = user.getSecurityStamp();
        authService.rotateStampAndRevoke(user);
        assertEquals(false, before.equals(user.getSecurityStamp()));
        verify(sessionRevocationService).revokeAllSessionsForUserId(9L, "x@y.com");
    }

    private static void setId(UserAccount user, Long id) {
        try {
            var f = UserAccount.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
