package com.companytracker.service;

import com.companytracker.domain.Company;
import com.companytracker.domain.JobApplication;
import com.companytracker.domain.Note;
import com.companytracker.domain.ResourceItem;
import com.companytracker.domain.Round;
import com.companytracker.repo.NoteRepository;
import com.companytracker.repo.ResourceItemRepository;
import com.companytracker.security.CurrentUserService;
import com.companytracker.security.IdempotencyService;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.storage.FileStorageService;
import com.companytracker.storage.StoragePort;
import com.companytracker.web.error.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NoteResourceService {

    private final NoteRepository noteRepository;
    private final ResourceItemRepository resourceItemRepository;
    private final CompanyService companyService;
    private final JobApplicationService jobApplicationService;
    private final RoundService roundService;
    private final OwnershipGuard ownershipGuard;
    private final CurrentUserService currentUserService;
    private final IdempotencyService idempotencyService;
    private final FileStorageService fileStorageService;

    public NoteResourceService(
            NoteRepository noteRepository,
            ResourceItemRepository resourceItemRepository,
            CompanyService companyService,
            JobApplicationService jobApplicationService,
            RoundService roundService,
            OwnershipGuard ownershipGuard,
            CurrentUserService currentUserService,
            IdempotencyService idempotencyService,
            FileStorageService fileStorageService) {
        this.noteRepository = noteRepository;
        this.resourceItemRepository = resourceItemRepository;
        this.companyService = companyService;
        this.jobApplicationService = jobApplicationService;
        this.roundService = roundService;
        this.ownershipGuard = ownershipGuard;
        this.currentUserService = currentUserService;
        this.idempotencyService = idempotencyService;
        this.fileStorageService = fileStorageService;
    }

    public List<Note> notesForCompany(Long companyId) {
        companyService.getActive(companyId);
        return noteRepository.findActiveByCompany(companyId);
    }

    public List<Note> notesForApplication(Long applicationId) {
        jobApplicationService.getActive(applicationId);
        return noteRepository.findActiveByApplication(applicationId);
    }

    public List<Note> notesForRound(Long roundId) {
        roundService.getActive(roundId);
        return noteRepository.findActiveByRound(roundId);
    }

    public List<ResourceItem> resourcesForCompany(Long companyId) {
        companyService.getActive(companyId);
        return resourceItemRepository.findActiveByCompany(companyId);
    }

    public List<ResourceItem> resourcesForApplication(Long applicationId) {
        jobApplicationService.getActive(applicationId);
        return resourceItemRepository.findActiveByApplication(applicationId);
    }

    public List<ResourceItem> resourcesForRound(Long roundId) {
        roundService.getActive(roundId);
        return resourceItemRepository.findActiveByRound(roundId);
    }

    @Transactional
    public Note addNote(String parentType, Long parentId, String title, String body, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        Map<String, Object> payload = Map.of(
                "parentType", parentType,
                "parentId", parentId,
                "title", nullToEmpty(title),
                "body", nullToEmpty(body)
        );
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey, payload, () -> {
            if (title == null || title.isBlank() || body == null || body.isBlank()) {
                throw AppException.badRequest("VALIDATION_ERROR", "Title and body are required");
            }
            if (body.length() > 10000) {
                throw AppException.badRequest("VALIDATION_ERROR", "Note body too long");
            }
            Note note = new Note();
            note.setTitle(title.trim());
            note.setBody(body.trim());
            attachParent(note, parentType, parentId);
            return noteRepository.save(note);
        });
    }

    @Transactional
    public ResourceItem addResource(String parentType, Long parentId, String title, String url,
                                    MultipartFile file, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        Map<String, Object> payload = new HashMap<>();
        payload.put("parentType", parentType);
        payload.put("parentId", parentId);
        payload.put("title", nullToEmpty(title));
        payload.put("url", nullToEmpty(url));
        payload.put("hasFile", file != null && !file.isEmpty());
        return idempotencyService.execute("user:" + actor.getId(), idempotencyKey, payload, () -> {
            if (title == null || title.isBlank()) {
                throw AppException.badRequest("VALIDATION_ERROR", "Title is required");
            }
            boolean hasUrl = url != null && !url.isBlank();
            boolean hasFile = file != null && !file.isEmpty();
            if (!hasUrl && !hasFile) {
                throw AppException.badRequest("VALIDATION_ERROR", "Provide a URL and/or file");
            }
            ResourceItem item = new ResourceItem();
            item.setTitle(title.trim());
            item.setUrl(hasUrl ? url.trim() : null);
            attachParent(item, parentType, parentId);
            if (hasFile) {
                StoragePort.StoredObject stored = fileStorageService.storeUpload(item.getOwnerId(), idempotencyKey, file);
                item.setStorageKey(stored.key());
                item.setContentType(stored.contentType());
                item.setSizeBytes(stored.size());
                item.setOriginalFilename(file.getOriginalFilename());
            }
            return resourceItemRepository.save(item);
        });
    }

    @Transactional
    public void softDeleteNote(Long id, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "note-soft-delete", "id", id),
                () -> {
                    Note note = noteRepository.findActiveById(id)
                            .orElseThrow(() -> AppException.notFound("Note not found"));
                    ownershipGuard.requireActiveOwner(note.getOwner());
                    note.softDelete(UUID.randomUUID());
                    return noteRepository.save(note);
                });
    }

    @Transactional
    public void softDeleteResource(Long id, String idempotencyKey) {
        TrackerUserDetails actor = currentUserService.requireUser();
        idempotencyService.execute("user:" + actor.getId(), idempotencyKey,
                Map.of("action", "resource-soft-delete", "id", id),
                () -> {
                    ResourceItem item = resourceItemRepository.findActiveById(id)
                            .orElseThrow(() -> AppException.notFound("Resource not found"));
                    ownershipGuard.requireActiveOwner(item.getOwner());
                    item.softDelete(UUID.randomUUID());
                    return resourceItemRepository.save(item);
                });
    }

    private void attachParent(Note note, String parentType, Long parentId) {
        switch (parentType.toLowerCase()) {
            case "companies", "company" -> {
                Company company = companyService.getActive(parentId);
                note.setCompany(company);
                note.setOwner(company.getOwner());
            }
            case "applications", "application" -> {
                JobApplication app = jobApplicationService.getActive(parentId);
                note.setApplication(app);
                note.setOwner(app.getOwner());
            }
            case "rounds", "round" -> {
                Round round = roundService.getActive(parentId);
                note.setRound(round);
                note.setOwner(round.getOwner());
            }
            default -> throw AppException.badRequest("VALIDATION_ERROR", "Invalid parent type");
        }
    }

    private void attachParent(ResourceItem item, String parentType, Long parentId) {
        switch (parentType.toLowerCase()) {
            case "companies", "company" -> {
                Company company = companyService.getActive(parentId);
                item.setCompany(company);
                item.setOwner(company.getOwner());
            }
            case "applications", "application" -> {
                JobApplication app = jobApplicationService.getActive(parentId);
                item.setApplication(app);
                item.setOwner(app.getOwner());
            }
            case "rounds", "round" -> {
                Round round = roundService.getActive(parentId);
                item.setRound(round);
                item.setOwner(round.getOwner());
            }
            default -> throw AppException.badRequest("VALIDATION_ERROR", "Invalid parent type");
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
