# Jobs180 — Concepts (CS & web fundamentals, applied)

This document teaches the **ideas** behind Jobs180 using the codebase as the lab. Each section states the concept, how Jobs180 uses it, and **why that formulation instead of common alternatives**.

---

## 1. Client–server computing

**Concept:** Work is split between a client (browser/API consumer) and a server that owns authoritative state.

**In Jobs180:** Browsers never write Postgres directly. All mutations go through HTTP to the Spring app.

**Why not “local-first sync engine” (Automerge/CRDTs)?**  
Powerful for offline collaboration; far heavier than needed for a private tracker; conflict UX becomes the product. Jobs180 chooses **server authority** for simplicity and security reviewability.

---

## 2. HTTP as an application protocol

**Concepts:** methods, status codes, headers, cookies, redirects, content negotiation.

**In Jobs180:**

- `GET` for reads; `POST`/`PATCH`/`DELETE` for mutations.
- `302` after form posts (**PRG**).
- `404` for missing *or* forbidden-by-ownership.
- `409` for optimistic lock & idempotency conflicts.
- `429` for rate limits.

### Why PRG instead of rendering HTML on POST response?

| Approach | Pros | Cons |
|----------|------|------|
| **A. PRG (chosen)** | Refresh-safe; bookmarkable success URL | Extra round trip |
| B. Return 200 HTML on POST | One round trip | Refresh re-POSTs → duplicates |
| C. 303 only for some forms | Inconsistent | Easy to get wrong |

---

## 3. Stateless protocols vs application sessions

**Concept:** HTTP is stateless; applications add continuity via cookies + server-side session stores.

**In Jobs180:** Cookie holds session id; **Redis** holds session payload (Spring Session).

| Session store | Why accept / reject |
|---------------|---------------------|
| **A. Redis (chosen)** | Shared across replicas; fast; fits revoke-all-sessions |
| B. In-memory JVM | Breaks multi-node; lost on restart |
| C. JDBC/Postgres sessions | Works; more DB write churn; slower revoke patterns |
| D. JWT-only (no server session) | Hard immediate revoke; large cookies/headers; awkward SSR CSRF pairing |

---

## 4. Authentication vs authorization

**Authentication:** proving identity (password verify → session).  
**Authorization:** enforcing permissions (roles + ownership).

**In Jobs180:**

- Authn: form login / API login, BCrypt password match.
- Authz: `ROLE_ADMIN` / `ROLE_USER` + `OwnershipGuard`.

**Why both URL security and service checks?**  
URL rules are a coarse firewall (`/admin/**`). Service checks stop **IDOR** on `/companies/{id}` where the path is not role-specific. Defense in depth.

---

## 5. Password storage (hashing ≠ encryption)

**Concept:** one-way keyed hashing with salt and work factor.

**In Jobs180:** BCrypt cost 12.

| Approach | Verdict |
|----------|---------|
| **A. BCrypt (chosen)** | Native Spring support; intentionally slow |
| B. Plaintext | Negligent |
| C. Single SHA-256 | GPU-fast; insufficient alone |
| D. Argon2id | Excellent; can be a later drop-in if desired |
| E. Encrypt passwords (AES) | Recoverable → wrong threat model |

---

## 6. CSRF (confused deputy)

**Concept:** browser auto-sends cookies; malicious sites can trigger requests unless the server requires a secret the evil site cannot read.

**In Jobs180:** CSRF token cookie + form field / header. Login/signup API endpoints exempt to allow session bootstrap; authenticated mutations are not.

**Why not “SameSite=Strict fixes everything”?**  
SameSite helps but is not a complete substitute across all browser/embed cases; Spring’s CSRF remains standard for cookie auth SSR apps.

---

## 7. XSS

**Concept:** untrusted strings interpreted as code in a page.

**In Jobs180:** Thymeleaf escapes by default; CSP restricts script sources; notes are plain text (no HTML pipeline).

**Why not Markdown-with-HTML?**  
Rich text expands XSS surface area; not worth it for v1 tracker notes.

---

## 8. CORS

**Concept:** browsers enforce same-origin policy for JS; CORS is an opt-in relaxation.

**In Jobs180:** default deny. Jobs180 UI is same-origin. Cross-origin only if `APP_CORS_ALLOWED_ORIGINS` set.

**Why not `*` with credentials?**  
Invalid/dangerous combination; enables cross-site credentialed reads if misconfigured.

---

## 9. Access control models

