# Jobs180 — Decision log (X vs A/B/C)

Architecture Decision Records in compact form. Each entry states context, options, decision, and consequences.

---

## D1 — Product shape

**Context:** Need a tracker for applications, not a CRM.  
**Options:** (A) Application tracker (B) Company wiki/CRM (C) Hybrid.  
**Decision:** A.  
**Consequences:** Hierarchy optimizes for roles/rounds/resumes; research companies allowed without roles.

---

## D2 — Privacy model

**Context:** Multiple accounts on one deployment.  
**Options:** (A) Private per user + admin (B) Fully shared (C) Shared companies / private applications.  
**Decision:** A.  
**Consequences:** Simpler authz; no sharing UX; admin is break-glass.

---

## D3 — UI technology

**Context:** Need usable CRUD quickly, plus automation API.  
**Options:** (A) Thymeleaf SSR + REST (B) SPA-only (C) SSR-only.  
**Decision:** A.  
**Consequences:** Shared services; CSRF/session natural; SPA can be added later without rewriting rules.

---

## D4 — Language/runtime

**Options:** (A) Java 21 Spring Boot (B) Node (C) Django (D) Rails.  
**Decision:** A.  
**Consequences:** Strong Security/Session/JPA story; heavier memory than Node; excellent long-term maintainability for this author/context.

---

## D5 — Database

**Options:** (A) PostgreSQL (B) MySQL (C) MongoDB (D) SQLite.  
**Decision:** A.  
**Consequences:** Best constraint/index/lock toolkit for our invariants; requires a service in Compose.

---

## D6 — Schema migration tool

**Options:** (A) Flyway (B) Liquibase (C) Hibernate ddl-auto.  
**Decision:** A; prod validate-only.  
**Consequences:** SQL is reviewable; no surprise prod DDL.

---

## D7 — Session store

**Options:** (A) Redis (B) In-memory (C) JDBC (D) JWT-only.  
**Decision:** A.  
**Consequences:** Multi-replica auth; Redis becomes a hard dependency for readiness philosophy.

---

## D8 — File storage

**Options:** (A) S3 API / MinIO (B) Local disk only (C) Postgres BYTEA.  
**Decision:** A; local adapter for tests.  
**Consequences:** Env-only cloud swap; must back up bucket; MinIO in Compose.

---

## D9 — Soft delete semantics

**Options:** (A) deleted_at + deletion_batch_id (B) Hard only (C) deleted_at without batch.  
**Decision:** A.  
**Consequences:** Safe restore; slightly more service code.

---

## D10 — First admin

**Options:** (A) First signup (B) Env seed (C) Invite-only.  
**Decision:** A + lock row.  
**Consequences:** Easy self-host; must protect sole-admin thereafter.

---

## D11 — Non-owner HTTP code

**Options:** (A) 404 (B) 403.  
**Decision:** A.  
**Consequences:** Less enumeration; slightly harder client debugging.

---

## D12 — Idempotency

**Options:** (A) Redis keys (B) None (C) DB unique constraints only.  
**Decision:** A for unsafe creates/actions.  
**Consequences:** Clients should send keys; Redis TTL required.

---

## D13 — Rate limiting

**Options:** (A) Redis fixed window (B) Local memory (C) API gateway only.  
**Decision:** A in-app.  
**Consequences:** Works without mandatory gateway; gateway can still be added.

---

## D14 — API soft-delete method

**Options:** (A) POST action (B) DELETE soft (C) PATCH tombstone field only).  
**Decision:** A; DELETE = hard purge.  
**Consequences:** Clear semantics; more endpoints.

---

## D15 — Polymorphic notes/resources

**Options:** (A) Three nullable FKs + CHECK (B) parent_type/id (C) JSON array on parent).  
**Decision:** A.  
**Consequences:** Real FKs; CHECK enforces exactly one parent.

---

## D16 — Optimistic vs pessimistic locking

**Options:** (A) Version on aggregates + pessimistic for first admin (B) All pessimistic (C) Last write wins.  
**Decision:** A.  
**Consequences:** Good UX for forms; explicit 409 on conflict.

---

## D17 — Branding

**Options:** (A) Jobs180 (B) Company Tracker descriptive name.  
**Decision:** A for product/repo; internal Java package may retain historical namespace.  
**Consequences:** Clear product identity; package rename optional later (large churn).

---

## D18 — Non-goals v1

Explicitly deferred: OAuth, email verify/reset, sharing, AV scanning, GraphQL, microservices, billing.

---

## D19 — Atomic rate-limit windows

**Context:** The first limiter did `INCR` then `EXPIRE` as two round trips. A crash between them leaves a counter with no TTL, and that client stays locked out forever.  
**Options:** (A) One Lua script: INCR, set TTL only when new, return count + remaining TTL (B) `MULTI`/`EXEC` (C) Redis 7 `INCR` + `EXPIRE ... NX` pipelined.  
**Decision:** A.  
**Consequences:** Atomic on the Redis side, one round trip, and `Retry-After` is the real time left in the window instead of a fixed 60. Still a fixed window, so up to 2x the limit can pass across a boundary; accepted because the goal is stopping brute force, not metering.

---

## D20 — Which IP to rate-limit

**Context:** Reading `X-Forwarded-For` directly lets any client send a fake IP per request and walk around per-IP limits.  
**Options:** (A) `server.forward-headers-strategy=native`: Tomcat honours the header only when the direct peer is a trusted internal proxy (B) Trust the header from everyone (C) Ignore proxies entirely.  
**Decision:** A.  
**Consequences:** Behind a reverse proxy on a private network the real client IP is used; direct callers cannot spoof it. Add the proxy range to `server.tomcat.remoteip.internal-proxies` if it is not a private address.

---

## D21 — Rate limiter when Redis is down

**Options:** (A) Fail open and log a warning (B) Fail closed (429 everything).  
**Decision:** A.  
**Consequences:** A Redis blip does not turn into a full outage from the limiter alone. Sessions still need Redis, so readiness still reports Redis down; the limiter just does not add a second failure mode.

---

## D22 — When the idempotency record is written

**Context:** Creates run inside `@Transactional` service methods. Writing the Redis record before commit let a concurrent retry replay an id whose row was not committed yet (false 404), and left a record pointing at nothing if the transaction rolled back.  
**Options:** (A) Write the record and release the lock in `afterCompletion`, only on commit (B) Store keys in a Postgres table in the same transaction (C) Leave it.  
**Decision:** A.  
**Consequences:** Replays only ever see committed rows; rollbacks leave no record, so a retry runs again. The remaining gap is a process crash after commit and before the Redis write (milliseconds). B closes it fully and is the upgrade path if that gap ever matters.

---

## D23 — Lock ownership and payload fingerprint

**Context:** (1) Releasing the in-flight lock with a plain `DEL` could delete another request's lock if the first one outlived the 30 s TTL. (2) Payload hashes came from `Map.of(...)` JSON, whose key order is randomised per JVM, so the same retry could hash differently on another replica.  
**Decision:** Lock value is a random token released by a compare-and-delete Lua script; payloads are hashed with map keys sorted.  
**Consequences:** A request can only release its own lock, and honest retries match on any replica.
