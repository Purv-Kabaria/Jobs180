package com.companytracker.service;

import com.companytracker.domain.Company;
import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.CompanyRepository;
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

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompanyServiceTest {

    @Mock CompanyRepository companyRepository;
    @Mock UserAccountRepository userAccountRepository;
    @Mock OwnershipGuard ownershipGuard;
    @Mock CurrentUserService currentUserService;
    @Mock IdempotencyService idempotencyService;
    @Mock CascadeDeleteService cascadeDeleteService;

    CompanyService companyService;

    @BeforeEach
    void setUp() {
        companyService = new CompanyService(
                companyRepository, userAccountRepository, ownershipGuard,
                currentUserService, idempotencyService, cascadeDeleteService);
        UserAccount owner = user(1L);
        when(currentUserService.requireUser()).thenReturn(new TrackerUserDetails(owner));
        lenient().when(idempotencyService.execute(anyString(), anyString(), any(),
                        ArgumentMatchers.<Supplier<Company>>any(),
                        ArgumentMatchers.<Function<Long, Company>>any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
        lenient().when(idempotencyService.execute(anyString(), anyString(), any(),
                        ArgumentMatchers.<Supplier<Object>>any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
    }

    @Test
    void createRequiresName() {
        AppException ex = assertThrows(AppException.class,
                () -> companyService.create("  ", null, null, null, null, "k"));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void updateOptimisticLockConflict() {
        Company company = company(5L, user(1L));
        company.setVersion(2L);
        when(companyRepository.findActiveById(5L)).thenReturn(Optional.of(company));

        AppException ex = assertThrows(AppException.class,
                () -> companyService.update(5L, "Acme", null, null, null, null, 1L));
        assertEquals("OPTIMISTIC_LOCK", ex.getCode());
    }

    @Test
    void hardDeleteRequiresArchived() {
        Company company = company(5L, user(1L));
        when(companyRepository.findById(5L)).thenReturn(Optional.of(company));

        AppException ex = assertThrows(AppException.class,
                () -> companyService.hardDelete(5L, "k"));
        assertEquals("NOT_ARCHIVED", ex.getCode());
    }

    private UserAccount user(Long id) {
        UserAccount u = new UserAccount();
        u.setEmail(id + "@t.com");
        u.setPasswordHash("h");
        u.setRole(UserRole.USER);
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

    private Company company(Long id, UserAccount owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("Co");
        try {
            var f = Company.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(c, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return c;
    }
}
