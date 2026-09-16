# Jobs180

**Jobs180** is a self-hostable **job application tracker** for individuals (and small teams) who need a durable system of record for companies, roles, interview rounds, resumes, notes, and prep resources—without handing that data to a third-party SaaS.

It ships as a single **Spring Boot** application with **server-rendered UI** (Thymeleaf) and a parallel **versioned JSON API** (`/api/v1`), backed by **PostgreSQL**, **Redis**, and **MinIO** (S3-compatible object storage).

> Product name: **Jobs180**  
> Maven artifact: `jobs180`  
> Java package (implementation namespace): `com.companytracker` (stable internal module path; product branding is Jobs180 everywhere user-facing)  
> Repository: [github.com/Purv-Kabaria/Jobs180](https://github.com/Purv-Kabaria/Jobs180)

---

## Table of contents

1. [Why Jobs180 exists](#1-why-jobs180-exists)
2. [Who it is for](#2-who-it-is-for)
3. [What you get (feature map)](#3-what-you-get-feature-map)
4. [Quick start](#4-quick-start)
5. [Architecture at a glance](#5-architecture-at-a-glance)
6. [Why this stack (defended choices)](#6-why-this-stack-defended-choices)
7. [Domain model (how thinking maps to tables)](#7-domain-model-how-thinking-maps-to-tables)
8. [Security model](#8-security-model)
9. [API & UI](#9-api--ui)
10. [Configuration reference](#10-configuration-reference)
11. [Operations: backup, scale, failure modes](#11-operations-backup-scale-failure-modes)
12. [Development & tests](#12-development--tests)
13. [Repository layout](#13-repository-layout)
14. [Documentation map](#14-documentation-map)
15. [Roadmap / non-goals](#15-roadmap--non-goals)
16. [License / contributing posture](#16-license--contributing-posture)

---

## 1. Why Jobs180 exists

Job hunting is a **stateful workflow**: many companies, many role variants, many resume versions, many rounds, and a pile of unstructured notes/links. Spreadsheets fail because:

- they do not model **hierarchy** (company → role → round) cleanly;
- they do not enforce **privacy** if you ever share a file;
- they do not handle **file attachments** with access control;
- they do not survive **retries**, **concurrent edits**, or **admin lifecycle** events.

Jobs180 exists to be a **personal system of record** with production-shaped engineering: sessions that scale, object storage that scales, rate limits, idempotent writes, soft-delete with restore, and explicit admin safety (you cannot demote the last admin and lock yourself out).

### Naming

**Jobs180** signals a full-circle job search system: track the loop from wishlist → interviews → offer/outcome, with enough structure to review what worked 180° later—not a generic “company CRM.”

---

## 2. Who it is for

| Audience | Fit |
|----------|-----|
| Individual job seekers | Primary. Private data, resume library, round tracking |
| Friends/family sharing one deployment | Works: each user isolated; first user is admin |
| Recruiting agencies / multi-tenant SaaS | **Not** the v1 target (no org workspaces, billing, or cross-user sharing) |
| Enterprise SSO shops | Not yet (no OAuth/SAML in v1) |

---

## 3. What you get (feature map)

### Accounts & roles

- Email + password signup/login
- **First registered user becomes `ADMIN`**; later users are `USER`
- Admin: promote/demote, enable/disable, soft-delete/restore/hard-purge users
- Sole-admin protections (cannot demote/disable/delete the last enabled admin)
- Mid-session revoke when admin changes a logged-in user’s status/role

### Job-hunt data

- **Companies** (can exist with zero roles—research-first)
- **Applications / roles** with JD text/link, work mode, comp, dates, referral, source, status
- **Rounds** with schedule, outcome, interviewers, attached resume
- **Resume library** (upload PDF/DOC/DOCX and/or external URL); attach to rounds
- **Notes** & **resources** on company, application, *and* round
- Soft delete → archive/restore via deletion batch → hard purge (DB + object store)

### Product surfaces

- Dashboard (status counts + upcoming rounds)
- Search/filter applications
- Upcoming rounds list
- Thymeleaf UI + `/api/v1` JSON API

### Platform features

- Redis sessions (multi-replica ready)
- Redis cache (dashboard)
- Redis rate limits
- Redis idempotency keys
- MinIO/S3 file storage with streaming download
- Docker Compose one-command bring-up
- Flyway migrations; Hibernate validate-only in prod

---

## 4. Quick start

### Prerequisites

- Docker + Docker Compose
- Ports free: `8080`, `5432`, `6379`, `9000`, `9001`

### Run

```bash
git clone https://github.com/Purv-Kabaria/Jobs180.git
cd Jobs180
docker compose up --build
```

Open **http://localhost:8080** → **Sign up**. The first account is admin.

| Service | Where |
|---------|--------|
| Jobs180 app | http://localhost:8080 |
| Postgres | `localhost:5432` — db `company_tracker`, user/pass `tracker` / `tracker` |
| Redis | `localhost:6379` |
| MinIO API | http://localhost:9000 |
| MinIO console | http://localhost:9001 — `minioadmin` / `minioadmin` |

> Note: the Postgres database name remains `company_tracker` for migration continuity in this codebase revision. The **product** name is Jobs180; renaming the physical DB is a future ops migration, not required to use the app.

### First 5 minutes

1. Sign up as admin.
2. Create a company.
3. Add a role/application under it; set status.
4. Upload a resume in **Resumes**.
5. Add a round; attach the resume; add notes/resources.

---

## 5. Architecture at a glance

```
                 ┌─────────────────────────────────────────┐
   Browser/API ─►│  Jobs180 (Spring Boot, stateless app)   │
                 │  Thymeleaf MVC  +  /api/v1 REST         │
                 └───────┬───────────────┬─────────┬───────┘
                         │               │         │
                    PostgreSQL         Redis      MinIO/S3
                   (source of         sessions    files
                    truth)            cache
                                      rate limit
                                      idempotency
```

**Mental model:** Postgres is truth; Redis is shared *coordination* for ephemeral cross-node concerns; MinIO/S3 is the blob plane. The app process holds no must-keep local state (except optional test-only local storage).

Deep dive: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## 6. Why this stack (defended choices)

This section is the “why X instead of A/B/C” summary. Full narratives live in the docs.

### Application framework

| Choice | Rejected alternatives | Why Jobs180 picked this |
|--------|----------------------|-------------------------|
| **Spring Boot 3 + Java 21** | Node/Express, Django, Rails, Quarkus, Micronaut | Security/session/JPA/Actuator ecosystem is battle-tested; Java 21 LTS; team familiarity with Spring Security patterns |
| **Monolith** | Microservices from day one | One product, one deployable; distributed systems tax is unjustified at this scale |
| **Thymeleaf SSR + REST** | React/Vue SPA-only; HTMX-only; mobile-first | SSR gives CSRF/forms/sessions “for free”; REST shares services for future clients without rewriting rules |

### Data & files

| Choice | Rejected | Why |
|--------|----------|-----|
| **PostgreSQL** | MySQL, MongoDB, SQLite-only | Relational integrity (FK/CHECK), partial indexes, `FOR UPDATE` locks, ops maturity |
| **Flyway** | Liquibase, Hibernate `ddl-auto=update` | Reviewable SQL migrations; prod uses `validate` only |
| **MinIO / S3 API** | DB BYTEA, local disk only, GridFS | Multi-node safe; cheap large objects; env-only swap to AWS/R2 |
| **Redis** | In-memory sessions only, JDBC sessions | Horizontal scale without sticky sessions; shared rate limits/idempotency |

### Security posture

| Choice | Rejected | Why |
|--------|----------|-----|
| **Server sessions in Redis** | JWT access tokens as primary | Easy revoke; no “logout is client honor system”; fits SSR cookie model |
| **BCrypt** | plaintext, MD5/SHA, scrypt-only | Slow hash default in Spring Security; adequate for password auth |
| **404 for non-owners** | 403 Forbidden | Reduces resource existence leaks |
| **Sole-admin guards** | Allow self-demote always | Prevents irreversible lockout on self-hosted boxes |

See [docs/DECISIONS.md](docs/DECISIONS.md) and [docs/SECURITY.md](docs/SECURITY.md).

---

## 7. Domain model (how thinking maps to tables)

```
User
 ├── Resume library
 └── Company
      ├── Notes / Resources
      └── JobApplication (role)
           ├── Notes / Resources
           └── Round
                ├── optional Resume attachment
                └── Notes / Resources
```

**Statuses (application):** Wishlist → Applied → OA → Interviewing → Offer / Rejected / Withdrawn / Ghosted / On Hold  

**Round outcomes:** Pending / Pass / Fail / Skipped  

**Soft delete** uses `deleted_at` + `deletion_batch_id` so restore does not accidentally revive older archives.

Full schema reasoning: [docs/DATA_MODEL.md](docs/DATA_MODEL.md).

---

## 8. Security model

- Authentication: email/password → Redis-backed session cookie
- Authorization: Spring roles + service-level ownership checks
- CSRF on mutating requests (API login/signup exempt to bootstrap sessions)
- XSS mitigated via Thymeleaf escaping + CSP
- CORS deny-by-default
- Rate limits (login/signup/write/upload/IP)
- Security stamp + session wipe on disable/delete/role change
- Upload allowlisting + size caps + server-generated object keys

Deep dive: [docs/SECURITY.md](docs/SECURITY.md), concepts: [docs/CONCEPTS.md](docs/CONCEPTS.md).

---

## 9. API & UI

### UI

Server-rendered pages under `/dashboard`, `/companies`, `/applications`, `/rounds`, `/resumes`, `/search`, `/upcoming`, `/admin/users`. Forms use **Post/Redirect/Get** and hidden idempotency keys.

### API

Base path: **`/api/v1`**. Soft-delete/restore are `POST` actions; `DELETE` means hard purge. Send `Idempotency-Key` on creates and destructive actions.

Full route map & conventions: [docs/API.md](docs/API.md).

---

## 10. Configuration reference

Copy [`.env.example`](.env.example). Everything important is environment-driven (12-factor).

| Variable group | Purpose |
|----------------|---------|
| `SPRING_DATASOURCE_*` | Postgres connectivity + pool |
| `SPRING_DATA_REDIS_*` | Redis host/port |
| `SPRING_PROFILES_ACTIVE` | Use `prod` in Compose/cloud |
| `APP_STORAGE_TYPE` | `s3` (default) or `local` (tests) |
| `APP_S3_*` | Endpoint, bucket `jobs180`, keys, region, path-style |
| `APP_UPLOAD_MAX_BYTES` | Multipart cap |
| `APP_IDEMPOTENCY_TTL_SECONDS` | Idempotency record TTL |
| `APP_RATE_LIMIT_*` | Per-action limits |
| `APP_CORS_ALLOWED_ORIGINS` | Empty = deny cross-origin |
| `APP_SIGNUPS_ENABLED` | Incident kill-switch |
| `JAVA_OPTS` | Heap / JVM flags |

Ops detail: [docs/OPERATIONS.md](docs/OPERATIONS.md).

---

## 11. Operations: backup, scale, failure modes

### Backup

1. Postgres dump/snapshot (required)
2. MinIO/S3 bucket (required if you care about resumes)
3. Redis optional (sessions/cache; users re-login if lost)

### Scale-out checklist

- App replicas: **stateless**
- Redis: shared (sessions + RL + idempotency + cache)
- Postgres: shared primary
- Files: **S3/MinIO only** (local disk is wrong for multi-node)

### Failure modes Jobs180 is designed for

| Event | Behavior |
|-------|----------|
| App pod dies | Other replicas serve; sessions in Redis |
| Admin disables logged-in user | Sessions revoked; next request forced re-login |
| Double-submit create | Idempotency key replays same result |
| Login brute force | 429 after Redis counter threshold |
| Redis down | Readiness should fail; do not silently fall back to local sessions in prod |

---

## 12. Development & tests

```bash
# unit tests (JDK 21)
docker run --rm -v "${PWD}:/app" -w /app maven:3.9.9-eclipse-temurin-21-alpine mvn test
```

Covered today: sole-admin demotion, ownership 404, idempotency replay/conflict, email normalization.

---

## 13. Repository layout

```
Jobs180/
  docs/                 # Deep documentation (start here after README)
  src/main/java/com/companytracker/
    config/ security/ storage/ domain/ repo/ service/
    web/mvc/ web/api/ web/dto/ web/error/
  src/main/resources/
    db/migration/       # Flyway
    templates/ static/
  src/test/java/
  docker-compose.yml
  Dockerfile
  pom.xml
  .env.example
  README.md
```

---

## 14. Documentation map

| Document | Read when you want… |
|----------|---------------------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | End-to-end system design + defended layering |
| [docs/CONCEPTS.md](docs/CONCEPTS.md) | CS/web fundamentals mapped onto Jobs180 |
| [docs/DATA_MODEL.md](docs/DATA_MODEL.md) | Tables, constraints, cascades, indexes |
| [docs/SECURITY.md](docs/SECURITY.md) | Threat model, authn/authz, headers, limits |
| [docs/API.md](docs/API.md) | REST surface, conventions, idempotency |
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | Deploy, backup, scale, incidents |
| [docs/DECISIONS.md](docs/DECISIONS.md) | ADR-style choice log (X vs A/B/C) |
| [docs/PRODUCT.md](docs/PRODUCT.md) | Product rules, personas, edge-case catalog |
| [docs/REQUEST_LIFECYCLE.md](docs/REQUEST_LIFECYCLE.md) | One request through filters → DB → response |

---

## 15. Roadmap / non-goals

**Intentionally not in v1**

- OAuth/SAML, email verification, password reset mail
- Shared company workspaces / collaboration
- SPA-only frontend rewrite
- Antivirus pipeline for uploads
- GraphQL
- Billing / multi-tenant SaaS packaging

---

## 16. License / contributing posture

This repository is the Jobs180 product codebase. Prefer discussion via issues/PRs on GitHub. Keep changes aligned with the decision log: privacy-first, service-layer invariants, env-based deployability.

---

**Jobs180** — track the whole job loop, with engineering that does not fall apart when you actually rely on it.
