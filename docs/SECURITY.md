# Jobs180 — Security

## 1. Threat model

### In scope

- Internet attacker probing login, signup, APIs
- Authenticated user attempting **IDOR** on another user’s ids
- Browser CSRF from a malicious site while user is logged into Jobs180
- XSS via stored notes/titles if escaping failed
- Abuse: credential stuffing, spam signup, upload floods
- Admin mistakes: demoting the only admin; deleting a user who is online

### Out of scope (v1)

- Malicious admin with shell/DB access
- Physical theft of unlocked workstation
- Nation-state side channels
- Guaranteed malware-free uploads (no AV gateway yet)

---

## 2. Authentication design

### Chosen: email/password + server session (Redis)

| Alternative | Why rejected |
|-------------|--------------|
| Magic link only | Needs mail infra; worse offline/dev UX |
| OAuth-only | Couples self-host to Google/GitHub; account bootstrap harder |
| JWT access+refresh as primary | Revocation complexity; SSR cookie CSRF still needed if used from browser |
| API keys for humans | Bad UX for the HTML app |

### Password hashing: BCrypt(12)

Defended in [CONCEPTS.md](CONCEPTS.md). Rotate algorithm later if desired; store hashes opaquely.

### First-user admin bootstrap

| Alternative | Why rejected |
|-------------|--------------|
| **First signup = admin (chosen)** | Zero-config self-host |
| Env-seeded admin only | Requires secret distribution before first use |
| Open admin toggle in UI | Trivial takeover |

Concurrent bootstrap protected by `app_locks` + transaction.

---

## 3. Authorization design

### Layers

1. **Spring Security matchers** — `/admin/**`, `/api/v1/admin/**` need `ROLE_ADMIN`.
2. **OwnershipGuard** — owner or admin; else `NOT_FOUND`.
3. **DB constraints** — cannot create structurally invalid notes/resumes even if a bug skips validation.

### Why 404 instead of 403 for non-owners?

| Code | Effect |
|------|--------|
| 403 | Confirms resource exists |
| **404 (chosen)** | Ambiguous missing vs forbidden |

Admins still find resources via admin listings / cross-owner queries.

---

## 4. Session revocation & security stamps

When admin disables/deletes/demotes (or stamp rotates):

1. Persist new `security_stamp`.
2. Delete Redis sessions for that principal.
3. Filter rejects stale in-memory authentication on next request.

| Alternative | Why rejected |
|-------------|--------------|
| Wait for session TTL | User keeps access for minutes/hours |
| Only clear local replica session | Other replicas still authenticated |
| JWT blacklist | Needs shared blacklist ≈ Redis anyway; more moving parts |

---

## 5. CSRF strategy

- Cookie `XSRF-TOKEN` + request token for mutations.
- Exempt: `/api/v1/auth/login`, `/api/v1/auth/signup`, actuator health.

**Why exempt login/signup?**  
Clients need a way to establish a session before they can hold a CSRF token tied to that session. After login, CSRF applies.

**Why not disable CSRF globally for `/api/**`?**  
Browser JS on another origin could still abuse cookie auth if CORS were ever loosened carelessly. Keeping CSRF for authenticated API mutations is safer default for cookie sessions.

---

## 6. XSS & response headers

- Thymeleaf escaping
- CSP: default-src self; tight object-src/frame-ancestors
- `X-Frame-Options: DENY` (clickjacking)
- nosniff, referrer policy, permissions policy
- HSTS in `prod` profile (assumes HTTPS edge)

**Why allow `'unsafe-inline'` for styles?**  
Pragmatic for simple CSS/Bootstrap-less styling; scripts stay `'self'`. A future pass can nonce styles.

---

## 7. CORS

Default: no allowed origins. Jobs180 UI is same-site.

If you set origins, you are explicitly creating a cross-site API consumer—do it knowingly.

---

## 8. Rate limiting

Redis fixed windows:

| Bucket | Default intent |
|--------|----------------|
| Login / IP (+ email dimension in filter design) | Brute force |
| Signup / IP | Account spam |
| Authenticated writes / user | Runaway scripts |
| Uploads / user | Disk/S3 cost bombs |
| Global IP | Coarse DoS cushion |

Returns `429` + `Retry-After`.

**Why Redis not Guava/Bucket4j local?**  
Local limits don’t work across replicas—attackers spray nodes.

---

## 9. Uploads

- Extension allowlist: pdf/doc/docx
- Size cap via env
- Server-minted keys `{userId}/{idempotencyOrRandom}/{uuid}.ext`
- Stream downloads after ownership check

**Why not trust client Content-Type alone?**  
Clients lie. Extension allowlist is necessary but not perfect; content sniffing is best-effort.

---

## 10. Idempotency as safety

Not only a convenience—reduces duplicate side effects under retry storms (also an abuse amplifier if free). Keys expire (TTL) so Redis doesn’t grow forever.

---

## 11. Admin safety invariants

- Cannot demote/disable/soft-delete/hard-delete the last enabled admin.
- Self-demote allowed only if another enabled admin exists; then sessions revoke.

These exist because self-hosted apps die when operators lock themselves out.

---

## 12. Security test mindset

Automate at least:

- sole-admin rejection
- ownership 404
- idempotency conflict
- (manual/integration) session dead after admin disable

Add adversarial cases when touching auth filters.
