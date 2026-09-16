package com.companytracker.service;

import com.companytracker.domain.Resume;
import com.companytracker.domain.UserAccount;
import com.companytracker.repo.ResumeRepository;
import com.companytracker.repo.RoundRepository;
import com.companytracker.repo.UserAccountRepository;
import com.companytracker.security.CurrentUserService;
import com.companytracker.security.IdempotencyService;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.storage.FileStorageService;
import com.companytracker.storage.StoragePort;
import com.companytracker.web.error.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final RoundRepository roundRepository;
    private final UserAccountRepository userAccountRepository;
    private final OwnershipGuard ownershipGuard;
    private final CurrentUserService currentUserService;
    private final IdempotencyService idempotencyService;
    private final FileStorageService fileStorageService;

    public ResumeService(
            ResumeRepository resumeRepository,
            RoundRepository roundRepository,
            UserAccountRepository userAccountRepository,
            OwnershipGuard ownershipGuard,
            CurrentUserService currentUserService,
            IdempotencyService idempotencyService,
            FileStorageService fileStorageService) {
        this.resumeRepository = resumeRepository;
        this.roundRepository = roundRepository;
        this.userAccountRepository = userAccountRepository;
        this.ownershipGuard = ownershipGuard;
        this.currentUserService = currentUserService;
        this.idempotencyService = idempotencyService;
        this.fileStorageService = fileStorageService;
    }

    public List<Resume> listMineOrAdmin(Long ownerId) {
        TrackerUserDetails user = currentUserService.requireUser();
        Long effective = user.isAdmin() && ownerId != null ? ownerId : user.getId();
        if (!user.isAdmin() && ownerId != null && !ownerId.equals(user.getId())) {
            throw AppException.notFound("Resume not found");
        }
        return resumeRepository.findActiveByOwner(effective);
    }

    public Resume getActive(Long id) {
        Resume resume = resumeRepository.findActiveById(id)
                .orElseThrow(() -> AppException.notFound("Resume not found"));
        ownershipGuard.requireActiveOwner(resume.getOwner());
        return resume;
    }

    @Transactional
    public Resume create(String label, String externalUrl, MultipartFile file, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        Map<String, Object> payload = new HashMap<>();
        payload.put("label", nullToEmpty(label));
        payload.put("externalUrl", nullToEmpty(externalUrl));
        payload.put("hasFile", file != null && !file.isEmpty());
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey, payload, () -> {
            if (label == null || label.isBlank()) {
                throw AppException.badRequest("VALIDATION_ERROR", "Label is required");
            }
            boolean hasUrl = externalUrl != null && !externalUrl.isBlank();
            boolean hasFile = file != null && !file.isEmpty();
            if (!hasUrl && !hasFile) {
                throw AppException.badRequest("VALIDATION_ERROR", "Provide a file and/or external URL");
            }
            UserAccount owner = userAccountRepository.findById(actor.getId())
                    .orElseThrow(() -> AppException.notFound("User not found"));
            Resume resume = new Resume();
            resume.setOwner(owner);
            resume.setLabel(label.trim());
            resume.setExternalUrl(hasUrl ? externalUrl.trim() : null);
            if (hasFile) {
                StoragePort.StoredObject stored = fileStorageService.storeUpload(owner.getId(), idempotencyKey, file);
                resume.setStorageKey(stored.key());
                resume.setContentType(stored.contentType());
                resume.setSizeBytes(stored.size());
                resume.setOriginalFilename(file.getOriginalFilename());
            }
            return resumeRepository.save(resume);
        });
    }

    @Transactional
    public Resume softDelete(Long id, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "resume-soft-delete", "id", id),
                () -> {
                    Resume resume = getActive(id);
                    resume.softDelete(UUID.randomUUID());
                    return resumeRepository.save(resume);
                });
    }

    @Transactional
    public void hardDelete(Long id, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "resume-hard-delete", "id", id),
                () -> {
                    Resume resume = resumeRepository.findById(id)
                            .orElseThrow(() -> AppException.notFound("Resume not found"));
                    ownershipGuard.requireActiveOwner(resume.getOwner());
                    if (!resume.isDeleted()) {
                        throw AppException.badRequest("NOT_ARCHIVED", "Soft-delete before hard delete");
                    }
                    roundRepository.clearResumeReferences(resume.getId());
                    String key = resume.getStorageKey();
                    resumeRepository.delete(resume);
                    fileStorageService.deleteQuietly(key);
                    return Map.of("deleted", true);
                });
    }

    public InputStream openContent(Long id) {
        Resume resume = getActive(id);
        if (resume.getStorageKey() == null) {
            throw AppException.badRequest("NO_FILE", "Resume has no uploaded file");
        }
        return fileStorageService.open(resume.getStorageKey());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
