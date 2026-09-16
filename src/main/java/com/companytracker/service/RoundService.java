package com.companytracker.service;

import com.companytracker.domain.JobApplication;
import com.companytracker.domain.Resume;
import com.companytracker.domain.Round;
import com.companytracker.domain.RoundOutcome;
import com.companytracker.repo.ResumeRepository;
import com.companytracker.repo.RoundRepository;
import com.companytracker.security.CurrentUserService;
import com.companytracker.security.IdempotencyService;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.web.error.AppException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RoundService {

    private final RoundRepository roundRepository;
    private final ResumeRepository resumeRepository;
    private final JobApplicationService jobApplicationService;
    private final OwnershipGuard ownershipGuard;
    private final CurrentUserService currentUserService;
    private final IdempotencyService idempotencyService;
    private final CascadeDeleteService cascadeDeleteService;

    public RoundService(
            RoundRepository roundRepository,
            ResumeRepository resumeRepository,
            JobApplicationService jobApplicationService,
            OwnershipGuard ownershipGuard,
            CurrentUserService currentUserService,
            IdempotencyService idempotencyService,
            CascadeDeleteService cascadeDeleteService) {
        this.roundRepository = roundRepository;
        this.resumeRepository = resumeRepository;
        this.jobApplicationService = jobApplicationService;
        this.ownershipGuard = ownershipGuard;
        this.currentUserService = currentUserService;
        this.idempotencyService = idempotencyService;
        this.cascadeDeleteService = cascadeDeleteService;
    }

    public List<Round> listByApplication(Long applicationId) {
        jobApplicationService.getActive(applicationId);
        return roundRepository.findActiveByApplication(applicationId);
    }

    public Round getActive(Long id) {
        Round round = roundRepository.findActiveById(id)
                .orElseThrow(() -> AppException.notFound("Round not found"));
        ownershipGuard.requireActiveOwner(round.getOwner());
        return round;
    }

    public List<Round> upcoming(int withinDays) {
        int days = withinDays < 1 ? 14 : Math.min(withinDays, 90);
        Instant from = Instant.now();
        Instant to = from.plusSeconds(days * 86400L);
        return roundRepository.findUpcoming(ownershipGuard.effectiveOwnerFilter(), from, to);
    }

    @Transactional
    @CacheEvict(cacheNames = {"dashboard", "upcoming"}, allEntries = true)
    public Round create(Long applicationId, String name, Instant scheduledAt, RoundOutcome outcome,
                        String interviewers, Long resumeId, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        Map<String, Object> payload = new HashMap<>();
        payload.put("applicationId", applicationId);
        payload.put("name", nullToEmpty(name));
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey, payload, () -> {
            JobApplication app = jobApplicationService.getActive(applicationId);
            Round round = new Round();
            round.setApplication(app);
            round.setOwner(app.getOwner());
            apply(round, name, scheduledAt, outcome, interviewers, resumeId);
            return roundRepository.save(round);
        });
    }

    @Transactional
    @CacheEvict(cacheNames = {"dashboard", "upcoming"}, allEntries = true)
    public Round update(Long id, String name, Instant scheduledAt, RoundOutcome outcome,
                        String interviewers, Long resumeId, Long version) {
        Round round = getActive(id);
        if (version != null && !version.equals(round.getVersion())) {
            throw AppException.conflict("OPTIMISTIC_LOCK", "Resource was modified elsewhere — reload and retry");
        }
        apply(round, name, scheduledAt, outcome, interviewers, resumeId);
        return roundRepository.save(round);
    }

    @Transactional
    @CacheEvict(cacheNames = {"upcoming"}, allEntries = true)
    public Round attachResume(Long roundId, Long resumeId, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "attach-resume", "roundId", roundId, "resumeId", resumeId),
                () -> {
                    Round round = getActive(roundId);
                    Resume resume = resumeRepository.findActiveById(resumeId)
                            .orElseThrow(() -> AppException.notFound("Resume not found"));
                    if (!resume.getOwnerId().equals(round.getOwnerId())) {
                        throw AppException.notFound("Resume not found");
                    }
                    round.setResume(resume);
                    return roundRepository.save(round);
                });
    }

    @Transactional
    public Round detachResume(Long roundId) {
        Round round = getActive(roundId);
        round.setResume(null);
        return roundRepository.save(round);
    }

    @Transactional
    @CacheEvict(cacheNames = {"upcoming"}, allEntries = true)
    public Round softDelete(Long id, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "round-soft-delete", "id", id),
                () -> {
                    Round round = getActive(id);
                    cascadeDeleteService.softDeleteRoundTree(round, UUID.randomUUID());
                    return round;
                });
    }

    @Transactional
    public void hardDelete(Long id, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "round-hard-delete", "id", id),
                () -> {
                    Round round = roundRepository.findById(id)
                            .orElseThrow(() -> AppException.notFound("Round not found"));
                    ownershipGuard.requireActiveOwner(round.getOwner());
                    if (!round.isDeleted()) {
                        throw AppException.badRequest("NOT_ARCHIVED", "Soft-delete before hard delete");
                    }
                    cascadeDeleteService.hardDeleteRound(round);
                    return Map.of("deleted", true);
                });
    }

    private void apply(Round round, String name, Instant scheduledAt, RoundOutcome outcome,
                       String interviewers, Long resumeId) {
        if (name == null || name.isBlank()) {
            throw AppException.badRequest("VALIDATION_ERROR", "Round name is required");
        }
        round.setName(name.trim());
        round.setScheduledAt(scheduledAt);
        round.setOutcome(outcome == null ? RoundOutcome.PENDING : outcome);
        round.setInterviewers(interviewers == null || interviewers.isBlank() ? null : interviewers.trim());
        if (resumeId == null) {
            round.setResume(null);
        } else {
            Resume resume = resumeRepository.findActiveById(resumeId)
                    .orElseThrow(() -> AppException.notFound("Resume not found"));
            if (!resume.getOwnerId().equals(round.getOwnerId())) {
                throw AppException.notFound("Resume not found");
            }
            round.setResume(resume);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
