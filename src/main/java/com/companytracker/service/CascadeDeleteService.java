package com.companytracker.service;

import com.companytracker.domain.Company;
import com.companytracker.domain.JobApplication;
import com.companytracker.domain.Note;
import com.companytracker.domain.ResourceItem;
import com.companytracker.domain.Resume;
import com.companytracker.domain.Round;
import com.companytracker.domain.UserAccount;
import com.companytracker.repo.CompanyRepository;
import com.companytracker.repo.JobApplicationRepository;
import com.companytracker.repo.NoteRepository;
import com.companytracker.repo.ResourceItemRepository;
import com.companytracker.repo.ResumeRepository;
import com.companytracker.repo.RoundRepository;
import com.companytracker.storage.FileStorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CascadeDeleteService {

    private final CompanyRepository companyRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final RoundRepository roundRepository;
    private final NoteRepository noteRepository;
    private final ResourceItemRepository resourceItemRepository;
    private final ResumeRepository resumeRepository;
    private final FileStorageService fileStorageService;

    public CascadeDeleteService(
            CompanyRepository companyRepository,
            JobApplicationRepository jobApplicationRepository,
            RoundRepository roundRepository,
            NoteRepository noteRepository,
            ResourceItemRepository resourceItemRepository,
            ResumeRepository resumeRepository,
            FileStorageService fileStorageService) {
        this.companyRepository = companyRepository;
        this.jobApplicationRepository = jobApplicationRepository;
        this.roundRepository = roundRepository;
        this.noteRepository = noteRepository;
        this.resourceItemRepository = resourceItemRepository;
        this.resumeRepository = resumeRepository;
        this.fileStorageService = fileStorageService;
    }

    @Transactional
    public void softDeleteCompanyTree(Company company, UUID batch) {
        if (company.isDeleted()) {
            return;
        }
        List<JobApplication> apps = jobApplicationRepository.findActiveByCompany(company.getId());
        for (JobApplication app : apps) {
            softDeleteApplicationTree(app, batch);
        }
        for (Note note : noteRepository.findActiveByCompany(company.getId())) {
            note.softDelete(batch);
            noteRepository.save(note);
        }
        for (ResourceItem resource : resourceItemRepository.findActiveByCompany(company.getId())) {
            resource.softDelete(batch);
            resourceItemRepository.save(resource);
        }
        company.softDelete(batch);
        companyRepository.save(company);
    }

    @Transactional
    public void softDeleteApplicationTree(JobApplication app, UUID batch) {
        if (app.isDeleted()) {
            return;
        }
        for (Round round : roundRepository.findActiveByApplication(app.getId())) {
            softDeleteRoundTree(round, batch);
        }
        for (Note note : noteRepository.findActiveByApplication(app.getId())) {
            note.softDelete(batch);
            noteRepository.save(note);
        }
        for (ResourceItem resource : resourceItemRepository.findActiveByApplication(app.getId())) {
            resource.softDelete(batch);
            resourceItemRepository.save(resource);
        }
        app.softDelete(batch);
        jobApplicationRepository.save(app);
    }

    @Transactional
    public void softDeleteRoundTree(Round round, UUID batch) {
        if (round.isDeleted()) {
            return;
        }
        for (Note note : noteRepository.findActiveByRound(round.getId())) {
            note.softDelete(batch);
            noteRepository.save(note);
        }
        for (ResourceItem resource : resourceItemRepository.findActiveByRound(round.getId())) {
            resource.softDelete(batch);
            resourceItemRepository.save(resource);
        }
        round.softDelete(batch);
        roundRepository.save(round);
    }

    @Transactional
    public void softDeleteAllForUser(UserAccount user, UUID batch) {
        for (Company company : companyRepository.findAllByOwner(user.getId())) {
            if (!company.isDeleted()) {
                softDeleteCompanyTree(company, batch);
            }
        }
        for (Resume resume : resumeRepository.findAllByOwner(user.getId())) {
            if (!resume.isDeleted()) {
                resume.softDelete(batch);
                resumeRepository.save(resume);
            }
        }
    }

    @Transactional
    public void restoreBatchForUser(Long ownerId, UUID batch) {
        companyRepository.findByOwner_IdAndDeletionBatchId(ownerId, batch).forEach(c -> {
            c.restore();
            companyRepository.save(c);
        });
        jobApplicationRepository.findByOwner_IdAndDeletionBatchId(ownerId, batch).forEach(a -> {
            a.restore();
            jobApplicationRepository.save(a);
        });
        roundRepository.findByOwner_IdAndDeletionBatchId(ownerId, batch).forEach(r -> {
            r.restore();
            roundRepository.save(r);
        });
        noteRepository.findByOwner_IdAndDeletionBatchId(ownerId, batch).forEach(n -> {
            n.restore();
            noteRepository.save(n);
        });
        resourceItemRepository.findByOwner_IdAndDeletionBatchId(ownerId, batch).forEach(r -> {
            r.restore();
            resourceItemRepository.save(r);
        });
        resumeRepository.findByOwner_IdAndDeletionBatchId(ownerId, batch).forEach(r -> {
            r.restore();
            resumeRepository.save(r);
        });
    }

    @Transactional
    public void restoreCompanyBatch(Company company) {
        UUID batch = company.getDeletionBatchId();
        if (batch == null) {
            company.restore();
            companyRepository.save(company);
            return;
        }
        jobApplicationRepository.findByCompany_IdAndDeletionBatchId(company.getId(), batch).forEach(app -> {
            roundRepository.findByApplication_IdAndDeletionBatchId(app.getId(), batch).forEach(r -> {
                r.restore();
                roundRepository.save(r);
            });
            app.restore();
            jobApplicationRepository.save(app);
        });
        company.restore();
        companyRepository.save(company);
    }

    @Transactional
    public void hardDeleteAllForUser(UserAccount user) {
        List<String> keys = new ArrayList<>();
        for (ResourceItem resource : resourceItemRepository.findAllByOwner(user.getId())) {
            if (resource.getStorageKey() != null) {
                keys.add(resource.getStorageKey());
            }
            resourceItemRepository.delete(resource);
        }
        noteRepository.findAllByOwner(user.getId()).forEach(noteRepository::delete);
        for (Round round : roundRepository.findAllByOwner(user.getId())) {
            round.setResume(null);
            roundRepository.delete(round);
        }
        jobApplicationRepository.findAllByOwner(user.getId()).forEach(jobApplicationRepository::delete);
        companyRepository.findAllByOwner(user.getId()).forEach(companyRepository::delete);
        for (Resume resume : resumeRepository.findAllByOwner(user.getId())) {
            if (resume.getStorageKey() != null) {
                keys.add(resume.getStorageKey());
            }
            resumeRepository.delete(resume);
        }
        keys.forEach(fileStorageService::deleteQuietly);
    }

    @Transactional
    public void hardDeleteCompany(Company company) {
        List<String> keys = new ArrayList<>();
        for (Note n : noteRepository.findActiveByCompany(company.getId())) {
            noteRepository.delete(n);
        }
        for (ResourceItem r : resourceItemRepository.findActiveByCompany(company.getId())) {
            if (r.getStorageKey() != null) {
                keys.add(r.getStorageKey());
            }
            resourceItemRepository.delete(r);
        }
        for (JobApplication app : jobApplicationRepository.findAllByCompanyId(company.getId())) {
            hardDeleteApplication(app, keys);
        }
        companyRepository.delete(company);
        keys.forEach(fileStorageService::deleteQuietly);
    }

    @Transactional
    public void hardDeleteApplication(JobApplication app) {
        List<String> keys = new ArrayList<>();
        hardDeleteApplication(app, keys);
        keys.forEach(fileStorageService::deleteQuietly);
    }

    private void hardDeleteApplication(JobApplication app, List<String> keys) {
        for (Round round : roundRepository.findAllByApplicationId(app.getId())) {
            hardDeleteRound(round, keys);
        }
        noteRepository.findActiveByApplication(app.getId()).forEach(noteRepository::delete);
        for (ResourceItem r : resourceItemRepository.findActiveByApplication(app.getId())) {
            if (r.getStorageKey() != null) {
                keys.add(r.getStorageKey());
            }
            resourceItemRepository.delete(r);
        }
        jobApplicationRepository.delete(app);
    }

    @Transactional
    public void hardDeleteRound(Round round) {
        List<String> keys = new ArrayList<>();
        hardDeleteRound(round, keys);
        keys.forEach(fileStorageService::deleteQuietly);
    }

    private void hardDeleteRound(Round round, List<String> keys) {
        noteRepository.findActiveByRound(round.getId()).forEach(noteRepository::delete);
        for (ResourceItem r : resourceItemRepository.findActiveByRound(round.getId())) {
            if (r.getStorageKey() != null) keys.add(r.getStorageKey());
            resourceItemRepository.delete(r);
        }
        roundRepository.delete(round);
    }
}
