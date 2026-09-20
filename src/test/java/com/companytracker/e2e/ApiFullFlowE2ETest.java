package com.companytracker.e2e;

import com.companytracker.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApiFullFlowE2ETest extends AbstractIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void signupLoginCompanyApplicationRoundNotesIsolationAndAdminGate() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "admin-" + suffix + "@e2e.test";
        String userEmail = "user-" + suffix + "@e2e.test";

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","idempotencyKey":"%s"}
                                """.formatted(adminEmail, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","idempotencyKey":"%s"}
                                """.formatted(userEmail, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("USER"));

        Cookie[] userCookies = login(userEmail, "password123");
        Cookie[] adminCookies = login(adminEmail, "password123");

        MvcResult companyResult = mockMvc.perform(post("/api/v1/companies")
                        .with(sessionCookies(userCookies))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Acme %s","website":"https://acme.example","industry":"Tech"}
                                """.formatted(suffix)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(containsString("Acme")))
                .andReturn();
        long companyId = objectMapper.readTree(companyResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult appResult = mockMvc.perform(post("/api/v1/companies/" + companyId + "/applications")
                        .with(sessionCookies(userCookies))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Backend Engineer","workMode":"REMOTE","status":"APPLIED"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Backend Engineer"))
                .andReturn();
        long appId = objectMapper.readTree(appResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult roundResult = mockMvc.perform(post("/api/v1/applications/" + appId + "/rounds")
                        .with(sessionCookies(userCookies))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Phone screen","outcome":"PENDING"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long roundId = objectMapper.readTree(roundResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/companies/" + companyId + "/notes")
                        .with(sessionCookies(userCookies))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Culture","body":"Values engineering blog"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/resumes")
                        .with(sessionCookies(userCookies))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"General CV","externalUrl":"https://example.com/cv.pdf"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("General CV"));

        mockMvc.perform(get("/api/v1/companies/" + companyId).with(sessionCookies(userCookies)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(companyId));

        mockMvc.perform(get("/api/v1/rounds/" + roundId).with(sessionCookies(userCookies)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Phone screen"));

        String otherEmail = "other-" + suffix + "@e2e.test";
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","idempotencyKey":"%s"}
                                """.formatted(otherEmail, UUID.randomUUID())))
                .andExpect(status().isCreated());
        Cookie[] otherCookies = login(otherEmail, "password123");

        mockMvc.perform(get("/api/v1/companies/" + companyId).with(sessionCookies(otherCookies)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/companies/" + companyId).with(sessionCookies(adminCookies)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/users").with(sessionCookies(userCookies)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/users").with(sessionCookies(adminCookies)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/companies/" + companyId + "/soft-delete")
                        .with(sessionCookies(userCookies))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/companies/" + companyId).with(sessionCookies(userCookies)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/companies/" + companyId)
                        .with(sessionCookies(userCookies))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedApiRejectedAndCsrfRequired() throws Exception {
        mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "csrf-" + suffix + "@e2e.test";
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","idempotencyKey":"%s"}
                                """.formatted(email, UUID.randomUUID())))
                .andExpect(status().isCreated());
        Cookie[] cookies = login(email, "password123");

        mockMvc.perform(post("/api/v1/companies")
                        .with(sessionCookies(cookies))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"No CSRF\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void optimisticLockViaApi() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "opt-" + suffix + "@e2e.test";
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","idempotencyKey":"%s"}
                                """.formatted(email, UUID.randomUUID())))
                .andExpect(status().isCreated());
        Cookie[] cookies = login(email, "password123");

        MvcResult created = mockMvc.perform(post("/api/v1/companies")
                        .with(sessionCookies(cookies))
                        .with(csrf())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Opt Co\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(created.getResponse().getContentAsString());
        long id = node.get("id").asLong();
        long version = node.get("version").asLong();

        mockMvc.perform(patch("/api/v1/companies/" + id)
                        .with(sessionCookies(cookies))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Opt Co 2\",\"version\":" + version + "}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/companies/" + id)
                        .with(sessionCookies(cookies))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Opt Co stale\",\"version\":" + version + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK"));
    }

    @Test
    void idempotentCompanyCreateViaApi() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "idem-api-" + suffix + "@e2e.test";
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","idempotencyKey":"%s"}
                                """.formatted(email, UUID.randomUUID())))
                .andExpect(status().isCreated());
        Cookie[] cookies = login(email, "password123");
        String idem = "company-idem-" + suffix;

        MvcResult first = mockMvc.perform(post("/api/v1/companies")
                        .with(sessionCookies(cookies))
                        .with(csrf())
                        .header("Idempotency-Key", idem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Idem API Co\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();

        MvcResult second = mockMvc.perform(post("/api/v1/companies")
                        .with(sessionCookies(cookies))
                        .with(csrf())
                        .header("Idempotency-Key", idem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Idem API Co\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asLong();
        assertEquals(firstId, secondId);

        mockMvc.perform(post("/api/v1/companies")
                        .with(sessionCookies(cookies))
                        .with(csrf())
                        .header("Idempotency-Key", idem)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Different Name\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    private Cookie[] login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "Redis session cookie missing after login");
        Cookie xsrf = result.getResponse().getCookie("XSRF-TOKEN");
        return xsrf == null ? new Cookie[]{session} : new Cookie[]{session, xsrf};
    }

    private RequestPostProcessor sessionCookies(Cookie[] cookies) {
        return request -> {
            for (Cookie cookie : cookies) {
                request.setCookies(cookie);
            }
            // setCookies replaces — set all at once
            request.setCookies(cookies);
            return request;
        };
    }
}
