package com.companytracker.security;

import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.UserAccountRepository;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityStampFilterTest {

    @Mock UserAccountRepository userAccountRepository;
    @Mock FilterChain filterChain;

    @Test
    void continuesWhenStampMatches() throws Exception {
        UserAccount user = user(1L, true, false);
        TrackerUserDetails details = new TrackerUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(user));

        SecurityStampFilter filter = new SecurityStampFilter(userAccountRepository);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/dashboard");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, filterChain);
        verify(filterChain).doFilter(req, res);
        SecurityContextHolder.clearContext();
    }

    @Test
    void revokesApiWhenDisabled() throws Exception {
        UserAccount dbUser = user(1L, false, false);
        UserAccount sessionUser = user(1L, true, false);
        sessionUser.setSecurityStamp(dbUser.getSecurityStamp());
        TrackerUserDetails details = new TrackerUserDetails(sessionUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(dbUser));

        SecurityStampFilter filter = new SecurityStampFilter(userAccountRepository);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/me");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, filterChain);

        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("SESSION_REVOKED"));
        verify(filterChain, never()).doFilter(req, res);
        SecurityContextHolder.clearContext();
    }

    @Test
    void redirectsMvcWhenStampChanged() throws Exception {
        UserAccount dbUser = user(1L, true, false);
        UserAccount sessionUser = user(1L, true, false);
        sessionUser.setSecurityStamp(UUID.randomUUID());
        TrackerUserDetails details = new TrackerUserDetails(sessionUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        when(userAccountRepository.findById(1L)).thenReturn(Optional.of(dbUser));

        SecurityStampFilter filter = new SecurityStampFilter(userAccountRepository);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/dashboard");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, filterChain);

        assertEquals(302, res.getStatus());
        assertTrue(res.getRedirectedUrl().contains("session_revoked"));
        SecurityContextHolder.clearContext();
    }

    private UserAccount user(Long id, boolean enabled, boolean deleted) {
        UserAccount u = new UserAccount();
        u.setEmail("u@t.com");
        u.setPasswordHash("h");
        u.setRole(UserRole.USER);
        u.setEnabled(enabled);
        u.setSecurityStamp(UUID.randomUUID());
        if (deleted) {
            u.softDelete(UUID.randomUUID());
        }
        try {
            var f = UserAccount.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(u, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return u;
    }
}
