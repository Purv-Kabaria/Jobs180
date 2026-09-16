# Jobs180 — API

Base URL prefix: **`/api/v1`**

The HTML UI is not a second business backend—it calls the same services. This document defines the HTTP contract and defends its conventions.

---

## 1. Design goals

1. Resource-oriented URLs a human can guess.
2. Soft delete ≠ hard delete at the method level.
3. Stable machine error codes.
4. Safe retries via idempotency.
5. Cookie session auth compatible with the SSR app (CSRF after login).

---

## 2. Conventions (and rejected alternatives)

### Soft delete as POST, hard delete as DELETE

| Approach | Why accept/reject |
|----------|-------------------|
| **A. POST `/soft-delete` + DELETE hard (chosen)** | Unambiguous; matches UI wording |
| B. DELETE always soft | Then how do you purge? Extra headers are easy to miss |
| C. DELETE soft + `?force=true` hard | Dangerous footgun; proxies log poorly |

### PATCH for updates

Partial updates without inventing a verb zoo. Clients should send `version` for optimistic concurrency.

### Pagination envelope

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 12,
  "totalPages": 1
}
```

`size` is clamped (max 100). **Why clamp?** Unbounded `size=999999` is a trivial DoS.

### Errors

```json
{ "code": "SOLE_ADMIN", "message": "..." }
```

Optional `fields` on validation errors.

### Non-owner → 404

Defended in [SECURITY.md](SECURITY.md).

---

## 3. Authentication

### `POST /api/v1/auth/signup`

Body: `{ "email", "password", "idempotencyKey"? }`  
Header: `Idempotency-Key` recommended.  
Result: `201` user DTO; first user `ADMIN`.

### `POST /api/v1/auth/login`

Body: `{ "email", "password" }`  
Establishes Redis session cookie. CSRF exempt.

### `GET /api/v1/me`

Current principal identity.

**Why session cookies instead of Bearer JWT for v1?**  
One auth story for UI and API; immediate revoke; no token refresh machinery.

---

## 4. Resource catalog

### Dashboard / search

| Method | Path | Notes |
|--------|------|-------|
| GET | `/dashboard` | Counts + upcoming |
| GET | `/search/applications` | Filters + pagination |
| GET | `/upcoming-rounds?withinDays=` | Capped window |

### Companies

| Method | Path |
|--------|------|
| GET/POST | `/companies` |
| GET/PATCH | `/companies/{id}` |
| POST | `/companies/{id}/soft-delete` |
| POST | `/companies/{id}/restore` |
| DELETE | `/companies/{id}` |

### Applications

| Method | Path |
|--------|------|
| GET/POST | `/companies/{id}/applications` |
| GET/PATCH | `/applications/{id}` |
| POST | `/applications/{id}/soft-delete` |
| DELETE | `/applications/{id}` |

### Rounds

| Method | Path |
|--------|------|
| GET/POST | `/applications/{id}/rounds` |
| GET/PATCH | `/rounds/{id}` |
| POST | `/rounds/{id}/resume` body `{ "resumeId" }` |
| DELETE | `/rounds/{id}/resume` |
| POST/DELETE | soft-delete / hard-delete patterns as elsewhere |

### Resumes

| Method | Path |
|--------|------|
| GET/POST | `/resumes` (multipart supported) |
| GET | `/resumes/{id}/content` stream |
| POST/DELETE | soft/hard delete |

### Notes & resources

```
POST /{parentType}/{parentId}/notes
POST /{parentType}/{parentId}/resources
```

`parentType ∈ { companies, applications, rounds }`

### Admin

```
GET  /admin/users
POST /admin/users/{id}/role|enable|disable|soft-delete|restore
DELETE /admin/users/{id}
```

---

## 5. Idempotency contract

Header: `Idempotency-Key: <client-uuid>`

| Scenario | Result |
|----------|--------|
| First request | Execute; store status/id/hash |
| Retry same key + same payload hash | Replay; no duplicate side effects |
| Same key + different payload | `409 IDEMPOTENCY_CONFLICT` |
| Parallel in-flight same key | Lock; wait or `IDEMPOTENCY_IN_FLIGHT` |

**Why require clients to send keys instead of server-only dedupe?**  
Only the client knows which HTTP attempts are “the same user intent.”

TTL default 24h (`APP_IDEMPOTENCY_TTL_SECONDS`).

---

## 6. CSRF for authenticated API calls

After login, mutating calls from browsers must send CSRF header matching cookie. Non-browser scripts should either:

- use the HTML flow, or
- login then read `XSRF-TOKEN` cookie and echo it.

**Why keep CSRF on API?** Cookie session = browser-accessible authn.

---

## 7. Versioning policy

URL prefix `/api/v1` is a **compatibility fence**. Breaking changes go to `/api/v2`; v1 stays until deprecated.

| Alternative | Why not for v1 |
|-------------|----------------|
| Header versioning only | Harder to explore in browser |
| No version | Painful first break |

---

## 8. Example session

```bash
# signup
curl -c cookies.txt -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: s1' \
  -d '{"email":"you@example.com","password":"password123"}' \
  http://localhost:8080/api/v1/auth/signup

# login
curl -c cookies.txt -b cookies.txt -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"password123"}' \
  http://localhost:8080/api/v1/auth/login

# create company (add X-XSRF-TOKEN from cookies as needed)
curl -b cookies.txt -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: c1' \
  -d '{"name":"Acme"}' \
  http://localhost:8080/api/v1/companies
```
