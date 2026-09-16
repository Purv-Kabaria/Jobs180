package com.companytracker.web.mvc;

import com.companytracker.domain.UserRole;
import com.companytracker.service.AdminUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/admin")
public class AdminMvcController {

    private final AdminUserService adminUserService;

    public AdminMvcController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", adminUserService.listActive());
        model.addAttribute("deletedUsers", adminUserService.listDeleted());
        return "admin/users";
    }

    @PostMapping("/users/{id}/role")
    public String role(@PathVariable Long id, @RequestParam UserRole role, RedirectAttributes ra) {
        adminUserService.changeRole(id, role, UUID.randomUUID().toString());
        ra.addFlashAttribute("success", "Role updated");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/enable")
    public String enable(@PathVariable Long id, RedirectAttributes ra) {
        adminUserService.setEnabled(id, true, UUID.randomUUID().toString());
        ra.addFlashAttribute("success", "User enabled");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/disable")
    public String disable(@PathVariable Long id, RedirectAttributes ra) {
        adminUserService.setEnabled(id, false, UUID.randomUUID().toString());
        ra.addFlashAttribute("success", "User disabled");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/soft-delete")
    public String softDelete(@PathVariable Long id, RedirectAttributes ra) {
        adminUserService.softDelete(id, UUID.randomUUID().toString());
        ra.addFlashAttribute("success", "User archived");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/restore")
    public String restore(@PathVariable Long id, RedirectAttributes ra) {
        adminUserService.restore(id, UUID.randomUUID().toString());
        ra.addFlashAttribute("success", "User restored");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/hard-delete")
    public String hardDelete(@PathVariable Long id, RedirectAttributes ra) {
        adminUserService.hardDelete(id, UUID.randomUUID().toString());
        ra.addFlashAttribute("success", "User permanently deleted");
        return "redirect:/admin/users";
    }
}
