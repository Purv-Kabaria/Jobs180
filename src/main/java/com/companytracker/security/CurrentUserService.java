package com.companytracker.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserService {

    public TrackerUserDetails requireUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof TrackerUserDetails details)) {
            throw new IllegalStateException("Not authenticated");
        }
        return details;
    }

    public Long requireUserId() {
        return requireUser().getId();
    }

    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.getPrincipal() instanceof TrackerUserDetails details
                && details.isAdmin();
    }
}
