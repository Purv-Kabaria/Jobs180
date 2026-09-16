package com.companytracker.web.mvc;

import com.companytracker.config.AppProperties;
import com.companytracker.service.AuthService;
import com.companytracker.web.dto.Forms;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
public class AuthMvcController {

    private final AuthService authService;
    private final AppProperties appProperties;

    public AuthMvcController(AuthService authService, AppProperties appProperties) {
        this.authService = authService;
        this.appProperties = appProperties;
    }

    @GetMapping("/login")
    public String login(@RequestParam(value = "reason", required = false) String reason,
                        @RequestParam(value = "error", required = false) String error,
                        Model model) {
        if ("session_revoked".equals(reason)) {
            model.addAttribute("warning", "Your account was updated. Please sign in again.");
        }
        if ("rate_limited".equals(error)) {
            model.addAttribute("error", "Too many requests. Try again later.");
        }
        return "auth/login";
    }

    @GetMapping("/signup")
    public String signupForm(Model model) {
        Forms.SignupForm form = new Forms.SignupForm();
        form.setIdempotencyKey(UUID.randomUUID().toString());
        model.addAttribute("form", form);
        model.addAttribute("signupsEnabled", appProperties.signupsEnabled());
        return "auth/signup";
    }

    @PostMapping("/signup")
    public String signup(@Valid @ModelAttribute("form") Forms.SignupForm form,
                         BindingResult bindingResult,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes,
                         Model model) {
        if (!appProperties.signupsEnabled()) {
            model.addAttribute("error", "Signups are disabled");
            return "auth/signup";
        }
        if (bindingResult.hasErrors()) {
            return "auth/signup";
        }
        try {
            authService.signup(form.getEmail(), form.getPassword(), form.getIdempotencyKey(), request.getRemoteAddr());
            redirectAttributes.addFlashAttribute("success", "Account created. Please sign in.");
            return "redirect:/login";
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            return "auth/signup";
        }
    }
}
