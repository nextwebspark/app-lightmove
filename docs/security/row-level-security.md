# Row-level security: PR #1132 (with a note on the stacked #1173)

Epic #1080. **#1132** (`feature/1084-rls-core`) closes #1084: the core. **#1173** (`feature/1085-rls-rest`, stacked on
#1132) closes #1085: every other tenant table. This document is mainly about **#1132**: what it does, how it does it,
what could go wrong in production, how to deploy it safely, and how to test it locally. §8 summarises #1173.

The design rationale lives in the `db-ops` skill, in the section "Row-level security is the backstop to the workspace
filters". This document points to it rather than repeating it.

**Status (checked 2026-11-03)**

| | #1132 | #1173 |
|---|---|---|
| Branch head | `8cd564a7` | `aa609431` |
| State | open, **draft** | open, **draft** |
| Against `main` | 13 commits ahead, 0 behind; mergeable | behind #1132's head (needs #1132 merged in) |
| Migrations | `V171__row_level_security_core.sql` | `V172__tenant_resolvers.sql`, `V173__row_level_security_rest.sql` |
| Diff | 152 files, +1,496 / −257 | (not re-measured) |
| CI | all green except **API, still pending** | not checked |
| Reviews | none yet | one review: **Not Ready to Merge** (§8.2) |

> The migration was renumbered twice as `main` moved: V166, then V170, then **V171** (V170 is now `main`'s
> `V170__user_avatar.sql`). Older notes or commit messages that say V166 or V170 mean this migration.

---

## 0. Verdict in one paragraph

**Merging #1132 into `main` does not touch production by itself.** Production changes only when someone runs the
manual **Release** workflow, which calls `deploy.yml`. The code is careful, and the migration is small and
transactional. The real danger is the **deploy itself**. `deploy.yml` runs Flyway *before* the new revision takes
traffic, so for a minute or more the **old revision serves against V171**. The old revision never sets a workspace on
its connections, so during that window:

- every position, candidate, company row and note **reads as empty**;
- every write to those tables **fails**;
- the old outreach dispatcher **permanently stops any sequence that comes due**, with reason `UNMAPPED`.

If the new revision then fails to boot, that window has no end. **Redeploying the previous tag does not fix it**,
because V171 stays applied. Ship it as its own release, at a quiet hour, with the pre-flight checks done and the
rollback SQL in hand (§5). Or remove the window entirely by splitting the release (§4, risk 1, option C).

---

## 1. What this PR does (plain language)

Uncava is multi-tenant: many firms (workspaces) share one database. Today, isolation rests entirely on the
application: every query adds `WHERE workspace_id = <the caller's workspace>`. **If one query ever forgets that filter,
one firm could read another firm's candidates.**

This PR adds a **second, independent lock in the database itself**: Postgres row-level security (RLS). Every database
connection the API uses is now labelled with the workspace it works for. Postgres then hides every row of every other
workspace on the protected tables, whatever the SQL says. The application filters stay. RLS is a backstop under them,
not a replacement.

It protects the **people and position core**, the most sensitive data:

- positions: `app_lm_project`;
- the Companies grid: `app_lm_project_candidate`, `app_lm_project_triage_company`;
- people: `app_lm_person` and its contacts, notes, documents, document versions, tags and photo;
- company notes: `app_lm_company_note`.

That is 11 tables. Everything else (outreach, billing, tasks, clients and so on) is #1173.

Three consequences follow:

1. **Fail closed.** A connection with no workspace sees **nothing** in those tables, and cannot write to them. A bug
   now shows up as "empty" or "not found", never as a leak.
2. **Background work must say which workspace it is working for.** Scheduled jobs, webhooks, workers and public pages
   have no signed-in user. Each now names the workspace before it touches a protected table.
3. **The app refuses to start if RLS would be ineffective.** This covers a superuser role, a role that bypasses RLS,
   a protected table left unforced, or a pooled connection left in maintenance mode.

---

## 2. How it does it

### 2.1 A request, end to end

```text
HTTP request ──► Spring Security puts AuthPrincipal (workspace W) in the SecurityContext
             ──► a service starts a transaction ──► the pool hands out a connection
                  └─ TenantAwareDataSource runs: select set_config('app.workspace_id', 'W', false)
             ──► SQL runs; every policy compares row.workspace_id with app.workspace_id
             ──► only W's rows are read or written; another workspace's rows are invisible
```

### 2.2 The label, set on every connection checkout

`core/tenant/TenantAwareDataSource` wraps Boot's Hikari pool. `TenantDataSourceConfig` installs it through a
`BeanPostProcessor`, so every consumer of the `DataSource` goes through it: JPA, `JdbcTemplate` and the native
queries. On **every checkout** it runs:

```sql
select set_config('app.workspace_id', '<uuid or empty string>', false)
```

