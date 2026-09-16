# Architecture

This document explains **how Company Tracker is put together** and **why** each major choice was made.

## What the system is

A multi-user **job application tracker**: each person tracks companies, roles, interview rounds, resumes, notes, and resources. Data is **private per user**; an **admin** can manage users and everyone’s data. The first registered account becomes admin.

```
Browser ──► Spring Boot app ──► PostgreSQL (source of truth)
                │
                ├──► Redis (sessions, cache, rate limits, idempotency)
                └──► MinIO / S3 (resume & resource files)
```

## High-level layers

| Layer | Responsibility | Why separate it |
|-------|----------------|-----------------|
| **Web (MVC + API)** | HTTP, forms, JSON, redirects | Keep transport concerns out of business rules |
| **Security** | Authn/authz, CSRF, rate limits, session revoke | Cross-cutting; must run before controllers |
| **Services** | Invariants, cascades, ownership | One place for “what the product allows” |
| **Repositories** | Persistence queries | Hide SQL/JPA details from services |
| **Domain** | Entities & enums | Model the business nouns |
| **Storage** | Object store put/get/delete | Swap MinIO ↔ S3 without rewriting domain code |

This is a classic **layered architecture** (presentation → application → domain → infrastructure). It is not a microservice system: one deployable is enough for this product’s scale and team size.

## Why Spring Boot + Thymeleaf (not SPA-only)

- **Server-rendered HTML** is simpler for a CRUD tracker: forms, CSRF, and sessions work naturally.
- A **JSON API** (`/api/v1`) shares the same services so machines and future frontends can reuse logic.
- Dual UI/API without duplicating rules is an application of **DRY** (Don’t Repeat Yourself) at the service boundary.

## Why PostgreSQL

- Relational model fits nested ownership: User → Company → Application → Round.
- **Foreign keys** and **CHECK constraints** enforce integrity even if application bugs slip through.
- Mature indexing, transactions (`ACID`), and ops tooling.

## Why Redis

The app is designed to run as **stateless replicas**. Anything that must be shared across instances lives in Redis:

| Use | Concept |
|-----|---------|
| Spring Session | Horizontal scale without sticky sessions |
| Dashboard cache | Reduce repeated aggregate queries |
| Rate limits | Distributed counters (not per-node memory) |
| Idempotency keys | Safe retries across nodes |

## Why MinIO / S3 (not DB BLOBs or local disk)

- Databases are poor object stores at size/cost; blobs bloat backups.
- Local disk breaks when you run **two app containers** (file on node A, request on node B).
- S3 API is the industry standard; MinIO is S3-compatible for local/dev; production can point at AWS/R2 via env vars only (**12-factor config**).

## Request lifecycle (mental model)

1. Request hits Tomcat inside Spring Boot.
2. **Filters**: rate limit → Spring Security (session/CSRF/auth) → security stamp check.
3. **Controller** binds input (form or JSON) and calls a **service**.
4. Service checks **ownership** (or admin), applies business rules, uses **idempotency** when keyed.
5. **Repository** / JPA persists; **StoragePort** stores files when needed.
6. Response: HTML (PRG redirect) or JSON.

## Package map

```
com.companytracker
  config/      # Security, cache, MVC, typed properties
  domain/      # Entities & enums
  repo/        # Spring Data JPA
  security/    # UserDetails, filters, idempotency, session revoke
  service/     # Product rules
  storage/     # S3/local adapters
  web.mvc/     # Thymeleaf controllers
  web.api/     # REST
  web.dto/     # Form objects (mass-assignment safe)
  web.error/   # Stable error codes
```

## Design principles used

- **Separation of concerns** — UI ≠ rules ≠ storage.
- **Fail closed** — unknown/unauthorized → treat as not found (404) for non-owners.
- **Defense in depth** — DB constraints + service checks + security filters.
- **Explicit over clever** — soft-delete/restore as POST actions; DELETE means hard purge.
- **Environment-based config** — same artifact, different env (Compose vs cloud).

## What we deliberately did *not* build

- JWT/OAuth (session cookies suffice for v1).
- Microservices / message buses (unnecessary complexity).
- Rich-text HTML notes (XSS surface).
- Multi-tenant “shared company wiki” (privacy model is per-user).

See also: [CONCEPTS.md](CONCEPTS.md), [DATA_MODEL.md](DATA_MODEL.md), [SECURITY.md](SECURITY.md), [API.md](API.md), [OPERATIONS.md](OPERATIONS.md).
