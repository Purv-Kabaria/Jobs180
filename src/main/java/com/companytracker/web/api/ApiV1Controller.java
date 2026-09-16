package com.companytracker.web.api;

import com.companytracker.domain.ApplicationStatus;
import com.companytracker.domain.Company;
import com.companytracker.domain.JobApplication;
import com.companytracker.domain.Resume;
import com.companytracker.domain.Round;
import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.security.CurrentUserService;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.service.AdminUserService;
import com.companytracker.service.AuthService;
import com.companytracker.service.CompanyService;
import com.companytracker.service.DashboardService;
import com.companytracker.service.JobApplicationService;
import com.companytracker.service.NoteResourceService;
import com.companytracker.service.ResumeService;
import com.companytracker.service.RoundService;
import com.companytracker.web.dto.Forms;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ApiV1Controller {

    private final AuthService authService;
    private final CompanyService companyService;
    private final JobApplicationService jobApplicationService;
    private final RoundService roundService;
    private final ResumeService resumeService;
    private final NoteResourceService noteResourceService;
    private final DashboardService dashboardService;
    private final AdminUserService adminUserService;
    private final CurrentUserService currentUserService;
    private final AuthenticationManager authenticationManager;

    public ApiV1Controller(
            AuthService authService,
            CompanyService companyService,
            JobApplicationService jobApplicationService,
            RoundService roundService,
            ResumeService resumeService,
            NoteResourceService noteResourceService,
            DashboardService dashboardService,
            AdminUserService adminUserService,
            CurrentUserService currentUserService,
            AuthenticationManager authenticationManager) {
        this.authService = authService;
        this.companyService = companyService;
        this.jobApplicationService = jobApplicationService;
        this.roundService = roundService;
        this.resumeService = resumeService;
        this.noteResourceService = noteResourceService;
        this.dashboardService = dashboardService;
        this.adminUserService = adminUserService;
        this.currentUserService = currentUserService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/auth/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody Forms.SignupForm form,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idemKey,
                                    HttpServletRequest request) {
        String key = idemKey != null ? idemKey : form.getIdempotencyKey();
        UserAccount user = authService.signup(form.getEmail(), form.getPassword(), key, request.getRemoteAddr());
        return ResponseEntity.status(201).body(userDto(user));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody Forms.SignupForm form, HttpServletRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(AuthService.normalizeEmail(form.getEmail()), form.getPassword()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        TrackerUserDetails details = (TrackerUserDetails) auth.getPrincipal();
        return ResponseEntity.ok(Map.of(
                "id", details.getId(),
                "email", details.getUsername(),
                "role", details.getRole().name()
        ));
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        TrackerUserDetails user = currentUserService.requireUser();
        return Map.of("id", user.getId(), "email", user.getUsername(), "role", user.getRole().name());
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return Map.of(
                "statusCounts", dashboardService.statusCounts(),
                "upcoming", roundService.upcoming(14).stream().map(this::roundDto).toList()
        );
    }

    @GetMapping("/companies")
    public Map<String, Object> companies(@RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return pageDto(companyService.list(q, page, size).map(this::companyDto));
    }

    @PostMapping("/companies")
    public ResponseEntity<?> createCompany(@Valid @RequestBody Forms.CompanyForm form,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        Company c = companyService.create(form.getName(), form.getWebsite(), form.getIndustry(),
                form.getLocation(), form.getCareerPageUrl(), idemKey != null ? idemKey : form.getIdempotencyKey());
        return ResponseEntity.status(201).body(companyDto(c));
    }

    @GetMapping("/companies/{id}")
    public Map<String, Object> getCompany(@PathVariable Long id) {
        return companyDto(companyService.getActive(id));
    }

    @PatchMapping("/companies/{id}")
    public Map<String, Object> patchCompany(@PathVariable Long id, @RequestBody Forms.CompanyForm form) {
        return companyDto(companyService.update(id, form.getName(), form.getWebsite(), form.getIndustry(),
                form.getLocation(), form.getCareerPageUrl(), form.getVersion()));
    }

    @PostMapping("/companies/{id}/soft-delete")
    public Map<String, Object> softDeleteCompany(@PathVariable Long id,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return companyDto(companyService.softDelete(id, idemOrNew(idemKey)));
    }

    @PostMapping("/companies/{id}/restore")
    public Map<String, Object> restoreCompany(@PathVariable Long id,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return companyDto(companyService.restore(id, idemOrNew(idemKey)));
    }

    @DeleteMapping("/companies/{id}")
    public Map<String, Object> hardDeleteCompany(@PathVariable Long id,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        companyService.hardDelete(id, idemOrNew(idemKey));
        return Map.of("deleted", true);
    }

    @GetMapping("/companies/{id}/applications")
    public Map<String, Object> listApps(@PathVariable Long id) {
        return Map.of("content", jobApplicationService.listByCompany(id).stream().map(this::appDto).toList());
    }

    @PostMapping("/companies/{id}/applications")
    public ResponseEntity<?> createApp(@PathVariable Long id,
                                       @Valid @RequestBody Forms.ApplicationForm form,
                                       @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        JobApplication app = jobApplicationService.create(id, form.getTitle(), form.getJdText(), form.getJdLink(),
                form.getWorkMode(), form.getCompensation(), form.getAppliedDate(), form.isReferral(),
                form.getSource(), form.getStatus(), idemKey != null ? idemKey : form.getIdempotencyKey());
        return ResponseEntity.status(201).body(appDto(app));
    }

    @GetMapping("/applications/{id}")
    public Map<String, Object> getApp(@PathVariable Long id) {
        return appDto(jobApplicationService.getActive(id));
    }

    @PatchMapping("/applications/{id}")
    public Map<String, Object> patchApp(@PathVariable Long id, @RequestBody Forms.ApplicationForm form) {
        return appDto(jobApplicationService.update(id, form.getTitle(), form.getJdText(), form.getJdLink(),
                form.getWorkMode(), form.getCompensation(), form.getAppliedDate(), form.isReferral(),
                form.getSource(), form.getStatus(), form.getVersion()));
    }

    @PostMapping("/applications/{id}/soft-delete")
    public Map<String, Object> softDeleteApp(@PathVariable Long id,
                                             @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return appDto(jobApplicationService.softDelete(id, idemOrNew(idemKey)));
    }

    @DeleteMapping("/applications/{id}")
    public Map<String, Object> hardDeleteApp(@PathVariable Long id,
                                             @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        jobApplicationService.hardDelete(id, idemOrNew(idemKey));
        return Map.of("deleted", true);
    }

    @GetMapping("/applications/{id}/rounds")
    public Map<String, Object> listRounds(@PathVariable Long id) {
        return Map.of("content", roundService.listByApplication(id).stream().map(this::roundDto).toList());
    }

    @PostMapping("/applications/{id}/rounds")
    public ResponseEntity<?> createRound(@PathVariable Long id,
                                         @RequestBody Forms.RoundForm form,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        Instant scheduled = form.getScheduledAt() == null || form.getScheduledAt().isBlank()
                ? null : Instant.parse(form.getScheduledAt());
        Round round = roundService.create(id, form.getName(), scheduled, form.getOutcome(),
                form.getInterviewers(), form.getResumeId(), idemKey != null ? idemKey : form.getIdempotencyKey());
        return ResponseEntity.status(201).body(roundDto(round));
    }

    @GetMapping("/rounds/{id}")
    public Map<String, Object> getRound(@PathVariable Long id) {
        return roundDto(roundService.getActive(id));
    }

    @PatchMapping("/rounds/{id}")
    public Map<String, Object> patchRound(@PathVariable Long id, @RequestBody Forms.RoundForm form) {
        Instant scheduled = form.getScheduledAt() == null || form.getScheduledAt().isBlank()
                ? null : Instant.parse(form.getScheduledAt());
        return roundDto(roundService.update(id, form.getName(), scheduled, form.getOutcome(),
                form.getInterviewers(), form.getResumeId(), form.getVersion()));
    }

    @PostMapping("/rounds/{id}/resume")
    public Map<String, Object> attachResume(@PathVariable Long id,
                                            @RequestBody Map<String, Long> body,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return roundDto(roundService.attachResume(id, body.get("resumeId"), idemOrNew(idemKey)));
    }

    @DeleteMapping("/rounds/{id}/resume")
    public Map<String, Object> detachResume(@PathVariable Long id) {
        return roundDto(roundService.detachResume(id));
    }

    @GetMapping("/resumes")
    public Map<String, Object> resumes() {
        return Map.of("content", resumeService.listMineOrAdmin(null).stream().map(this::resumeDto).toList());
    }

    @PostMapping(value = "/resumes", consumes = {"multipart/form-data", "application/json"})
    public ResponseEntity<?> createResume(@RequestParam(required = false) String label,
                                          @RequestParam(required = false) String externalUrl,
                                          @RequestPart(value = "file", required = false) MultipartFile file,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        Resume resume = resumeService.create(label, externalUrl, file, idemOrNew(idemKey));
        return ResponseEntity.status(201).body(resumeDto(resume));
    }

    @GetMapping("/resumes/{id}/content")
    public void resumeContent(@PathVariable Long id, HttpServletResponse response) throws Exception {
        Resume resume = resumeService.getActive(id);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + (resume.getOriginalFilename() != null ? resume.getOriginalFilename() : "resume") + "\"");
        try (InputStream in = resumeService.openContent(id)) {
            StreamUtils.copy(in, response.getOutputStream());
        }
    }

    @PostMapping("/{parentType}/{parentId}/notes")
    public ResponseEntity<?> addNote(@PathVariable String parentType,
                                     @PathVariable Long parentId,
                                     @Valid @RequestBody Forms.NoteForm form,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        var note = noteResourceService.addNote(parentType, parentId, form.getTitle(), form.getBody(),
                idemKey != null ? idemKey : form.getIdempotencyKey());
        return ResponseEntity.status(201).body(Map.of("id", note.getId(), "title", note.getTitle()));
    }

    @PostMapping("/{parentType}/{parentId}/resources")
    public ResponseEntity<?> addResource(@PathVariable String parentType,
                                         @PathVariable Long parentId,
                                         @RequestParam String title,
                                         @RequestParam(required = false) String url,
                                         @RequestPart(required = false) MultipartFile file,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        var item = noteResourceService.addResource(parentType, parentId, title, url, file, idemOrNew(idemKey));
        return ResponseEntity.status(201).body(Map.of("id", item.getId(), "title", item.getTitle()));
    }

    @GetMapping("/search/applications")
    public Map<String, Object> search(@RequestParam(required = false) String company,
                                      @RequestParam(required = false) ApplicationStatus status,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return pageDto(jobApplicationService.search(company, status, from, to, page, size).map(this::appDto));
    }

    @GetMapping("/upcoming-rounds")
    public Map<String, Object> upcoming(@RequestParam(defaultValue = "14") int withinDays) {
        return Map.of("content", roundService.upcoming(withinDays).stream().map(this::roundDto).toList());
    }

    @GetMapping("/admin/users")
    public Map<String, Object> adminUsers() {
        return Map.of(
                "content", adminUserService.listActive().stream().map(this::userDto).toList(),
                "deleted", adminUserService.listDeleted().stream().map(this::userDto).toList()
        );
    }

    @PostMapping("/admin/users/{id}/role")
    public Map<String, Object> adminRole(@PathVariable Long id,
                                         @RequestBody Map<String, String> body,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return userDto(adminUserService.changeRole(id, UserRole.valueOf(body.get("role")), idemOrNew(idemKey)));
    }

    @PostMapping("/admin/users/{id}/enable")
    public Map<String, Object> adminEnable(@PathVariable Long id,
                                           @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return userDto(adminUserService.setEnabled(id, true, idemOrNew(idemKey)));
    }

    @PostMapping("/admin/users/{id}/disable")
    public Map<String, Object> adminDisable(@PathVariable Long id,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return userDto(adminUserService.setEnabled(id, false, idemOrNew(idemKey)));
    }

    @PostMapping("/admin/users/{id}/soft-delete")
    public Map<String, Object> adminSoftDelete(@PathVariable Long id,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return userDto(adminUserService.softDelete(id, idemOrNew(idemKey)));
    }

    @PostMapping("/admin/users/{id}/restore")
    public Map<String, Object> adminRestore(@PathVariable Long id,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        return userDto(adminUserService.restore(id, idemOrNew(idemKey)));
    }

    @DeleteMapping("/admin/users/{id}")
    public Map<String, Object> adminHardDelete(@PathVariable Long id,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String idemKey) {
        adminUserService.hardDelete(id, idemOrNew(idemKey));
        return Map.of("deleted", true);
    }

    private String idemOrNew(String key) {
        return key == null || key.isBlank() ? UUID.randomUUID().toString() : key;
    }

    private Map<String, Object> pageDto(Page<?> page) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("content", page.getContent());
        map.put("page", page.getNumber());
        map.put("size", page.getSize());
        map.put("totalElements", page.getTotalElements());
        map.put("totalPages", page.getTotalPages());
        return map;
    }

    private Map<String, Object> userDto(UserAccount u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("email", u.getEmail());
        m.put("role", u.getRole().name());
        m.put("enabled", u.isEnabled());
        m.put("deleted", u.isDeleted());
        return m;
    }

    private Map<String, Object> companyDto(Company c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("website", c.getWebsite());
        m.put("industry", c.getIndustry());
        m.put("location", c.getLocation());
        m.put("careerPageUrl", c.getCareerPageUrl());
        m.put("ownerId", c.getOwnerId());
        m.put("version", c.getVersion());
        return m;
    }

    private Map<String, Object> appDto(JobApplication a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("companyId", a.getCompany().getId());
        m.put("title", a.getTitle());
        m.put("status", a.getStatus().name());
        m.put("workMode", a.getWorkMode().name());
        m.put("appliedDate", a.getAppliedDate());
        m.put("referral", a.isReferral());
        m.put("source", a.getSource());
        m.put("version", a.getVersion());
        return m;
    }

    private Map<String, Object> roundDto(Round r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("applicationId", r.getApplication().getId());
        m.put("name", r.getName());
        m.put("scheduledAt", r.getScheduledAt());
        m.put("outcome", r.getOutcome().name());
        m.put("interviewers", r.getInterviewers());
        m.put("resumeId", r.getResume() != null ? r.getResume().getId() : null);
        m.put("version", r.getVersion());
        return m;
    }

    private Map<String, Object> resumeDto(Resume r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("label", r.getLabel());
        m.put("externalUrl", r.getExternalUrl());
        m.put("hasFile", r.getStorageKey() != null);
        m.put("originalFilename", r.getOriginalFilename());
        return m;
    }
}