The label is set at checkout, not as `SET LOCAL` at transaction begin, for two reasons:

- Spring Data's declared query methods, and a `JdbcTemplate` used outside a transaction, run in autocommit, where a
  transaction-local setting is gone before the statement runs. The project authorizer's `findByIdAndWorkspaceId` was
  the first read to 404 that way.
- Every checkout **overwrites** the value, with `''` when there is no workspace. A pooled connection therefore never
  carries the previous borrower's workspace.

### 2.3 Where the workspace comes from

`TenantContext.currentWorkspaceId()` returns the first of these that is present:

1. a workspace that an open `WorkspaceScope` named (a thread-local), used by background work;
2. the signed-in principal's workspace. `AuthPrincipal` (the SPA and the extension), `ApiKeyPrincipal` (the public
   API) and `McpCaller` (MCP) all implement `TenantPrincipal`.

It **never** comes from a request parameter.

### 2.4 The SQL side (V171)

```sql
app_lm_current_workspace()   -- NULLIF(current_setting('app.workspace_id', true), '')::uuid
app_lm_rls_maintenance()     -- current_setting('app.rls_maintenance', true) = 'on'
app_lm_tenant_visible(ws)    -- ws = app_lm_current_workspace() OR app_lm_rls_maintenance()
```

- Each of the 11 tables is `ENABLE` **and `FORCE` ROW LEVEL SECURITY**. `FORCE` matters because `lm_app` still
  *owns* the tables (until #256), and an owner skips a policy that is not forced.
- **Direct policy** (7 tables with a `workspace_id`): `USING` and `WITH CHECK` are both
  `app_lm_tenant_visible(workspace_id)`, so nothing can be read from, or written into, another workspace.
- **Through the person** (4 children with no `workspace_id`: contacts, tags, document versions, photo):
  `EXISTS (SELECT 1 FROM app_lm_person p WHERE p.id = person_id)`. The person's own policy answers.
- The `NULLIF` is needed because a custom setting that was set once reads back as `''` after the transaction, not as
  NULL, and `''::uuid` throws.

**The two grid tables gain a column.** `app_lm_project_candidate` and `app_lm_project_triage_company` get their
project's `workspace_id`, so the policy is a plain comparison rather than a probe of the project on every grid line.
The column is:

- backfilled from the project in the migration;
- filled on insert by the trigger `app_lm_fill_workspace_from_project`, so neither Hibernate nor
  `TriageCompanyWriter`'s native insert has to name it. The trigger reads the project through the project's own
  policy, so an insert into another workspace's project leaves the column null and is refused;
- held to the project's value by a composite FK, `(project_id, workspace_id) → app_lm_project (id, workspace_id)`,
  backed by a new unique index `app_lm_project_id_workspace_uk`.

### 2.5 Work with no signed-in user

- **`WorkspaceScope.run` / `call(workspaceId, task)`** binds a workspace for the task and restores the previous one
  afterwards.
  - It is **refused inside a live transaction begun for another workspace**, because the task would join that
    transaction's connection.
  - One case is handled specially: an after-commit listener on the publishing thread. This always happens in tests
    (`SynchronousAuditWrites`) and in production once the async pool is full (`CallerRunsPolicy`). Spring reports the
    transaction as inactive while still binding its connection, so the scope relabels that connection for the length
    of the task and then puts the old label back.
- **`EveryWorkspace`** lists `app_lm_workspace` (which has no policy) and runs a job's work once per workspace, inside
  that workspace's scope (`forEach`, `sum`).
- **`MdcPropagation`** now carries the submitter's workspace into `@Async` tasks and hand-built pools, through
  `TenantContext.propagating`. This covers the assistant's tool threads, Find executives, People Search filing and the
  AI enrich worker. The two virtual-thread pools that do not propagate (`BackgroundProposer`,
  `OutreachOpenerService.draftAll`) call only the model, never the database.

**Paths converted in this PR:**

| Path | How it names its workspace |
|---|---|
| Outreach send (`OutreachSendService`, `OutreachMessageSendService`) | reads the enrollment or message (not protected yet), then `WorkspaceScope.run(row.workspaceId)` |
| Reply poll and inbox, `MeetingBackfill`, `RecallCalendars` | scope per mailbox row |
| Public booking page (`DirectBookingPage`) | scope from the mailbox the slug names |
| Notetaker workers (`MeetingNotesWorker`, `InterviewCoachWorker`) | scope from the recording row |
| `TaskDueScanner` | `EveryWorkspace.sum`, and its claim SQL adds `workspace_id = ?` |
| `NotificationEmails` (delayed emails and digest) | `EveryWorkspace` |
| `PersonNotePurge`, `CompanyNotePurge` | `EveryWorkspace` |
| MCP tools (`McpToolCalls`) | run in the caller's workspace |

**Two fail-open traps are fixed.** With no workspace set, `NOT IN (archived ids)` and
`existsByIdAndArchivedAtIsNotNull` would have seen *no* archived positions. Tasks on archived positions would then have
fallen due, and mentions of deleted notes would have been emailed. Both now run inside a workspace.

### 2.6 The guard rails

- **`RowLevelSecurityPosture`**: an `ApplicationRunner` at `HIGHEST_PRECEDENCE`. It fails the boot if:
  - the runtime role is a superuser or holds `BYPASSRLS`;
  - any of the 11 tables is not both enabled and forced;
  - `app.rls_maintenance` is on for a pooled connection.

  The setting `lightmove.row-level-security.require-enforced` (`RLS_REQUIRE_ENFORCED`, default `true`) turns those
  failures into warnings when false. Production never sets it: `deploy.yml` uses `--set-env-vars`, which replaces the
  whole list, so the default `true` holds.
- **Flyway's maintenance flag.** `application.yml` gives Flyway its own `user`/`password`, so Flyway builds its own
  DataSource rather than borrowing from the pool, and `init-sqls: SET app.rls_maintenance = 'on'`. `deploy.yml` and
  `ops/gcp/deploy.sh` add `"-initSql=SET app.rls_maintenance = 'on'"` to the Flyway container. Today and in future,
  migrations' backfills therefore still reach every workspace, while the flag never reaches the app's pool.

### 2.7 Test, dev and e2e infrastructure

A superuser skips every policy, so before this PR the test suite proved nothing about RLS. Now:

- **Tests.** `TestcontainersConfig` runs `testcontainers/app-role.sql`. That creates `lm_app` as `NOSUPERUSER
  NOBYPASSRLS` owner of the database, and points `spring.datasource.*` at it. `max_connections` is raised to 300,
  because a non-superuser cannot use the reserved slots. Fixture SQL runs as the superuser through `@FixtureJdbc`, and
  tests that call beans directly wrap them in `TestWorkspaceScopes`.
- **`npm run dev`** (`ops/dev/db.sh`). The container's superuser is now `postgres`, and `lm_app` is an ordinary owner
  (`ensure_app_role`). `dev:db:psql`, `seed-report` and `seed-firm` connect as `postgres`. A volume made before this
  change is refused with "run `npm run dev:db:reset`", because Postgres will not demote its bootstrap superuser.
- **e2e.** `e2e/stack/up.sh` starts Postgres as `postgres` and creates `lm_app`. `e2e/api/lib.sh` and the
  `e2e/spa/*.mjs` fixtures connect as `postgres`.

New tests:

- `RowLevelSecurityIntegrationTest`, which checks that:
  - the posture holds;
  - a workspace reads only its own rows, including through repositories;
  - a workspace cannot write into another;
  - a borrowed connection carries only its borrower's label;
  - a scope cannot join another workspace's transaction.
- `RowLevelSecurityMigrationTest`, which checks the backfill, the trigger and the composite key.

---

## 3. What RLS does *not* stop yet (until #256)

- `lm_app` still **owns** the tables. It could `ALTER TABLE … DISABLE ROW LEVEL SECURITY`.
- Any session can call `set_config('app.workspace_id', …)` or `set_config('app.rls_maintenance', 'on', …)`.

So this is a backstop against **application bugs** (a forgotten filter), **not against SQL injection**. #256 moves
ownership to `lm_migrate`, drops the maintenance arm, and makes the posture check also assert that the runtime role owns
nothing. Even after #256, a role that can set `app.workspace_id` can choose its workspace. Closing that needs a signed
context or per-tenant roles, and no issue builds them yet.

---

## 4. Production risks if #1132 is merged and released

They are ranked by how much harm they could do. "Window" means the time from the end of the `Migrate the database` step
until `gcloud run deploy` has moved traffic to the new revision. That is normally one to a few minutes. It has no end if
the new revision never starts.

### Risk 1 (HIGH): the old revision serves against V171 during the deploy window

**Why.** `deploy.yml` builds the image, **migrates**, and only then deploys. Between those steps, the running revision
is `main`'s old code, which never sets `app.workspace_id`. Under V171 its connections see nothing in the 11 tables.

**What users and jobs would see in the window:**

- **Users:** empty positions list, empty grids and Candidates page, "not found" on a position. Any save that touches a
  protected table fails with `new row violates row-level security policy`, shown as a 500.
- **Outreach dispatcher (every minute), the one lasting harm.** `prepare()` looks the candidate up, finds nothing, and
  calls `outcomes.stop(…, UNMAPPED)` (`OutreachSendService` on `main`, line 162). **Every sequence step that comes due
  in the window is stopped for good**, with an `OUTREACH_STOPPED` line and an audit event. A consultant's own scheduled
  email that comes due is likely failed the same way. I have not traced it line by line.
- **Task due scan (old code):** sees no archived positions, so tasks on archived positions may be announced on the bell.
- **Notification emails (old code):** may email a mention whose note was deleted.
- **Webhooks** (Nylas, Recall) reaching the old revision cannot match attendees or candidates. Meetings may be missed
  until the next calendar read, and inbound replies may be lost or left to the provider's retry.
- Note purges find nothing. That is harmless.

**Mitigations, in increasing order of safety:**

- **A. Pick a quiet window.** Release outside every sequence's sending hours (Mon–Fri 08:00–18:00, mailbox time zone,
  `Asia/Dubai` by default). The best time is a weekend night in Dubai, when the warm-hours schedule also has the fewest
  instances up. Then confirm that nothing is due during the window (§5.1, query 3). After the release, run the recovery
  query (§5.3) for anything stopped as `UNMAPPED` during it.
