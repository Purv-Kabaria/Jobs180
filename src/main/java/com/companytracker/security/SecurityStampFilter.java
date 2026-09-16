package com.companytracker.security;

import com.companytracker.repo.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class SecurityStampFilter extends OncePerRequestFilter {

    private final UserAccountRepository userAccountRepository;

    public SecurityStampFilter(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TrackerUserDetails details) {
            var userOpt = userAccountRepository.findById(details.getId());
            boolean invalid = userOpt.isEmpty()
                    || userOpt.get().isDeleted()
                    || !userOpt.get().isEnabled()
                    || !userOpt.get().getSecurityStamp().equals(details.getSecurityStamp());
            if (invalid) {
                new SecurityContextLogoutHandler().logout(request, response, auth);
                SecurityContextHolder.clearContext();
                if (request.getRequestURI().startsWith("/api/")) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\":\"SESSION_REVOKED\",\"message\":\"Please sign in again\"}");
                    return;
                }
                response.sendRedirect("/login?reason=session_revoked");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
