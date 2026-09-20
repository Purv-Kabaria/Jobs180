package com.companytracker.performance;

import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.CompanyRepository;
import com.companytracker.repo.UserAccountRepository;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.service.CompanyService;
import com.companytracker.service.JobApplicationService;
import com.companytracker.support.AbstractIntegrationTest;
import com.companytracker.support.TestDataFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("performance")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScalabilityPerformanceIT extends AbstractIntegrationTest {

    private static final int COMPANY_COUNT = 200;
    private static final long LIST_BUDGET_MS = 3000;
    private static final long SEARCH_BUDGET_MS = 3000;
    private static final long CREATE_BATCH_BUDGET_MS = 20000;

    @Autowired UserAccountRepository userAccountRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired CompanyService companyService;
    @Autowired JobApplicationService jobApplicationService;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void companyListAndSearchScaleUnderBudget() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount user = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder,
                "perf-" + suffix + "@test.com", "password123", UserRole.USER);
        authenticate(user);

        long createStart = System.nanoTime();
        for (int i = 0; i < COMPANY_COUNT; i++) {
            companyService.create("Perf Co " + suffix + " " + i, null, "Tech", "Remote", null,
                    "perf-create-" + suffix + "-" + i);
        }
        long createMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - createStart);
        assertTrue(createMs < CREATE_BATCH_BUDGET_MS,
                "Creating " + COMPANY_COUNT + " companies took " + createMs + "ms (budget " + CREATE_BATCH_BUDGET_MS + "ms)");

        long listStart = System.nanoTime();
        Page<?> page = companyService.list(null, 0, 50);
        long listMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - listStart);
        assertTrue(page.getTotalElements() >= COMPANY_COUNT);
        assertTrue(listMs < LIST_BUDGET_MS,
                "Listing companies took " + listMs + "ms (budget " + LIST_BUDGET_MS + "ms)");

        long searchStart = System.nanoTime();
        Page<?> search = companyService.list("Perf Co " + suffix + " 1", 0, 20);
        long searchMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - searchStart);
        assertTrue(search.getTotalElements() >= 1);
        assertTrue(searchMs < SEARCH_BUDGET_MS,
                "Searching companies took " + searchMs + "ms (budget " + SEARCH_BUDGET_MS + "ms)");

        SecurityContextHolder.clearContext();
    }

    @Test
    void applicationSearchUnderBudgetWithManyRows() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserAccount user = TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder,
                "perf-app-" + suffix + "@test.com", "password123", UserRole.USER);
        authenticate(user);

        var company = companyService.create("Search Co " + suffix, null, null, null, null,
                UUID.randomUUID().toString());
        for (int i = 0; i < 100; i++) {
            jobApplicationService.create(company.getId(), "Role " + i, null, null, null, null, null,
                    false, "src", null, "perf-app-" + suffix + "-" + i);
        }

        long start = System.nanoTime();
        var page = jobApplicationService.search("Search Co " + suffix, null, null, null, 0, 50);
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(page.getTotalElements() >= 100);
        assertTrue(ms < SEARCH_BUDGET_MS, "Application search took " + ms + "ms");

        SecurityContextHolder.clearContext();
    }

    private void authenticate(UserAccount user) {
        TrackerUserDetails details = new TrackerUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }
}