- **B. Pause the scheduler during the window.** There is no switch for this today. `SchedulingConfig` is on in
  production, so this would need a small change, which makes option C the better use of the effort.
- **C. (Removes the window.) Split the release in two:**
  1. **Release 1:** the code, plus a migration that creates only the three functions, the `workspace_id` column, the
     trigger and the composite key, with **no** `ENABLE`/`FORCE`/`CREATE POLICY`. The new code works on that schema:
     `set_config` is harmless and the policies do not exist yet. The posture check *would* fail ("not forced on …"),
     so it must be told to wait. Either make the "not forced" check a warning until release 2, or pass
     `RLS_REQUIRE_ENFORCED=false` through `deploy.yml` for that one release.
  2. **Release 2:** a migration with only `ENABLE`/`FORCE`/policies, and the posture strict again. The revision
     serving during its window is already the RLS-aware code, so nothing breaks.

  This needs a change to the PR: split V171 and adjust the posture check or `deploy.yml`.

### Risk 2 (HIGH): a failed boot after V171 leaves production broken, and the normal rollback does not fix it

If the new revision does not start after V171 has committed, the old revision keeps serving **under V171** (risk 1, with
no end). Possible causes: the posture check (`lm_app` is a superuser or holds `BYPASSRLS`), a missing secret, or any
other boot failure.

