package com.companytracker.web.mvc;

import com.companytracker.domain.Resume;
import com.companytracker.service.NoteResourceService;
import com.companytracker.service.ResumeService;
import com.companytracker.web.dto.Forms;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StreamUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.InputStream;
import java.util.UUID;

@Controller
public class ResumeNoteMvcController {

    private final ResumeService resumeService;
    private final NoteResourceService noteResourceService;

    public ResumeNoteMvcController(ResumeService resumeService, NoteResourceService noteResourceService) {
        this.resumeService = resumeService;
        this.noteResourceService = noteResourceService;
    }

    @GetMapping("/resumes")
    public String list(Model model) {
        model.addAttribute("resumes", resumeService.listMineOrAdmin(null));
        Forms.ResumeForm form = new Forms.ResumeForm();
        form.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("form", form);
        return "resumes/list";
    }

    @PostMapping("/resumes")
    public String create(@Valid @ModelAttribute("form") Forms.ResumeForm form,
                         BindingResult bindingResult,
                         @RequestPart(value = "file", required = false) MultipartFile file,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("resumes", resumeService.listMineOrAdmin(null));
            return "resumes/list";
        }
        resumeService.create(form.getLabel(), form.getExternalUrl(), file, form.getIdempotencyKey());
        redirectAttributes.addFlashAttribute("success", "Resume added");
        return "redirect:/resumes";
    }

    @GetMapping("/resumes/{id}/content")
    public void download(@PathVariable Long id, HttpServletResponse response) throws Exception {
        Resume resume = resumeService.getActive(id);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + (resume.getOriginalFilename() != null ? resume.getOriginalFilename() : "resume") + "\"");
        if (resume.getContentType() != null) {
            response.setContentType(resume.getContentType());
        }
        try (InputStream in = resumeService.openContent(id)) {
            StreamUtils.copy(in, response.getOutputStream());
        }
    }

    @PostMapping("/resumes/{id}/soft-delete")
    public String softDelete(@PathVariable Long id,
                             @RequestParam String idempotencyKey,
                             RedirectAttributes redirectAttributes) {
        resumeService.softDelete(id, idempotencyKey);
        redirectAttributes.addFlashAttribute("success", "Resume archived");
        return "redirect:/resumes";
    }

    @PostMapping("/companies/{id}/notes")
    public String addCompanyNote(@PathVariable Long id,
                                 @Valid @ModelAttribute("noteForm") Forms.NoteForm form,
                                 RedirectAttributes redirectAttributes) {
        noteResourceService.addNote("companies", id, form.getTitle(), form.getBody(), form.getIdempotencyKey());
        redirectAttributes.addFlashAttribute("success", "Note added");
        return "redirect:/companies/" + id;
    }

    @PostMapping("/applications/{id}/notes")
    public String addAppNote(@PathVariable Long id,
                             @Valid @ModelAttribute("noteForm") Forms.NoteForm form,
                             RedirectAttributes redirectAttributes) {
        noteResourceService.addNote("applications", id, form.getTitle(), form.getBody(), form.getIdempotencyKey());
        redirectAttributes.addFlashAttribute("success", "Note added");
        return "redirect:/applications/" + id;
    }

    @PostMapping("/rounds/{id}/notes")
    public String addRoundNote(@PathVariable Long id,
                               @Valid @ModelAttribute("noteForm") Forms.NoteForm form,
                               RedirectAttributes redirectAttributes) {
        noteResourceService.addNote("rounds", id, form.getTitle(), form.getBody(), form.getIdempotencyKey());
        redirectAttributes.addFlashAttribute("success", "Note added");
        return "redirect:/rounds/" + id;
    }

    @PostMapping("/companies/{id}/resources")
    public String addCompanyResource(@PathVariable Long id,
                                     @Valid @ModelAttribute("resourceForm") Forms.ResourceForm form,
                                     @RequestPart(value = "file", required = false) MultipartFile file,
                                     RedirectAttributes redirectAttributes) {
        noteResourceService.addResource("companies", id, form.getTitle(), form.getUrl(), file, form.getIdempotencyKey());
        redirectAttributes.addFlashAttribute("success", "Resource added");
        return "redirect:/companies/" + id;
    }

    @PostMapping("/applications/{id}/resources")
    public String addAppResource(@PathVariable Long id,
                                 @Valid @ModelAttribute("resourceForm") Forms.ResourceForm form,
                                 @RequestPart(value = "file", required = false) MultipartFile file,
                                 RedirectAttributes redirectAttributes) {
        noteResourceService.addResource("applications", id, form.getTitle(), form.getUrl(), file, form.getIdempotencyKey());
        redirectAttributes.addFlashAttribute("success", "Resource added");
        return "redirect:/applications/" + id;
    }

    @PostMapping("/rounds/{id}/resources")
    public String addRoundResource(@PathVariable Long id,
                                   @Valid @ModelAttribute("resourceForm") Forms.ResourceForm form,
                                   @RequestPart(value = "file", required = false) MultipartFile file,
                                   RedirectAttributes redirectAttributes) {
        noteResourceService.addResource("rounds", id, form.getTitle(), form.getUrl(), file, form.getIdempotencyKey());
        redirectAttributes.addFlashAttribute("success", "Resource added");
        return "redirect:/rounds/" + id;
    }
}
