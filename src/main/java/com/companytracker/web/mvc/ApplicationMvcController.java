package com.companytracker.web.mvc;

import com.companytracker.domain.ApplicationStatus;
import com.companytracker.domain.JobApplication;
import com.companytracker.domain.WorkMode;
import com.companytracker.service.JobApplicationService;
import com.companytracker.service.NoteResourceService;
import com.companytracker.service.ResumeService;
import com.companytracker.service.RoundService;
import com.companytracker.web.dto.Forms;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.UUID;

@Controller
public class ApplicationMvcController {

    private final JobApplicationService jobApplicationService;
    private final RoundService roundService;
    private final NoteResourceService noteResourceService;
    private final ResumeService resumeService;

    public ApplicationMvcController(
            JobApplicationService jobApplicationService,
            RoundService roundService,
            NoteResourceService noteResourceService,
            ResumeService resumeService) {
        this.jobApplicationService = jobApplicationService;
        this.roundService = roundService;
        this.noteResourceService = noteResourceService;
        this.resumeService = resumeService;
    }

    @GetMapping("/companies/{companyId}/applications/new")
    public String createForm(@PathVariable Long companyId, Model model) {
        Forms.ApplicationForm form = new Forms.ApplicationForm();
        form.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("form", form);
        model.addAttribute("companyId", companyId);
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("workModes", WorkMode.values());
        return "applications/form";
    }

    @PostMapping("/companies/{companyId}/applications")
    public String create(@PathVariable Long companyId,
                         @Valid @ModelAttribute("form") Forms.ApplicationForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("companyId", companyId);
            model.addAttribute("statuses", ApplicationStatus.values());
            model.addAttribute("workModes", WorkMode.values());
            return "applications/form";
        }
        JobApplication app = jobApplicationService.create(
                companyId, form.getTitle(), form.getJdText(), form.getJdLink(), form.getWorkMode(),
                form.getCompensation(), form.getAppliedDate(), form.isReferral(), form.getSource(),
                form.getStatus(), form.getIdempotencyKey());
        redirectAttributes.addFlashAttribute("success", "Application created");
        return "redirect:/applications/" + app.getId();
    }

    @GetMapping("/applications/{id}")
    public String detail(@PathVariable Long id, Model model) {
        JobApplication app = jobApplicationService.getActive(id);
        model.addAttribute("app", app);
        model.addAttribute("rounds", roundService.listByApplication(id));
        model.addAttribute("notes", noteResourceService.notesForApplication(id));
        model.addAttribute("resources", noteResourceService.resourcesForApplication(id));
        Forms.NoteForm noteForm = new Forms.NoteForm();
        noteForm.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("noteForm", noteForm);
        Forms.ResourceForm resourceForm = new Forms.ResourceForm();
        resourceForm.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("resourceForm", resourceForm);
        return "applications/detail";
    }

    @GetMapping("/applications/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        JobApplication app = jobApplicationService.getActive(id);
        Forms.ApplicationForm form = new Forms.ApplicationForm();
        form.setTitle(app.getTitle());
        form.setJdText(app.getJdText());
        form.setJdLink(app.getJdLink());
        form.setWorkMode(app.getWorkMode());
        form.setCompensation(app.getCompensation());
        form.setAppliedDate(app.getAppliedDate());
        form.setReferral(app.isReferral());
        form.setSource(app.getSource());
        form.setStatus(app.getStatus());
        form.setVersion(app.getVersion());
        model.addAttribute("form", form);
        model.addAttribute("applicationId", id);
        model.addAttribute("companyId", app.getCompany().getId());
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("workModes", WorkMode.values());
        return "applications/form";
    }

    @PostMapping("/applications/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") Forms.ApplicationForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            JobApplication app = jobApplicationService.getActive(id);
            model.addAttribute("applicationId", id);
            model.addAttribute("companyId", app.getCompany().getId());
            model.addAttribute("statuses", ApplicationStatus.values());
            model.addAttribute("workModes", WorkMode.values());
            return "applications/form";
        }
        jobApplicationService.update(id, form.getTitle(), form.getJdText(), form.getJdLink(), form.getWorkMode(),
                form.getCompensation(), form.getAppliedDate(), form.isReferral(), form.getSource(),
                form.getStatus(), form.getVersion());
        redirectAttributes.addFlashAttribute("success", "Application updated");
        return "redirect:/applications/" + id;
    }

    @PostMapping("/applications/{id}/soft-delete")
    public String softDelete(@PathVariable Long id,
                             @RequestParam String idempotencyKey,
                             RedirectAttributes redirectAttributes) {
        JobApplication app = jobApplicationService.getActive(id);
        Long companyId = app.getCompany().getId();
        jobApplicationService.softDelete(id, idempotencyKey);
        redirectAttributes.addFlashAttribute("success", "Application archived");
        return "redirect:/companies/" + companyId;
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String company,
                         @RequestParam(required = false) ApplicationStatus status,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        model.addAttribute("results", jobApplicationService.search(company, status, from, to, page, 20));
        model.addAttribute("company", company);
        model.addAttribute("status", status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("statuses", ApplicationStatus.values());
        return "search";
    }

    @GetMapping("/upcoming")
    public String upcoming(Model model) {
        model.addAttribute("rounds", roundService.upcoming(30));
        return "upcoming";
    }
}