The Deploy workflow's own rollback (`workflow_dispatch` with an older tag) **runs Flyway (a no-op) and deploys the old
code under V171: still broken.** The only fix is to turn RLS off on the 11 tables by hand (§5.4). Have that SQL and
credentials for the owning role ready before releasing. `ops/cloudsql/psql.sh` connects as your IAM user, read-only,
which cannot `ALTER` these tables.

### Risk 3 (MEDIUM): migration locks on hot tables

V171 runs in one transaction. It takes `ACCESS EXCLUSIVE` locks on all 11 tables (`ADD COLUMN`, `ENABLE/FORCE ROW LEVEL
SECURITY`, `CREATE POLICY`), rewrites every candidate and triage row once (the backfill `UPDATE`), and validates two
foreign keys. Two things follow:

- A long query from the old revision, such as an export, the assistant or a large grid read, makes the migration wait.
- While the migration waits, **every new query on that table queues behind it**. The pool is small
  (`DB_POOL_MAX=5` × at most 3 instances), so requests can stall.

The rows are probably few enough (thousands, not millions) that the migration itself takes seconds. Mitigation: the
quiet window from risk 1. Optionally add `SET lock_timeout = '10s'` to the Flyway `-initSql`, so a blocked migration
fails cleanly (and transactionally) instead of stalling the site.

### Risk 4 (MEDIUM): the production role and table owner must be what V171 assumes

- `lm_app` must be `rolsuper = f` and `rolbypassrls = f`, or the boot fails (risk 2). `harden.sql` revoked
  `cloudsqlsuperuser` from `lm_app`, so this is expected, but check it (§5.1, query 1).
- **The Flyway role must own the 11 tables**, or `ALTER TABLE … ENABLE ROW LEVEL SECURITY` fails. That failure is
  **safe**: the migration rolls back and the old revision keeps serving untouched. It just means the release did not
  happen. It matters if `create-migrate-role.sh` has already switched Flyway to `lm_migrate` (§5.1, query 2).

### Risk 5 (MEDIUM): a missed path fails *silently*

Fail-closed means a background path that does not name its workspace does not crash: it sees nothing and carries on.
A webhook would match nobody, a job would find no work, and a check would see "no archived positions". I swept:

- every `@Scheduled` job;
- every hand-built thread pool;
- every principal type;
- account-level code (auth, workspace, platform, billing) that touches the 11 tables.

Everything that touches a protected table is converted, or runs under a principal, or propagates its workspace.
Coverage is still weakest where tests use fakes:

- real Nylas, Recall and Stripe deliveries;
- the Chrome extension;
- the public booking page with a real calendar.

