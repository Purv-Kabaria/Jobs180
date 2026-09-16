# API design

Base path: `/api/v1`  
UI routes mirror the same resources under `/companies`, `/applications`, etc.

## Dual interface, one domain

Controllers (MVC and REST) are thin. **Services** own rules. That keeps HTML and JSON consistent (same soft-delete semantics, same ownership errors).

## Conventions

| Topic | Choice | Reasoning |
|-------|--------|-----------|
| Soft delete | `POST …/soft-delete` | Don’t overload `DELETE` |
| Restore | `POST …/restore` | Explicit action |
| Hard purge | `DELETE …` | Irreversible only |
| Partial update | `PATCH` | Sparse field updates |
| Lists | `{ content, page, size, totalElements, totalPages }` | Predictable pagination envelope |
| Errors | `{ code, message, fields? }` | Machine-stable `code` |
| Non-owner | `404 NOT_FOUND` | Less enumeration |
| Versions | body `version` / conflict `409` | Optimistic concurrency |

## Auth

```
POST /api/v1/auth/signup
POST /api/v1/auth/login
POST /api/v1/auth/logout   # via form logout on UI; session invalidate
GET  /api/v1/me
```

Login returns session cookie. Subsequent mutating calls need CSRF (except signup/login).

## Core resources

```
GET|POST            /companies
GET|PATCH           /companies/{id}
POST                /companies/{id}/soft-delete|restore
DELETE              /companies/{id}

GET|POST            /companies/{id}/applications
GET|PATCH           /applications/{id}
POST|DELETE         /applications/{id}/soft-delete | /applications/{id}

GET|POST            /applications/{id}/rounds
GET|PATCH           /rounds/{id}
POST                /rounds/{id}/resume     { "resumeId": n }
DELETE              /rounds/{id}/resume

GET|POST            /resumes
GET                 /resumes/{id}/content   # stream download

POST                /{parentType}/{parentId}/notes
POST                /{parentType}/{parentId}/resources
# parentType ∈ companies | applications | rounds

GET /dashboard
GET /search/applications?company=&status=&from=&to=&page=
GET /upcoming-rounds?withinDays=14
```

## Admin

```
GET    /admin/users
POST   /admin/users/{id}/role|enable|disable|soft-delete|restore
DELETE /admin/users/{id}
```

Sole-admin protections return `409 SOLE_ADMIN`.

## Idempotency

Send header:

```
Idempotency-Key: <uuid>
```

Recommended/required on creates and destructive actions. Replay returns the original logical result without duplicating rows or object-store puts.

## Pagination & clamping

`page` defaults to `0`; `size` defaults to `20` and is **clamped to max 100** to prevent accidental full-table pulls (resource exhaustion).

## Example: create company

```http
POST /api/v1/companies
Content-Type: application/json
Idempotency-Key: 7b1e…
Cookie: SESSION=…; XSRF-TOKEN=…
X-XSRF-TOKEN: …

{"name":"Acme","website":"https://acme.example"}
```

```http
HTTP/1.1 201
{"id":1,"name":"Acme",...}
```
