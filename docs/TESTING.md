# Jobs180 — Testing Guide

How the test suite is organized, what it proves, and how to run it—including performance budgets.

---

## 1. Why this shape of suite?

| Layer | What it catches | Cost |
|-------|-----------------|------|
| **Unit** (Mockito) | Validation, ownership 404, sole-admin, optimistic lock, filter reject paths | Fast, no Docker |
| **Integration** (Testcontainers Postgres + Redis) | Flyway schema, cascades, idempotency against real Redis, session stamp revocation | Medium |
| **E2E / MockMvc** | CSRF, auth entry points, full API flows, MVC form login, admin gates, cross-user isolation | Higher |
| **Performance** (`@Tag("performance")`) | List/search latency under hundreds of rows | Highest wall time |

**Not chosen:** H2-only IT (misses Postgres SQL + Flyway quirks), WireMock-only API (misses security filter chain), browser Selenium for v1 (MockMvc covers the same request pipeline cheaper).

---

## 2. Prerequisites

- **Docker Desktop** (or equivalent) — Testcontainers starts `postgres:16-alpine` and `redis:7-alpine` once per JVM.
- **JDK 21** + Maven 3.9+, **or** the Maven Docker image below.

Shared container wiring lives in `TestContainersSupport` + `application-test.yml` (`app.storage-type=local`, high rate limits so tests do not self-throttle).

---

## 3. How to run

```bash
# Full suite (unit + IT + e2e + performance)
docker run --rm -v "${PWD}:/app" -w /app --network host ^
  -v /var/run/docker.sock:/var/run/docker.sock ^
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal ^
  maven:3.9.9-eclipse-temurin-21-alpine mvn test

# Or locally with JDK 21
mvn test
```

On Windows with Docker Desktop, prefer running `mvn test` on the host JDK if available; Testcontainers needs access to the Docker engine. If you run Maven *inside* a container, mount the Docker socket and set Testcontainers host override appropriately.

### Selective runs

```bash
mvn test -Dtest='*Test'                    # unit-ish name pattern
mvn test -Dtest='*IT,*E2ETest'              # integration + e2e
mvn test -Dgroups=performance               # scalability budgets only
mvn test -Dtest='ApiFullFlowE2ETest'        # one class
```

---

## 4. What is covered (map)

### Auth & accounts
- First user → `ADMIN`, later → `USER` (empty-DB aware)
- Email normalize / duplicate `EMAIL_TAKEN`
- Short password / signups disabled
- Sole-admin cannot disable / soft-delete self when alone
- Security stamp rotation → API `401 SESSION_REVOKED`

### Domain lifecycle
- Company create / soft-delete / restore / hard-delete
- Cascade soft-delete company → apps → rounds → notes
- Restore batch restores notes/resources as well as apps/rounds
- Hard-delete purges soft-deleted children (not only “active” rows)
- Optimistic lock `OPTIMISTIC_LOCK` (service + API)
- Idempotency replay + `IDEMPOTENCY_CONFLICT`

### Authorization / privacy
- Non-owner `NOT_FOUND` (not 403) for company read
- Admin can read any company
- Cross-user API isolation (E2E)
- `/api/v1/admin/**` forbidden for `USER`

### API / MVC surface
- Signup → login → company → application → round → note → resume
- CSRF required on mutating API (except auth signup/login)
- Unauthenticated API → `401 UNAUTHORIZED` JSON
- MVC login + company POST PRG redirect

### Storage / filters
- Upload empty / oversize / bad extension rejected
- Local storage path traversal rejected
- Rate limit filter returns `429` for API when over quota

### Performance budgets (asserted)

| Scenario | Volume | Budget | Example measured |
|----------|--------|--------|------------------|
| Batch create companies | 200 | &lt; 20s | ~1.5s |
| Company list page 50 | after 200 | &lt; 3s | ~2–5ms |
| Company name search | after 200 | &lt; 3s | ~7–10ms |
| Deep page (page 3) | after 200 | &lt; 3s | ~5–7ms |
| Page-size clamp | — | ≤100 rows | ~3ms |
| Application search | 100 apps | &lt; 3s | ~28–34ms |
| Upcoming rounds | 80 rounds | &lt; 2s | ~11–15ms |
| Dashboard cold / warm | 40 apps | &lt; 2s / &lt; 500ms | ~30ms / ~13ms |
| Cascade soft-delete tree | 25 apps+rounds | &lt; 5s | ~0.4s |
| Idempotency replay ×25 | vs fresh ×25 | replay &lt; fresh | ~79ms vs ~206ms |
| Concurrent list 8×40 | wall clock | &lt; 8s | ~0.2s |

Logged as `PERF_METRIC` lines from `ScalabilityPerformanceIT`. These are **regression fences**, not load-test SLAs.

---

## 5. Failure modes the suite tries to break

- Replay create with same idempotency key but different payload
- Hard-delete without soft-delete (`NOT_ARCHIVED`)
- Attach another user’s resume to a round
- Mid-session disable / stamp rotate
- Soft-deleted tree left behind after “hard” purge (fixed + tested)
- Company restore that forgot notes (fixed + tested)

---

## 6. Writing new tests

1. Prefer **unit** for pure validation / branching.
2. Prefer **IT** when Redis/Postgres semantics matter (idempotency, Flyway, cascade).
3. Prefer **E2E MockMvc** when CSRF, session cookies, or HTTP status contracts matter.
4. Use `TestDataFactory` + `AbstractIntegrationTest`; do not hardcode container ports.
5. E2E that must persist across requests: `@Transactional(propagation = NOT_SUPPORTED)` so commits are visible to the security/session stack.
6. Never assert “first user is admin” without checking `count() == 0` — parallel committed E2E data may already exist.

---

## 7. Related docs

- [ARCHITECTURE.md](ARCHITECTURE.md) — layers under test
- [SECURITY.md](SECURITY.md) — stamp, CSRF, rate limits
- [API.md](API.md) — status codes and idempotency headers
- [OPERATIONS.md](OPERATIONS.md) — CI / runtime notes