After the release, watch the logs for `violates row-level security policy`. Also watch for unexpected
`UNMAPPED` / `LEFT_THE_RUNNING` stops, and for webhook deliveries that match nobody.

### Risk 6 (LOW to MEDIUM): performance

- **One extra round trip per connection checkout** (the `set_config`), on every transaction and every autocommit query.
  Through the Cloud SQL connector that is roughly a millisecond each. On a screen that makes many small reads, it adds
  up.
- **The person children's policy is an `EXISTS` per row** (a primary-key probe on `app_lm_person`). This matters on the
  Candidates page (`PersonPoolQuery`) and anywhere many contacts or tags are read.
- **Not yet measured:** query plans before and after for the Candidates list and the Companies grid
  (`StageLinesService`). This is an open item in the PR. See §6.6.

### Risk 7 (LOW): people and tools reading the database directly

- Anyone using `ops/cloudsql/psql.sh` (IAM, read-only) sees **zero rows** in the 11 tables until they run
  `SET app.workspace_id = '<id>'`, or `SET app.rls_maintenance = 'on'` to see everything. Saved queries, BI tools and ad
  hoc reports on those tables silently return nothing.
- `ops/eval/export-golden.sh` connects as `lm_app` and will export an **empty golden set** unless it sets the
  maintenance flag or connects as `postgres`. It was not updated in this PR.

### Risk 8 (LOW, team): the shared dev database and existing local volumes

- **`npm run dev:cloud` from this branch applies V171 to the shared Cloud SQL dev database at boot.** Everyone running
  `main` against it then sees empty screens. Do not run `dev:cloud` from this branch until it is merged.
- Every developer's local volume needs **one `npm run dev:db:reset`** after this lands. The reset keeps the Apollo
  snapshot but wipes local accounts and data. Switching back to an older branch afterwards needs another reset (§6.8).

### Risk 9 (LOW): the CI build on `main`

- The PR's **API** check was still **pending** at the time of writing. Do not merge until it is green.
- The PR body records 2,456 tests with 2 failures locally: `CandidateReadsGoldenIntegrationTest` and
  `OutreachReplyMigrationTest`. Both are red on a clean `main` on the author's machine (time-zone and golden-file
  drift), so they are not from this change. CI is the judge.
- Every future migration that creates a table *with a `workspace_id` among the 11's children* should add RLS itself.
  #1132 does not enforce that, because its posture check reads a fixed list. #1173's `TenantTableGuard` will.

---

## 5. Release runbook for #1132

### 5.1 Pre-flight (read-only, `ops/cloudsql/psql.sh` is enough)

```sql
-- 1. The runtime role is bound by policies: must be  f | f
SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'lm_app';

-- 2. The Flyway role owns every table V171 alters (expect one owner: the Flyway user, lm_app today)
SELECT relname, pg_get_userbyid(relowner) AS owner
  FROM pg_class
 WHERE relnamespace = 'public'::regnamespace AND relkind = 'r'
   AND relname IN ('app_lm_project','app_lm_project_candidate','app_lm_project_triage_company','app_lm_person',
                   'app_lm_person_contact','app_lm_person_note','app_lm_person_document',
                   'app_lm_person_document_version','app_lm_person_tag','app_lm_person_photo','app_lm_company_note')
 ORDER BY 1;

-- 3. Nothing due during the window (both should be 0; run just before releasing)
SELECT count(*) AS sequence_steps_due
  FROM app_lm_outreach_enrollment
 WHERE status IN ('SCHEDULED','ACTIVE') AND next_send_at <= now() + interval '30 minutes';
SELECT count(*) AS own_emails_due
  FROM app_lm_outreach_message
 WHERE status = 'SCHEDULED' AND send_at <= now() + interval '30 minutes';

-- 4. Size of what the backfill rewrites (sanity check on lock time)
SELECT (SELECT count(*) FROM app_lm_project_candidate) AS candidates,
       (SELECT count(*) FROM app_lm_project_triage_company) AS triage_rows;
```

Also:

- check that **CI is green** on `main` after the merge;
- make sure **nothing else is in the release**: release #1132 alone, so a rollback is about one thing;
- have the **rollback SQL (§5.4) and the owner's credentials** ready. The owner is `lm_app`; its password is the
  `lightmove-flyway-password` / `lightmove-db-password` secret, used through the Cloud SQL Auth Proxy as the deploy step
  does.

### 5.2 Release

Run **Release** (it calls Deploy) in the quiet window. Note the time the `Migrate the database` step ends and the time
the `Smoke test` step passes. Those two times bound the window.

### 5.3 After the release