**Concept:** ACL vs RBAC vs ABAC vs ownership.

**In Jobs180:** **RBAC** (admin/user) + **ownership** (resource.ownerId == currentUser).

| Model | Fit |
|-------|-----|
| Pure RBAC | Too coarse (all users would see all companies) |
| Pure ACL per row UI | Heavy admin UX |
| **RBAC + ownership (chosen)** | Matches personal tracker + break-glass admin |

---

## 10. Relational modeling & integrity

**Concepts:** entities, keys, FK, normalization, controlled denormalization, constraints.

**In Jobs180:**

- FK trees for hierarchy.
- CHECK for single-parent notes/resources and resume file-or-URL.
- Denormalized `owner_id` on children for authz query speed.

**Why denormalize owner_id?**  
Pure 3NF would join through parents on every list. Denormalization is a **performance/authorization** trade; services must keep it consistent.

---

## 11. Soft delete

**Concept:** logical deletion marker instead of physical remove.

**In Jobs180:** `deleted_at` + `deletion_batch_id`.

| Approach | Verdict |
|----------|---------|
| **A. Soft delete + batch restore (chosen)** | Undo without resurrecting unrelated archives |
| B. Hard delete only | No undo; scary for users |
| C. Soft delete without batch id | Restore may revive too much or too little |
| D. Temporal tables | Powerful; operationally heavier than needed |

---

## 12. Transactions & ACID

**In Jobs180:** `@Transactional` on services that must atomic-commit multi-row changes.

**First-admin:** lock row then read counts—**pessimistic locking** to serialize bootstrap.

**Row edits:** `@Version`—**optimistic locking** for low-contention forms.

| Strategy | When Jobs180 uses it |
|----------|----------------------|
| Pessimistic | Rare high-stakes races (first admin) |
| Optimistic | Common edits (applications, companies) |

---

## 13. Caching

**Concept:** store expensive read results closer/cheaper; accept staleness bounds.

**In Jobs180:** Redis cache for dashboard status aggregates; TTL + eviction on writes.

**Why not cache every entity page?**  
Authz-sensitive pages cached wrong → data leaks. Aggregates keyed by user are safer.

---

## 14. Idempotency

**Concept:** performing an operation once or N times yields the same effect.

**In Jobs180:** `Idempotency-Key` + payload hash in Redis; in-flight lock.

| Approach | Verdict |
|----------|---------|
| **A. Explicit keys (chosen)** | Client-controlled safe retries |
| B. Dedupe only by natural key (email) | Doesn’t cover “create company” |
| C. Ignore retries | Duplicate data under flaky networks |

---

## 15. Rate limiting

**Concept:** admission control against abuse.

**In Jobs180:** Redis fixed-window counters.

| Algorithm | Note |
|-----------|------|
| Fixed window (chosen) | Simple, good enough for v1 |
| Token bucket / sliding window | Smoother; more code; later upgrade path |

---

## 16. Object storage & streaming

**Concept:** separate large opaque bytes from relational metadata; stream to avoid O(file size) heap usage.

**In Jobs180:** metadata in Postgres; bytes in MinIO/S3; streaming download.

**Why server-generated keys?**  
Stops path traversal and user-controlled overwrite of others’ objects.

---

## 17. Horizontal scaling

**Concept:** add identical workers behind a load balancer; keep them **stateless** relative to requests.

**In Jobs180:** sessions/RL/idempotency/cache in Redis; files in S3 API; DB shared.

**Why not sticky sessions?**  
They work until they don’t (node drain, uneven load). Redis sessions remove the need.

---

## 18. Observability basics

**Concept:** health checks distinguish “process up” vs “ready for traffic.”

**In Jobs180:** Spring Actuator health with DB/Redis influence; prod hides details from anonymous callers.

---

## 19. Error semantics as API design

Stable `code` strings (`SOLE_ADMIN`, `RATE_LIMITED`, …) turn failures into **programmable contracts**, not only human messages. That is part of treating the API as a product surface.

---

## 20. How to study the code with these concepts

1. Read `OwnershipGuard` + a service method → authz.  
2. Read `IdempotencyService` → retries.  
3. Read `SecurityStampFilter` + `SessionRevocationService` → session invalidation.  
4. Read Flyway `V1__schema.sql` → constraints as last-line defense.  
5. Read `RateLimitFilter` → admission control.

Architecture context: [ARCHITECTURE.md](ARCHITECTURE.md).
