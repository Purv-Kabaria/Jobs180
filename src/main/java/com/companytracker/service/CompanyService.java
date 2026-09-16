package com.companytracker.service;

import com.companytracker.domain.Company;
import com.companytracker.domain.UserAccount;
import com.companytracker.repo.CompanyRepository;
import com.companytracker.repo.UserAccountRepository;
import com.companytracker.security.CurrentUserService;
import com.companytracker.security.IdempotencyService;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.web.error.AppException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final UserAccountRepository userAccountRepository;
    private final OwnershipGuard ownershipGuard;
    private final CurrentUserService currentUserService;
    private final IdempotencyService idempotencyService;
    private final CascadeDeleteService cascadeDeleteService;

    public CompanyService(
            CompanyRepository companyRepository,
            UserAccountRepository userAccountRepository,
            OwnershipGuard ownershipGuard,
            CurrentUserService currentUserService,
            IdempotencyService idempotencyService,
            CascadeDeleteService cascadeDeleteService) {
        this.companyRepository = companyRepository;
        this.userAccountRepository = userAccountRepository;
        this.ownershipGuard = ownershipGuard;
        this.currentUserService = currentUserService;
        this.idempotencyService = idempotencyService;
        this.cascadeDeleteService = cascadeDeleteService;
    }

    public Page<Company> list(String q, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clamp(size), Sort.by("name"));
        String query = (q == null || q.isBlank()) ? null : q.trim();
        TrackerUserDetails user = currentUserService.requireUser();
        if (user.isAdmin()) {
            return companyRepository.searchActiveAll(query, pageable);
        }
        return companyRepository.searchActiveByOwner(user.getId(), query, pageable);
    }

    public Company getActive(Long id) {
        Company company = companyRepository.findActiveById(id)
                .orElseThrow(() -> AppException.notFound("Company not found"));
        ownershipGuard.requireActiveOwner(company.getOwner());
        return company;
    }

    @Transactional
    public Company create(String name, String website, String industry, String location, String careerPageUrl, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        Map<String, Object> payload = Map.of(
                "name", nullToEmpty(name),
                "website", nullToEmpty(website),
                "industry", nullToEmpty(industry),
                "location", nullToEmpty(location),
                "careerPageUrl", nullToEmpty(careerPageUrl)
        );
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey, payload, () -> {
            if (name == null || name.isBlank()) {
                throw AppException.badRequest("VALIDATION_ERROR", "Name is required");
            }
            UserAccount owner = userAccountRepository.findById(actor.getId())
                    .orElseThrow(() -> AppException.notFound("User not found"));
            Company company = new Company();
            company.setOwner(owner);
            company.setName(name.trim());
            company.setWebsite(blankToNull(website));
            company.setIndustry(blankToNull(industry));
            company.setLocation(blankToNull(location));
            company.setCareerPageUrl(blankToNull(careerPageUrl));
            return companyRepository.save(company);
        }, this::getActive);
    }

    @Transactional
    public Company update(Long id, String name, String website, String industry, String location, String careerPageUrl, Long version) {
        Company company = getActive(id);
        if (version != null && !version.equals(company.getVersion())) {
            throw AppException.conflict("OPTIMISTIC_LOCK", "Resource was modified elsewhere — reload and retry");
        }
        if (name == null || name.isBlank()) {
            throw AppException.badRequest("VALIDATION_ERROR", "Name is required");
        }
        company.setName(name.trim());
        company.setWebsite(blankToNull(website));
        company.setIndustry(blankToNull(industry));
        company.setLocation(blankToNull(location));
        company.setCareerPageUrl(blankToNull(careerPageUrl));
        return companyRepository.save(company);
    }

    @Transactional
    @CacheEvict(cacheNames = "dashboard", allEntries = true)
    public Company softDelete(Long id, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "company-soft-delete", "id", id),
                () -> {
                    Company company = getActive(id);
                    cascadeDeleteService.softDeleteCompanyTree(company, UUID.randomUUID());
                    return company;
                });
    }

    @Transactional
    public Company restore(Long id, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "company-restore", "id", id),
                () -> {
                    Company company = companyRepository.findById(id)
                            .orElseThrow(() -> AppException.notFound("Company not found"));
                    ownershipGuard.requireActiveOwner(company.getOwner());
                    if (!company.isDeleted()) {
                        return company;
                    }
                    cascadeDeleteService.restoreCompanyBatch(company);
                    return company;
                });
    }

    @Transactional
    public void hardDelete(Long id, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "company-hard-delete", "id", id),
                () -> {
                    Company company = companyRepository.findById(id)
                            .orElseThrow(() -> AppException.notFound("Company not found"));
                    ownershipGuard.requireActiveOwner(company.getOwner());
                    if (!company.isDeleted()) {
                        throw AppException.badRequest("NOT_ARCHIVED", "Soft-delete before hard delete");
                    }
                    cascadeDeleteService.hardDeleteCompany(company);
                    return Map.of("deleted", true);
                });
    }

    private int clamp(int size) {
        if (size < 1) return 20;
        return Math.min(size, 100);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
