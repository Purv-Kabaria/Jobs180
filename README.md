# Company Tracker

Personal **job application tracker** with admin/user roles, built as a deployable Spring Boot system (Postgres + Redis + MinIO).

## Documentation map

| Doc | What’s inside |
|-----|----------------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System shape, layers, request flow, why each piece exists |
| [docs/CONCEPTS.md](docs/CONCEPTS.md) | CS & web fundamentals mapped to this codebase |
| [docs/DATA_MODEL.md](docs/DATA_MODEL.md) | Schema, ownership, soft delete, indexes |
| [docs/SECURITY.md](docs/SECURITY.md) | Authn/authz, CSRF/XSS/CORS, rate limits, session revoke |
| [docs/API.md](docs/API.md) | REST conventions, routes, idempotency |
| [docs/OPERATIONS.md](docs/OPERATIONS.md) | Compose, env, backups, scaling |
| [docs/DECISIONS.md](docs/DECISIONS.md) | Tradeoff log |

Start with **ARCHITECTURE** then **CONCEPTS** if you are learning; start with **OPERATIONS** if you only need to run it.

## Features

- Roles: `ADMIN` / `USER` (first signup becomes admin)
- Per-user private data; admins can manage all users and data
- Companies → applications → interview rounds
- Resume library (upload and/or URL), attach to rounds
- Notes and resources on company, application, and round
- Dashboard, search, upcoming rounds
- Soft delete / restore / hard purge with cascade rules
- Redis sessions, cache, rate limits, idempotency keys
- REST API under `/api/v1` + server-rendered Thymeleaf UI

## Quick start

```bash
docker compose up --build
```

Open http://localhost:8080 — sign up (first user is **ADMIN**).

| Service | URL / port |
|---------|------------|
| App | http://localhost:8080 |
| Postgres | localhost:5432 (`tracker` / `tracker` / `company_tracker`) |
| Redis | localhost:6379 |
| MinIO API | http://localhost:9000 |
| MinIO console | http://localhost:9001 (`minioadmin` / `minioadmin`) |

## Configuration

Copy [`.env.example`](.env.example). Runtime is env-driven (same image for local and cloud).

Highlights: `SPRING_DATASOURCE_*`, `SPRING_DATA_REDIS_*`, `APP_S3_*`, `APP_RATE_LIMIT_*`, `APP_IDEMPOTENCY_TTL_SECONDS`, `APP_SIGNUPS_ENABLED`.

## Stack

Java 21 · Spring Boot 3 · Thymeleaf · Spring Security · Spring Data JPA · Flyway · PostgreSQL · Redis · MinIO (S3 API)

## Project layout

```
src/main/java/com/companytracker/
  config/ security/ storage/ domain/ repo/ service/
  web/mvc/ web/api/
src/main/resources/db/migration/
docs/
docker-compose.yml
Dockerfile
```

## Tests

```bash
docker run --rm -v "${PWD}:/app" -w /app maven:3.9.9-eclipse-temurin-21-alpine mvn test
```

(or `mvn test` with JDK 21 installed)
