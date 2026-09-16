# Operations

## Local run

```bash
docker compose up --build
```

Services: app `:8080`, Postgres `:5432`, Redis `:6379`, MinIO `:9000` / console `:9001`.

First visit `/signup`. That user is admin.

## Configuration (12-factor)

All runtime knobs are environment variables. See [`.env.example`](../.env.example).

Same container image should run in Compose and in cloud by changing:

- datasource URL
- Redis host
- `APP_S3_*` endpoint/credentials/bucket

## Profiles

- Default/`application.yml` — local-friendly Thymeleaf cache off
- `prod` — Thymeleaf cache on, secure cookies, HSTS, stricter health details

Compose sets `SPRING_PROFILES_ACTIVE=prod`.

## Health

- `/actuator/health` — aggregate
- Kubernetes-style probes enabled via Boot (`liveness` / `readiness`)

Do not expose sensitive actuator endpoints publicly without auth.

## Backups

1. **Postgres** — logical dump (`pg_dump`) or managed snapshots.
2. **MinIO/S3** — bucket versioning / replication as needed.
3. **Redis** — optional AOF volume in Compose; treat as disposable for sessions/cache (users re-login if wiped).

Restore order: database first, then ensure object keys referenced by rows still exist.

## Scaling checklist

- [ ] `APP_STORAGE_TYPE=s3` (never local disk for multi-node)
- [ ] Shared Redis for sessions + rate limits + idempotency
- [ ] Shared Postgres
- [ ] Stateless app replicas behind a load balancer
- [ ] TLS terminator sets forwarded headers (Boot `forward-headers-strategy=framework`)

## JVM

`JAVA_OPTS` (e.g. `-XX:MaxRAMPercentage=75.0`) controls heap inside containers.

## Migrations

Flyway owns schema. `ddl-auto=validate` in prod—**never** auto-mutate production schema from Hibernate.

## Incident switches

- `APP_SIGNUPS_ENABLED=false` — stop new registrations
- Rate limit env vars — tighten under abuse
- Disable user in admin UI — immediate session revoke

## Logs

Prefer structured messages with user ids on admin actions; avoid logging passwords, session ids, or file contents.
