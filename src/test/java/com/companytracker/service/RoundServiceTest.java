package com.companytracker.service;

import com.companytracker.domain.JobApplication;
import com.companytracker.domain.Resume;
import com.companytracker.domain.Round;
import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.ResumeRepository;
import com.companytracker.repo.RoundRepository;
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
class RoundServiceTest {

    @Mock RoundRepository roundRepository;
    @Mock ResumeRepository resumeRepository;
    @Mock JobApplicationService jobApplicationService;
    @Mock OwnershipGuard ownershipGuard;
    @Mock CurrentUserService currentUserService;
    @Mock IdempotencyService idempotencyService;
    @Mock CascadeDeleteService cascadeDeleteService;

    RoundService roundService;

    @BeforeEach
    void setUp() {
        roundService = new RoundService(
                roundRepository, resumeRepository, jobApplicationService, ownershipGuard,
                currentUserService, idempotencyService, cascadeDeleteService);
        when(currentUserService.requireUser()).thenReturn(new TrackerUserDetails(user(1L)));
        lenient().when(idempotencyService.execute(anyString(), anyString(), any(),
                        ArgumentMatchers.<Supplier<Object>>any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());
    }

    @Test
    void attachResumeRejectsCrossOwner() {
        Round round = round(10L, user(1L));
        when(roundRepository.findActiveById(10L)).thenReturn(Optional.of(round));
        Resume foreign = resume(20L, user(2L));
        when(resumeRepository.findActiveById(20L)).thenReturn(Optional.of(foreign));

        AppException ex = assertThrows(AppException.class,
                () -> roundService.attachResume(10L, 20L, "k"));
        assertEquals("NOT_FOUND", ex.getCode());
    }

    @Test
    void upcomingClampsDays() {
        when(ownershipGuard.effectiveOwnerFilter()).thenReturn(1L);
        when(roundRepository.findUpcoming(any(), any(), any())).thenReturn(java.util.List.of());
        roundService.upcoming(999);
        // withinDays capped at 90 → to = from + 90 days; verify interaction happened
        org.mockito.Mockito.verify(roundRepository).findUpcoming(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
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

    private Round round(Long id, UserAccount owner) {
        Round r = new Round();
        r.setOwner(owner);
        r.setName("R1");
        JobApplication app = new JobApplication();
        app.setOwner(owner);
        r.setApplication(app);
        try {
            var f = Round.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(r, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return r;
    }

    private Resume resume(Long id, UserAccount owner) {
        Resume r = new Resume();
        r.setOwner(owner);
        r.setLabel("cv");
        r.setExternalUrl("https://example.com/cv.pdf");
        try {
            var f = Resume.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(r, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return r;
    }
}
