package com.companytracker.security;

import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class TrackerUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final UserRole role;
    private final boolean enabled;
    private final UUID securityStamp;

    public TrackerUserDetails(UserAccount user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.enabled = user.isEnabled() && !user.isDeleted();
        this.securityStamp = user.getSecurityStamp();
    }

    public Long getId() {
        return id;
    }

    public UserRole getRole() {
        return role;
    }

    public UUID getSecurityStamp() {
        return securityStamp;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
