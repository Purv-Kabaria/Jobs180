package com.companytracker.service;

import com.companytracker.domain.ApplicationStatus;
import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.JobApplicationRepository;
import com.companytracker.security.CurrentUserService;
import com.companytracker.security.TrackerUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock JobApplicationRepository jobApplicationRepository;
    @Mock RoundService roundService;
    @Mock CurrentUserService currentUserService;
    @Mock DashboardService self;
    DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(jobApplicationRepository, roundService, currentUserService, self);
    }

    @Test
    void statusCountsDelegatesThroughProxyForCache() {
        UserAccount u = new UserAccount();
        u.setEmail("a@b.com");
        u.setPasswordHash("h");
        u.setRole(UserRole.USER);
        u.setEnabled(true);
        u.setSecurityStamp(UUID.randomUUID());
        try {
            var f = UserAccount.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, 7L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(currentUserService.requireUser()).thenReturn(new TrackerUserDetails(u));
        when(self.statusCountsFor(anyString(), anyBoolean(), anyLong())).thenReturn(Map.of("APPLIED", 3L));

        Map<String, Long> counts = dashboardService.statusCounts();
        assertEquals(3L, counts.get("APPLIED"));
    }

    @Test
    void statusCountsForAggregatesRows() {
        when(jobApplicationRepository.countByStatusForOwner(7L)).thenReturn(List.of(
                status(ApplicationStatus.APPLIED, 3),
                status(ApplicationStatus.WISHLIST, 1)
        ));

        Map<String, Long> counts = dashboardService.statusCountsFor("user:7", false, 7L);
        assertEquals(3L, counts.get("APPLIED"));
        assertEquals(1L, counts.get("WISHLIST"));
    }

    private JobApplicationRepository.StatusCount status(ApplicationStatus s, long c) {
        return new JobApplicationRepository.StatusCount() {
            @Override public ApplicationStatus getStatus() { return s; }
            @Override public long getCnt() { return c; }
        };
    }
}
