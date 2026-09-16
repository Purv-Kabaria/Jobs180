package com.companytracker.service;

import com.companytracker.domain.ApplicationStatus;
import com.companytracker.domain.Company;
import com.companytracker.domain.JobApplication;
import com.companytracker.domain.WorkMode;
import com.companytracker.repo.JobApplicationRepository;
import com.companytracker.security.CurrentUserService;
import com.companytracker.security.IdempotencyService;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.web.error.AppException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final CompanyService companyService;
    private final OwnershipGuard ownershipGuard;
    private final CurrentUserService currentUserService;
    private final IdempotencyService idempotencyService;
    private final CascadeDeleteService cascadeDeleteService;

    public JobApplicationService(
            JobApplicationRepository jobApplicationRepository,
            CompanyService companyService,
            OwnershipGuard ownershipGuard,
            CurrentUserService currentUserService,
            IdempotencyService idempotencyService,
            CascadeDeleteService cascadeDeleteService) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.companyService = companyService;
        this.ownershipGuard = ownershipGuard;
        this.currentUserService = currentUserService;
        this.idempotencyService = idempotencyService;
        this.cascadeDeleteService = cascadeDeleteService;
    }

    public List<JobApplication> listByCompany(Long companyId) {
        companyService.getActive(companyId);
        return jobApplicationRepository.findActiveByCompany(companyId);
    }

    public JobApplication getActive(Long id) {
        JobApplication app = jobApplicationRepository.findActiveById(id)
                .orElseThrow(() -> AppException.notFound("Application not found"));
        ownershipGuard.requireActiveOwner(app.getOwner());
        return app;
    }

    public Page<JobApplication> search(String company, ApplicationStatus status, LocalDate from, LocalDate to, int page, int size) {
        int pageSize = size < 1 ? 20 : Math.min(size, 100);
        Long ownerFilter = ownershipGuard.effectiveOwnerFilter();
        return jobApplicationRepository.search(
                ownerFilter,
                blankToNull(company),
                status,
                from,
                to,
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "updatedAt"))
        );
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public JobApplication create(Long companyId, String title, String jdText, String jdLink, WorkMode workMode,
                                 String compensation, LocalDate appliedDate, boolean referral, String source,
                                 ApplicationStatus status, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        Map<String, Object> payload = new HashMap<>();
        payload.put("companyId", companyId);
        payload.put("title", nullToEmpty(title));
        payload.put("status", status == null ? ApplicationStatus.WISHLIST.name() : status.name());
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey, payload, () -> {
            Company company = companyService.getActive(companyId);
            if (title == null || title.isBlank()) {
                throw AppException.badRequest("VALIDATION_ERROR", "Title is required");
            }
            JobApplication app = new JobApplication();
            app.setCompany(company);
            app.setOwner(company.getOwner());
            applyFields(app, title, jdText, jdLink, workMode, compensation, appliedDate, referral, source, status);
            return jobApplicationRepository.save(app);
        });
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public JobApplication update(Long id, String title, String jdText, String jdLink, WorkMode workMode,
                                 String compensation, LocalDate appliedDate, boolean referral, String source,
                                 ApplicationStatus status, Long version) {
        JobApplication app = getActive(id);
        if (version != null && !version.equals(app.getVersion())) {
            throw AppException.conflict("OPTIMISTIC_LOCK", "Resource was modified elsewhere — reload and retry");
        }
        applyFields(app, title, jdText, jdLink, workMode, compensation, appliedDate, referral, source, status);
        return jobApplicationRepository.save(app);
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public JobApplication softDelete(Long id, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "app-soft-delete", "id", id),
                () -> {
                    JobApplication app = getActive(id);
                    cascadeDeleteService.softDeleteApplicationTree(app, UUID.randomUUID());
                    return app;
                });
    }

    @Transactional
    public void hardDelete(Long id, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "app-hard-delete", "id", id),
                () -> {
                    JobApplication app = jobApplicationRepository.findById(id)
                            .orElseThrow(() -> AppException.notFound("Application not found"));
                    ownershipGuard.requireActiveOwner(app.getOwner());
                    if (!app.isDeleted()) {
                        throw AppException.badRequest("NOT_ARCHIVED", "Soft-delete before hard delete");
                    }
                    cascadeDeleteService.hardDeleteApplication(app);
                    return Map.of("deleted", true);
                });
    }

    private void applyFields(JobApplication app, String title, String jdText, String jdLink, WorkMode workMode,
                             String compensation, LocalDate appliedDate, boolean referral, String source,
                             ApplicationStatus status) {
        if (title == null || title.isBlank()) {
            throw AppException.badRequest("VALIDATION_ERROR", "Title is required");
        }
        if (jdText != null && jdText.length() > 50000) {
            throw AppException.badRequest("VALIDATION_ERROR", "JD text too long");
        }
        app.setTitle(title.trim());
        app.setJdText(blankToNull(jdText));
        app.setJdLink(blankToNull(jdLink));
        app.setWorkMode(workMode == null ? WorkMode.UNKNOWN : workMode);
        app.setCompensation(blankToNull(compensation));
        app.setAppliedDate(appliedDate);
        app.setReferral(referral);
        app.setSource(blankToNull(source));
        app.setStatus(status == null ? ApplicationStatus.WISHLIST : status);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
