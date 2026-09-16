# Jobs180 — Request lifecycle

A single request, dissected. Use this when debugging “why did I get 302/404/429/409?”

---

## 1. Entry

Client → (optional reverse proxy with TLS) → Tomcat in the Jobs180 JVM.

Forward headers enabled so secure cookies / redirects honor `X-Forwarded-*` behind proxies.

---

## 2. Filter chain (conceptual order)

```
RateLimitFilter
    │  Redis INCR; maybe 429
    ▼
Spring Security (CORS → CSRF → Session → Authorization matchers)
    │  maybe 302 login / 403 CSRF
    ▼
SecurityStampFilter
    │  stamp mismatch → logout / 401 JSON / redirect login
    ▼
DispatcherServlet → Controller
```

### Why rate limit before auth success paths?

Login/signup are expensive (BCrypt, user lookups) and are the first abuse targets. Limiting early saves CPU and reduces lock pressure.

### Why stamp filter after Security authenticates?

We need a principal to check. Stamp comparison catches “session still in Redis but user was revoked on another node microseconds ago” if session delete raced—belt and suspenders with revocation service.

---

## 3. Controller responsibilities

Controllers **may**:

- bind and validate DTOs (`@Valid`)
- translate HTTP ↔ service calls
- choose view names or JSON bodies
- issue PRG redirects

Controllers **must not**:

- implement ownership rules
- perform cascades
- talk to MinIO directly (go through services/storage)

---

## 4. Service transaction boundary

Typical mutating call:

```
idempotency.execute(scope, key, payloadHashable, () -> {
    load entity
    ownershipGuard…
    mutate / cascade
    save
    return entity
}, reloadById)
```

- On miss: run supplier inside normal `@Transactional` service method.  
- On hit: reload by id (avoid serializing JPA proxies into Redis).

---

## 5. Persistence & storage ordering

**Upload create resume (happy path):**

1. Validate label + file/url constraints.  
2. Put object to MinIO (server key).  
3. Insert resume row with metadata.  

If (3) fails after (2), compensating delete of object is best-effort—prefer validate-before-put and transactional clarity.

**Download:**

1. Load resume; ownership check.  
2. Open stream from storage.  
3. Copy to response (no full buffer).

---

## 6. Response paths

| Path | Shape |
|------|-------|
| MVC success mutate | `302` redirect + flash |
| MVC read | `200` Thymeleaf |
| API success create | `201` + JSON |
| API success read | `200` + JSON |
| Domain rule fail | `4xx` + `{code,message}` (API) or error/flash (MVC) |

---

## 7. Worked examples

### Example A — IDOR attempt

`GET /companies/42` as user 5, company owned by user 9.

1. Auth OK.  
2. Service loads company 42.  
3. OwnershipGuard fails → `NOT_FOUND`.  
4. API returns 404 JSON; MVC error page/redirect policy applies.

### Example B — Admin disables online user

1. Admin POST disable.  
2. `enabled=false`, stamp rotates, sessions deleted in Redis.  
3. Victim’s next request: Security loads session miss **or** stamp filter fails.  
4. Victim sees login with session-revoked reason.

### Example C — Duplicate create with same Idempotency-Key

1. First POST creates company id=7; Redis stores hash+id.  
2. Network retries same POST.  
3. Service returns company 7 again; no second insert.

### Example D — Rate limited login

1. Redis counter for IP exceeds threshold.  
2. Filter returns 429 before authentication.  
3. BCrypt never runs for excess attempts in that window.

---

## 8. Debugging checklist

1. Check Compose: postgres/redis/minio healthy.  
2. Check app logs for Flyway/Security startup.  
3. For API mutations: cookie + CSRF header present?  
4. For 404: wrong id or ownership?  
5. For 409: version mismatch or idempotency conflict?  
6. For 429: wait window or adjust env limits in non-prod carefully.
