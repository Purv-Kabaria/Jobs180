# Jobs180

**Jobs180** is a self-hostable job application tracker for companies, roles, interview rounds, resumes, notes, and prep resources.

It is a single **Spring Boot** app with a **Thymeleaf** UI and a versioned JSON API (`/api/v1`), backed by **PostgreSQL**, **Redis**, and **MinIO** (S3-compatible object storage).

| | |
|--|--|
| Product | Jobs180 |
| Maven artifact | `jobs180` |
| Java package | `com.companytracker` |
| Repository | [github.com/Purv-Kabaria/Jobs180](https://github.com/Purv-Kabaria/Jobs180) |

---

## Table of contents

1. [Overview](#1-overview)
2. [Who it is for](#2-who-it-is-for)
3. [Features](#3-features)
4. [Quick start](#4-quick-start)
5. [Architecture](#5-architecture)
6. [Stack](#6-stack)
7. [Domain model](#7-domain-model)
8. [Security](#8-security)
9. [API & UI](#9-api--ui)
10. [Configuration](#10-configuration)
11. [Operations](#11-operations)
12. [Development & tests](#12-development--tests)
13. [Repository layout](#13-repository-layout)
14. [Documentation](#14-documentation)
15. [Roadmap / non-goals](#15-roadmap--non-goals)
16. [Contributing](#16-contributing)

---

## 1. Overview

Jobs180 is a personal system of record for a job search: hierarchy (company → role → round), private data, file attachments, retries, concurrent edits, and admin lifecycle.

- Redis-backed sessions (multi-replica ready)
- Object storage for resumes and resources
- Rate limits and idempotent writes
- Soft-delete with restore; hard purge when needed
- Sole-admin protection (cannot remove the last enabled admin)

---

## 2. Who it is for

| Audience | Notes |
|----------|-------|
| Individual job seekers | Primary audience |
| Small shared deployments | Each user is isolated; first user is admin |
| Agencies / multi-tenant SaaS | Out of scope for v1 |
| Enterprise SSO | Out of scope for v1 (no OAuth/SAML yet) |

---

## 3. Features

### Accounts & roles

- Email + password signup/login
- First registered user is `ADMIN`; later users are `USER`
- Admin can promote/demote, enable/disable, soft-delete/restore/hard-purge users
- Sole-admin guards on demote/disable/delete
- Sessions revoked when an admin changes a user’s status or role

### Job-hunt data

- Companies (including companies with no roles yet)
- Applications with JD text/link, work mode, compensation, dates, referral, source, status
- Rounds with schedule, outcome, interviewers, optional resume attachment
- Resume library (PDF/DOC/DOCX upload and/or external URL)
- Notes and resources on company, application, and round
- Soft delete → restore by deletion batch → hard purge (DB + object store)

### Surfaces

- Dashboard (status counts + upcoming rounds)
- Search/filter applications; upcoming rounds list
- Thymeleaf UI and `/api/v1` JSON API

### Platform

- Redis: sessions, dashboard cache, rate limits, idempotency
- MinIO/S3 file storage with streaming download
- Docker Compose bring-up
- Flyway migrations; Hibernate `validate` in prod

---

## 4. Quick start

### Prerequisites

- Docker + Docker Compose
- Ports: `8080`, `5432`, `6379`, `9000`, `9001`

### Run

```bash
git clone https://github.com/Purv-Kabaria/Jobs180.git
cd Jobs180
docker compose up --build
```

Open **http://localhost:8080** and sign up. The first account is admin.

| Service | URL / connection |
|---------|------------------|
| App | http://localhost:8080 |
| Postgres | `localhost:5432` — db `company_tracker`, user/pass `tracker` / `tracker` |
| Redis | `localhost:6379` |
| MinIO API | http://localhost:9000 |
| MinIO console | http://localhost:9001 — `minioadmin` / `minioadmin` |

The Postgres database name is still `company_tracker` in this revision. The product name is Jobs180.

### First steps

1. Sign up as admin.
2. Create a company.
3. Add a role/application; set status.
4. Upload a resume under **Resumes**.
5. Add a round; attach the resume; add notes/resources.

---

## 5. Architecture

```
                 ┌─────────────────────────────────────────┐
   Browser/API ─►│  Jobs180 (Spring Boot)                  │
                 │  Thymeleaf MVC  +  /api/v1 REST         │
                 └───────┬───────────────┬─────────┬───────┘
                         │               │         │
                    PostgreSQL         Redis      MinIO/S3
                   (source of         sessions    files
                    truth)            cache
                                      rate limit
                                      idempotency
```

Postgres holds durable data. Redis holds shared ephemeral state (sessions, cache, rate limits, idempotency). MinIO/S3 holds files. App instances are otherwise stateless.

Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## 6. Stack

| Area | Choice |
|------|--------|
| Runtime | Java 21, Spring Boot 3 |
| Shape | Single deployable monolith |
| UI + API | Thymeleaf SSR and `/api/v1` REST (shared services) |
| Database | PostgreSQL + Flyway |
| Files | MinIO / S3 API |
| Coordination | Redis (sessions, cache, rate limits, idempotency) |
| Passwords | BCrypt |
| Ownership misses | HTTP 404 (not 403) |

Longer decision notes: [docs/DECISIONS.md](docs/DECISIONS.md), [docs/SECURITY.md](docs/SECURITY.md).

---

## 7. Domain model

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

**Application statuses:** Wishlist → Applied → OA → Interviewing → Offer / Rejected / Withdrawn / Ghosted / On Hold  

**Round outcomes:** Pending / Pass / Fail / Skipped  

Soft delete uses `deleted_at` + `deletion_batch_id` so restore is scoped to one deletion batch.

Schema: [docs/DATA_MODEL.md](docs/DATA_MODEL.md).

---

## 8. Security

- Email/password auth; Redis session cookie
- Spring roles plus ownership checks in the service layer
- CSRF on mutating requests (API login/signup exempt)
- Thymeleaf escaping + CSP
- CORS deny-by-default
- Rate limits (login/signup/write/upload/IP)
- Security stamp + session wipe on disable/delete/role change
- Upload allowlisting, size caps, server-generated object keys

More: [docs/SECURITY.md](docs/SECURITY.md), [docs/CONCEPTS.md](docs/CONCEPTS.md).

---

## 9. API & UI

### UI

Pages under `/dashboard`, `/companies`, `/applications`, `/rounds`, `/resumes`, `/search`, `/upcoming`, `/admin/users`. Forms use Post/Redirect/Get and idempotency keys.

### API

Base path: **`/api/v1`**. Soft-delete/restore are `POST` actions; `DELETE` is hard purge. Send `Idempotency-Key` on creates and destructive actions.

Routes: [docs/API.md](docs/API.md).

---

## 10. Configuration

Copy [`.env.example`](.env.example). Configuration is environment-driven.

| Variable group | Purpose |
|----------------|---------|
| `SPRING_DATASOURCE_*` | Postgres |
| `SPRING_DATA_REDIS_*` | Redis |
| `SPRING_PROFILES_ACTIVE` | Use `prod` in Compose/cloud |
| `APP_STORAGE_TYPE` | `s3` (default) or `local` (tests) |
| `APP_S3_*` | Endpoint, bucket, keys, region, path-style |
| `APP_UPLOAD_MAX_BYTES` | Multipart cap |
| `APP_IDEMPOTENCY_TTL_SECONDS` | Idempotency TTL |
| `APP_RATE_LIMIT_*` | Per-action limits |
| `APP_CORS_ALLOWED_ORIGINS` | Empty = deny cross-origin |
| `APP_SIGNUPS_ENABLED` | Signup kill-switch |
| `JAVA_OPTS` | JVM flags |

Ops: [docs/OPERATIONS.md](docs/OPERATIONS.md).

---

## 11. Operations

### Backup

1. Postgres dump/snapshot
2. MinIO/S3 bucket (resumes/resources)
3. Redis optional (sessions/cache; users re-login if lost)

### Scale-out

- App replicas: stateless
- Shared Redis, Postgres primary, and S3/MinIO for files
- Do not use local disk storage for multi-node

### Failure behavior

| Event | Behavior |
|-------|----------|
| App instance dies | Other replicas continue; sessions in Redis |
| Admin disables a logged-in user | Sessions revoked |
| Double-submit create | Idempotency key returns the same result |
| Login brute force | 429 after rate-limit threshold |
| Redis down | Readiness fails; do not fall back to local sessions in prod |

---

## 12. Development & tests

Guide: [docs/TESTING.md](docs/TESTING.md).

```bash
# Full suite — Docker required for Testcontainers (Postgres 16 + Redis 7)
mvn test
```

| Layer | Coverage |
|-------|----------|
| Unit | Auth, sole-admin, ownership, optimistic lock, idempotency, uploads, filters |
| Integration | Cascades, Redis idempotency, isolation, session revoke |
| E2E (MockMvc) | Signup → company → app → round → notes; CSRF; admin gate; MVC login |
| Performance | List/search budgets under hundreds of rows |

---

## 13. Repository layout

```
Jobs180/
  docs/
  src/main/java/com/companytracker/
    config/ security/ storage/ domain/ repo/ service/
    web/mvc/ web/api/ web/dto/ web/error/
  src/main/resources/
    db/migration/
    templates/ static/
  src/test/java/
  docker-compose.yml
  Dockerfile
  pom.xml
  .env.example
  README.md
```

---

## 14. Documentation

| Document | Contents |
|----------|----------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design |
| [docs/CONCEPTS.md](docs/CONCEPTS.md) | Concepts mapped to Jobs180 |
| [docs/DATA_MODEL.md](docs/DATA_MODEL.md) | Tables, constraints, indexes |
| [docs/SECURITY.md](docs/SECURITY.md) | Authn/authz, headers, limits |
| [docs/API.md](docs/API.md) | REST surface |
| [docs/TESTING.md](docs/TESTING.md) | Test suite and performance budgets |
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | Deploy, backup, scale, incidents |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Decision log |
| [docs/PRODUCT.md](docs/PRODUCT.md) | Product rules and edge cases |
| [docs/REQUEST_LIFECYCLE.md](docs/REQUEST_LIFECYCLE.md) | Request path through the stack |

---

## 15. Roadmap / non-goals

**Not in v1**

- OAuth/SAML, email verification, password-reset mail
- Shared company workspaces / collaboration
- SPA-only frontend
- Antivirus pipeline for uploads
- GraphQL
- Billing / multi-tenant packaging

---

## 16. Contributing

Open issues and PRs on GitHub. Prefer changes that keep privacy defaults, service-layer invariants, and env-based deployability.
