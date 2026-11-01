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

## Row-level security is the backstop to the workspace filters (V170, #1084)

Every workspace-scoped query still filters by `AuthPrincipal.requireWorkspaceId()`; Postgres row-level security is
what stops one missed filter from leaking across firms. V170 forces it on the people and position core —
`app_lm_project`, `_project_candidate`, `_project_triage_company`, `_person` and its contacts, notes, documents and
versions, tags and photo, and `_company_note`; #1085 covers the rest. `RowLevelSecurityPosture.PROTECTED_TABLES` is
the list, and the boot fails if any of them is not forced.

- **The setting.** `app.workspace_id`, written by `core/tenant`'s `TenantAwareDataSource` on **every connection
  checkout** — `set_config(…, false)`, overwritten each time, the empty string when there is no workspace. Not
  `SET LOCAL` at transaction begin: Spring Data's declared query methods and a `JdbcTemplate` outside a transaction
  run in autocommit, where a transaction-local setting is gone before the statement runs (the project authorizer's
  `findByIdAndWorkspaceId` was the first to 404). Because every checkout overwrites it, a pooled connection never
  carries one borrower's workspace into the next. The workspace is `TenantContext`'s: a `WorkspaceScope` if one is
  open, else the signed-in principal's (`TenantPrincipal`: `AuthPrincipal`, `ApiKeyPrincipal`, `McpCaller`) —
  never a request parameter.
- **Policies fail closed.** `app_lm_tenant_visible(workspace_id)` compares the row with
  `app_lm_current_workspace()` — `NULLIF(current_setting('app.workspace_id', true), '')::uuid`, the `NULLIF`
  because a setting once set reads back as `''`, which would throw as a uuid. No setting, no rows; `WITH CHECK`
  refuses an insert or update into another workspace. A person's children (contacts, tags, document versions,
  photo) carry no workspace and are visible through `EXISTS` on their person, whose own policy answers. The two
  grid tables carry their project's `workspace_id` (filled by `app_lm_fill_workspace_from_project`, held by a
  composite key to the project's) so their policy is a plain comparison.
- **System work names its workspace — per item, no bypass role.** A scheduled job, a public webhook, or an
  `@Async` worker woken by one has no principal, so it reads nothing until it opens `WorkspaceScope.run(id, …)` from
  the row it is working on (the enrollment, the message, the mailbox, the recording). A statement over every
  workspace becomes a walk (`EveryWorkspace`): the note purges, the due scan, the bell's emails. `@Async` tasks and
  hand-built pools inherit the submitter's workspace through `MdcPropagation`. A scope is refused inside a
  transaction already holding another workspace's connection.
- **Fail-open traps, now pinned by tests.** A check that reads "not found" as "allowed" turns RLS's empty answer
  into a pass: `existsByIdAndArchivedAtIsNotNull` reads an unseen project as live, and `NOT IN (archived ids)`
  excludes nothing when the subquery sees nothing. Every such read must run in the right scope.
- **Who is above it.** A superuser skips every policy, forced or not, and so does a `BYPASSRLS` role. The runtime
  role must be neither — `RowLevelSecurityPosture` refuses to boot otherwise (`lightmove.row-level-security.
  require-enforced`). Tests, `npm run dev` and e2e therefore run the API as a non-superuser `lm_app` that owns the
  database; their own fixture SQL (`@FixtureJdbc`, `dev:db:psql`, the seeds, `e2e/api/lib.sh`) runs as the
  container's superuser, above the policies. A dev volume older than V170 has `lm_app` as its bootstrap superuser,
  which Postgres will not demote: `npm run dev:db:reset`.
