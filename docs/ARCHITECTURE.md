# Jobs180 — Architecture

This document is the **system design bible** for Jobs180: what runs where, how a request moves, and—most importantly—**why the design looks like this instead of the common alternatives**.

---

## 1. Problem framing

Jobs180 must:

1. Persist a **hierarchical job-hunt graph** (company → application → round) plus a **resume library**.
2. Keep data **private per user**, with a privileged **admin**.
3. Be **self-hostable** with one command locally and **env-only** promotion to cloud.
4. Survive **retries**, **concurrent edits**, **replica restarts**, and **admin lifecycle** actions against live sessions.
5. Store **files** without coupling them to a single machine’s disk.

That is an application + platform problem, not a “CRUD tutorial” problem.

---

## 2. Runtime topology

```
┌──────────────┐     ┌──────────────────────────────────────────┐
│ Browser /    │────►│ Jobs180 Spring Boot process (N replicas) │
│ API client   │     │  - Tomcat                                │
└──────────────┘     │  - Security filter chain                 │
                     │  - MVC + REST controllers                │
                     │  - Services / JPA                        │
                     └───┬──────────────┬──────────────┬────────┘
                         │              │              │
                         ▼              ▼              ▼
                   PostgreSQL        Redis         MinIO/S3
                   durable           ephemeral      objects
                   relational        shared state   (resumes)
```

### Why three data planes (not one)

| Plane | Stores | Why not put everything here |
|-------|--------|------------------------------|
| Postgres | Entities, constraints, transactions | Bad for large binary objects; expensive backups if BLOBs dominate |
| Redis | Sessions, counters, short-lived idempotency & cache | Not a system of record; volatile by design |
| Object store | Resume/resource bytes | No rich query; needs relational metadata alongside |

**Alternative rejected: “Postgres only”** (sessions in JDBC, files as BYTEA). Simpler ops demo, but: session table churn, huge DB backups, and weak multi-node file story.

**Alternative rejected: “MongoDB for everything.”** Document model can encode trees, but you lose cheap relational constraints, mature Flyway-style migrations discipline, and join/index patterns Jobs180 relies on (owner+status, upcoming `scheduled_at`).

---

## 3. Process architecture (inside the JVM)

Jobs180 uses a **layered monolith**:

```
web.mvc / web.api     presentation (HTTP mapping, DTO binding, PRG)
        │
security.*            cross-cutting (authn, CSRF, RL, stamp, idempotency helpers)
        │
service.*             application/business rules (the product)
        │
repo.* + domain.*     persistence model
        │
storage.*             object I/O port/adapters
```

### Why layers instead of “controllers talk to repositories”

If controllers own rules, you get:

- duplicated ownership checks between MVC and API;
- inconsistent soft-delete behavior;
- untestable HTML-coupled logic.

Services are the **single chokepoint** for invariants (sole-admin, cascades, ownership). Controllers become translation.

### Why not hexagonal/ports-and-adapters purity everywhere

We *do* use a port for storage (`StoragePort`) because backends truly swap (MinIO ↔ S3 ↔ local test). We *don’t* abstract Postgres behind a fantasy repository interface beyond Spring Data—JPA is already the port, and extra indirection would add noise without a second DB target.

---

## 4. Request lifecycle (happy path)

1. Load balancer → any app replica (no sticky session required).
2. `RateLimitFilter` — Redis INCR windows.
3. Spring Security — session resolution from Redis, CSRF (when applicable), authz matchers.
4. `SecurityStampFilter` — compare session principal stamp to DB; revoke if stale.
5. Controller — bind form/JSON; call service with idempotency key when present.
6. Service — ownership + domain rules; `@Transactional` boundary; optional StoragePort put.
7. Response — HTML redirect (PRG) or JSON body with stable error codes on failure.

Detailed walkthrough: [REQUEST_LIFECYCLE.md](REQUEST_LIFECYCLE.md).

---

## 5. Consistency model

| Concern | Mechanism |
|---------|-----------|
| Multi-row updates (cascades, signup) | DB transactions |
| First-admin race | `app_locks` row `SELECT … FOR UPDATE` |
| Concurrent edits same row | `@Version` optimistic locking → `409` |
| Cross-node “did this POST already run?” | Redis idempotency records |
| Cross-node “is this user still allowed?” | security stamp + session delete |

Jobs180 does **not** claim linearizability across Redis and Postgres for all reads. Dashboard cache may lag up to TTL; mutations evict cache aggressively. That is an intentional **latency vs freshness** tradeoff for an aggregate that is not payment-critical.

---

## 6. Multi-tenancy model

**Logical tenancy via `owner_id`**, one shared schema.

| Approach | Verdict |
|----------|---------|
| **A. Shared DB, owner_id (chosen)** | Right-sized; simple backups; indexes per owner |
| B. Schema-per-user | Ops nightmare for a personal tracker |
| C. Database-per-user | Absurd cost for this product |
| D. Row Level Security only | Good hardening later; not a substitute for service checks in v1 |

Admin is a **break-glass role** that can read/write across owners—not a separate tenant.

---

## 7. UI + API dual surface

```
Thymeleaf controllers ─┐
                       ├──► service.* ──► repo / storage
/api/v1 controllers  ─┘
```

| Approach | Verdict |
|----------|---------|
| **A. SSR + API sharing services (chosen)** | One rule engine; progressive enhancement possible |
| B. SPA + API only | More frontend complexity; CSRF/session story harder for little gain |
| C. SSR only, no API | Blocks scripting/automation/future mobile |
| D. Separate BFF microservice | Premature distribution |

---

## 8. Configuration & packaging

- **Docker multi-stage build**: JDK build image → JRE runtime, non-root user.
- **Compose**: postgres + redis + minio + minio-init + app.
- **12-factor**: env vars for all secrets/URLs; same artifact for local/cloud.

| Approach | Verdict |
|----------|---------|
| **A. Compose + env (chosen)** | Lowest friction self-host |
| B. Kubernetes manifests first | Higher barrier; can be added later |
| C. Fat “all-in-one” JVM with embedded H2/Redis | Lies about production shape; teaches bad habits |

---

## 9. Failure domains

| Dependency down | Intended app behavior |
|-----------------|----------------------|
| Postgres | Fail readiness; no false “healthy” |
| Redis | Fail readiness in prod philosophy; sessions/RL/idempotency unavailable |
| MinIO | Metadata reads work; upload/download fail clearly |

We explicitly **reject** silent fallback from Redis sessions to in-memory sessions in production: that creates split-brain auth across replicas.

---

## 10. What “good architecture” means for Jobs180

Not clever diagrams—**invariants that hold under stress**:

- User A never reads User B’s companies by IDOR.
- Last admin cannot be removed.
- Retrying a create with the same Idempotency-Key does not duplicate companies or MinIO objects.
- Deleting a logged-in user kills their session cluster-wide.
- Two app replicas do not need sticky sessions.

If a future change breaks one of those, it is an architectural regression—even if the UI still “looks fine.”
