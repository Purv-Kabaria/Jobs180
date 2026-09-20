package com.companytracker.service;

import com.companytracker.config.CacheConfig;
import com.companytracker.domain.ApplicationStatus;
import com.companytracker.domain.Round;
import com.companytracker.repo.JobApplicationRepository;
import com.companytracker.security.CurrentUserService;
import com.companytracker.security.TrackerUserDetails;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final JobApplicationRepository jobApplicationRepository;
    private final RoundService roundService;
    private final CurrentUserService currentUserService;

    public DashboardService(
            JobApplicationRepository jobApplicationRepository,
            RoundService roundService,
            CurrentUserService currentUserService) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.roundService = roundService;
        this.currentUserService = currentUserService;
    }

    public Map<String, Long> statusCounts() {
        TrackerUserDetails user = currentUserService.requireUser();
        return statusCountsFor(user.isAdmin() ? "admin:global" : ("user:" + user.getId()), user.isAdmin(), user.getId());
    }

    @Cacheable(cacheNames = CacheConfig.DASHBOARD_CACHE, key = "#cacheKey")
    public Map<String, Long> statusCountsFor(String cacheKey, boolean admin, Long userId) {
        List<JobApplicationRepository.StatusCount> rows = admin
                ? jobApplicationRepository.countByStatusAll()
                : jobApplicationRepository.countByStatusForOwner(userId);
        Map<String, Long> map = new LinkedHashMap<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            map.put(status.name(), 0L);
        }
        for (JobApplicationRepository.StatusCount row : rows) {
            map.put(row.getStatus().name(), row.getCnt());
        }
        return map;
    }

    public List<Round> upcoming(int withinDays) {
        return roundService.upcoming(withinDays);
    }
}
