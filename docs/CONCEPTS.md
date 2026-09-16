# Concepts (CS & web fundamentals)

This guide maps **what the project does** to **concepts you would learn in CS / web courses**, so the codebase is readable as applied theory—not just framework glue.

## 1. Client–server & HTTP

- The **browser** (or API client) is the client; Spring Boot is the server.
- Communication uses **HTTP**: methods (`GET` safe/idempotent by convention, `POST`/`PATCH`/`DELETE` mutate), status codes (`200`, `201`, `302`, `404`, `409`, `429`), headers (cookies, CSRF, `Idempotency-Key`).
- **Stateless HTTP** vs **application sessions**: HTTP itself doesn’t remember you; we store a session id in a cookie and the session payload in **Redis**.

### Why redirects after POST (PRG)

**Post/Redirect/Get** prevents double-submit when the user refreshes. After a successful form POST, the server responds `302` to a GET URL. This is a classic web pattern for HTML apps.

## 2. Authentication vs authorization

| Term | Meaning here |
|------|----------------|
| **Authentication** | “Who are you?” — email/password, session cookie |
| **Authorization** | “What may you do?” — `ROLE_USER` vs `ROLE_ADMIN`, ownership checks |

Passwords are stored as **BCrypt hashes** (one-way + salt). We never store plaintext. That is applied **cryptographic hashing** for secrets at rest (not encryption—you cannot reverse a hash to get the password).

### Security stamp

When an admin disables/deletes/demotes a user (or password/role changes), we rotate a **security stamp** UUID and delete Redis sessions. The next request fails closed. Conceptually this is **credential versioning** / forced re-auth—similar to invalidating refresh tokens.

## 3. Multi-tenancy (logical)

Each row is owned by a user (`owner_id`). Tenants share one database but **row-level isolation** is enforced in services (and indexes support efficient filters). This is **soft multi-tenancy**, not separate DBs per user.

Non-owners get **404** (not 403) to reduce **user enumeration** / information leakage.

## 4. Relational modeling & normalization

- Entities map to tables (Users, Companies, JobApplications, …).
- Relationships are **1:N** (company has many applications) and optional **N:1** (round → resume).
- **Foreign keys** preserve referential integrity.
- **CHECK constraints** encode domain invariants (e.g. a note has exactly one parent).
- We denormalize `owner_id` onto child tables for **query performance** (authz without deep joins)—a deliberate tradeoff against pure 3NF purity.

### Soft delete vs hard delete

| Soft delete | Hard delete |
|-------------|-------------|
| Set `deleted_at` | Remove row (+ blob) |
| Reversible with `deletion_batch_id` | Permanent |
| Lists filter `deleted_at IS NULL` | Used from archive after soft delete |

`deletion_batch_id` is how we restore **only** what was cascaded in one admin/user purge—not older personal archives. That is **batch correlation** for cascading lifecycle operations.

## 5. Transactions & ACID

Service methods marked `@Transactional` run in a **database transaction**:

- **Atomicity** — signup + role assignment commit together; cascade soft-deletes commit together.
- **Consistency** — constraints + app rules.
- **Isolation** — concurrent signups use `app_locks` row `FOR UPDATE` so only one first admin wins (**pessimistic locking**).
- **Durability** — Postgres WAL.

**Optimistic locking** (`@Version`) detects lost updates: two tabs edit the same application → second save gets conflict (`409`).

## 6. Caching

Dashboard status counts are cached in Redis with TTL and eviction on writes.

Concepts:

- **Cache hit/miss**
- **TTL** (time-to-live) as a freshness bound
- **Cache invalidation** on mutation (harder than caching itself—hence short TTL + explicit evict)
- Keyed by user (or `admin:global`) so we never leak another user’s aggregates

## 7. Idempotency

Networks retry. Without care, a double `POST /companies` creates two companies.

Clients send `Idempotency-Key`. Redis stores:

- payload hash
- result id (or map body)

Same key + same payload → replay. Same key + different payload → `409 IDEMPOTENCY_CONFLICT`. An in-flight lock prevents parallel double-execute.

This is the same idea as **idempotent APIs** in payment systems (Stripe-style keys).

## 8. Rate limiting

Redis `INCR` + expiry implements a **fixed window** counter per IP/user/action. Exceeding the limit returns **429 Too Many Requests**.

Related CS idea: **admission control** / protecting shared resources from abuse (brute-force login, signup spam).

## 9. Object storage & streaming I/O

Files go to MinIO/S3 by **key** (not filesystem path from the user). Keys are server-generated UUIDs → prevents **path traversal** (`../`).

Downloads **stream** bytes to the response to avoid loading entire files into heap (**bounded memory**).

## 10. MVC & REST

- **MVC**: Model (entities/DTOs), View (Thymeleaf), Controller (request mapping).
- **REST**: resource-oriented URLs, nouns not verbs for collections; actions like soft-delete use explicit subpaths so `DELETE` can mean hard purge only.

Form DTOs (`Forms.*`) avoid **mass assignment**: clients cannot set `role` or `ownerId` via binding to entities.

## 11. XSS, CSRF, CORS (web security triad extras)

| Threat | Mitigation here |
|--------|-----------------|
| **XSS** | Thymeleaf escapes output; CSP headers; no `th:utext` for user content |
| **CSRF** | Token on state-changing form/API calls (cookie + header/param) |
| **CORS** | Deny-by-default; optional allowlist via env |

## 12. Horizontal scaling & shared nothing (almost)

App processes are **stateless**. Shared state:

- Postgres (durable data)
- Redis (ephemeral coordination)
- Object store (files)

Load balancers can send any request to any replica (**no sticky sessions** required) because sessions live in Redis.

## 13. Observability basics

Actuator exposes **liveness/readiness**-style health. Readiness should fail if Postgres/Redis are down so orchestrators don’t send traffic to a broken instance.

## 14. Testing concepts

Unit tests mock collaborators (**test doubles**) to verify:

- sole-admin invariant
- ownership 404
- idempotency conflict/replay
- email normalization

That’s **behavior-focused** testing of rules, not the whole container stack.

---

Further reading in-repo: [ARCHITECTURE.md](ARCHITECTURE.md), [SECURITY.md](SECURITY.md), [DATA_MODEL.md](DATA_MODEL.md).
