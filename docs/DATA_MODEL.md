# Data model

## Goals

1. Represent the job-hunt graph cleanly.
2. Enforce privacy via ownership.
3. Support soft delete + selective restore.
4. Stay fast for common lists (by owner, status, upcoming dates).

## Entity relationship (logical)

```
UserAccount
 ├── Company
 │    ├── JobApplication
 │    │    └── Round ──(optional)──► Resume
 │    │         ├── Note
 │    │         └── ResourceItem
 │    ├── Note
 │    └── ResourceItem
 └── Resume (library)
```

Notes and resources also hang off **company** or **application** directly. Exactly one parent is allowed (DB `CHECK`).

## Why `JobApplication` (not “Role”)

“Role” collides with security roles (`USER`/`ADMIN`). The table is `job_applications`; the Java type is `JobApplication` to avoid clashing with Spring’s `Application`.

## Ownership denormalization

Every tenant table carries `owner_id` even when reachable via parents.

**Reasoning:** authorization and “list my stuff” queries become simple indexed filters. The cost is ensuring services copy `owner_id` from the parent and reject cross-owner attaches (e.g. another user’s resume).

## Soft delete fields

On mutable aggregates:

- `deleted_at` — null means active
- `deletion_batch_id` — UUID shared by a cascade operation
- `version` — optimistic lock
- `created_at` / `updated_at` — audit timestamps

**Partial indexes** (`WHERE deleted_at IS NULL`) keep active-list queries small.

## Enums as TEXT + CHECK

Statuses and outcomes are stored as stable string codes (`WISHLIST`, `OA`, …) with SQL `CHECK` constraints—not PostgreSQL `ENUM` types.

**Why:** PG enums are awkward to migrate; text + check is portable and readable in Flyway.

### Application statuses

`WISHLIST`, `APPLIED`, `OA`, `INTERVIEWING`, `OFFER`, `REJECTED`, `WITHDRAWN`, `GHOSTED`, `ON_HOLD`

### Round outcomes

`PENDING`, `PASS`, `FAIL`, `SKIPPED`

### Work modes

`REMOTE`, `HYBRID`, `ONSITE`, `UNKNOWN`

## Resume invariants

A resume must have **file and/or URL** (`CHECK`). Rounds reference resumes with `ON DELETE SET NULL` so hard-deleting a resume detaches rounds instead of blocking or cascading wrongly.

## First-admin lock

Table `app_locks` holds a singleton row `first_admin`. Signup does `SELECT … FOR UPDATE` so concurrent first registrations cannot both become admin (**mutual exclusion**).

## Email uniqueness

Unique index on `LOWER(email)` includes soft-deleted users so emails stay reserved until hard purge—avoids identity confusion / account takeover via recycle.

## Cascade rules (summary)

| Action | Effect |
|--------|--------|
| Soft-delete company | Soft-delete apps → rounds → notes/resources under that tree; resumes untouched |
| Soft-delete user | Soft-delete all owned trees + resumes; revoke sessions |
| Restore user/company | Restore rows matching the same `deletion_batch_id` |
| Hard delete | Delete children first, delete MinIO keys, then rows |

Schema source of truth: [`V1__schema.sql`](../src/main/resources/db/migration/V1__schema.sql).
