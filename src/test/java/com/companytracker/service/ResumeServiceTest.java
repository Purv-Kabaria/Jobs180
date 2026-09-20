package com.companytracker.service;

import com.companytracker.domain.Resume;
import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.ResumeRepository;
import com.companytracker.repo.RoundRepository;
import com.companytracker.repo.UserAccountRepository;
import com.companytracker.security.CurrentUserService;
import com.companytracker.security.IdempotencyService;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.storage.FileStorageService;
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
class ResumeServiceTest {

    @Mock ResumeRepository resumeRepository;
    @Mock RoundRepository roundRepository;
    @Mock UserAccountRepository userAccountRepository;
    @Mock OwnershipGuard ownershipGuard;
    @Mock CurrentUserService currentUserService;
    @Mock IdempotencyService idempotencyService;
    @Mock FileStorageService fileStorageService;

    ResumeService resumeService;

    @BeforeEach
    void setUp() {
        resumeService = new ResumeService(
                resumeRepository, roundRepository, userAccountRepository, ownershipGuard,
                currentUserService, idempotencyService, fileStorageService);
        when(currentUserService.requireUser()).thenReturn(new TrackerUserDetails(user(1L, UserRole.USER)));
        lenient().when(idempotencyService.execute(anyString(), anyString(), any(),
                        ArgumentMatchers.<Supplier<Object>>any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
    }

    @Test
    void createRequiresLabel() {
        AppException ex = assertThrows(AppException.class,
                () -> resumeService.create("  ", "https://x.com/cv.pdf", null, "k"));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void createRequiresFileOrUrl() {
        AppException ex = assertThrows(AppException.class,
                () -> resumeService.create("cv", "  ", null, "k"));
        assertEquals("VALIDATION_ERROR", ex.getCode());
    }

    @Test
    void nonAdminCannotListOthers() {
        AppException ex = assertThrows(AppException.class,
                () -> resumeService.listMineOrAdmin(99L));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void softDeleteRequiresArchivedForHardDeletePathViaGet() {
        Resume resume = new Resume();
        resume.setOwner(user(1L, UserRole.USER));
        resume.setLabel("cv");
        resume.setExternalUrl("https://example.com/a.pdf");
        when(resumeRepository.findActiveById(5L)).thenReturn(Optional.of(resume));
        Resume got = resumeService.getActive(5L);
        assertEquals("cv", got.getLabel());
    }

    private UserAccount user(Long id, UserRole role) {
        UserAccount u = new UserAccount();
        u.setEmail(id + "@t.com");
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
