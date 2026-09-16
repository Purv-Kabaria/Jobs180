package com.companytracker.web.mvc;

import com.companytracker.domain.Round;
import com.companytracker.domain.RoundOutcome;
import com.companytracker.service.JobApplicationService;
import com.companytracker.service.NoteResourceService;
import com.companytracker.service.ResumeService;
import com.companytracker.service.RoundService;
import com.companytracker.web.dto.Forms;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Controller
public class RoundMvcController {

    private final RoundService roundService;
    private final JobApplicationService jobApplicationService;
    private final ResumeService resumeService;
    private final NoteResourceService noteResourceService;

    public RoundMvcController(
            RoundService roundService,
            JobApplicationService jobApplicationService,
            ResumeService resumeService,
            NoteResourceService noteResourceService) {
        this.roundService = roundService;
        this.jobApplicationService = jobApplicationService;
        this.resumeService = resumeService;
        this.noteResourceService = noteResourceService;
    }

    @GetMapping("/applications/{applicationId}/rounds/new")
    public String createForm(@PathVariable Long applicationId, Model model) {
        var app = jobApplicationService.getActive(applicationId);
        Forms.RoundForm form = new Forms.RoundForm();
        form.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("form", form);
        model.addAttribute("applicationId", applicationId);
        model.addAttribute("outcomes", RoundOutcome.values());
        model.addAttribute("resumes", resumeService.listMineOrAdmin(app.getOwnerId()));
        return "rounds/form";
    }

    @PostMapping("/applications/{applicationId}/rounds")
    public String create(@PathVariable Long applicationId,
                         @Valid @ModelAttribute("form") Forms.RoundForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            var app = jobApplicationService.getActive(applicationId);
            model.addAttribute("applicationId", applicationId);
            model.addAttribute("outcomes", RoundOutcome.values());
            model.addAttribute("resumes", resumeService.listMineOrAdmin(app.getOwnerId()));
            return "rounds/form";
        }
        Round round = roundService.create(
                applicationId, form.getName(), parseInstant(form.getScheduledAt()),
                form.getOutcome(), form.getInterviewers(), form.getResumeId(), form.getIdempotencyKey());
        redirectAttributes.addFlashAttribute("success", "Round created");
        return "redirect:/rounds/" + round.getId();
    }

    @GetMapping("/rounds/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Round round = roundService.getActive(id);
        model.addAttribute("round", round);
        model.addAttribute("notes", noteResourceService.notesForRound(id));
        model.addAttribute("resources", noteResourceService.resourcesForRound(id));
        Forms.NoteForm noteForm = new Forms.NoteForm();
        noteForm.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("noteForm", noteForm);
        Forms.ResourceForm resourceForm = new Forms.ResourceForm();
        resourceForm.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("resourceForm", resourceForm);
        return "rounds/detail";
    }

    @GetMapping("/rounds/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Round round = roundService.getActive(id);
        Forms.RoundForm form = new Forms.RoundForm();
        form.setName(round.getName());
        if (round.getScheduledAt() != null) {
            form.setScheduledAt(LocalDateTime.ofInstant(round.getScheduledAt(), ZoneOffset.UTC).toString());
        }
        form.setOutcome(round.getOutcome());
        form.setInterviewers(round.getInterviewers());
        form.setResumeId(round.getResume() != null ? round.getResume().getId() : null);
        form.setVersion(round.getVersion());
        model.addAttribute("form", form);
        model.addAttribute("roundId", id);
        model.addAttribute("applicationId", round.getApplication().getId());
        model.addAttribute("outcomes", RoundOutcome.values());
        model.addAttribute("resumes", resumeService.listMineOrAdmin(round.getOwnerId()));
        return "rounds/form";
    }

    @PostMapping("/rounds/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") Forms.RoundForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            Round round = roundService.getActive(id);
            model.addAttribute("roundId", id);
            model.addAttribute("applicationId", round.getApplication().getId());
            model.addAttribute("outcomes", RoundOutcome.values());
            model.addAttribute("resumes", resumeService.listMineOrAdmin(round.getOwnerId()));
            return "rounds/form";
        }
        roundService.update(id, form.getName(), parseInstant(form.getScheduledAt()),
                form.getOutcome(), form.getInterviewers(), form.getResumeId(), form.getVersion());
        redirectAttributes.addFlashAttribute("success", "Round updated");
        return "redirect:/rounds/" + id;
    }

    @PostMapping("/rounds/{id}/soft-delete")
    public String softDelete(@PathVariable Long id,
                             @RequestParam String idempotencyKey,
                             RedirectAttributes redirectAttributes) {
        Round round = roundService.getActive(id);
        Long appId = round.getApplication().getId();
        roundService.softDelete(id, idempotencyKey);
        redirectAttributes.addFlashAttribute("success", "Round archived");
        return "redirect:/applications/" + appId;
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (Exception ex) {
            try {
                return Instant.parse(value);
            } catch (Exception ex2) {
                return null;
            }
        }
    }
}
