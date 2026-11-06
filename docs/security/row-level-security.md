# Row-level security: PRs #1132 and #1173

Epic #1080, in two PRs:

- **#1132** (`feature/1084-rls-core`) closes #1084: the tenant label on every connection, and RLS on the people and
  position core. **Merged into `main`** (squashed as `8c5050ad`).
- **#1173** (`feature/1085-rls-rest`) closes #1085: RLS on every other tenant table, the resolvers that let a door
  with no signed-in user find its workspace, the job resolvers, and a guard that reads the tenant tables off the
  schema. **Open, targeting `main`.**

This document covers both: what they do, how, what could go wrong in production, how to release each safely, and how
to test them locally. Where a section applies to one PR only, its heading says so.

The design rationale lives in the `db-ops` skill, in the section "Row-level security is the backstop to the workspace
filters". This document points to it rather than repeating it.

**Status (checked 2026-11-05)**

| | #1132 | #1173 |
|---|---|---|
| State | **merged** into `main` (`8c5050ad`, 2026-11-04) | open, **draft**, base `main` |
| Against `main` | — | has merged #1132's final head and `main` |
| Migrations | `V184__row_level_security_core.sql` | `V189__tenant_resolvers.sql`, `V190__row_level_security_rest.sql`, `V191__workspaces_with_work.sql` |
| Tables protected | 11 | 68 more (79 in all) |
| Released to production | check the Release history for a tag containing `8c5050ad` | no |
| Reviews | done | one review: **Not Ready to Merge** (§8); R2, R3 and the job-walk cost since fixed |

> **Migration numbers moved as `main` moved.** #1132's migration was V166, then V170, then V171, and is **V184**
> (`main`'s V170 is `V170__user_avatar.sql`, its V183 `V183__person_contact_primary.sql`). #1173's were V172 and
> V173, then V185 and V186, and are **V189** and **V190**; V191 (once V187) is new. `main`'s own V185, V186 and V188
> are person notes and invitations, not these. Older notes and commit messages that say V166, V170, V171, V172, V173,
> V185, V186 or V187 mean these migrations.

---

## 0. Verdict in one paragraph

**Merging does not touch production by itself.** Production changes only when someone runs the manual **Release**
workflow, which calls `deploy.yml`. The code is careful and the migrations are transactional. The danger in both is
the **deploy itself**. `deploy.yml` runs Flyway *before* the new revision takes traffic, so for a minute or more the
**old revision serves against the new policies**, and an old revision does not name the workspace where the new
policies need it:

- **Releasing #1132 (V184)** with pre-RLS code serving: positions, grids, people and notes read as empty, writes to
  them fail, and the old outreach dispatcher **stops for good every sequence that comes due** (`UNMAPPED`).
- **Releasing #1173 (V190)** with #1132's code serving: **Stripe webhook events are claimed and dropped**, the hourly
  OAuth purge (if it fires) **deletes AI-app clients with live connections**, and API keys, MCP and several webhooks
  fail until the new revision serves (§4.2).

If the new revision fails to boot, the window has no end, and **redeploying the previous tag does not fix it**,
because the migration stays applied. Release each PR on its own, at a quiet hour, with the pre-flight checks done and
the rollback SQL in hand (§5). Or remove the window by splitting each release in two (option C in §4.1 and §4.2).

---

## 1. What the two PRs do (plain language)

Uncava is multi-tenant: many firms (workspaces) share one database. Isolation rested entirely on the application:
every query adds `WHERE workspace_id = <the caller's workspace>`. **If one query ever forgot that filter, one firm could
read another firm's data.**

The two PRs add a **second, independent lock in the database itself**: Postgres row-level security (RLS). Every
database connection the API uses is labelled with the workspace it works for, and Postgres hides every row of every
other workspace on the protected tables, whatever the SQL says. The application filters stay; RLS is a backstop under
them, not a replacement.

**#1132 protects the people and position core** (11 tables):

- positions: `app_lm_project`;
- the Companies grid: `app_lm_project_candidate`, `app_lm_project_triage_company`;
- people: `app_lm_person` and its contacts, notes, documents, document versions, tags and photo;
- company notes: `app_lm_company_note`.

**#1173 protects every other tenant table** (68 more): outreach (sequences, enrollments, messages, mailboxes,
meetings, Zoom), the Notetaker's recordings and plans, tasks and the bell, clients and their representatives,
invitations, API keys and OAuth grants, the assistant, billing (subscriptions, customers, the credit ledger, usage),
the workspace's catalogs and settings, the position brief and its lists, strategies, custom columns, the person
activity timeline, and the audit trail. Two tables stay outside on purpose (`TenantTableGuard.EXCLUDED`):
`app_lm_workspace_member` and `app_lm_refresh_token`, which decide *which* workspace a session is in and are read
before one is chosen. Global tables (the company universe, vendor caches, geocoding, industries, the RBAC and plan
catalogues, OAuth clients, users and their factors, `app_lm_workspace` itself) carry no workspace and are not tenant
data.

Four consequences follow:

1. **Fail closed.** A connection with no workspace sees **nothing** in a protected table and cannot write to it. A bug
   shows up as "empty" or "not found", never as a leak.
2. **Background work must say which workspace it is working for.** Scheduled jobs, webhooks, workers and public pages
   have no signed-in user. Each names its workspace before it touches a protected table.
3. **A door that knows only a key resolves its workspace first** (#1173). A webhook names a grant, a calendar, a bot
   or a Stripe customer; a public page names a slug; a credential arrives as a hash. Once their tables are protected,
   none of those rows can be read until the workspace is known, so the door asks a narrow database function for it.
4. **The app refuses to start if RLS would be ineffective**: a superuser runtime role, a role that bypasses RLS, a
   tenant table left unprotected, or a pooled connection left in maintenance mode.

---

## 2. How it works

### 2.1 A request, end to end

```text
HTTP request ──► Spring Security puts AuthPrincipal (workspace W) in the SecurityContext
             ──► a service starts a transaction ──► the pool hands out a connection
                  └─ TenantAwareDataSource runs: select set_config('app.workspace_id', 'W', false), …
             ──► SQL runs; every policy compares row.workspace_id with app.workspace_id
             ──► only W's rows are read or written; another workspace's rows are invisible
```

### 2.2 The label, set on every connection checkout (#1132)

`core/tenant/TenantAwareDataSource` wraps Boot's Hikari pool. `TenantDataSourceConfig` installs it through a
`BeanPostProcessor`, so every consumer of the `DataSource` goes through it: JPA, `JdbcTemplate` and the native
queries. On **every checkout** it runs:

```sql
select set_config('app.workspace_id', '<uuid or empty string>', false), set_config('app.rls_maintenance', '', false)
```

The label is set at checkout, not as `SET LOCAL` at transaction begin, for two reasons:

- Spring Data's declared query methods, and a `JdbcTemplate` used outside a transaction, run in autocommit, where a
  transaction-local setting is gone before the statement runs. The project authorizer's `findByIdAndWorkspaceId` was
  the first read to 404 that way.
- Every checkout **overwrites** the value, with `''` when there is no workspace. A pooled connection therefore never
  carries the previous borrower's workspace, and a maintenance flag set on a borrowed connection dies with its
  borrower.

### 2.3 Where the workspace comes from

`TenantContext.currentWorkspaceId()` returns the first of these that is present:

1. a workspace that an open `WorkspaceScope` named (a thread-local), used by background work;
2. the signed-in principal's workspace. `AuthPrincipal` (the SPA and the extension), `ApiKeyPrincipal` (the public
   API) and `McpCaller` (MCP) all implement `TenantPrincipal`.

