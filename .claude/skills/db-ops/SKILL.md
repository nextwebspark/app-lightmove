---
name: db-ops
description: LightMove database operations — Flyway migration workflow, Cloud SQL roles and hardening, granting humans access, and the ETL-owned Apollo company universe. Load for any work on migrations, db/migration SQL, grants, harden.sql, ops/cloudsql scripts, or app_lm_apollo_companies.
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
every table; after `harden.sql` neither `lm_app` nor `lm_migrate` owns `app_lm_audit_event` or the company
tables, and a non-owner cannot grant. So the grant runs as `postgres`, which makes it an
ops script and not a migration. `--write` covers `app_lm_*` but never those: the audit trail
stays append-only, and the company universe belongs to the pipeline.

## The company universe is ETL-owned, and the application only reads it

**`app_lm_apollo_companies`** is the universe: 71,822 GCC companies, loaded **out of band by the
pipeline** — there is no script for it in this repo, and `row_hash` is the loader's change detector.
The application never writes it (`harden.sql` leaves `lm_app` with `SELECT`), and `grant-db-user.sh
--write` never covers it.

Its primary key is **`apollo_account_id`**, stable across exports. Everything that stores a company —
a mandate's off-limits list, its triaged universe, a client record's provenance — stores that id plus
a **write-time snapshot of the display fields**, and never a foreign key. Both halves matter: the
pipeline reloads the table wholesale, so a foreign key would let a load cascade away a mandate's
decisions, and a row with no snapshot would render blank the day its company stops being published.

What the live data actually looks like, because it shapes what a filter can offer:

| Column | Coverage of 71,822 rows |
|---|---|
| `num_employees`, `company_country` | 100%. Countries are exactly six — UAE, Saudi Arabia, Qatar, Kuwait, Oman, Bahrain, spelled out |
| `industry` | 98.8%, **148 distinct lower-cased labels**, no hierarchy. `sector-taxonomy.json` groups them |
| `keywords[]` | 93.7%, lower-case throughout, GIN-indexed. Backs the market-segment filter via `keywords && ARRAY[…]::text[]` |
| `annual_revenue` | **9.9%** — which is why `RevenueBand.R_UNKNOWN` exists |

There is **no ownership column**, and none can be derived honestly: `latest_funding` covers 2,123 rows
and `parent_company` 1,811.

**`app_lm_companies`** is the retired brightdata copy — 54k rows synced from `brightdata.app_companies`
by a `sync-companies.sh` that has been deleted. Nothing reads it and nothing refills it. It is left in
place rather than dropped, so if you meet it, ignore it.

Locally the table starts empty — V23 creates it and nothing fills it. There is no automated restore
path for a developer's own Postgres (the old `ops/dev/db.sh apollo-pull`/`apollo-restore` were
Docker-specific — `docker exec`/`docker cp` into a named container — and were removed when local dev
moved off Docker; not replaced). Strategy/Companies screens render empty locally until the table is
filled by hand: a `pg_restore` of an `apollo.dump` (schema + data, or `--data-only` against V23's
already-created empty table) straight against your own database, or a manual pull over
`cloud-sql-proxy` following the same column-spelled-out shape the old script used — the deployed
table was created by the pipeline and the local one by V23, so they agree on the 46 columns and not
on their order, and a positional `COPY`/`SELECT *` would shift every value one column sideways.

Profiling the universe read-only is `./ops/cloudsql/psql.sh -c "…"` — it opens a session as *you*
over cloud-sql-proxy with `--auto-iam-authn`, no password and no application boot, which is the only
safe way to look at the shared database while a migration is unfinished in your tree.

## Connecting

Local dev runs the API directly against a developer's own Postgres — `cd apps/api && ./mvnw
spring-boot:run -Dspring-boot.run.profiles=local` (or an IntelliJ run config, active profile
`local`) — reading `apps/api/src/main/resources/application-local.yml` (gitignored) for the
datasource. Flyway at boot applies your migrations there and nowhere else. Prove a new migration
here first: drop and recreate your local database, then boot again, to re-run the whole chain from
V1 on a virgin schema — the only way to catch a migration that only works against a schema that
already exists.

`npm run dev:cloud` is the **shared dev database** on Cloud SQL. First run:
`cp apps/api/src/main/resources/application-local.yml{.example,}` and fill in the DB password; the
Cloud SQL connector authenticates as you — `gcloud auth application-default login`. Flyway still runs
at boot there, so a migration in your tree applies to everyone the moment the API starts. Don't start
`dev:cloud` casually with an unfinished migration in the tree.
