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
