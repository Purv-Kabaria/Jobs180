# Design decisions

Short record of **choices and tradeoffs**. For narrative, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Product

| Decision | Choice | Alternative considered | Why |
|----------|--------|------------------------|-----|
| App type | Job application tracker | Company CRM only | User intent: pipeline + rounds + resumes |
| Privacy | Per-user private data | Shared workspace | Simpler threat model; personal tracker |
| First admin | First signup | Seed env user | Zero-config bootstrap for self-host |
| UI | Thymeleaf + API | SPA-only | Faster CRUD delivery; API still available |

## Platform

| Decision | Choice | Why |
|----------|--------|-----|
| Language | Java 21 | Long-term support, strong Spring ecosystem |
| Framework | Spring Boot 3 | Security, JPA, Session, Actuator integration |
| DB | PostgreSQL 16 | Constraints, indexes, ops maturity |
| Cache/session | Redis 7 | Multi-replica coordination |
| Files | MinIO (S3 API) | Portable object storage |
| Migrations | Flyway | Explicit, reviewable schema history |

## Domain

| Decision | Choice | Why |
|----------|--------|-----|
| Soft delete | `deleted_at` + batch id | Undo without resurrecting unrelated archives |
| Optimistic lock | `@Version` | Catch concurrent edits cheaply |
| Resume attach | FK nullable + SET NULL | Library entries outlive round links |
| Status storage | TEXT + CHECK | Easier migrations than PG ENUM |

## Security

| Decision | Choice | Why |
|----------|--------|-----|
| Session store | Redis | Horizontal scale |
| Password hash | BCrypt(12) | Standard, slow-by-design |
| Non-owner | 404 | Reduce enumeration |
| Sole admin | Block demote/disable/delete | Avoid lockout |
| CSRF on API | Required after login | Browser-safe cookies |

## API

| Decision | Choice | Why |
|----------|--------|-----|
| Soft delete method | POST action | Clear semantics vs DELETE |
| Idempotency | Redis keys | Safe retries |
| Page size cap | 100 | DoS / accident protection |

## Explicit non-goals (v1)

- OAuth / magic links / email verification
- Cross-user sharing
- Antivirus scanning of uploads
- Read replicas / CQRS
- GraphQL
