# Jobs180 — Data model

This document explains the **schema as a set of intentional decisions**, not merely a table dump. SQL source of truth: [`V1__schema.sql`](../src/main/resources/db/migration/V1__schema.sql).

---

## 1. Design goals

1. Model the job-hunt hierarchy without forcing a role to exist for every company.
2. Make authorization queries cheap (`owner_id` filters).
3. Enforce critical invariants in the database (last line of defense).
4. Support soft delete + **selective** restore.
5. Keep migrations boring and reversible in spirit (TEXT+CHECK over PG ENUM).

---

## 2. Logical ER

```
UserAccount
 ├─ Resume (library)
 └─ Company
     ├─ Note / ResourceItem (parent = company)
     └─ JobApplication
         ├─ Note / ResourceItem (parent = application)
         └─ Round
             ├─ Resume? (optional FK)
             └─ Note / ResourceItem (parent = round)
```

`app_locks` is orthogonal: a singleton lock table for bootstrap races.

---

## 3. Table-by-table reasoning

### `users`

| Column | Why |
|--------|-----|
| `email` | Login identifier; normalized to lowercase in app |
| `password_hash` | BCrypt output only |
| `role` | `USER` \| `ADMIN` |
| `enabled` | Soft disable without deleting data |
| `security_stamp` | Session invalidation generation |
| `deleted_at` / `deletion_batch_id` | Soft purge lifecycle |
| `version` | Optimistic locking for admin edits |

**Unique `LOWER(email)` including soft-deleted:**  
Prevents “delete then recreate email” takeover confusion until hard purge.

**Why not username + email?**  
One identifier reduces UX and unique-constraint complexity for v1.

### `app_locks`

Holds `first_admin`. Signup locks this row so two concurrent empty-DB signups cannot both become admin.

| Alternative | Why not |
|-------------|---------|
| Application-level synchronized | Doesn’t work across replicas |
| Unique partial index alone | Doesn’t serialize “count then insert” cleanly without retry loops |
| SERIALIZABLE transactions only | Harder to reason about; lock row is explicit |

### `companies`

Research-first: **zero applications allowed**. Fields cover identity + career page. Soft-deleting a company cascades in **services** (not DB `ON DELETE CASCADE`) so we control batch ids and blob retention.

**Why service cascades instead of FK ON DELETE CASCADE?**  
DB cascades are blunt: hard to attach `deletion_batch_id`, hard to soft-delete, easy to surprise hard-delete files. Application cascades are explicit and testable.

### `job_applications`

Named to avoid “role” confusion with security roles. Status codes are stable machine strings (`OA`, `GHOSTED`, …).

**Why store status as TEXT + CHECK instead of PostgreSQL ENUM?**

| Approach | Pros | Cons |
|----------|------|------|
| **TEXT + CHECK (chosen)** | Easy Flyway updates; readable dumps | Slightly weaker than native enum typing |
| PG ENUM | Typed | Painful `ALTER TYPE`; awkward ORM mapping |
| Lookup table | Extensible UI | Extra joins for little v1 value |

### `resumes`

Library at user scope. Must have `storage_key` and/or non-empty `external_url`.

**Why allow both file and URL?**  
Real seekers keep Drive links *and* PDF variants. Forcing one rejects real workflows.

### `rounds`

Belongs to an application; optional `resume_id` with **`ON DELETE SET NULL`**.

**Why SET NULL on resume hard delete?**  
Rounds are historical facts (“we interviewed”). Deleting a resume file should not delete the round; it should detach.

### `notes` / `resources`

Polymorphic parent via three nullable FKs + CHECK that **exactly one** is set.

| Alternative | Why not |
|-------------|---------|
| Separate note tables per parent | Schema explosion |
| `parent_type` + `parent_id` without FK | Weaker integrity; orphan risk |
| JSON blob of notes on parent row | Poor query/index; rewrite-heavy updates |

Resources allow URL and/or file—same rationale as resumes.

---

## 4. Ownership denormalization

Every tenant table includes `owner_id`.

**Defended against pure normalization:**

- List “my companies/apps/rounds” stays index-friendly.
- Authz does not require join chains that can be wrong under soft-delete of an intermediate parent.

**Cost:** writers must copy owner from parent and reject cross-owner attaches. Jobs180 services do this centrally.

---

## 5. Index strategy

Partial indexes `WHERE deleted_at IS NULL` keep hot paths on active rows:

- companies by owner (+ lower name)
- applications by owner+status, company, applied_date
- rounds by owner+scheduled_at, application
- notes/resources by parent and owner

**Why partial?**  
Soft-deleted rows would otherwise pollute indexes and slow active lists as archives grow.

---

## 6. Soft delete & `deletion_batch_id`

When a user or company is soft-deleted, Jobs180 stamps one UUID on all rows touched by that operation.

**Restore rule:** restore only rows with that batch id.

| Without batch ids | Failure mode |
|-------------------|--------------|
| Restore all deleted children of user | Revives items the user archived last month intentionally |
| Restore only top row | Leaves orphaned deleted children invisible/broken |

---

## 7. Hard delete order

Children → parents; collect `storage_key`s; delete DB rows; delete objects.

**Why app-ordered deletes instead of relying on FK cascades?**  
Need to delete MinIO objects deliberately and fail closed if storage deletion policy requires transactional compensations.

---

## 8. Enumerated status sets (product meaning)

### Application statuses

| Code | Meaning |
|------|---------|
| `WISHLIST` | Interested, not applied |
| `APPLIED` | Application submitted |
| `OA` | Online assessment stage |
| `INTERVIEWING` | Live interview loop |
| `OFFER` | Offer received |
| `REJECTED` | Closed negative |
| `WITHDRAWN` | Candidate withdrew |
| `GHOSTED` | No response / silence |
| `ON_HOLD` | Paused |

**Why include Ghosted / On Hold?**  
Real pipelines are messy; omitting them forces people to misuse Rejected/Withdrawn and pollutes analytics.

### Round outcomes

`PENDING`, `PASS`, `FAIL`, `SKIPPED` — round-local, independent of application status (you can fail a round and still be interviewing elsewhere in the loop).

---

## 9. What we store vs what we refuse to store

| Store | Refuse (v1) |
|-------|-------------|
| JD text (size-capped) | Unbounded HTML email dumps |
| Resume files metadata + object key | Executable uploads |
| Plain notes | Sanitized rich HTML |

---

## 10. Evolution guidelines

- Add columns with Flyway; never rely on `ddl-auto=update` in prod.
- Prefer new CHECK values via migration rewriting checks carefully.
- Keep `owner_id` invariants if new child tables appear.
- Do not introduce cross-user FKs without a product decision (sharing is a non-goal).