- **Migrations.** Flyway runs as the same forced owner until #256, so its own session sets
  `app.rls_maintenance = 'on'` (`spring.flyway.init-sqls`, the deploy step's `-initSql`), which the direct
  policies honour; a backfill then reaches every workspace. `spring.flyway.user` gives Flyway its own DataSource —
  on a borrowed pool connection the flag would outlive the migration and serve the app, which the posture check
  also refuses. A standalone migration test runs as the container superuser and is unaffected.
- **Humans.** An IAM user reading production through `psql.sh` sees no rows of these tables. To look into one
  workspace, `SET app.workspace_id = '<id>'` first — deliberately, one workspace at a time. Until #256 the
  break-glass for all of them is `SET app.rls_maintenance = 'on'`.

**What it does not stop yet.** Until #256 `lm_app` owns these tables and can `ALTER TABLE … DISABLE ROW LEVEL
SECURITY`, and anyone holding a connection can `set_config` either setting. So this is a backstop against
*application bugs* — a missed filter — not against SQL injection. **When #256 lands:** ownership moves to
`lm_migrate`, `lm_app` keeps DML only; a migration gives each protected table a `TO lm_migrate USING (true) WITH
CHECK (true)` policy and redefines `app_lm_tenant_visible` without the maintenance arm (then drops
`app_lm_rls_maintenance`); `spring.flyway.init-sqls` and `-initSql` go and `spring.flyway.user` names `lm_migrate`;
the posture check also asserts the runtime role owns no protected table and lacks `CREATE` on `public`; tests and
the dev and e2e containers split the same way (Flyway as the owner, the API as a grantee under `ALTER DEFAULT
PRIVILEGES`); and the human break-glass becomes a role (`grant-db-user.sh --support`). FORCE stays: #256's rollback
leaves ownership with `lm_migrate`, and FORCE keeps the owner bound whatever happens to ownership later. Even then a
runtime role that can set `app.workspace_id` can choose its workspace: closing that needs a signed context or
per-tenant roles, which neither issue builds.

**Rollback.** `ALTER TABLE <t> NO FORCE ROW LEVEL SECURITY; ALTER TABLE <t> DISABLE ROW LEVEL SECURITY;` on the
listed tables, as their owner, restores the pre-V170 behaviour; the column, trigger and functions can stay.

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

A LightMove **super admin** (V57's platform role) is made the same way, by
`ops/cloudsql/grant-platform-role.sh <email> [--revoke] [--local]`, never by the application. The account
must already exist; the change lands on the user's next request. It connects as the table's owner —
`lm_app` until `harden.sql` reassigns `app_lm_user_platform_role` to `postgres`, then set
`DB_GRANTOR=postgres` — and writes the grant to the audit trail itself, since no request carries it.
That row names the **operator**, not `session_user`: every operator connects as the same table owner, so
the database role cannot say who granted a privilege that reaches every workspace. It is gcloud's active
account where there is one, else the shell user and host, and `LM_OPERATOR` overrides both.
`--local` targets `npm run dev`'s container instead of Cloud SQL.

## The company universe is ETL-owned, and the application only reads it

**`app_lm_apollo_companies`** is the universe: 100,631 companies, loaded **out of band by the
pipeline** — there is no script for it in this repo, and `row_hash` is the loader's change detector.
The application never writes it (`harden.sql` leaves `lm_app` with `SELECT`), and `grant-db-user.sh
--write` never covers it.

Its primary key is **`apollo_account_id`**, stable across exports. Everything that stores a company —
a mandate's off-limits list, its triaged universe, a client record's provenance — stores that id plus
a **write-time snapshot of the display fields**, and never a foreign key. Both halves matter: the
pipeline reloads the table wholesale, so a foreign key would let a load cascade away a mandate's
decisions, and a row with no snapshot would render blank the day its company stops being published.

What the live data actually looks like, because it shapes what a filter can offer:

| Column | Coverage of 100,631 rows |
|---|---|
| `num_employees`, `company_country` | 100%. **Thirteen** spelled-out values, not the six this table claimed until 2026-09-10: UAE 37,012 · Saudi Arabia 20,492 · **Egypt 19,053** · **Türkiye 12,017** · Qatar 4,576 · Kuwait 2,944 · Oman 2,591 · Bahrain 1,928, then a handful of strays (US 6, Brazil 4, Jordan 3, China 1) and 4 blanks. The pipeline has grown past the GCC twice without this row being updated, so **count it before you trust it** — `GET /api/v1/countries` serves the markets counted live for exactly that reason |
| `industry` | 98.8%, **148 distinct lower-cased labels**, no hierarchy. `sector-taxonomy.json` groups them |
| `keywords[]` | 93.7%, lower-case throughout, GIN-indexed. Backs the market-segment filter via `keywords && ARRAY[…]::text[]` |
| `annual_revenue` | **9.9%** — which is why `RevenueBand.R_UNKNOWN` exists |

There is **no ownership column**, and none can be derived honestly: `latest_funding` covers 2,123 rows
and `parent_company` 1,811.

**`app_lm_companies`** is the retired brightdata copy — 54k rows synced from `brightdata.app_companies`
by a `sync-companies.sh` that has been deleted. Nothing reads it and nothing refills it. It is left in
place rather than dropped, so if you meet it, ignore it.

Locally the table starts empty — V23 creates it and nothing fills it. `npm run dev:db:apollo`
(`ops/dev/db.sh apollo-pull`) copies the rows down from the shared Cloud SQL database over
cloud-sql-proxy, straight into the container with no CSV on disk. It spells the 46 columns out on both
sides of the pipe rather than `SELECT *`: the deployed table was created by the pipeline and the local
one by V23, so they agree on the columns and not on their order, and a positional COPY would shift
every value one column sideways.

`reset` would otherwise throw those rows away, so it dumps the table to `ops/dev/.cache/apollo.dump`
(gitignored) before dropping the volume, and `up` restores it before the API boots. Restoring first is
what makes it work: Flyway then meets the table already present and V23's guard skips its body, exactly
as it does against the deployed database. A snapshot that cannot be written aborts the wipe rather than
proceeding without it.

Profiling the universe read-only is `./ops/cloudsql/psql.sh -c "…"` — it opens a session as *you*
over cloud-sql-proxy with `--auto-iam-authn`, no password and no application boot, which is the only
safe way to look at the shared database while a migration is unfinished in your tree.

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