```sql
-- As lm_app or any role, with the maintenance flag so the protected tables are visible:
SET app.rls_maintenance = 'on';

-- Coverage: 11 rows, all t | t, one policy each
SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity,
       (SELECT count(*) FROM pg_policy p WHERE p.polrelid = c.oid) AS policies
  FROM pg_class c
 WHERE c.relnamespace = 'public'::regnamespace AND c.relrowsecurity ORDER BY 1;

-- Backfill: no grid row without a workspace (expect 0, 0)
SELECT (SELECT count(*) FROM app_lm_project_candidate WHERE workspace_id IS NULL),
       (SELECT count(*) FROM app_lm_project_triage_company WHERE workspace_id IS NULL);

-- Recovery: sequences the OLD revision stopped during the window (replace the two times)
SELECT id, workspace_id, project_id, candidate_id, stopped_at
  FROM app_lm_outreach_enrollment
 WHERE stop_reason = 'UNMAPPED' AND stopped_at BETWEEN '<migrate end>' AND '<smoke test passed>';

RESET app.rls_maintenance;
```

Any row from the recovery query is a sequence that stopped wrongly. Tell its consultant, who can add that person to the
sequence again. There is no "resume" in the app.

Then smoke-test the app as a real user: positions list, the Companies grid, the Candidates page, a person drawer, and
Outreach. In Cloud Logging, search for `violates row-level security policy` and `Row-level security is not in force`.

### 5.4 Rollback (only if the new revision will not serve, or the app misbehaves)

Run this as the **owner** (`lm_app`). It makes the old code whole again. The new column, trigger and foreign key are
harmless to it: the trigger still fills `workspace_id` on insert.

```sql
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['app_lm_project','app_lm_project_candidate','app_lm_project_triage_company',
                           'app_lm_person','app_lm_person_contact','app_lm_person_note','app_lm_person_document',
                           'app_lm_person_document_version','app_lm_person_tag','app_lm_person_photo',
                           'app_lm_company_note']
  LOOP
    EXECUTE format('ALTER TABLE %I NO FORCE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I DISABLE ROW LEVEL SECURITY', t);
  END LOOP;
END $$;
```

Then redeploy the previous tag, if the new revision is the problem. Two things to know:

- After this, the **new** code will refuse to boot ("not forced on …") until RLS is back on.
- Flyway still records V171 as applied, so it will never re-run. Turning RLS back on needs either the same `DO` block
  with `ENABLE` / `FORCE`, or a new migration that does it.

---

## 6. Testing #1132 locally

Use Git Bash from the repository root, with Docker running. The dev Postgres container is `lm-dev-pg` on `:55433`,
password `lm`.

**Note:** `npm run dev:db:psql` connects as `postgres`, a superuser, which is **above** RLS. To see what the API sees,
connect as `lm_app`:

```bash
docker exec -it lm-dev-pg psql -U lm_app -d lightmove
```

### 6.1 Bring the stack up on the branch

```bash
git checkout feature/1084-rls-core && git pull   # #1132 alone (feature/1085-rls-rest is #1173 on top)
npm run dev:db:reset                             # once: keeps the Apollo snapshot, wipes local accounts and data
npm run dev                                      # postgres :55433 + api :8080 + web :5173
```

In the API log, check that:

- Flyway applied **V171** (`row level security core`);
- there is **no** `Row-level security is not in force: …` (that message would have stopped the boot).

If `db.sh` prints "this volume predates row-level security", the reset was skipped. Run it and start again.

### 6.2 Inspect the database as `lm_app`

```sql
-- 1. The runtime role is bound by policies: expect f | f
SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user;

-- 2. Coverage: expect the 11 tables, t | t, one policy each
SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity,
       (SELECT count(*) FROM pg_policy p WHERE p.polrelid = c.oid) AS policies
  FROM pg_class c
 WHERE c.relnamespace = 'public'::regnamespace AND c.relrowsecurity ORDER BY 1;

-- 3. Fail closed: with no workspace set, protected tables are empty
SELECT set_config('app.workspace_id', '', false);
SELECT (SELECT count(*) FROM app_lm_project)           AS projects,
       (SELECT count(*) FROM app_lm_person)            AS people,
       (SELECT count(*) FROM app_lm_project_candidate) AS grid_people,
       (SELECT count(*) FROM app_lm_person_contact)    AS contacts;     -- all 0

-- 4. One workspace at a time (app_lm_workspace has no policy, so it lists them all)
SELECT id, name FROM app_lm_workspace ORDER BY created_at;
SELECT set_config('app.workspace_id', '<workspace A id>', false);
SELECT count(*), count(DISTINCT workspace_id) FROM app_lm_project;   -- only A's: distinct = 1

-- 5. A write into another workspace is refused (A is still set)
UPDATE app_lm_project SET position_title = position_title WHERE workspace_id = '<workspace B id>';   -- UPDATE 0: B is invisible
INSERT INTO app_lm_company_note (id, workspace_id, project_id, triage_company_id, kind, body, author_user_id)
VALUES (gen_random_uuid(), '<workspace B id>', gen_random_uuid(), gen_random_uuid(), 'GENERAL', 'probe', gen_random_uuid());
-- expected: ERROR: new row violates row-level security policy for table "app_lm_company_note"
-- (the policy is checked before the foreign keys, so the made-up ids never get a say)

-- 6. The maintenance flag is off on your session
SELECT app_lm_rls_maintenance();   -- f
```

