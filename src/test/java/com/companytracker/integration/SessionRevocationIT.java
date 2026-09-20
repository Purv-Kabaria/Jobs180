package com.companytracker.integration;

import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.UserAccountRepository;
import com.companytracker.security.SecurityStampFilter;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.service.AuthService;
import com.companytracker.support.AbstractIntegrationTest;
import com.companytracker.support.TestDataFactory;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SessionRevocationIT extends AbstractIntegrationTest {

    @Autowired UserAccountRepository userAccountRepository;
    @Autowired AuthService authService;
    @Autowired SecurityStampFilter securityStampFilter;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void rotatingStampInvalidatesExistingSessionPrincipal() throws Exception {
        UserAccount user = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder,
                "stamp-" + UUID.randomUUID().toString().substring(0, 8) + "@it.com",
                "password123", UserRole.USER);
        TrackerUserDetails sessionDetails = new TrackerUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(sessionDetails, null, sessionDetails.getAuthorities()));

        authService.rotateStampAndRevoke(userAccountRepository.findById(user.getId()).orElseThrow());

        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/me");
        MockHttpServletResponse res = new MockHttpServletResponse();
        securityStampFilter.doFilter(req, res, chain);

        assertEquals(401, res.getStatus());
        verify(chain, never()).doFilter(req, res);
        SecurityContextHolder.clearContext();
    }
}
