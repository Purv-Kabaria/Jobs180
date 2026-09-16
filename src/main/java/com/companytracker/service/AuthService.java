package com.companytracker.service;

import com.companytracker.config.AppProperties;
import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.AppLockRepository;
import com.companytracker.repo.UserAccountRepository;
import com.companytracker.security.IdempotencyService;
import com.companytracker.security.SessionRevocationService;
import com.companytracker.web.error.AppException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final AppLockRepository appLockRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final IdempotencyService idempotencyService;
    private final SessionRevocationService sessionRevocationService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            AppLockRepository appLockRepository,
            PasswordEncoder passwordEncoder,
            AppProperties appProperties,
            IdempotencyService idempotencyService,
            SessionRevocationService sessionRevocationService) {
        this.userAccountRepository = userAccountRepository;
        this.appLockRepository = appLockRepository;
        this.passwordEncoder = passwordEncoder;
        this.appProperties = appProperties;
        this.idempotencyService = idempotencyService;
        this.sessionRevocationService = sessionRevocationService;
    }

    @Transactional
    public UserAccount signup(String email, String password, String idempotencyKey, String ip) {
        if (!appProperties.signupsEnabled()) {
            throw AppException.forbidden("SIGNUPS_DISABLED", "Signups are temporarily disabled");
        }
        String normalized = normalizeEmail(email);
        String scope = "anon:" + (ip == null ? "unknown" : ip);
        Map<String, String> payload = Map.of("email", normalized, "password", password);
        return idempotencyService.execute(scope, idempotencyKey, payload, () -> doSignup(normalized, password));
    }

    private UserAccount doSignup(String normalizedEmail, String password) {
        if (password == null || password.length() < 8) {
            throw AppException.badRequest("VALIDATION_ERROR", "Password must be at least 8 characters");
        }
        appLockRepository.lockFirstAdmin();
        if (userAccountRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw AppException.conflict("EMAIL_TAKEN", "Email already registered");
        }
        long totalUsers = userAccountRepository.count();
        UserRole role = totalUsers == 0 ? UserRole.ADMIN : UserRole.USER;

        UserAccount user = new UserAccount();
        user.setEmail(normalizedEmail);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEnabled(true);
        user.setSecurityStamp(UUID.randomUUID());
        return userAccountRepository.save(user);
    }

    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw AppException.badRequest("VALIDATION_ERROR", "Email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public void rotateStampAndRevoke(UserAccount user) {
        user.rotateSecurityStamp();
        userAccountRepository.save(user);
        sessionRevocationService.revokeAllSessionsForUserId(user.getId(), user.getEmail());
    }
}