### 6.3 App smoke test: two firms

1. Sign up **firm A** in one browser profile and **firm B** in another (or a private window). In each:
   - create a business unit or client and a position;
   - add a company and an executive;
   - add a person note, a company note and a document.
2. Signed in as A, open B's URLs: `/projects/<B project id>`, and a B person in the Candidates drawer. Expect
   **404 / not found**, never B's data.
3. Check A's own screens, which must render normally:
   - Companies grid, Map and Pipeline;
   - the Candidates page and a person drawer (Notes, Documents, Contact, Activity);
   - the Position brief steps;
   - the Dashboard;
   - Outreach;
   - Settings → Security → Audit log.
4. **Several workspaces:** from A, invite B's admin; accept from the topbar menu; switch workspaces. Each workspace
   shows only its own data.
5. **Doors with no browser session:**
   - Create a workspace API key (Settings → API keys) and call `GET /api/v1/public/...` with it. It reads only that
     workspace.
   - `GET /api/v1/outreach/booking/no-such-slug` answers 404.
   - The API log shows no `violates row-level security policy`.
6. Where configured locally, also try:
   - the Chrome extension capture;
   - Find executives;
   - the spreadsheet import and the export;
   - Assess with AI;
   - an assistant question.

   These cross threads and doors, which is where a missed workspace would show up as "empty".

### 6.4 Background jobs

The jobs run on their own in `npm run dev`. To exercise them deliberately:

- **Task due scan:** create a task due in a few minutes on a position, and another on a position you then **archive**.
  Only the live one should ring the bell.
- **Note purge:** delete a note, then (as `postgres`) set its `deleted_at` back past `lightmove.notes.purge-after`. It
  is purged on the next run (`NOTES_PURGE_INTERVAL`, one hour by default). Set it lower in your environment to see it
  sooner.
- **Outreach** (needs a connected mailbox): start a one-step sequence with `NOW`. The email goes out, and the run is
  **not** stopped as `UNMAPPED`.

### 6.5 Negative check: the posture refuses to boot

```sql
-- as lm_app
ALTER TABLE app_lm_person NO FORCE ROW LEVEL SECURITY;
```

Restart the API (`npm run dev:stop`, then `npm run dev`). The boot should fail with:
`Row-level security is not in force: not forced on app_lm_person`.

Start it once with `RLS_REQUIRE_ENFORCED=false`: it boots and logs the same text as a warning. Then put it back and
restart normally:

```sql
ALTER TABLE app_lm_person FORCE ROW LEVEL SECURITY;
```

### 6.6 Query plans

As `lm_app`, with a workspace set (§6.2, step 4), run `EXPLAIN (ANALYZE, BUFFERS)` on:

- the SQL behind the Candidates list (`PersonPoolQuery`);
- the Companies grid's lines read (`StageLinesService`);
- a person drawer's contacts and tags read.

To capture the statements, set `logging.level.org.hibernate.SQL=DEBUG`, or use `pg_stat_statements`. Compare against
`main` on the same data, for example after `npm run dev:db:seed-report`. Look for a `SubPlan` per row on the person
children, and for sequential scans where an index scan used to be.

### 6.7 Backend tests and e2e

```bash
cd apps/api
./mvnw test -Dtest='RowLevelSecurity*Test'
./mvnw test                                  # full suite, needs Docker
cd ../../e2e && PROFILE=e2e ./run-all.sh     # always PROFILE=e2e
```

- The full suite should pass, apart from the two tests that are also red on `main` locally (risk 9).
- If a reused Testcontainers database predates this branch, it may lack the `lm_app` role. Remove the container and the
  next run recreates it.
- Web tests on Node 26 need `NODE_OPTIONS=--no-experimental-webstorage`.

### 6.8 Going back to `main` afterwards

Your local database now has V171 applied. `main`'s code does not set the workspace, so on that database **every screen
reads empty**. Flyway ignores the unknown future migration, so nothing errors. Run `npm run dev:db:reset` when you switch
back.

---

## 7. File map (#1132)

