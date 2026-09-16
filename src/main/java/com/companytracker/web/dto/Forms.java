package com.companytracker.web.dto;

import com.companytracker.domain.ApplicationStatus;
import com.companytracker.domain.WorkMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class Forms {

    private Forms() {}

    public static class SignupForm {
        @NotBlank
        @Size(max = 320)
        private String email;
        @NotBlank
        @Size(min = 8, max = 100)
        private String password;
        private String idempotencyKey;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }

    public static class CompanyForm {
        @NotBlank @Size(max = 200) private String name;
        @Size(max = 500) private String website;
        @Size(max = 200) private String industry;
        @Size(max = 200) private String location;
        @Size(max = 500) private String careerPageUrl;
        private Long version;
        private String idempotencyKey;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getWebsite() { return website; }
        public void setWebsite(String website) { this.website = website; }
        public String getIndustry() { return industry; }
        public void setIndustry(String industry) { this.industry = industry; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        public String getCareerPageUrl() { return careerPageUrl; }
        public void setCareerPageUrl(String careerPageUrl) { this.careerPageUrl = careerPageUrl; }
        public Long getVersion() { return version; }
        public void setVersion(Long version) { this.version = version; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }

    public static class ApplicationForm {
        @NotBlank @Size(max = 200) private String title;
        @Size(max = 50000) private String jdText;
        @Size(max = 1000) private String jdLink;
        private WorkMode workMode = WorkMode.UNKNOWN;
        @Size(max = 200) private String compensation;
        private LocalDate appliedDate;
        private boolean referral;
        @Size(max = 200) private String source;
        private ApplicationStatus status = ApplicationStatus.WISHLIST;
        private Long version;
        private String idempotencyKey;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getJdText() { return jdText; }
        public void setJdText(String jdText) { this.jdText = jdText; }
        public String getJdLink() { return jdLink; }
        public void setJdLink(String jdLink) { this.jdLink = jdLink; }
        public WorkMode getWorkMode() { return workMode; }
        public void setWorkMode(WorkMode workMode) { this.workMode = workMode; }
        public String getCompensation() { return compensation; }
        public void setCompensation(String compensation) { this.compensation = compensation; }
        public LocalDate getAppliedDate() { return appliedDate; }
        public void setAppliedDate(LocalDate appliedDate) { this.appliedDate = appliedDate; }
        public boolean isReferral() { return referral; }
        public void setReferral(boolean referral) { this.referral = referral; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public ApplicationStatus getStatus() { return status; }
        public void setStatus(ApplicationStatus status) { this.status = status; }
        public Long getVersion() { return version; }
        public void setVersion(Long version) { this.version = version; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }

    public static class RoundForm {
        @NotBlank @Size(max = 200) private String name;
        private String scheduledAt;
        private com.companytracker.domain.RoundOutcome outcome = com.companytracker.domain.RoundOutcome.PENDING;
        @Size(max = 500) private String interviewers;
        private Long resumeId;
        private Long version;
        private String idempotencyKey;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getScheduledAt() { return scheduledAt; }
        public void setScheduledAt(String scheduledAt) { this.scheduledAt = scheduledAt; }
        public com.companytracker.domain.RoundOutcome getOutcome() { return outcome; }
        public void setOutcome(com.companytracker.domain.RoundOutcome outcome) { this.outcome = outcome; }
        public String getInterviewers() { return interviewers; }
        public void setInterviewers(String interviewers) { this.interviewers = interviewers; }
        public Long getResumeId() { return resumeId; }
        public void setResumeId(Long resumeId) { this.resumeId = resumeId; }
        public Long getVersion() { return version; }
        public void setVersion(Long version) { this.version = version; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }

    public static class NoteForm {
        @NotBlank @Size(max = 200) private String title;
        @NotBlank @Size(max = 10000) private String body;
        private String idempotencyKey;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }

    public static class ResourceForm {
        @NotBlank @Size(max = 200) private String title;
        @Size(max = 1000) private String url;
        private String idempotencyKey;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }

    public static class ResumeForm {
        @NotBlank @Size(max = 200) private String label;
        @Size(max = 1000) private String externalUrl;
        private String idempotencyKey;

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getExternalUrl() { return externalUrl; }
        public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }
        public String getIdempotencyKey() { return idempotencyKey; }
        public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    }
}
