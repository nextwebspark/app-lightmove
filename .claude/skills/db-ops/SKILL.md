---
name: db-ops
description: LightMove database operations — Flyway migration workflow, Cloud SQL roles and hardening, granting humans access, and the company-universe sync from the brightdata warehouse. Load for any work on migrations, db/migration SQL, grants, harden.sql, ops/cloudsql scripts, app_lm_companies, or the sync pipeline.
---

# LightMove database operations

Cloud SQL Postgres 16, instance `bright-gcc`, database `lightmove`. All tables prefixed `app_lm_`.
Schema is hand-written SQL in `apps/api/src/main/resources/db/migration/`, applied by Flyway;
Hibernate never touches it (`ddl-auto: none`; `validate` in the test profile only, where
entity/schema drift becomes a red build). **Never edit an applied migration; add a new one.**

## Flyway runs at boot locally, and as a deploy step in production

`FLYWAY_ENABLED=false` on Cloud Run. Not for speed — because migrating at boot forces `lm_app`, the
*runtime* role on the other end of any SQL injection we ever ship, to hold `CREATE ON SCHEMA public`
forever, which is precisely what `harden.sql` revokes. In the pipeline it runs as `lm_migrate`
instead, so a bad migration fails a deploy and the old revision keeps serving — rather than
crash-looping production, where you can roll back an image but not a schema.

## Humans and roles

`ops/cloudsql/create-database.sh` creates the database, the app user, and registers the IAM
principals. Set `DB_IAM_USER` and Flyway's V2 grants that principal read access, so a human can query
the database with their Google identity and no password.

To add *another* human, run `ops/cloudsql/grant-db-user.sh <email> [--write]` — don't copy V2. V2
takes one principal, and it could grant on the whole schema only because Flyway ran as the owner of
every table; after `harden.sql` neither `lm_app` nor `lm_migrate` owns `app_lm_audit_event` or
`app_lm_companies`, and a non-owner cannot grant. So the grant runs as `postgres`, which makes it an
ops script and not a migration. `--write` covers `app_lm_*` but never those two: the audit trail
stays append-only, and the company universe belongs to the pipeline.

## The company universe is a copy, not a link

The same instance hosts a second database, **`brightdata`** — the ETL warehouse. It holds the scrape
sources (`src_linkedin`, `src_zoominfo`, `supabase_company_dnb`, …) and `app_companies`, a built
projection over them: ~54k companies, the list a consultant actually searches.

`app_lm_companies` is a **copy** of it, refreshed by `ops/cloudsql/sync-companies.sh`. Don't reach
for a second `DataSource` or `postgres_fdw` — Postgres has no cross-database queries, and a company
list that can't be joined to a project is not a company list. It is reference data: the pipeline
writes it, the application only reads it (`harden.sql` reassigns the table to `postgres` and leaves
`lm_app` with `SELECT`).

The sync goes out through GCS — `gcloud sql export csv` → bucket → `gcloud sql import csv` — which
looks like a detour and isn't. `brightdata.app_companies` is owned by `postgres` with **no grants at
all**, so no role you can log in as is able to `SELECT` from it; the export runs server-side as the
instance's service agent and is authorised by your *gcloud* identity, not by any database password.
That agent needs `roles/storage.objectAdmin` on the bucket, granted once (the script's header has
the command).

The sync **upserts on `(source, source_id)`**, never on `id`. Upstream ids are re-minted on every
pipeline rebuild, so anything that references a company must reference *our* id — adopt the
warehouse's and the next rebuild silently repoints every project. Rows that vanish upstream are
reported, never deleted.

Eventually the pipeline writes into `lightmove` directly and the sync script retires. Nothing about
the table changes when it does — which is the point of keying it that way now.

## Connecting

`npm run dev` runs against a **local Docker Postgres** (`ops/dev/db.sh`, :55433) — Flyway at boot
applies your migrations there and nowhere else. Prove a new migration here first:
`npm run dev:db:reset && npm run dev` re-runs the whole chain from V1 on a virgin database, which is
the only way to catch a migration that only works against a schema that already exists.
`npm run dev:db:psql` is a shell in it.

`npm run dev:cloud` is the **shared dev database** on Cloud SQL. First run:
`cp apps/api/src/main/resources/application-local.yml{.example,}` and fill in the DB password; the
Cloud SQL connector authenticates as you — `gcloud auth application-default login`. Flyway still runs
at boot there, so a migration in your tree applies to everyone the moment the API starts. Don't start
`dev:cloud` casually with an unfinished migration in the tree.
