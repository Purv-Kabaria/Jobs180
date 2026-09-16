package com.companytracker.service;

import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.UserAccountRepository;
import com.companytracker.security.CurrentUserService;
import com.companytracker.security.IdempotencyService;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.web.error.AppException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminUserService {

    private final UserAccountRepository userAccountRepository;
    private final CurrentUserService currentUserService;
    private final AuthService authService;
    private final CascadeDeleteService cascadeDeleteService;
    private final IdempotencyService idempotencyService;

    public AdminUserService(
            UserAccountRepository userAccountRepository,
            CurrentUserService currentUserService,
            AuthService authService,
            CascadeDeleteService cascadeDeleteService,
            IdempotencyService idempotencyService) {
        this.userAccountRepository = userAccountRepository;
        this.currentUserService = currentUserService;
        this.authService = authService;
        this.cascadeDeleteService = cascadeDeleteService;
        this.idempotencyService = idempotencyService;
    }

    public List<UserAccount> listActive() {
        return userAccountRepository.findAllActive();
    }

    public List<UserAccount> listDeleted() {
        return userAccountRepository.findAllDeleted();
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public UserAccount changeRole(Long userId, UserRole newRole, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "role", "userId", userId, "role", newRole.name()),
                () -> {
                    UserAccount user = userAccountRepository.findById(userId)
                            .orElseThrow(() -> AppException.notFound("User not found"));
                    if (user.isDeleted()) {
                        throw AppException.notFound("User not found");
                    }
                    if (user.getRole() == UserRole.ADMIN && newRole == UserRole.USER) {
                        ensureNotSoleAdmin(user.getId());
                    }
                    user.setRole(newRole);
                    authService.rotateStampAndRevoke(user);
                    return user;
                });
    }

    @Transactional
    public UserAccount setEnabled(Long userId, boolean enabled, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", enabled ? "enable" : "disable", "userId", userId),
                () -> {
                    UserAccount user = userAccountRepository.findById(userId)
                            .orElseThrow(() -> AppException.notFound("User not found"));
                    if (user.isDeleted()) {
                        throw AppException.notFound("User not found");
                    }
                    if (!enabled && user.getRole() == UserRole.ADMIN) {
                        ensureNotSoleAdmin(user.getId());
                    }
                    user.setEnabled(enabled);
                    authService.rotateStampAndRevoke(user);
                    return userAccountRepository.save(user);
                });
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public UserAccount softDelete(Long userId, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "soft-delete", "userId", userId),
                () -> {
                    UserAccount user = userAccountRepository.findById(userId)
                            .orElseThrow(() -> AppException.notFound("User not found"));
                    if (user.isDeleted()) {
                        return user;
                    }
                    if (user.getRole() == UserRole.ADMIN && user.isEnabled()) {
                        ensureNotSoleAdmin(user.getId());
                    }
                    UUID batch = UUID.randomUUID();
                    user.setEnabled(false);
                    user.softDelete(batch);
                    cascadeDeleteService.softDeleteAllForUser(user, batch);
                    authService.rotateStampAndRevoke(user);
                    return userAccountRepository.save(user);
                });
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public UserAccount restore(Long userId, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "restore", "userId", userId),
                () -> {
                    UserAccount user = userAccountRepository.findById(userId)
                            .orElseThrow(() -> AppException.notFound("User not found"));
                    if (!user.isDeleted()) {
                        return user;
                    }
                    UUID batch = user.getDeletionBatchId();
                    user.restore();
                    user.setEnabled(true);
                    if (batch != null) {
                        cascadeDeleteService.restoreBatchForUser(user.getId(), batch);
                    }
                    return userAccountRepository.save(user);
                });
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public void hardDelete(Long userId, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "hard-delete", "userId", userId),
                () -> {
                    UserAccount user = userAccountRepository.findById(userId)
                            .orElseThrow(() -> AppException.notFound("User not found"));
                    if (user.getRole() == UserRole.ADMIN && !user.isDeleted() && user.isEnabled()) {
                        ensureNotSoleAdmin(user.getId());
                    }
                    authService.rotateStampAndRevoke(user);
                    cascadeDeleteService.hardDeleteAllForUser(user);
                    userAccountRepository.delete(user);
                    return Map.of("deleted", true);
                });
    }

    private void ensureNotSoleAdmin(Long userId) {
        if (userAccountRepository.countEnabledAdminsExcluding(userId) == 0) {
            throw AppException.conflict("SOLE_ADMIN", "Cannot modify the only enabled admin");
        }
    }
}
