package com.companytracker.performance;

import com.companytracker.domain.RoundOutcome;
import com.companytracker.domain.UserAccount;
import com.companytracker.domain.UserRole;
import com.companytracker.repo.UserAccountRepository;
import com.companytracker.security.TrackerUserDetails;
import com.companytracker.service.CompanyService;
import com.companytracker.service.DashboardService;
import com.companytracker.service.JobApplicationService;
import com.companytracker.service.RoundService;
import com.companytracker.support.AbstractIntegrationTest;
import com.companytracker.support.TestDataFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scalability / speed regression fences (Testcontainers Postgres + Redis).
 * Timings are logged so README / CI can cite measured results.
 */
@Tag("performance")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ScalabilityPerformanceIT extends AbstractIntegrationTest {

    private static final int COMPANY_COUNT = 200;
    private static final int APP_COUNT = 100;
    private static final int ROUND_COUNT = 80;
    private static final int TREE_APPS = 25;
    private static final int CONCURRENT_READERS = 8;
    private static final int CONCURRENT_LIST_CALLS = 40;

    private static final long CREATE_BATCH_BUDGET_MS = 20_000;
    private static final long LIST_BUDGET_MS = 3_000;
    private static final long SEARCH_BUDGET_MS = 3_000;
    private static final long DASHBOARD_COLD_BUDGET_MS = 2_000;
    private static final long DASHBOARD_WARM_BUDGET_MS = 500;
    private static final long UPCOMING_BUDGET_MS = 2_000;
    private static final long IDEMPOTENCY_REPLAY_BUDGET_MS = 500;
    private static final long CASCADE_SOFT_DELETE_BUDGET_MS = 5_000;
    private static final long CONCURRENT_LIST_BUDGET_MS = 8_000;
    private static final long DEEP_PAGE_BUDGET_MS = 3_000;

    @Autowired UserAccountRepository userAccountRepository;
    @Autowired CompanyService companyService;
    @Autowired JobApplicationService jobApplicationService;
    @Autowired RoundService roundService;
    @Autowired DashboardService dashboardService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired CacheManager cacheManager;

    @Test
    void companyListAndSearchScaleUnderBudget() {
        String suffix = unique();
        UserAccount user = user("perf-" + suffix + "@test.com");
        authenticate(user);

        long createMs = timed(() -> {
            for (int i = 0; i < COMPANY_COUNT; i++) {
                companyService.create("Perf Co " + suffix + " " + i, null, "Tech", "Remote", null,
                        "perf-create-" + suffix + "-" + i);
            }
        });
        logMetric("batch_create_companies", COMPANY_COUNT, createMs, CREATE_BATCH_BUDGET_MS);
        assertTrue(createMs < CREATE_BATCH_BUDGET_MS, "create " + createMs + "ms");

        long listMs = timed(() -> {
            Page<?> page = companyService.list(null, 0, 50);
            assertTrue(page.getTotalElements() >= COMPANY_COUNT);
        });
        logMetric("company_list_page50", COMPANY_COUNT, listMs, LIST_BUDGET_MS);
        assertTrue(listMs < LIST_BUDGET_MS, "list " + listMs + "ms");

        long searchMs = timed(() -> {
            Page<?> search = companyService.list("Perf Co " + suffix + " 1", 0, 20);
            assertTrue(search.getTotalElements() >= 1);
        });
        logMetric("company_name_search", COMPANY_COUNT, searchMs, SEARCH_BUDGET_MS);
        assertTrue(searchMs < SEARCH_BUDGET_MS, "search " + searchMs + "ms");

        long deepPageMs = timed(() -> {
            Page<?> deep = companyService.list(null, 3, 50);
            assertTrue(deep.getNumber() == 3 || deep.getTotalElements() >= COMPANY_COUNT);
        });
        logMetric("company_list_deep_page", COMPANY_COUNT, deepPageMs, DEEP_PAGE_BUDGET_MS);
        assertTrue(deepPageMs < DEEP_PAGE_BUDGET_MS, "deep page " + deepPageMs + "ms");

        clearAuth();
    }

    @Test
    void applicationSearchUnderBudgetWithManyRows() {
        String suffix = unique();
        UserAccount user = user("perf-app-" + suffix + "@test.com");
        authenticate(user);

        var company = companyService.create("Search Co " + suffix, null, null, null, null, unique());
        for (int i = 0; i < APP_COUNT; i++) {
            jobApplicationService.create(company.getId(), "Role " + i, null, null, null, null, null,
                    false, "src", null, "perf-app-" + suffix + "-" + i);
        }

        long ms = timed(() -> {
            var page = jobApplicationService.search("Search Co " + suffix, null, null, null, 0, 50);
            assertTrue(page.getTotalElements() >= APP_COUNT);
        });
        logMetric("application_search", APP_COUNT, ms, SEARCH_BUDGET_MS);
        assertTrue(ms < SEARCH_BUDGET_MS, "app search " + ms + "ms");
        clearAuth();
    }

    @Test
    void dashboardCacheColdVsWarm() {
        String suffix = unique();
        UserAccount user = user("perf-dash-" + suffix + "@test.com");
        authenticate(user);

        var company = companyService.create("Dash Co " + suffix, null, null, null, null, unique());
        for (int i = 0; i < 40; i++) {
            jobApplicationService.create(company.getId(), "Role " + i, null, null, null, null, null,
                    false, null, null, "dash-app-" + suffix + "-" + i);
        }

        if (cacheManager.getCache("dashboard") != null) {
            cacheManager.getCache("dashboard").clear();
        }

        long coldMs = timed(() -> {
            Map<String, Long> counts = dashboardService.statusCounts();
            assertTrue(counts.values().stream().mapToLong(Long::longValue).sum() >= 40);
        });
        logMetric("dashboard_status_counts_cold", 40, coldMs, DASHBOARD_COLD_BUDGET_MS);
        assertTrue(coldMs < DASHBOARD_COLD_BUDGET_MS, "dashboard cold " + coldMs + "ms");

        long warmMs = timed(() -> {
            Map<String, Long> counts = dashboardService.statusCounts();
            assertTrue(counts.values().stream().mapToLong(Long::longValue).sum() >= 40);
        });
        logMetric("dashboard_status_counts_warm_redis", 40, warmMs, DASHBOARD_WARM_BUDGET_MS);
        assertTrue(warmMs < DASHBOARD_WARM_BUDGET_MS, "dashboard warm " + warmMs + "ms");
        assertTrue(warmMs <= coldMs + 50,
                "warm cache should not be slower than cold+overhead (cold=" + coldMs + " warm=" + warmMs + ")");

        clearAuth();
    }

    @Test
    void upcomingRoundsQueryUnderBudget() {
        String suffix = unique();
        UserAccount user = user("perf-round-" + suffix + "@test.com");
        authenticate(user);

        var company = companyService.create("Round Co " + suffix, null, null, null, null, unique());
        var app = jobApplicationService.create(company.getId(), "SWE", null, null, null, null, null,
                false, null, null, unique());
        Instant base = Instant.now().plusSeconds(3600);
        for (int i = 0; i < ROUND_COUNT; i++) {
            roundService.create(app.getId(), "R" + i, base.plusSeconds(i * 3600L), RoundOutcome.PENDING,
                    null, null, "round-" + suffix + "-" + i);
        }

        long ms = timed(() -> {
            var upcoming = roundService.upcoming(90);
            assertTrue(upcoming.size() >= ROUND_COUNT);
        });
        logMetric("upcoming_rounds_query", ROUND_COUNT, ms, UPCOMING_BUDGET_MS);
        assertTrue(ms < UPCOMING_BUDGET_MS, "upcoming " + ms + "ms");
        clearAuth();
    }

    @Test
    void idempotencyReplayFasterThanFreshCreate() {
        String suffix = unique();
        UserAccount user = user("perf-idem-" + suffix + "@test.com");
        authenticate(user);

        String key = "idem-perf-" + suffix;
        long firstMs = timed(() -> companyService.create("Idem Perf " + suffix, null, null, null, null, key));

        int iterations = 25;
        long replayMs = timed(() -> {
            for (int i = 0; i < iterations; i++) {
                companyService.create("Idem Perf " + suffix, null, null, null, null, key);
            }
        });
        long freshMs = timed(() -> {
            for (int i = 0; i < iterations; i++) {
                companyService.create("Idem Fresh " + suffix + " " + i, null, null, null, null,
                        "fresh-" + suffix + "-" + i);
            }
        });

        logMetric("idempotency_first_create", 1, firstMs, CREATE_BATCH_BUDGET_MS);
        logMetric("idempotency_replay_x" + iterations, iterations, replayMs, IDEMPOTENCY_REPLAY_BUDGET_MS * iterations);
        logMetric("idempotency_fresh_create_x" + iterations, iterations, freshMs, CREATE_BATCH_BUDGET_MS);
        assertTrue(replayMs < IDEMPOTENCY_REPLAY_BUDGET_MS * iterations,
                "replay batch " + replayMs + "ms");
        assertTrue(replayMs < freshMs,
                "replay batch should beat fresh creates (replay=" + replayMs + " fresh=" + freshMs + ")");
        clearAuth();
    }

    @Test
    void cascadeSoftDeleteTreeUnderBudget() {
        String suffix = unique();
        UserAccount user = user("perf-cascade-" + suffix + "@test.com");
        authenticate(user);

        var company = companyService.create("Cascade Co " + suffix, null, null, null, null, unique());
        for (int i = 0; i < TREE_APPS; i++) {
            var app = jobApplicationService.create(company.getId(), "Role " + i, null, null, null, null, null,
                    false, null, null, "tree-app-" + suffix + "-" + i);
            roundService.create(app.getId(), "Phone", Instant.now().plusSeconds(86400), RoundOutcome.PENDING,
                    null, null, "tree-round-" + suffix + "-" + i);
        }

        long ms = timed(() -> companyService.softDelete(company.getId(), unique()));
        logMetric("cascade_soft_delete_company_tree", TREE_APPS, ms, CASCADE_SOFT_DELETE_BUDGET_MS);
        assertTrue(ms < CASCADE_SOFT_DELETE_BUDGET_MS, "cascade soft-delete " + ms + "ms");
        clearAuth();
    }

    @Test
    void concurrentCompanyListThroughput() throws Exception {
        String suffix = unique();
        UserAccount user = user("perf-conc-" + suffix + "@test.com");
        authenticate(user);

        for (int i = 0; i < 100; i++) {
            companyService.create("Conc Co " + suffix + " " + i, null, null, null, null,
                    "conc-create-" + suffix + "-" + i);
        }

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_READERS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicLong errors = new AtomicLong();
        List<Future<?>> futures = new ArrayList<>();

        long wallMs = timed(() -> {
            for (int i = 0; i < CONCURRENT_LIST_CALLS; i++) {
                futures.add(pool.submit(() -> {
                    try {
                        start.await(10, TimeUnit.SECONDS);
                        authenticate(user);
                        Page<?> page = companyService.list(null, 0, 20);
                        if (page.getTotalElements() < 100) {
                            errors.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        clearAuth();
                    }
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    errors.incrementAndGet();
                }
            }
        });

        pool.shutdownNow();
        logMetric("concurrent_company_list_" + CONCURRENT_READERS + "x" + CONCURRENT_LIST_CALLS,
                CONCURRENT_LIST_CALLS, wallMs, CONCURRENT_LIST_BUDGET_MS);
        assertEquals(0, errors.get(), "concurrent list errors");
        assertTrue(wallMs < CONCURRENT_LIST_BUDGET_MS, "concurrent list wall " + wallMs + "ms");
        clearAuth();
    }

    @Test
    void pageSizeClampKeepsResponseBounded() {
        String suffix = unique();
        UserAccount user = user("perf-clamp-" + suffix + "@test.com");
        authenticate(user);
        for (int i = 0; i < 30; i++) {
            companyService.create("Clamp Co " + suffix + " " + i, null, null, null, null, unique());
        }

        long ms = timed(() -> {
            Page<?> huge = companyService.list(null, 0, 10_000);
            assertTrue(huge.getSize() <= 100, "page size must clamp to <=100, was " + huge.getSize());
        });
        logMetric("company_list_page_size_clamp", 30, ms, LIST_BUDGET_MS);
        assertTrue(ms < LIST_BUDGET_MS);
        clearAuth();
    }

    private UserAccount user(String email) {
        return TestDataFactory.persistUser(
                userAccountRepository, passwordEncoder, email, "password123", UserRole.USER);
    }

    private void authenticate(UserAccount user) {
        TrackerUserDetails details = new TrackerUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    private void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    private static String unique() {
        return UUID.randomUUID().toString();
    }

    private static long timed(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static void logMetric(String name, int volume, long ms, long budgetMs) {
        System.out.printf("PERF_METRIC name=%s volume=%d ms=%d budget_ms=%d%n", name, volume, ms, budgetMs);
    }
}
