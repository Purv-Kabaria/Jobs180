package com.companytracker.service;

import com.companytracker.domain.ApplicationStatus;
import com.companytracker.domain.Company;
import com.companytracker.domain.JobApplication;
import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.domain.WorkMode;
import com.companytracker.repo.JobApplicationRepository;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobApplicationServiceTest {

    @Mock JobApplicationRepository jobApplicationRepository;
    @Mock CompanyService companyService;
    @Mock OwnershipGuard ownershipGuard;
    @Mock CurrentUserService currentUserService;
    @Mock IdempotencyService idempotencyService;
    @Mock CascadeDeleteService cascadeDeleteService;

    JobApplicationService service;

    @BeforeEach
    void setUp() {
        service = new JobApplicationService(
                jobApplicationRepository, companyService, ownershipGuard,
                currentUserService, idempotencyService, cascadeDeleteService);
        when(currentUserService.requireUser()).thenReturn(new TrackerUserDetails(user(1L)));
        lenient().when(idempotencyService.execute(anyString(), anyString(), any(),
                        ArgumentMatchers.<Supplier<Object>>any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
    }

    @Test
    void createRequiresTitle() {
        Company company = new Company();
        company.setOwner(user(1L));
        company.setName("Acme");
        when(companyService.getActive(3L)).thenReturn(company);

        AppException ex = assertThrows(AppException.class,
                () -> service.create(3L, "  ", null, null, WorkMode.REMOTE, null, null, false, null,
                        ApplicationStatus.WISHLIST, "k"));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void updateOptimisticLockConflict() {
        JobApplication app = new JobApplication();
        app.setOwner(user(1L));
        app.setTitle("Eng");
        app.setVersion(5L);
        when(jobApplicationRepository.findActiveById(9L)).thenReturn(Optional.of(app));

        AppException ex = assertThrows(AppException.class,
                () -> service.update(9L, "Eng2", null, null, WorkMode.ONSITE, null, null, false, null,
                        ApplicationStatus.APPLIED, 4L));
        assertEquals("OPTIMISTIC_LOCK", ex.getCode());
    }

    @Test
    void hardDeleteRequiresArchive() {
        JobApplication app = new JobApplication();
        app.setOwner(user(1L));
        app.setTitle("Eng");
        when(jobApplicationRepository.findById(9L)).thenReturn(Optional.of(app));

        AppException ex = assertThrows(AppException.class, () -> service.hardDelete(9L, "k"));
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
}
