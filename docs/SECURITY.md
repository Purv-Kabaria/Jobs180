# Security

## Threat model (practical)

We assume:

- The network may be hostile (brute force, CSRF, XSS).
- Users should not see each other’s data.
- Admins are trusted operators but must not lock themselves out (sole-admin rules).
- App instances may be restarted or multiplied at any time.

We do **not** claim protection against a fully compromised server or malicious admin with DB access.

## Authentication

- Form login (Thymeleaf) and API login (`POST /api/v1/auth/login`) establish a **server session**.
- Session data lives in **Redis** (`Spring Session`), cookie holds the session id.
- Passwords: **BCrypt** (cost factor 12 by default).

### First user = admin

Under a DB lock, if there are zero users, signup assigns `ADMIN`; otherwise `USER`. This bootstraps ops without baking credentials into images.

## Authorization

1. Spring Security URL rules (`/admin/**` requires `ROLE_ADMIN`).
2. Service-layer **OwnershipGuard**: owner or admin; else `NOT_FOUND`.
3. File download checks ownership before streaming.

## Session revocation

On disable / soft-delete / hard-delete / role change:

1. Rotate `security_stamp`.
2. Delete that user’s sessions in Redis.
3. `SecurityStampFilter` compares stamp on each request; mismatch → logout / 401.

So an admin deleting a logged-in user takes effect on the **next request**, not after cookie TTL.

## CSRF

State-changing browser calls need a CSRF token. Cookie `XSRF-TOKEN` (readable by JS) + form field / `X-XSRF-TOKEN` header.

API auth endpoints (`/api/v1/auth/login|signup`) skip CSRF so non-browser clients can bootstrap a session; afterward CSRF applies like the browser.

## XSS & headers

- Output escaping via Thymeleaf defaults.
- CSP, `X-Frame-Options: DENY`, nosniff, referrer & permissions policies.
- HSTS enabled when `prod` profile is active (assume HTTPS terminator).

## CORS

Empty allowlist = no cross-origin API use. Set `APP_CORS_ALLOWED_ORIGINS` only if a separate frontend origin must call the API with cookies.

## Rate limiting

Redis fixed-window limits on login, signup, writes, uploads, and per-IP traffic. Returns `429` with `Retry-After`.

## Uploads

- Allowlist extensions/MIME (PDF/DOC/DOCX).
- Max size via config.
- Server-minted object keys only.
- Store in object storage after validation; avoid orphan metadata when possible.

## Idempotency & abuse

Idempotency keys are scoped per user (or anon IP for signup) and expire—reducing accidental duplication without becoming a forever log.

## Error code hygiene

Stable codes (`SOLE_ADMIN`, `IDEMPOTENCY_CONFLICT`, `RATE_LIMITED`, …) help clients; messages stay generic where enumeration matters (e.g. login failures).