It **never** comes from a request parameter.

### 2.4 The SQL side

**V184 (#1132)** adds the three functions every policy uses:

```sql
app_lm_current_workspace()   -- NULLIF(current_setting('app.workspace_id', true), '')::uuid
app_lm_rls_maintenance()     -- current_setting('app.rls_maintenance', true) = 'on'
app_lm_tenant_visible(ws)    -- ws = app_lm_current_workspace() OR app_lm_rls_maintenance()
```

Every protected table is `ENABLE` **and `FORCE` ROW LEVEL SECURITY**. `FORCE` matters because `lm_app` still *owns*
the tables (until #256), and an owner skips a policy that is not forced. The `NULLIF` is needed because a custom
setting that was set once reads back as `''`, not NULL, and `''::uuid` throws.

The policies come in a few shapes:

| Shape | Policy | Where |
|---|---|---|
| **Direct** | `USING` and `WITH CHECK` both `app_lm_tenant_visible(workspace_id)` | every table with a `workspace_id`: 7 in V184, 41 in V190 |
| **Through the parent** | `EXISTS (SELECT 1 FROM <parent> p WHERE p.id = <parent key>)`; the parent's own policy answers, hop by hop | the person's children in V184 (contacts, tags, document versions, photo); 23 in V190 (the brief and its lists, strategies, custom columns, team seats, sequence steps, a recording's live lines and viewers, a note's mentions, a grant's retired tokens, template keywords, the frozen candidate contact and photo tables) |
| **Shared rows** | `workspace_id IS NULL OR app_lm_tenant_visible(workspace_id)` | V190: `app_lm_position_template` (the shared library) and `app_lm_oauth_authorization` (a grant still waiting for consent) |
| **The audit trail** | insert always allowed; read where `workspace_id IS NULL OR app_lm_tenant_visible(workspace_id)` | V190 or harden.sql step 2c: `app_lm_audit_event`. A sign-in is audited in the workspace it opens, an account's own events in none. `AuditEvent` takes its id from the sequence before the insert, because Postgres checks a row an `INSERT … RETURNING` hands back against the read policy |
| **The credit ledger** | direct | V190 or harden.sql step 2c: `app_lm_credit_entry` |

**The two grid tables gain a column (V184).** `app_lm_project_candidate` and `app_lm_project_triage_company` get their
project's `workspace_id`, so the policy is a plain comparison rather than a probe of the project on every grid line.
The column is backfilled from the project; filled on insert by the trigger `app_lm_fill_workspace_from_project`, so
neither Hibernate nor `TriageCompanyWriter`'s native insert has to name it; and held to the project's value by a
composite FK, `(project_id, workspace_id) → app_lm_project (id, workspace_id)`, backed by
`app_lm_project_id_workspace_uk`.

**harden.sql owns two of the tables.** Once it has run, `app_lm_audit_event` and `app_lm_credit_entry` belong to
`postgres`, which `lm_app` cannot `ALTER`. V190 protects them where Flyway still owns them and skips them with a
warning where it does not; harden.sql's step 2c does the same as their owner, idempotently. **Re-run harden.sql as
`postgres` after V190 deploys.**

### 2.5 Work with no signed-in user

- **`WorkspaceScope.run` / `call(workspaceId, task)`** binds a workspace for the task and restores the previous one
  afterwards.
  - It is **refused inside a live transaction begun for another workspace**, because the task would join that
    transaction's connection.
  - An after-commit listener on the publishing thread is handled specially. This always happens in tests
    (`SynchronousAuditWrites`) and in production once the async pool is full (`CallerRunsPolicy`). Spring still binds
    the committed transaction's connection, so the scope relabels that connection for the task and puts the old label
    back.
  - `runInJoinedTransaction` (#1173) is the one deliberate exception, for an account's work that must commit with the
    change behind it: founding a workspace writes its trial in the founding transaction, and a password change ends
    the account's AI-app grants in every workspace.
- **`EveryWorkspace`** runs a job's work once per workspace, inside that workspace's scope. `sum` / `forEach` walk
  every row of `app_lm_workspace` (which has no policy); the overloads taking a list walk only the workspaces given. A
  workspace that throws is logged and the walk carries on.
- **`ThreadContextPropagation`** (`core/concurrent`) carries the submitter's MDC and workspace into `@Async` tasks
  (`ThreadContextTaskDecorator`) and hand-built pools, composing `MdcPropagation` with `TenantContext.propagating`.
  This covers the assistant's tool threads, Find executives, People Search filing and the AI enrich worker. The two
  virtual-thread pools that do not propagate (`BackgroundProposer`, `OutreachOpenerService.draftAll`) call only the
  model, never the database.

### 2.6 Resolve, then scope (#1173)

**V189, the door resolvers.** A webhook, public page or credential knows only a key. One `SECURITY DEFINER` function
per key answers `(id, workspace_id)` and nothing of the row; the door then opens `WorkspaceScope` on that workspace and
reads the row under the fence. Each raises the maintenance flag transaction-locally for its one query and puts it back
(a function-level `SET` of a placeholder setting needs a superuser since Postgres 15). `TenantResolvers` is the only
caller.

| Key | Function |
|---|---|
| Nylas grant, booking configuration, Recall calendar | `app_lm_resolve_mailboxes_by_grant`, `…_by_booking_configuration`, `…_by_recall_calendar` |
| Booking slug | `app_lm_resolve_mailbox_by_booking_slug` |
| A user's mailboxes in every workspace (a time-zone change reaches each) | `app_lm_resolve_mailboxes_of_user` |
| Mailbox / Zoom consent state | `app_lm_resolve_mailbox_authorization` |
| Recall bot, final transcript | `app_lm_resolve_recording_by_bot`, `…_by_final_transcript` |
| API key hash | `app_lm_resolve_api_key` |
| OAuth token or state hash, grant id, a user's grants | `app_lm_resolve_oauth_authorization`, `…_by_id`, `…_oauth_authorizations_of_user` |
| Invitation token, invitations waiting for an address | `app_lm_resolve_invitation`, `…_pending_invitations_for` |
| Stripe customer | `app_lm_resolve_billing_customer` |
| One trial per founder, across workspaces | `app_lm_trial_started_by` (a boolean) |
| The platform library's per-code count of firm copies | `app_lm_count_firm_templates_by_code` (counts only) |

**V191, the job resolvers.** A frequent job used to open every workspace's scope on every tick to ask its own claim:
a transaction per tenant per tick, most answering nothing, which also took the backend suite from about 11 to about
18½ minutes. Each of these asks the question once, across the tenants, before any scope, and answers workspace ids
alone. The job then walks only those workspaces and claims there, under the fence, as before. Each condition is
**wider than its job's claim, never narrower**: a workspace named with nothing to claim costs one empty pass; one left
out would leave its work stuck.

| Job | Interval | Function | Asks for |
|---|---|---|---|
| `OutreachDispatcher.dispatchAt` | 60s | `app_lm_workspaces_with_sends_due(at)` | a live run's next email or a consultant's own scheduled one due, or any claim still held |
| `InterviewCoachWorker.sweepAt` | 15s | `app_lm_workspaces_with_copilot_listening()` | a bot in a call whose live copilot is listening |
| `NotificationEmails.emailUnreadAt` | 1 min | `app_lm_workspaces_with_notification_emails_due(at)` | an unread, unarchived row past its delay, not yet emailed |
| `TaskDueScanner.scanAt` | 5 min | `app_lm_workspaces_with_tasks_falling_due(after, until)` | an open task in the due-soon or overdue window not yet announced as both |
| `StripeSeatSync.syncDueAt` | 5 min | `app_lm_workspaces_owing_seat_sync(before, limit)` | a seat quantity owed to Stripe |
| `CreditLedgerSweeper.sweepAt` | 5 min | `app_lm_workspaces_with_lapsed_credits(at, limit)` | an unsettled hold past its time, or a lapsed grant still holding credits |

The hourly and daily jobs (the purges, the digest, the reply poll, the billing resets and notices, the reconcile) keep
walking every workspace: their cost is small at that rate.

A resolver is a deliberate hole in the fence. The rules: one key or one job question per function, one query, ids out,
`TenantResolvers` the only caller, and never widen a scope to make an empty read work — add a resolver instead.

### 2.7 Paths converted

| Path | How it names its workspace | PR |
|---|---|---|
| Outreach dispatcher (`OutreachSendService`, `OutreachMessageSendService`) | V191's `sends_due`, then claims inside each workspace's scope | #1132, narrowed in #1173 |
| Reply poll and inbox, `MeetingBackfill`, `RecallCalendars` | walks the workspaces, scope per mailbox | #1132 / #1173 |
| Nylas, Recall calendar and bot webhooks, the live feed | V189 by grant, calendar, bot or transcript, then scope | #1173 |
| Public booking page (`DirectBookingPage`, `BookingPages`) | V189 by slug, then scope | #1173 |
| Notetaker workers, reconcile, transcript purge | scope from the recording; the jobs walk the workspaces | #1132 / #1173 |
| Live copilot sweep (`InterviewCoachWorker`) | V191's `copilot_listening` | #1173 |
| `TaskDueScanner` | V191's `tasks_falling_due`; its claim SQL adds `workspace_id = ?` | #1132 / #1173 |
| `NotificationEmails` (delayed emails) | V191's `notification_emails_due` | #1132 / #1173 |
| `NotificationEmails` (digest), `PersonNotePurge`, `CompanyNotePurge` | `EveryWorkspace` (hourly) | #1132 |
| Billing jobs: reset, reconcile, notices, grandfathering | `EveryWorkspace` | #1173 |
| `StripeSeatSync`, `CreditLedgerSweeper` | V191's `owing_seat_sync`, `lapsed_credits` | #1173 |
| Stripe webhook (`StripeEventHandler`) | V189 by customer, then scope | #1173 |
| API keys (`ApiKeyIntrospector`), MCP (`McpCredentials`), OAuth server (`HashingAuthorizationService`, `OAuthGrantService`) | V189 by hash or id, then scope | #1173 |
| `OAuthGrantPurge` | walks the workspaces | #1173 |
| Invitations (accept, pending for an address), onboarding's trial | V189, then scope; `runInJoinedTransaction` for the trial | #1173 |
| `IntegrationSecretExpiryWarnings`, `ZoomService`, `MailboxService` | walks the workspaces / V189 by user | #1173 |
| Platform screens (credit grants, subscriptions, template library) | scope per workspace; the library counts through V189 | #1173 |
| MCP tools (`McpToolCalls`) | the caller's workspace | #1132 |

**Fail-open traps, all pinned by tests.** A check that reads "not found" as "allowed" turns RLS's empty answer into a
pass. #1132 fixed two: `NOT IN (archived ids)` and `existsByIdAndArchivedAtIsNotNull` would have seen no archived
positions, so tasks on archived positions would have fallen due and mentions of deleted notes been emailed. #1173's
were worse, each found while converting: run unscoped, the notetaker's moved-call check saw every calendar empty and
called back every waiting bot; the OAuth purge saw no grant and deleted every old client with live connections (its
cascade taking them); the drift check reported no drift; and a founder's every workspace started a fresh trial.

### 2.8 The guard rails

- **`RowLevelSecurityPosture`** is a `SmartInitializingSingleton`, so it runs after Flyway and before the web server and
  the scheduler start. It fails the boot if:
  - the runtime role is a superuser or holds `BYPASSRLS`;
  - a tenant table is unprotected (below);
  - `app.rls_maintenance` is on for a pooled connection.

  `lightmove.row-level-security.require-enforced` (`RLS_REQUIRE_ENFORCED`, default `true`) turns those failures into
  warnings when false. Production never sets it: `deploy.yml` uses `--set-env-vars`, which replaces the whole list, so
  the default holds.
- **`TenantTableGuard`** (#1173) replaces #1132's fixed list. It reads the tenant tables off the schema — every `app_lm_`
  table with a `workspace_id`, and every table holding a foreign key into one, however many hops down — and each must
  be enabled, forced and given a policy unless it is on `EXCLUDED` with its reason. **A new tenant table's migration
  must protect it in the same file**, or the next boot fails. `HARDEN_OWNED` tables still waiting on harden.sql are
  logged at every boot rather than refused: the deploy has already migrated, and a refused revision would leave the one
  before it serving the new policies.
- **Flyway's maintenance flag.** `application.yml` gives Flyway its own `user`/`password`, so Flyway builds its own
  DataSource rather than borrowing from the pool, with `init-sqls: SET app.rls_maintenance = 'on'`. `deploy.yml` and
  `ops/gcp/deploy.sh` add `"-initSql=SET app.rls_maintenance = 'on'"` to the Flyway container. Migrations' backfills
  therefore reach every workspace, while the flag never reaches the app's pool — and `TenantAwareDataSource` clears it
  on every checkout anyway.

### 2.9 Test, dev and e2e infrastructure (#1132)

A superuser skips every policy, so before #1132 the test suite proved nothing about RLS. Now:

- **Tests.** `TestcontainersConfig` runs `testcontainers/app-role.sql`, which creates `lm_app` as `NOSUPERUSER
  NOBYPASSRLS` owner of the database and points `spring.datasource.*` at it. `max_connections` is 300, because a
  non-superuser cannot use the reserved slots. Fixture SQL runs as the superuser through `@FixtureJdbc`; tests that
  call beans directly wrap them in `TestWorkspaceScopes`.
- **`npm run dev`** (`ops/dev/db.sh`). The container's superuser is `postgres`, and `lm_app` is an ordinary owner
  (`ensure_app_role`). `dev:db:psql`, `seed-report` and `seed-firm` connect as `postgres`. A volume made before #1132 is
  refused with "run `npm run dev:db:reset`", because Postgres will not demote its bootstrap superuser.
- **e2e.** `e2e/stack/up.sh` starts Postgres as `postgres` and creates `lm_app`. `e2e/api/lib.sh` and the
  `e2e/spa/*.mjs` fixtures connect as `postgres`.

Tests for the fence itself:

| Test | Checks | PR |
|---|---|---|
| `RowLevelSecurityIntegrationTest` | the posture holds; a workspace reads and writes only its own rows, through repositories too, on each policy shape; a borrowed connection carries only its borrower's label; a scope cannot join another workspace's transaction | #1132, extended in #1173 |
| `RowLevelSecurityMigrationTest` | V184's backfill, trigger and composite key | #1132 |
| `TenantTableGuardIntegrationTest` | a new table with a workspace, or a new child of a tenant table, is named until it is protected | #1173 |
| `TenantResolversIntegrationTest` | a key names its row's workspace and nothing more; an unknown key names nothing; V189's functions answer ids only, V191's workspace ids only; the maintenance flag goes back down | #1173 |
| `EveryWorkspaceTest` | a failing workspace stops none after it, each runs in its own scope | #1132 |
| The jobs' own integration tests (`OutreachDispatchIntegrationTest`, `TaskIntegrationTest`, `NotificationIntegrationTest`, `SeatBillingIntegrationTest`, `CreditJobsIntegrationTest`, …) | each job still finds and claims its work through its V191 resolver; `TaskIntegrationTest` also shows a quiet workspace is not named | #1173 |

---

## 3. What RLS does *not* stop yet (until #256)

- `lm_app` still **owns** the tables. It could `ALTER TABLE … DISABLE ROW LEVEL SECURITY`.
- Any session can call `set_config('app.workspace_id', …)` or `set_config('app.rls_maintenance', 'on', …)`. On the
  app's pool both are overwritten at the next checkout, so neither outlives the borrower that set it.
- V189's and V191's functions answer across workspaces by design. They return ids alone, but anyone who can run SQL as
  `lm_app` can call them.

So this is a backstop against **application bugs** (a forgotten filter), **not against SQL injection**. #256 moves
ownership to `lm_migrate`, drops the maintenance arm (from the policies and from each resolver), and makes the posture
check also assert that the runtime role owns nothing. Even after #256, a role that can set `app.workspace_id` can choose
its workspace. Closing that needs a signed context or per-tenant roles, and no issue builds them yet.

---

## 4. Production risks

"Window" means the time from the end of the `Migrate the database` step until `gcloud run deploy` has moved traffic to
the new revision. That is normally one to a few minutes. It has no end if the new revision never starts.

### 4.1 Releasing #1132 (V184)

Skip this section if a release containing `8c5050ad` has already gone out.

#### Risk 1 (HIGH): the old revision serves against V184 during the window

**Why.** `deploy.yml` builds the image, **migrates**, and only then deploys. In between, the running revision is the
pre-RLS code, which never sets `app.workspace_id`, so its connections see nothing in the 11 tables.

**What users and jobs see in the window:**

- **Users:** empty positions list, empty grids and Candidates page, "not found" on a position. A save that touches a
  protected table fails with `new row violates row-level security policy`, shown as a 500.
- **Outreach dispatcher (every minute), the one lasting harm.** `prepare()` looks the candidate up, finds nothing, and
  calls `outcomes.stop(…, UNMAPPED)`. **Every sequence step that comes due in the window is stopped for good**, with an
  `OUTREACH_STOPPED` line and an audit event. A consultant's own scheduled email that comes due is likely failed the
  same way.
- **Task due scan:** sees no archived positions, so tasks on archived positions may be announced on the bell.
- **Notification emails:** may email a mention whose note was deleted.
- **Webhooks** (Nylas, Recall) reaching the old revision cannot match attendees or candidates. Meetings may be missed
  until the next calendar read, and inbound replies left to the provider's retry or the next poll.

**Mitigations, in increasing order of safety:**

- **A. Pick a quiet window.** Release outside every sequence's sending hours (Mon–Fri 08:00–18:00 in each mailbox's
  time zone, `Asia/Dubai` by default); a weekend night in Dubai is best. Confirm nothing is due (§5.1, query 3), and
  after the release run the recovery query (§5.3).
- **B. Pause the scheduler during the window.** There is no switch for this today; it would need a small change.
- **C. (Removes the window.) Split the release:** release 1 carries the code and a migration with only the functions,
  the `workspace_id` column, the trigger and the composite key — no `ENABLE`/`FORCE`/`CREATE POLICY` — with the
  posture check told to wait (a warning, or `RLS_REQUIRE_ENFORCED=false` for that release through `deploy.yml`).
  Release 2 carries only the policies, and the revision serving during its window is already RLS-aware. Now that
  #1132 is merged as one migration, this means replacing V184 before any release, which is only possible while no
  environment that matters has applied it.

#### Risk 2 (HIGH): a failed boot after V184 leaves production broken, and the normal rollback does not fix it

If the new revision does not start after V184 has committed (the posture check, a missing secret, anything), the old
revision keeps serving under V184 — risk 1 with no end. The Deploy workflow's own rollback (`workflow_dispatch` with an
older tag) runs Flyway (a no-op) and deploys the old code under V184: **still broken.** The only fix is to turn RLS off
by hand (§5.4). Have that SQL and the owning role's credentials ready. `ops/cloudsql/psql.sh` connects as your IAM user,
read-only, which cannot `ALTER` these tables.

#### Risk 3 (MEDIUM): migration locks on hot tables

V184 runs in one transaction. It takes `ACCESS EXCLUSIVE` locks on all 11 tables, rewrites every candidate and triage
row once (the backfill), and validates two foreign keys. A long query from the old revision (an export, the assistant, a
large grid read) makes the migration wait, and while it waits **every new query on that table queues behind it**. The
pool is small (`DB_POOL_MAX=5` × at most 3 instances). The rows are probably thousands, so the migration itself takes
seconds. Mitigation: the quiet window; optionally `SET lock_timeout = '10s'` in Flyway's `-initSql`, so a blocked
migration fails cleanly instead of stalling the site.

#### Risk 4 (MEDIUM): the production role and table owner

- `lm_app` must be `rolsuper = f` and `rolbypassrls = f`, or the boot fails (risk 2). `harden.sql` revoked
  `cloudsqlsuperuser` from `lm_app`, so this is expected; check it (§5.1, query 1).
- **The Flyway role must own the tables it alters**, or `ALTER TABLE … ENABLE ROW LEVEL SECURITY` fails. That failure is
  **safe**: the migration rolls back and the old revision keeps serving. It matters if `create-migrate-role.sh` has
  already switched Flyway to `lm_migrate` (§5.1, query 2).

### 4.2 Releasing #1173 (V189, V190, V191)

V189 and V191 only add functions; they are harmless to any revision. **V190 is the one with a window**: the revision
serving during it is #1132's code, which names the workspace for signed-in requests and for the jobs #1132 converted,
but reads every row V190 newly protects without one.

#### Risk 5 (HIGH): the old revision serves against V190 during the window

What #1132's code does under V190:

| Path | Effect | Lasting? |
|---|---|---|
| **Stripe webhook** | `StripeEventHandler` claims the event in `app_lm_billing_webhook_event`, then looks the customer up unscoped, finds none and does nothing. A subscription change, a paid invoice (the month's credits), a failed payment or a bought pack in the window is **claimed and dropped**, and Stripe, answered 200, never resends it. | **Yes**: recover by hand (§5.3) |
| **OAuth purge** (hourly, if it fires in the window) | Sees no grants, so deletes every registered AI-app client last authorised over 30 days ago, and the cascade deletes its **live connections**. Users must reconnect those apps. | **Yes** |
| **Trial check** at founding | Sees no earlier trial, so a repeat founder's new workspace gets a fresh trial. | Yes, minor |
| API keys, MCP calls, the OAuth token endpoint | The key or grant is not found: **401** for every call. | No |
| Accepting an invitation | "Not found". | No |
| Nylas, Recall calendar and bot webhooks | The mailbox or recording is not found: the event is dropped. Calendar reads and the Notetaker reconcile catch up later; a reply is found by the next poll. | Mostly no |
| The outreach dispatcher, reply poll, billing jobs, due scan, emails | Their unscoped reads see nothing, so they do nothing until the new revision runs. Credits and reminders are late, not lost. | No |
| Audit lines for sign-ins | #1132's audit insert uses `RETURNING`, which the read policy checks; a sign-in's line (another workspace than the empty label) may fail to be written. | Lines lost |
| Signed-in users | Read and write normally: requests carry their workspace. | No |

**Mitigations:**

- **A. Pick a quiet window**, as for risk 1, and confirm with Stripe's dashboard that no renewal, invoice or pack
  purchase is expected in it (renewals fall on each subscription's period start). Do it outside the OAuth purge's hour
  if you can tell when it last ran (`MCP_PURGE_INTERVAL`, one hour from boot). Afterwards, run §5.3's #1173 checks.
- **C. (Removes the window.) Split the release:** release 1 = the code + V189 + V191, with V190 held back and the
  posture told to wait (`RLS_REQUIRE_ENFORCED=false` for that release, since the guard would name ~68 unprotected
  tables). The new code works on unprotected tables: its resolvers and scopes answer the same. Release 2 = V190 alone,
  with the posture strict again; the revision serving during its window is already #1173's. **The PR does not yet
  ship this way**: V190 would have to move to a follow-up PR merged after release 1.

#### Risk 6 (HIGH): a failed boot after V190

As risk 2: the old revision keeps serving under V190 (risk 5 with no end), and redeploying an older tag does not help.
The fix is §5.4's rollback for the V190 tables, leaving the 11 core tables on: #1132's code checks exactly those at boot
and expects them forced.

#### Risk 7 (MEDIUM): harden.sql must run again

Where harden.sql has already run (production), V190 skips `app_lm_audit_event` and `app_lm_credit_entry` with a warning.
Until someone re-runs harden.sql as `postgres`, those two are unprotected and every boot logs that they wait on it.
Nothing breaks; the backstop is just missing there.

#### Risk 8 (MEDIUM): locks on many tables at once

V190 rewrites no rows, so it is quick once it has its locks, but it takes `ACCESS EXCLUSIVE` on 68 tables in one
transaction, including the busiest (the audit trail, outreach messages, notifications, tasks). One long query on any
of them holds it up, and everything behind it queues. Same mitigation as risk 3.

#### Risk 9 (MEDIUM): shared rows are writable by every workspace (review item R1)

The shared-rows policy's `WITH CHECK (workspace_id IS NULL OR …)` lets any workspace's connection write a library
template or a grant still waiting for consent. The application gates both (the platform authorizer; the consent flow),
so this is a gap in the backstop, not a hole in the app. The fix is a shared read and an own-workspace write: separate
`FOR SELECT` and `FOR INSERT/UPDATE/DELETE` policies. Not done.

### 4.3 Risks common to both

#### Risk 10 (MEDIUM): a missed path fails *silently*

Fail-closed means a background path that does not name its workspace does not crash: it sees nothing and carries on. A
webhook matches nobody, a job finds no work, a check sees "no archived positions". Both PRs swept every `@Scheduled`
job, every hand-built pool, every principal type, every public door and the account-level code. Coverage is weakest
where tests use fakes: real Nylas, Recall and Stripe deliveries; the Chrome extension; the public booking page with a
real calendar. After each release, watch the logs for `violates row-level security policy`, unexpected `UNMAPPED` /
`LEFT_THE_RUNNING` stops, and webhook deliveries that match nobody.

A V191 resolver whose condition is narrower than its job's claim would be the same kind of silent miss: work that
never runs. Each condition is written as a superset of its claim, and the jobs' integration tests drive each job
through its resolver. **Change a job's claim and its V191 function together.**

#### Risk 11 (LOW to MEDIUM): performance

- **One extra round trip per connection checkout** (the `set_config`), on every transaction and autocommit query —
  about a millisecond through the Cloud SQL connector. On a screen that makes many small reads, it adds up.
- **Children's policies are an `EXISTS` per row**, a primary-key probe on the parent, two hops for the brief's lists and
  a sequence's steps. This matters on the Candidates page (`PersonPoolQuery`) and wherever many contacts, tags or
  brief rows are read.
- **Jobs.** The hourly and daily jobs open one scope per workspace per run. The frequent ones walk only V191's answer.
- **Not yet measured:** query plans before and after for the Candidates list and the Companies grid (§6.6).

#### Risk 12 (LOW): people and tools reading the database directly

- Anyone using `ops/cloudsql/psql.sh` (IAM, read-only) sees **zero rows** in every protected table until they run
  `SET app.workspace_id = '<id>'`, or `SET app.rls_maintenance = 'on'` to see everything. Saved queries, BI tools and
  ad hoc reports on those tables silently return nothing.
- `ops/eval/export-golden.sh` connects as `lm_app` and will export an **empty golden set** unless it sets the
  maintenance flag or connects as `postgres`. Not yet updated.

#### Risk 13 (LOW, team): the shared dev database and local volumes

- **`npm run dev:cloud` applies the branch's migrations to the shared Cloud SQL dev database at boot.** Everyone
  running older code against it then sees empty screens or the failures above. Do not run `dev:cloud` from
  `feature/1085-rls-rest` until it is merged.
- Every developer's local volume needed **one `npm run dev:db:reset`** after #1132 landed (it keeps the Apollo snapshot
  but wipes local accounts and data). A volume that has run #1173's migrations under their old numbers (V172, V173)
  needs another reset: Flyway refuses a database holding applied migrations it can no longer find.

#### Risk 14 (LOW): CI

- Two backend tests are red on a clean `main` on the author's machine (`CandidateReadsGoldenIntegrationTest`,
  `OutreachReplyMigrationTest`: time-zone and golden-file drift). CI is the judge.
- CI's "Migration versions are above main's" check refuses a migration not above `main`'s highest. If anything above
  V184 lands on `main` first, #1173's three must move up again, with every reference to them.

---

## 5. Release runbooks

### 5.1 Pre-flight (read-only; `ops/cloudsql/psql.sh` is enough)

```sql
-- 1. The runtime role is bound by policies: must be  f | f
SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'lm_app';

-- 2. Who owns the tables the migration alters (expect the Flyway user, lm_app today; after harden.sql,
--    app_lm_audit_event and app_lm_credit_entry are postgres's)
SELECT pg_get_userbyid(relowner) AS owner, count(*)
  FROM pg_class
 WHERE relnamespace = 'public'::regnamespace AND relkind = 'r' AND relname LIKE 'app\_lm\_%'
 GROUP BY 1;

-- 3. Nothing due during the window (both should be 0; run just before releasing)
SELECT count(*) AS sequence_steps_due
  FROM app_lm_outreach_enrollment
 WHERE status IN ('SCHEDULED','ACTIVE') AND next_send_at <= now() + interval '30 minutes';
SELECT count(*) AS own_emails_due
  FROM app_lm_outreach_message
 WHERE status = 'SCHEDULED' AND send_at <= now() + interval '30 minutes';

-- 4. (#1132 only) Size of what V184's backfill rewrites
SELECT (SELECT count(*) FROM app_lm_project_candidate) AS candidates,
       (SELECT count(*) FROM app_lm_project_triage_company) AS triage_rows;
```

For **#1173**, also check Stripe's dashboard for renewals, invoices or pack purchases due in the window (risk 5), and
have `postgres`'s credentials ready to re-run harden.sql afterwards (risk 7).

Then, for either:

- check that **CI is green** on `main` after the merge;
- release the PR **on its own**, so a rollback is about one thing;
- have the **rollback SQL (§5.4) and the owner's credentials** ready. The owner is `lm_app`; its password is the
  `lightmove-flyway-password` / `lightmove-db-password` secret, used through the Cloud SQL Auth Proxy as the deploy step
  does.

### 5.2 Release

Run **Release** (it calls Deploy) in the quiet window. Note the time the `Migrate the database` step ends and the time
the `Smoke test` step passes: those two times bound the window. For #1173, re-run `ops/cloudsql/harden.sql` as
`postgres` once the deploy has finished.

### 5.3 After the release

```sql
-- As lm_app or any role, with the maintenance flag so the protected tables are visible:
SET app.rls_maintenance = 'on';

-- Coverage: every protected table t | t, at least one policy each
-- (11 rows after #1132; 79 after #1173 and harden.sql)
SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity,
       (SELECT count(*) FROM pg_policy p WHERE p.polrelid = c.oid) AS policies
  FROM pg_class c
 WHERE c.relnamespace = 'public'::regnamespace AND c.relrowsecurity ORDER BY 1;

-- (#1132) Backfill: no grid row without a workspace (expect 0, 0)
SELECT (SELECT count(*) FROM app_lm_project_candidate WHERE workspace_id IS NULL),
       (SELECT count(*) FROM app_lm_project_triage_company WHERE workspace_id IS NULL);

-- (#1132) Sequences the OLD revision stopped during the window (replace the two times)
SELECT id, workspace_id, project_id, candidate_id, stopped_at
  FROM app_lm_outreach_enrollment
 WHERE stop_reason = 'UNMAPPED' AND stopped_at BETWEEN '<migrate end>' AND '<smoke test passed>';

-- (#1173) Stripe events the OLD revision claimed during the window
SELECT event_id, type, received_at
  FROM app_lm_billing_webhook_event
 WHERE received_at BETWEEN '<migrate end>' AND '<smoke test passed>';

-- (#1173) AI-app clients deleted in the window: compare with the count before the release
SELECT count(*) FROM app_lm_oauth_client WHERE source IN ('DCR', 'CIMD');

RESET app.rls_maintenance;
```

- **A stopped sequence** is one that stopped wrongly. Tell its consultant, who can add that person to the sequence
  again; there is no "resume" in the app.
- **A Stripe event claimed in the window** may have been dropped. As the owner, delete its row from
  `app_lm_billing_webhook_event`, then resend it from Stripe's dashboard (Developers → Events → Resend). The handler is
  idempotent: a grant is keyed per month or per payment, and an older subscription event never overwrites a newer one.
- **AI-app clients missing** after the window were taken by the purge with their connections. Their users reconnect
  from the AI app; nothing can be restored.

Then smoke-test the app as a real user: positions list, the Companies grid, the Candidates page, a person drawer,
Outreach, Settings → Billing and Settings → API keys, and an API key call. In Cloud Logging, search for
`violates row-level security policy`, `Row-level security is not in force` and `waits on ops/cloudsql/harden.sql`
(the last should be gone once harden.sql has run).

### 5.4 Rollback (only if the new revision will not serve, or the app misbehaves)

Run as the **owner** (`lm_app`; `postgres` for the audit trail and the ledger once harden.sql owns them). For #1132's
release, the list is the 11 core tables; the new column, trigger and foreign key are harmless to old code. For #1173's,
roll back V190's tables and leave the core on, since #1132's code expects them:

```sql
DO $$
DECLARE t text;
BEGIN
  FOR t IN
    SELECT c.relname FROM pg_class c
     WHERE c.relnamespace = 'public'::regnamespace AND c.relrowsecurity
       AND pg_get_userbyid(c.relowner) = current_user
       -- For #1173's rollback keep #1132's core on; for #1132's, drop this condition.
       AND c.relname NOT IN ('app_lm_project','app_lm_project_candidate','app_lm_project_triage_company',
                             'app_lm_person','app_lm_person_contact','app_lm_person_note','app_lm_person_document',
                             'app_lm_person_document_version','app_lm_person_tag','app_lm_person_photo',
                             'app_lm_company_note')
  LOOP
    EXECUTE format('ALTER TABLE %I NO FORCE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I DISABLE ROW LEVEL SECURITY', t);
  END LOOP;
END $$;
```

Then redeploy the previous tag, if the new revision is the problem. Two things to know:

- After this, the **newer** code refuses to boot ("not forced on …" / unprotected tenant tables) until RLS is back on.
- Flyway still records the migration as applied, so it never re-runs. Turning RLS back on needs the same block with
  `ENABLE` / `FORCE`, or a new migration that does it. The policies themselves are kept, so `ENABLE` and `FORCE` alone
  restore them.

---

## 6. Testing locally

Use Git Bash from the repository root, with Docker running. The dev Postgres container is `lm-dev-pg` on `:55433`,
password `lm`.

**Note:** `npm run dev:db:psql` connects as `postgres`, a superuser, which is **above** RLS. To see what the API sees,
connect as `lm_app`:

```bash
docker exec -it lm-dev-pg psql -U lm_app -d lightmove
```

### 6.1 Bring the stack up

```bash
git checkout feature/1085-rls-rest && git pull   # #1173 (main already has #1132)
npm run dev:db:reset                             # once: keeps the Apollo snapshot, wipes local accounts and data
npm run dev                                      # postgres :55433 + api :8080 + web :5173
```

In the API log, check that:

- Flyway applied **V184** (`row level security core`), **V189** (`tenant resolvers`), **V190**
  (`row level security rest`) and **V191** (`workspaces with work`);
- there is **no** `Row-level security is not in force: …` (that message would have stopped the boot), and no
  `waits on ops/cloudsql/harden.sql` (locally Flyway owns every table).

If `db.sh` prints "this volume predates row-level security", the reset was skipped. Run it and start again.

### 6.2 Inspect the database as `lm_app`

```sql
-- 1. The runtime role is bound by policies: expect f | f
SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user;

-- 2. Coverage: expect 79 tables, t | t, at least one policy each
SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity,
       (SELECT count(*) FROM pg_policy p WHERE p.polrelid = c.oid) AS policies
  FROM pg_class c
 WHERE c.relnamespace = 'public'::regnamespace AND c.relrowsecurity ORDER BY 1;

-- 3. Fail closed: with no workspace set, protected tables are empty
SELECT set_config('app.workspace_id', '', false);
SELECT (SELECT count(*) FROM app_lm_project)             AS projects,
       (SELECT count(*) FROM app_lm_person)              AS people,
       (SELECT count(*) FROM app_lm_outreach_enrollment) AS enrollments,
       (SELECT count(*) FROM app_lm_task)                AS tasks,
       (SELECT count(*) FROM app_lm_position)            AS briefs;   -- all 0

-- 4. One workspace at a time (app_lm_workspace has no policy, so it lists them all)
SELECT id, name FROM app_lm_workspace ORDER BY created_at;
SELECT set_config('app.workspace_id', '<workspace A id>', false);
SELECT count(*), count(DISTINCT workspace_id) FROM app_lm_project;   -- only A's: distinct = 1

-- 5. A write into another workspace is refused (A is still set)
UPDATE app_lm_project SET position_title = position_title WHERE workspace_id = '<workspace B id>';   -- UPDATE 0
INSERT INTO app_lm_company_note (id, workspace_id, project_id, triage_company_id, kind, body, author_user_id)
VALUES (gen_random_uuid(), '<workspace B id>', gen_random_uuid(), gen_random_uuid(), 'GENERAL', 'probe', gen_random_uuid());
-- expected: ERROR: new row violates row-level security policy for table "app_lm_company_note"

-- 6. The resolvers answer across workspaces, ids only, and leave the flag down
SELECT set_config('app.workspace_id', '', false);
SELECT * FROM app_lm_resolve_mailbox_by_booking_slug('<a booking slug>');   -- id | workspace_id
SELECT * FROM app_lm_workspaces_with_sends_due(now());                       -- workspace ids
SELECT app_lm_rls_maintenance();                                             -- f
```

### 6.3 App smoke test: two firms

1. Sign up **firm A** in one browser profile and **firm B** in another (or a private window). In each:
   - create a business unit or client and a position, and fill the brief's steps;
   - add a company and an executive;
   - add a person note, a company note, a document and a task;
   - create a sequence.
2. Signed in as A, open B's URLs: `/projects/<B project id>`, a B person in the Candidates drawer. Expect
   **404 / not found**, never B's data.
3. Check A's own screens render normally: Companies grid, Map and Pipeline; the Candidates page and a person drawer
   (Notes, Documents, Contact, Activity); the Position brief; the Dashboard; Outreach; My tasks and the bell;
   Settings → Billing, API keys, Connected AI apps, Integrations and Security → Audit log.
4. **Several workspaces:** from A, invite B's admin; accept from the topbar menu; switch workspaces. Each workspace
   shows only its own data. Accept another invitation from its **emailed link** while signed out (a V189 door).
5. **Doors with no browser session:**
   - Create a workspace API key and call `GET /api/v1/public/...` with it: it reads only that workspace.
   - Connect an MCP client (or call `/api/v1/mcp` with a key holding `mcp:use`).
   - `GET /api/v1/outreach/booking/no-such-slug` answers 404; a real slug's page loads its slots.
   - The API log shows no `violates row-level security policy`.
6. Where configured locally, also try: the Chrome extension capture; Find executives; the spreadsheet import and the
   export; Assess with AI; an assistant question; a Stripe test-mode checkout (its webhook is a V189 door).

### 6.4 Background jobs

The jobs run on their own in `npm run dev`. To exercise them deliberately:

- **Task due scan:** create a task due in a few minutes on a position, and another on a position you then **archive**.
  Only the live one rings the bell.
- **Note purge:** delete a note, then (as `postgres`) set its `deleted_at` back past `lightmove.notes.purge-after`. It
  is purged on the next run (`NOTES_PURGE_INTERVAL`, one hour by default).
- **Outreach** (needs a connected mailbox): start a one-step sequence with `NOW`. The email goes out within a minute,
  and the run is **not** stopped as `UNMAPPED`.
- **Quiet workspaces cost nothing:** with `logging.level.org.hibernate.SQL=DEBUG`, a dispatcher tick with nothing due
  anywhere runs `app_lm_workspaces_with_sends_due` and nothing else.

### 6.5 Negative checks: the posture refuses to boot

```sql
-- as lm_app: a protected table left unforced
ALTER TABLE app_lm_person NO FORCE ROW LEVEL SECURITY;
```

Restart the API (`npm run dev:stop`, then `npm run dev`). The boot fails with
`Row-level security is not in force: …app_lm_person…`. Start it once with `RLS_REQUIRE_ENFORCED=false`: it boots and
logs the same text as a warning. Then put it back:

```sql
ALTER TABLE app_lm_person FORCE ROW LEVEL SECURITY;
```

And a new tenant table nobody protected (the guard reads the schema, so no list needs updating):

```sql
CREATE TABLE app_lm_probe (id uuid PRIMARY KEY, workspace_id uuid REFERENCES app_lm_workspace (id));
-- restart: the boot names app_lm_probe; then
DROP TABLE app_lm_probe;
```

### 6.6 Query plans

As `lm_app`, with a workspace set (§6.2, step 4), run `EXPLAIN (ANALYZE, BUFFERS)` on:

- the SQL behind the Candidates list (`PersonPoolQuery`);
- the Companies grid's lines read (`StageLinesService`);
- a person drawer's contacts and tags read;
- the position brief's read (two-hop children);
- each V191 function as `postgres` with `SET app.rls_maintenance = 'on'` (they read across workspaces).

To capture the statements, set `logging.level.org.hibernate.SQL=DEBUG`, or use `pg_stat_statements`. Compare against
`main` before #1132 on the same data, for example after `npm run dev:db:seed-report`. Look for a `SubPlan` per row on
children, and for sequential scans where an index scan used to be.

### 6.7 Backend tests and e2e

```bash
cd apps/api
./mvnw test -Dtest='RowLevelSecurity*Test,TenantResolvers*Test,TenantTableGuard*Test,EveryWorkspaceTest'
./mvnw test                                  # full suite, needs Docker
cd ../../e2e && PROFILE=e2e ./run-all.sh     # always PROFILE=e2e
```

- The full suite should pass, apart from the two tests also red on `main` locally (risk 14).
- If a reused Testcontainers database predates #1132, it may lack the `lm_app` role. Remove the container and the next
  run recreates it.
- Web tests on Node 26 need `NODE_OPTIONS=--no-experimental-webstorage`.

### 6.8 Going back to an older branch

Your local database now has V184 and V189–V191 applied. Older code does not name the workspace everywhere these migrations
expect, so screens read empty and jobs do nothing. Flyway ignores unknown future migrations, so nothing errors. Run
`npm run dev:db:reset` when you switch back.

---

## 7. File map

| Area | #1132 | #1173 |
|---|---|---|
| Migrations | `V184__row_level_security_core.sql` | `V189__tenant_resolvers.sql`, `V190__row_level_security_rest.sql`, `V191__workspaces_with_work.sql` |
| Tenant core (`core/tenant/`) | `TenantAwareDataSource`, `TenantDataSourceConfig`, `TenantContext`, `TenantPrincipal`, `WorkspaceScope`, `EveryWorkspace`, `RowLevelSecurityPosture` | `TenantResolvers`, `ResolvedRow`, `TenantTableGuard`; `WorkspaceScope.runInJoinedTransaction`; `EveryWorkspace`'s list overloads |
| Config | `RowLevelSecuritySettings`, `LightMoveProperties`, `application.yml` (Flyway user and `init-sqls`, `lightmove.row-level-security`) | — |
| Principals and propagation | `AuthPrincipal`, `ApiKeyPrincipal`, `McpCaller`, `McpToolCalls`, `ThreadContextPropagation`, `ThreadContextTaskDecorator` | `ApiKeyIntrospector`, `McpCredentials`, `HashingAuthorizationService`, `OAuthGrantService`, `AuthResponseAssembler` |
| Outreach | `OutreachSendService`, `OutreachMessageSendService`, `OutreachInboxService`, `MeetingBackfill`, `RecallCalendars`, `DirectBookingPage` | the same, plus `OutreachDispatcher`, `BookingPages`, `MailboxService`, `ZoomService`, `IntegrationSecretExpiryWarnings`, `MailboxConnectionRepository` |
| Notetaker | `MeetingNotesWorker`, `InterviewCoachWorker` | `NotetakerService`, `NotetakerWebhook`, `NotetakerLiveFeed`, `NotetakerReconcile`, `NotetakerTranscriptPurge`, `InterviewCoachWorker` |
| Tasks and the bell | `TaskDueScanner`, `NotificationEmails` | the same, on V191 |
| Billing | — | `StripeEventHandler`, `BillingCustomerRepository`, `MonthlyCreditReset`, `CreditBalanceReconcile`, `CreditLedgerSweeper`, `GrandfatherCredits`, `BillingNotices`, `StripeSeatSync`, `UsageReport`, `WorkspaceTrials`, the two platform controllers |
| Workspace and templates | — | `InvitationAcceptService`, `InvitationRepository`, `OnboardingService`, `PositionTemplateLibraryService`, `PositionTemplateRepository`, `TemplateCodeCount` |
| Purges | `PersonNotePurge`, `CompanyNotePurge` | `OAuthGrantPurge` |
| Audit | — | `AuditEvent` (id from the sequence) |
| Ops | `deploy.yml`, `ops/gcp/deploy.sh` (Flyway `-initSql`), `ops/dev/db.sh` | `ops/cloudsql/harden.sql` step 2c |
| e2e | `e2e/stack/up.sh`, `e2e/api/lib.sh`, `e2e/spa/*.mjs`, `e2e/README.md` | — |
| Test scaffolding | `TestcontainersConfig`, `testcontainers/app-role.sql`, `FixtureJdbc`, `TestWorkspaceScopes`, `FlowTestSupport`, `IntegrationTest`, about 80 adjusted integration tests | adjusted integration tests for every converted door |
| New tests | `RowLevelSecurityIntegrationTest`, `RowLevelSecurityMigrationTest`, `EveryWorkspaceTest`, `ThreadContextPropagationTest` | `TenantResolversIntegrationTest`, `TenantTableGuardIntegrationTest` |
| Docs | `CLAUDE.md`, the `db-ops`, `java-spring-development` and `lightmove-domain` skills | the same, extended |

---

## 8. #1173's review (marked Not Ready to Merge)

| # | Issue | Status |
|---|---|---|
| R1 | `WITH CHECK (workspace_id IS NULL OR …)` lets any workspace write shared library templates and pending OAuth grants. Split it into a shared read and an own-workspace write. | **Open** (risk 9) |
| R2 | `EveryWorkspace` has no per-workspace failure isolation. | **Fixed** in #1132, kept in #1173's `forEach` too |
| R3 | The `harden.sql` step 2c comment says the boot refuses until it has run; the code only warns. | **Fixed**: the comment now says it warns |
| R4 | The verification checklist is unfinished: e2e; real Recall, Nylas and Stripe payloads; the extension; query plans; the production pre-flight. | **Open** (§9) |
| Perf | The job walk is serial over every workspace on every tick (backend suite 11 → 18½ minutes); the reviewer wants a resolver of "workspaces with work due" before production. | **Fixed** by V191 for the six frequent jobs (§2.6) |

**On merging #1173 with #1132's review fixes.** #1132 shipped `EveryWorkspace.sumWithWork`, which narrowed the due scan
and the bell's emails by reading `app_lm_task` and `app_lm_notification` unscoped. #1173 protects both tables, so that
query would always have answered nothing and the two jobs would have stopped. It is gone; V191's
`tasks_falling_due` and `notification_emails_due` answer the same question past the fence.

---

## 9. Checklists

### Before releasing #1132 (skip if already released)

- [x] Merged; review done.
- [ ] `cd e2e && PROFILE=e2e ./run-all.sh` is green.
- [ ] Query plans before and after for the Candidates list and the Companies grid (§6.6).
- [ ] Decide on risk 1: a quiet-window release (A); the split (C) is no longer practical once V184 is applied anywhere.
- [ ] Optional: `SET lock_timeout` in Flyway's `-initSql` (risk 3).
- [ ] Update `ops/eval/export-golden.sh` to read past RLS (risk 12).
- [ ] Production pre-flight queries (§5.1).
- [ ] Rollback SQL and owner credentials ready (§5.4).
- [ ] Release #1132 **on its own**, in the quiet window, then run the after-release checks and the recovery query
      (§5.3).
- [ ] Tell the team: one `npm run dev:db:reset` each.

### Before merging #1173

- [ ] The **API** CI check is green on the PR.
- [ ] Review done (by Fable or Codex); R1 decided (fix, or accept as a known gap).
- [ ] Decide on risk 5: a quiet-window release (A) or a split release (C), which means moving V190 to a follow-up PR
      **before** merging.
- [ ] `cd e2e && PROFILE=e2e ./run-all.sh` is green.
- [ ] Real Nylas, Recall and Stripe deliveries, the extension and the booking page tried against a local or staging
      stack (R4).

### Before releasing #1173

- [ ] #1132 released, and quiet since.
- [ ] Production pre-flight queries (§5.1), Stripe's dashboard checked for the window, `postgres` credentials ready.
- [ ] Rollback SQL and owner credentials ready (§5.4).
- [ ] Release #1173 **on its own**, in the quiet window.
- [ ] Re-run `ops/cloudsql/harden.sql` as `postgres` (risk 7).
- [ ] After-release checks: coverage (79), Stripe events in the window resent, AI-app clients counted (§5.3).
- [ ] Tell the team: one `npm run dev:db:reset` each, and no `npm run dev:cloud` from older branches against the shared
      database.
