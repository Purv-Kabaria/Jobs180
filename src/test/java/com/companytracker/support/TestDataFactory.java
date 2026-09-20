package com.companytracker.support;

import com.companytracker.domain.ApplicationStatus;
import com.companytracker.domain.Company;
import com.companytracker.domain.JobApplication;
import com.companytracker.domain.Note;
import com.companytracker.domain.Resume;
import com.companytracker.domain.Round;
import com.companytracker.domain.RoundOutcome;
import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.domain.WorkMode;
import com.companytracker.repo.CompanyRepository;
import com.companytracker.repo.JobApplicationRepository;
import com.companytracker.repo.NoteRepository;
import com.companytracker.repo.ResumeRepository;
import com.companytracker.repo.RoundRepository;
import com.companytracker.repo.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

public final class TestDataFactory {

    private TestDataFactory() {}

    public static UserAccount persistUser(
            UserAccountRepository repo,
            PasswordEncoder encoder,
            String email,
            String rawPassword,
            UserRole role) {
        UserAccount user = new UserAccount();
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(encoder.encode(rawPassword));
        user.setRole(role);
        user.setEnabled(true);
        user.setSecurityStamp(UUID.randomUUID());
        return repo.save(user);
    }

    public static Company persistCompany(CompanyRepository repo, UserAccount owner, String name) {
        Company company = new Company();
        company.setOwner(owner);
        company.setName(name);
        return repo.save(company);
    }

    public static JobApplication persistApplication(
            JobApplicationRepository repo, Company company, UserAccount owner, String title) {
        JobApplication app = new JobApplication();
        app.setCompany(company);
        app.setOwner(owner);
        app.setTitle(title);
        app.setWorkMode(WorkMode.REMOTE);
        app.setStatus(ApplicationStatus.APPLIED);
        app.setReferral(false);
        return repo.save(app);
    }

    public static Round persistRound(
            RoundRepository repo, JobApplication app, UserAccount owner, String name, Instant scheduledAt) {
        Round round = new Round();
        round.setApplication(app);
        round.setOwner(owner);
        round.setName(name);
        round.setScheduledAt(scheduledAt);
        round.setOutcome(RoundOutcome.PENDING);
        return repo.save(round);
    }

    public static Resume persistResumeUrl(ResumeRepository repo, UserAccount owner, String label, String url) {
        Resume resume = new Resume();
        resume.setOwner(owner);
        resume.setLabel(label);
        resume.setExternalUrl(url);
        return repo.save(resume);
    }

    public static Note persistCompanyNote(
            NoteRepository repo, UserAccount owner, Company company, String title, String body) {
        Note note = new Note();
        note.setOwner(owner);
        note.setCompany(company);
        note.setTitle(title);
        note.setBody(body);
        return repo.save(note);
    }
}
