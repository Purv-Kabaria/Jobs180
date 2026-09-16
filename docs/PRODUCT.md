# Jobs180 — Product rules & edge-case catalog

This is the **behavioral spec**: what the product promises when reality gets weird. Implementation lives in `service/*` and security filters; this document is the contract those classes must honor.

---

## 1. Personas

| Persona | Goals | Powers |
|---------|-------|--------|
| Seeker (USER) | Track own pipeline privately | CRUD own data; resume library |
| Operator (ADMIN) | Keep deployment healthy | All USER powers + manage users + access any data |
| Anonymous | Sign up / log in | No data access |

---

## 2. Core user journeys

1. Sign up → (if first) become admin → land on dashboard.  
2. Add company (optional research fields) → add application → set status.  
3. Upload resume(s) → add rounds → attach resume → jot notes/resources.  
4. Use dashboard/search/upcoming to prioritize.  
5. Archive completed/rejected trees; restore if mistake; hard purge when sure.

---

## 3. Status & outcome semantics

Application status is **pipeline-level**. Round outcome is **round-level**. They are intentionally independent: failing one round does not auto-set application to Rejected (operators decide).

Ghosted / On Hold exist so silence and pauses are not miscoded as Rejected.

---

## 4. Edge-case catalog

### 4.1 Auth & admin

| Case | Required behavior |
|------|-------------------|
| Two empty-DB signups concurrent | Exactly one ADMIN |
| Demote last enabled admin | Reject `SOLE_ADMIN` |
| Disable last enabled admin | Reject |
| Soft/hard delete last enabled admin | Reject |
| Admin demotes self with another admin present | Allowed; sessions revoked |
| Duplicate email (incl. soft-deleted) | Reject until hard purge |
| Weak password `< 8` | Reject |
| Signups disabled via env | Reject with clear code/message |
| Disabled / soft-deleted login | Fail authentication |

### 4.2 Live session vs account changes

| Case | Required behavior |
|------|-------------------|
| Admin disables logged-in user | Stamp rotate + Redis session wipe; next request → login |
| Admin soft/hard deletes logged-in user | Same |
| Admin changes role | Same |
| User has two browsers | All sessions die on revoke |
| In-flight request during purge | Mutations fail closed |

### 4.3 Cascades

| Case | Required behavior |
|------|-------------------|
| Soft-delete company | Cascade soft-delete apps→rounds→notes/resources; resumes untouched |
| Soft-delete application/round | Cascade children only |
| Soft-delete user | Soft-delete owned trees + resumes; revoke sessions; keep blobs |
| Restore user/company | Only `deletion_batch_id` matches |
| Hard purge user | Delete rows + MinIO keys; detach as needed |

### 4.4 Resumes & files

| Case | Required behavior |
|------|-------------------|
| Soft-delete resume still attached | Keep FK; UI shows archived; download policy per archive rules |
| Hard-delete resume | Null `resume_id` on rounds; delete blob |
| Neither file nor URL | Reject |
| Cross-user resume attach | Reject as not found |
| Oversized / bad type upload | Reject; no orphan metadata ideally |
| Download others’ file | 404 |

### 4.5 Notes/resources

| Case | Required behavior |
|------|-------------------|
| Zero or multiple parents | DB rejects |
| Empty resource (no url/file) | Reject |
| Add child on archived parent | Reject |

### 4.6 Concurrency & HTTP

| Case | Required behavior |
|------|-------------------|
| Stale `version` on PATCH | 409 OPTIMISTIC_LOCK |
| Double form submit | PRG + idempotency keys |
| Page size abuse | Clamp |
| CSRF mismatch | 403 with recover guidance |
| Rate limit exceeded | 429 |

### 4.7 Infrastructure

| Case | Required behavior |
|------|-------------------|
| Redis down | Do not pretend healthy multi-node auth |
| Postgres down | Readiness fail |
| One app node dies | Others serve using Redis sessions |

---

## 5. Admin ethics / product stance

Admin can see everything on the instance. Jobs180 is **not** marketed as end-to-end encrypted against operators. Self-hosters choosing shared deployments must trust their admin the same way they trust their email host.

---

## 6. Copy & UX principles (HTML)

- Prefer empty states over dead ends.
- Destructive actions ask confirm in UI.
- Flash messages for success/error after PRG.
- Brand name **Jobs180** is first-class in chrome (not a generic “Dashboard App”).

---

## 7. Acceptance checklist (manual)

- [ ] First signup is admin; second is user  
- [ ] User B cannot open User A company URL  
- [ ] Admin can open User A company  
- [ ] Sole admin demote blocked  
- [ ] Disable logged-in user → forced re-login  
- [ ] Idempotent company create replays  
- [ ] Resume upload then download works via MinIO  
- [ ] Soft-delete company hides apps; restore batch brings them back  
- [ ] Hard delete removes object from MinIO  
