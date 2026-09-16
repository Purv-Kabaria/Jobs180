package com.companytracker.web.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public Object handleApp(AppException ex, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (wantsJson(request)) {
            return ResponseEntity.status(ex.getStatus())
                    .body(Map.of("code", ex.getCode(), "message", ex.getMessage()));
        }
        if ("POST".equalsIgnoreCase(request.getMethod()) || "PATCH".equalsIgnoreCase(request.getMethod())
                || "DELETE".equalsIgnoreCase(request.getMethod())) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            String referer = request.getHeader("Referer");
            return "redirect:" + (referer != null ? referer.replaceAll("^https?://[^/]+", "") : "/");
        }
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatus.valueOf(ex.getStatus()));
        mav.addObject("code", ex.getCode());
        mav.addObject("message", ex.getMessage());
        return mav;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.put(error.getField(), error.getDefaultMessage());
        }
        if (wantsJson(request)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "VALIDATION_ERROR",
                    "message", "Validation failed",
                    "fields", fields
            ));
        }
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatus.BAD_REQUEST);
        mav.addObject("code", "VALIDATION_ERROR");
        mav.addObject("message", "Validation failed");
        return mav;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public Object handleOptimistic(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        if (wantsJson(request)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "code", "OPTIMISTIC_LOCK",
                    "message", "Resource was modified elsewhere — reload and retry"
            ));
        }
        ModelAndView mav = new ModelAndView("error");
        mav.setStatus(HttpStatus.CONFLICT);
        mav.addObject("code", "OPTIMISTIC_LOCK");
        mav.addObject("message", "Resource was modified elsewhere — reload and retry");
        return mav;
    }

    private boolean wantsJson(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/api/")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE);
    }
}
