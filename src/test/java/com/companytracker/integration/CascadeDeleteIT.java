package com.companytracker.integration;

import com.companytracker.domain.Company;
import com.companytracker.domain.JobApplication;
import com.companytracker.domain.Note;
import com.companytracker.domain.Round;
import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.CompanyRepository;
import com.companytracker.repo.JobApplicationRepository;
import com.companytracker.repo.NoteRepository;
import com.companytracker.repo.RoundRepository;
import com.companytracker.repo.UserAccountRepository;
import com.companytracker.service.CascadeDeleteService;
import com.companytracker.support.AbstractIntegrationTest;
import com.companytracker.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CascadeDeleteIT extends AbstractIntegrationTest {

    @Autowired UserAccountRepository userAccountRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired JobApplicationRepository jobApplicationRepository;
    @Autowired RoundRepository roundRepository;
    @Autowired NoteRepository noteRepository;
    @Autowired CascadeDeleteService cascadeDeleteService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void softDeleteCompanyCascadesToAppsRoundsNotes() {
        UserAccount owner = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, "cascade@test.com", "password123", UserRole.USER);
        Company company = TestDataFactory.persistCompany(companyRepository, owner, "Cascade Co");
        JobApplication app = TestDataFactory.persistApplication(
                jobApplicationRepository, company, owner, "Backend");
        Round round = TestDataFactory.persistRound(
                roundRepository, app, owner, "Phone", Instant.now().plusSeconds(86400));
        Note note = TestDataFactory.persistCompanyNote(
                noteRepository, owner, company, "Research", "Look at culture");

        UUID batch = UUID.randomUUID();
        cascadeDeleteService.softDeleteCompanyTree(company, batch);

        assertTrue(companyRepository.findById(company.getId()).orElseThrow().isDeleted());
        assertTrue(jobApplicationRepository.findById(app.getId()).orElseThrow().isDeleted());
        assertTrue(roundRepository.findById(round.getId()).orElseThrow().isDeleted());
        assertTrue(noteRepository.findById(note.getId()).orElseThrow().isDeleted());
        assertNotNull(companyRepository.findById(company.getId()).orElseThrow().getDeletionBatchId());
    }

    @Test
    void restoreBatchClearsSoftDeleteFlagsIncludingNotes() {
        UserAccount owner = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, "restore@test.com", "password123", UserRole.USER);
        Company company = TestDataFactory.persistCompany(companyRepository, owner, "Restore Co");
        JobApplication app = TestDataFactory.persistApplication(
                jobApplicationRepository, company, owner, "SWE");
        Note note = TestDataFactory.persistCompanyNote(
                noteRepository, owner, company, "N", "body");
        UUID batch = UUID.randomUUID();
        cascadeDeleteService.softDeleteCompanyTree(company, batch);

        cascadeDeleteService.restoreCompanyBatch(company);

        assertFalse(companyRepository.findById(company.getId()).orElseThrow().isDeleted());
        assertFalse(jobApplicationRepository.findById(app.getId()).orElseThrow().isDeleted());
        assertFalse(noteRepository.findById(note.getId()).orElseThrow().isDeleted());
        assertNull(companyRepository.findById(company.getId()).orElseThrow().getDeletionBatchId());
    }
}