| Area | Files |
|---|---|
| Migration | `db/migration/V171__row_level_security_core.sql` |
| Tenant core | `core/tenant/`: `TenantAwareDataSource`, `TenantDataSourceConfig`, `TenantContext`, `TenantPrincipal`, `WorkspaceScope`, `EveryWorkspace`, `RowLevelSecurityPosture` |
| Config | `core/config/RowLevelSecuritySettings`, `LightMoveProperties`, `application.yml` (Flyway user and `init-sqls`, `lightmove.row-level-security`) |
| Principals and propagation | `AuthPrincipal`, `ApiKeyPrincipal`, `McpCaller`, `McpToolCalls`, `MdcPropagation` |
| Paths with no principal | `OutreachSendService`, `OutreachMessageSendService`, `OutreachInboxService`, `MeetingBackfill`, `RecallCalendars`, `DirectBookingPage`, `MeetingNotesWorker`, `InterviewCoachWorker`, `TaskDueScanner`, `NotificationEmails`, `PersonNotePurge`, `CompanyNotePurge` |
| Ops | `.github/workflows/deploy.yml`, `ops/gcp/deploy.sh` (Flyway `-initSql`), `ops/dev/db.sh` |
| e2e | `e2e/stack/up.sh`, `e2e/api/lib.sh`, `e2e/spa/{companies,import-export,position,reports,strategy}.mjs`, `e2e/README.md` |
| Test scaffolding | `TestcontainersConfig`, `testcontainers/app-role.sql`, `FixtureJdbc`, `TestWorkspaceScopes`, `FlowTestSupport`, `IntegrationTest`, plus about 80 adjusted integration tests |
| New tests | `RowLevelSecurityIntegrationTest`, `RowLevelSecurityMigrationTest` |
| Docs | `CLAUDE.md`, and the `db-ops`, `java-spring-development` and `lightmove-domain` skills |

---

## 8. #1173 in brief (not re-checked on its latest head)

This section was written against #1173 at `ae6a6f73`, before the renumbering. Its head is now `aa609431`, with the
migrations as **V172** (resolvers) and **V173** (policies). It must take #1132's latest head before it can merge.

### 8.1 What it adds

- **V172, the resolvers.** A webhook, public page or credential knows only a key (grant id, Recall calendar or bot,
  Stripe customer, booking slug, token hash). One narrow `SECURITY DEFINER` function per key returns `(id,
  workspace_id)` and nothing else. The door then opens `WorkspaceScope` on that workspace.
- **V173, the policies.** RLS is extended to about 68 more tables (outreach, billing, tasks, notifications, clients,
  invitations, API keys, the assistant, templates and the position brief's lists), and, through `harden.sql` step 2c,
  to `app_lm_audit_event` and `app_lm_credit_entry`.
- **`TenantTableGuard`** reads the tenant tables off the schema, so a later migration that adds an unprotected tenant
  table fails the boot.
- **Every job walks the workspaces** through `EveryWorkspace`.

### 8.2 Open review items (from the review marked Not Ready to Merge)

| # | Issue |
|---|---|
| R1 | `WITH CHECK (workspace_id IS NULL OR …)` lets any workspace write shared library templates and pending OAuth grants. Split it into a shared read and an own-workspace write. |
| R2 | **`EveryWorkspace` has no per-workspace failure isolation.** One workspace that throws aborts the walk for every later workspace, on every tick. **This class ships in #1132**, where only the task scan, notification emails and note purges use it. Fixing it in #1132 is cheap and protects those jobs too. |
| R3 | The `harden.sql` step 2c comment says the boot refuses until it has run. The code only warns. |
| R4 | The verification checklist is unfinished: e2e; real Recall, Nylas and Stripe payloads; the extension; query plans; the production pre-flight. |

Performance: the job walk is serial over **every** workspace on every tick, which took the backend suite from about
11 to about 18½ minutes. The reviewer wants a resolver of "workspaces with work due" before production.

Deploying #1173 carries the same deploy-window problem as risk 1, on a larger scale. Its two commits (code plus V172,
then V173) must be **released separately**, and `harden.sql` must be re-run as `postgres` after V173.

---

## 9. Checklist before merging and releasing #1132

- [ ] The **API** CI check is green on the PR.
- [ ] The PR leaves draft. Review is done (by Fable or Codex).
- [ ] `cd e2e && PROFILE=e2e ./run-all.sh` is green.
- [ ] Query plans before and after for the Candidates list and the Companies grid (§6.6).
- [ ] Decide on risk 1: a quiet-window release (A) or a split release (C).
- [ ] Optional but cheap: per-workspace failure isolation in `EveryWorkspace` (R2), since it ships here.
- [ ] Optional: `SET lock_timeout` in Flyway's `-initSql` (risk 3).
- [ ] Update `ops/eval/export-golden.sh` to read past RLS (risk 7).
- [ ] Production pre-flight queries 1–4 (§5.1).
- [ ] Rollback SQL and owner credentials ready (§5.4).
- [ ] Release #1132 **on its own**, in the quiet window, then run the after-release checks and the recovery query
      (§5.3).
- [ ] Tell the team: one `npm run dev:db:reset` each, and no `npm run dev:cloud` from this branch before the merge.
