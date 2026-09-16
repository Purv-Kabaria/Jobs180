package com.companytracker.web.mvc;

import com.companytracker.domain.Company;
import com.companytracker.service.CompanyService;
import com.companytracker.service.JobApplicationService;
import com.companytracker.service.NoteResourceService;
import com.companytracker.web.dto.Forms;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/companies")
public class CompanyMvcController {

    private final CompanyService companyService;
    private final JobApplicationService jobApplicationService;
    private final NoteResourceService noteResourceService;

    public CompanyMvcController(
            CompanyService companyService,
            JobApplicationService jobApplicationService,
            NoteResourceService noteResourceService) {
        this.companyService = companyService;
        this.jobApplicationService = jobApplicationService;
        this.noteResourceService = noteResourceService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       Model model) {
        Page<Company> companies = companyService.list(q, page, size);
        model.addAttribute("companies", companies);
        model.addAttribute("q", q);
        return "companies/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        Forms.CompanyForm form = new Forms.CompanyForm();
        form.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("form", form);
        return "companies/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") Forms.CompanyForm form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "companies/form";
        }
        Company company = companyService.create(
                form.getName(), form.getWebsite(), form.getIndustry(),
                form.getLocation(), form.getCareerPageUrl(), form.getIdempotencyKey());
        redirectAttributes.addFlashAttribute("success", "Company created");
        return "redirect:/companies/" + company.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Company company = companyService.getActive(id);
        model.addAttribute("company", company);
        model.addAttribute("applications", jobApplicationService.listByCompany(id));
        model.addAttribute("notes", noteResourceService.notesForCompany(id));
        model.addAttribute("resources", noteResourceService.resourcesForCompany(id));
        Forms.NoteForm noteForm = new Forms.NoteForm();
        noteForm.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("noteForm", noteForm);
        Forms.ResourceForm resourceForm = new Forms.ResourceForm();
        resourceForm.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("resourceForm", resourceForm);
        return "companies/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Company company = companyService.getActive(id);
        Forms.CompanyForm form = new Forms.CompanyForm();
        form.setName(company.getName());
        form.setWebsite(company.getWebsite());
        form.setIndustry(company.getIndustry());
        form.setLocation(company.getLocation());
        form.setCareerPageUrl(company.getCareerPageUrl());
        form.setVersion(company.getVersion());
        model.addAttribute("form", form);
        model.addAttribute("companyId", id);
        return "companies/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute("form") Forms.CompanyForm form,
                         BindingResult bindingResult,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("companyId", id);
            return "companies/form";
        }
        companyService.update(id, form.getName(), form.getWebsite(), form.getIndustry(),
                form.getLocation(), form.getCareerPageUrl(), form.getVersion());
        redirectAttributes.addFlashAttribute("success", "Company updated");
        return "redirect:/companies/" + id;
    }

    @PostMapping("/{id}/soft-delete")
    public String softDelete(@PathVariable Long id,
                             @RequestParam String idempotencyKey,
                             RedirectAttributes redirectAttributes) {
        companyService.softDelete(id, idempotencyKey);
        redirectAttributes.addFlashAttribute("success", "Company archived");
        return "redirect:/companies";
    }
}
