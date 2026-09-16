package com.companytracker.service;

import com.companytracker.domain.SoftDeletableEntity;
import com.companytracker.domain.UserAccount;
import com.companytracker.security.CurrentUserService;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.web.error.AppException;
import org.springframework.stereotype.Component;

@Component
public class OwnershipGuard {

    private final CurrentUserService currentUserService;

    public OwnershipGuard(CurrentUserService currentUserService) {
        this.currentUserService = currentUserService;
    }

    public TrackerUserDetails current() {
        return currentUserService.requireUser();
    }

    public void requireActiveOwner(UserAccount owner) {
        TrackerUserDetails user = current();
        if (user.isAdmin()) {
            return;
        }
        if (owner == null || !owner.getId().equals(user.getId())) {
            throw AppException.notFound("Resource not found");
        }
    }

    public void requireActiveOwnerId(Long ownerId) {
        TrackerUserDetails user = current();
        if (user.isAdmin()) {
            return;
        }
        if (ownerId == null || !ownerId.equals(user.getId())) {
            throw AppException.notFound("Resource not found");
        }
    }

    public void requireNotDeleted(SoftDeletableEntity entity) {
        if (entity == null || entity.isDeleted()) {
            throw AppException.notFound("Resource not found");
        }
    }

    public Long effectiveOwnerFilter() {
        TrackerUserDetails user = current();
        return user.isAdmin() ? null : user.getId();
    }
}
