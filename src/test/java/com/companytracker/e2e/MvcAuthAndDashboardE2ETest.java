package com.companytracker.e2e;

import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.UserAccountRepository;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.support.AbstractIntegrationTest;
import com.companytracker.support.TestDataFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MvcAuthAndDashboardE2ETest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void loginPageAndDashboardRequireAuth() throws Exception {
        mockMvc.perform(get("/login")).andExpect(status().isOk()).andExpect(view().name("auth/login"));
        mockMvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
    }

    @Test
    void formLoginSucceedsAndShowsDashboard() throws Exception {
        String email = "mvc-" + UUID.randomUUID().toString().substring(0, 8) + "@e2e.test";
        UserAccount account = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, email, "password123", UserRole.USER);

        mockMvc.perform(formLogin("/login").user(email).password("password123"))
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/dashboard"));

        mockMvc.perform(get("/dashboard").with(user(new TrackerUserDetails(account))))
                .andExpect(status().isOk());
    }

    @Test
    void companyCreateViaMvcRedirects() throws Exception {
        String email = "mvc-co-" + UUID.randomUUID().toString().substring(0, 8) + "@e2e.test";
        UserAccount userAccount = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, email, "password123", UserRole.USER);

        mockMvc.perform(post("/companies")
                        .with(user(new TrackerUserDetails(userAccount)))
                        .with(csrf())
                        .param("name", "MVC Company")
                        .param("idempotencyKey", UUID.randomUUID().toString()))
                .andExpect(status().is3xxRedirection());
    }
}
