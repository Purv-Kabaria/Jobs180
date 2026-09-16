# Jobs180 — Operations

How to run, configure, back up, scale, and survive incidents.

---

## 1. Local bring-up

```bash
docker compose up --build
```

Services: app, postgres, redis, minio, minio-init.

App: http://localhost:8080  

### Why Compose as the default path?

| Approach | Verdict |
|----------|---------|
| **A. Compose (chosen)** | One file mirrors prod-shaped deps |
| B. “Install Postgres/Redis/MinIO yourself” README | High friction; drift from tested combo |
| C. Embedded H2+mock storage only | Doesn’t exercise real session/object paths |

---

## 2. Configuration philosophy

**Same artifact, different environment.** Secrets and URLs never baked into the image.

See [`.env.example`](../.env.example).

### Critical variables

| Variable | Meaning |
|----------|---------|
| `SPRING_DATASOURCE_*` | Postgres |
| `SPRING_DATA_REDIS_*` | Redis |
| `SPRING_PROFILES_ACTIVE=prod` | Secure cookies, template cache, HSTS |
| `APP_S3_*` | Object storage (bucket default `jobs180`) |
| `APP_STORAGE_TYPE` | `s3` prod/dev Compose; `local` tests |
| `APP_SIGNUPS_ENABLED` | Kill switch |
| `APP_RATE_LIMIT_*` | Abuse controls |
| `APP_IDEMPOTENCY_TTL_SECONDS` | Retry window |
| `APP_CORS_ALLOWED_ORIGINS` | Opt-in cross-origin |
| `JAVA_OPTS` | Container memory policy |

### Why path-style S3 for MinIO?

MinIO commonly requires path-style addressing. Virtual-host style is typical on AWS. `APP_S3_PATH_STYLE=true` makes local MinIO work; set appropriately for your cloud vendor.

---

## 3. Profiles

| Profile | Behavior |
|---------|----------|
| default | Dev-friendly Thymeleaf cache off |
| `prod` | Template cache on; secure session cookies; HSTS; less health detail leakage |

Compose runs `prod`.

---

## 4. Health & probes

Actuator exposes health/info. Use readiness to gate traffic on DB/Redis availability.

**Do not** expose unauthenticated detailed health with secrets on a public internet without a proxy ACL.

---

## 5. Backups

### What matters

1. **Postgres** — without it, Jobs180 is empty.  
2. **Object bucket** — resumes/resources.  
3. **Redis** — optional; losing it logs everyone out and clears RL/idempotency/cache.

### Suggested cadence (self-host)

- Daily Postgres dump + bucket sync
- Weekly restore drill to a scratch environment

### Restore order

1. Restore Postgres  
2. Restore/ensure bucket objects for keys referenced by rows  
3. Start app  
4. Accept Redis cold start

---

## 6. Scaling

### Horizontal app replicas

Requirements:

- Shared Redis
- Shared Postgres
- S3/MinIO (**not** local disk)
- LB without sticky sessions

### Vertical

Raise `JAVA_OPTS` / container memory; tune Hikari pool via env.

### What we don’t do in v1

- Read replicas / CQRS
- Redis Cluster specifics (document managed Redis instead)
- Multi-region active-active

---

## 7. Incident playbooks (short)

| Incident | Action |
|----------|--------|
| Signup spam | `APP_SIGNUPS_ENABLED=false`; tighten signup rate limit |
| Credential stuffing | Tighten login rate limit; disable abused accounts |
| Accidental user delete | Soft-delete → restore by batch; hard delete only if sure |
| Locked out as sole admin | Prevented by guards; if DB surgery needed, restore from backup |
| Disk/S3 full | Uploads fail; free space; raise quota |
| Redis outage | Fix Redis; do not run split in-memory sessions |

---

## 8. Migrations

Flyway on startup. Prod Hibernate `ddl-auto=validate`.

**Never** use `update` against production. Schema changes = new Flyway files reviewed in PRs.

---

## 9. Observability hygiene

Log admin mutations with actor id + target id. Never log passwords, session ids, or file contents. Prefer correlation ids at the reverse proxy.

---

## 10. Renaming notes

Product/repo: **Jobs180**.  
Postgres DB name may still be `company_tracker` in Compose for continuity—treat as internal identifier until a dedicated rename migration is scheduled.
