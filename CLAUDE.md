# LightMove

Multi-tenant SaaS for executive search and talent mapping.

A **Workspace** is the tenant. It holds **Members** whose membership carries a *set* of workspace roles
(`ADMIN` / `MEMBER` / `CLIENT`) who run **Projects** — search mandates for client companies — where each
seat holds **one** staff project role (`LEAD` / `RESEARCHER`) — the project tier has no admin; `LEAD`
owns the mandate. `CLIENT` is a hiring-company representative: read-only, scoped to the mandates
they're attached to. It is not a fence — a member may hold `CLIENT` **alongside** a staff role and is
then treated as staff; a *pure* client (only `CLIENT`) is the one kept out of staff surfaces. The
workspace `CLIENT` role grants nothing; access is the project `CLIENT` seat, which grants `WORK_VIEW`
(read a mandate's content, never edit).

**Built so far: auth, workspace management, projects, the RBAC layer, and the search layer.** Signup
(3 steps), login, OAuth sign-in (Google and LinkedIn), invitations, the roster, the projects/clients
screens, a project's Team & access tab, the client registry with representative invites and their
scoped read-only project access, and **Strategy → Companies**: a filter over the company universe, the
searches saved against it, and the three Companies pages (In universe / Shortlisted / Declined) where a
mandate triages what it took from it. A company reaches those pages four ways — out of the market
(Strategy's per-row add, or the Companies screen's own picker over the same universe, both `POST
/triage` with an id the server resolves), typed in by hand, captured by the browser plugin
(`POST /triage/capture`; the extension itself is not built), or **imported from a spreadsheet**.
Deleting one drops the project↔company row only: the Apollo universe is read-only to the app. On top of
that sits the **people half**: an executive mapped for a mandate, optionally against one of its triaged
companies, added by hand from the Companies grid — where a row is a *person at a company*, so a company
with three of them is three lines and one with none keeps its "Add executive" slot.
The **spreadsheet import** is that fourth door and carries both halves at once: a CSV or Excel file
whose rows are people at companies, mapped column-by-column onto our fields, then confirmed by a
person before anything is written. **The model is asked only where a header is in doubt** — a sheet
whose every header is a known spelling (anything built from the downloadable template, and most second
imports) maps with no LLM call at all; when it is asked it gets headers and a locally computed value
shape only, **never cell values**, and a synonym matcher answers alone when Vertex cannot be reached. A header no field covers becomes a **per-project custom column**: the definitions
are rows in `app_lm_project_custom_column` and the values a jsonb bag on the row, so the grid renders
it like any built-in while a new mandate still starts from the built-ins alone. The importer writes
nothing itself — it builds the same requests the Companies drawer posts and hands them to
`triagecompany` and `candidate`, so every scope check, duplicate rule and audit event stays where it
already lives. The standalone Candidates screen, and the pipeline and outreach tables, don't exist yet,
and neither does the plugin's profile capture (the columns and `CandidateSource` are there for it).
The **Position**
screen is the mandate's brief, edited as a six-step wizard (details, mandate context, reporting,
compensation, assessment, review) that autosaves one step at a time. It opens drafted rather than
blank: a **role-template library** of seventeen briefs (twelve C-suite, four functional heads, one
generic fallback) lives in the database, matched against the mandate's role title at creation. Step
one's **Role title is a combobox**: free text — a mandate is titled "Group CFO – Energy Division" as
often as it is titled "Chief Financial Officer" — that type-aheads the seventeen titles, and picking
one takes that title and redrafts the brief from its template (`GET /position-templates` +
`POST .../position/template`). A template is managed as a migration for now — the per-workspace
management screen is a later session, and the rows it will write are already keyed to a workspace. Step three is an editable
React Flow org chart — add, rename, re-parent and drag any seat; only the role's own seat is fixed.
Step one attaches the position
description, which is *stored and never read* — no extraction, no auto-fill. Publishing stamps who
called the brief ready and **freezes nothing** (V38 retired the lock deliberately). Don't build ahead of
the mockups: if a screen isn't being built this session, its tables and entities don't exist yet.

## Layout

| Path | What |
|---|---|
| `apps/api` | Spring Boot 4.1 (Java 21, Maven). Features: `core`, `common`, `workspace`, `project`, `position`, `strategy`, `triagecompany`, `candidate`, `customcolumn`, `dataimport` |
| `apps/web` | React 19 SPA (Vite 8, TypeScript, Tailwind v4) |
| `claude-design/` | HTML mockups — **the source of truth for all UI**. Read the relevant `*.dc.html` before building a screen. |
| `ops/cloudsql/` | Database bootstrap and hardening scripts |

`position` is the mandate's **brief** — what the role is, why it exists, what it pays and what a
candidate is scored against. It owns `app_lm_position` and its owned lists, and it depends on `project`
because the mandate keeps two of the fields the screen shows: the role title, which step one edits, and
the one target date (V8), which the screen only displays — it is set on the project and nowhere else. Nothing else depends on it, except `project`'s `ReportService`, which still reads the position
repository directly for the report's salary band — a known reverse edge left standing rather than
disguised as a seam, and the reason to lift the report into its own package.

`strategy` and `triagecompany` split one story in two, in the order a consultant works: **`strategy`
is the market side** — the saved filter, the saved searches, the reads over the Apollo universe, and
every band/facet/taxonomy the search is expressed in. A *strategy company* is a row of the market that
belongs to nobody. **`triagecompany` is the mapping side** — one project↔company row per decision,
carrying a triage stage (in universe / shortlisted / declined) and a write-time snapshot. A *triage
company* is a decision. Searching goes in `strategy` however company-shaped its name; `triagecompany`
holds only what a mandate *did* about a company. **`candidate` is the people side** — one row per
executive a mandate has mapped, belonging to the *project* and only optionally to one of its triaged
companies, because a researcher meets people at companies the universe does not carry. It depends on
`triagecompany` through one public method and `triagecompany` never depends back.
**`customcolumn` is the columns a mandate added to its own grid** — definitions only, plus the one
method (`applyTo`) that decides what a row may store in them, since the bag is open and nothing else
stands between it and arbitrary caller-chosen keys. `triagecompany` and `candidate` depend on it; it
depends on neither and knows nothing about companies or people. **`dataimport` is the spreadsheet** —
read the file, work out what its columns mean, and write what it carries through the doors that
already exist. It depends on those three and none of them depends back. Details in
`java-spring-development`.

## Commands

```bash
npm run dev                  # docker postgres (:55433) + api (:8080) + web (:5173)
npm run dev:db:reset         # drop the local database; next boot re-runs every migration from V1
npm run dev:db:psql          # psql shell in the local container
npm run dev:db:apollo        # copy the Apollo company universe down from Cloud SQL into it
npm run dev:cloud            # api + web against the SHARED Cloud SQL dev database
npm test                     # both suites
cd apps/api && ./mvnw test   # backend — needs Docker (Testcontainers)
cd apps/web && npx vitest    # frontend
cd apps/web && npm run build # the real frontend typecheck
cd e2e && PROFILE=e2e ./run-all.sh   # the end-to-end matrix — never without PROFILE=e2e
```

**The e2e matrix always runs `PROFILE=e2e`.** `stack/up.sh` still defaults to `local`, which is the
gitignored personal profile and does not raise `password-reset-requests-per-hour` — so a plain
`./run-all.sh` burns the production budget of 3/hour and fails six cases (N20.2-3, N30.1-4) that are
green on the profile CI uses. Those failures are the profile, never the code.

`npm run dev` needs Docker and nothing else — no gcloud, no `application-local.yml`. Its database is
yours alone, so a migration in your tree applies only to you. The one thing it cannot conjure is the
Apollo universe: `npm run dev:db:apollo` pulls the 71,822 rows down once (that step needs gcloud), and
from there `dev:db:reset` snapshots them out and back in rather than wiping them with everything else.

`npm run dev:cloud` hits the shared dev database and applies your migrations to everyone at boot. It
needs `cp apps/api/src/main/resources/application-local.yml{.example,}` with the DB password filled in,
and the Cloud SQL connector authenticates as you — `gcloud auth application-default login`. That file
is also where the OAuth client credentials live, so OAuth sign-in needs it on either path.

## Load the right skill before you start

Task-specific detail lives in `.claude/skills/`, not here. Load the matching skill **before** touching
its area — the invariants below are the summary; the skills hold the rationale and the traps.

| Skill | Load before |
|---|---|
| `lightmove-domain` | **any** auth / signup / OAuth / workspace / membership / invitation / RBAC / client-representative / verification work |
| `java-spring-development` | any backend code — architecture, conventions, Boot 4 notes, and the backend traps live there |
| `react` | any frontend code — real stack, conventions, and the frontend traps live there |
| `db-ops` | migrations, grants, `harden.sql`, `ops/cloudsql` scripts, the Apollo company universe |
| `pr-cleanup` | addressing PR review feedback |
| `verify` | running the app end-to-end |

## Security invariants (one line each — full rationale in `lightmove-domain`)

- Identity is a **work email**; the domain signals the firm but is **not** unique — a domain does not own a workspace.
- **Membership is invitation-only**; the one second door is a staff member naming a client representative.
- A user holds **at most one active workspace** (partial unique index on `user_id`).
- Verification gates the *proof of mailbox*, not the channel — an invite token or a password reset proves it too.
- **Tenant isolation:** every workspace-scoped query filters by `AuthPrincipal.requireWorkspaceId()`, never a request parameter.
- **Authorise by action, never by role** (`@PreAuthorize` + `@workspaceAuthorizer`/`@projectAuthorizer`); guard beans re-read the DB every check; the JWT `roles` claim is never trusted for a decision.
- Client access is **two tiers, two decisions**: registry (`CLIENT_RECORD_MANAGE`, ADMIN+MEMBER) vs mandate (`CLIENT_ACCESS_MANAGE`, LEAD only). Project content is seat-gated `WORK_VIEW`/`WORK_EXECUTE`, not `PROJECT_BROWSE`.
- **An identity provider is a yml block** — never branch on a provider name anywhere.
- **Tokens are never stored raw** (SHA-256); the refresh cookie rotates on every use; the access token lives in JS memory only.
- **The SPA and API are one origin**; every endpoint lives under `/api/v1`. Don't split hosts.
- **Auth errors are deliberately vague** — one sentence, one timing, for every password-login failure.

## Database

Cloud SQL Postgres 16, instance `bright-gcc`, database `lightmove`. All tables prefixed **`app_lm_`**.
**Hibernate never touches the schema** — `ddl-auto: none`; hand-written Flyway SQL in
`apps/api/src/main/resources/db/migration/`. **Never edit an applied migration; add a new one.**
`app_lm_apollo_companies` is the **company universe** — 71,822 GCC companies, ETL-owned and read-only
to the application, keyed on `apollo_account_id`. Anything that stores a company stores that id plus a
**write-time snapshot**, and never a foreign key: the pipeline reloads the table wholesale. A company
the market does not carry has no id to store, so `app_lm_project_triage_company.apollo_account_id` is
nullable and `source` records which door the row came through (V34).
`app_lm_companies` is the retired brightdata copy — nothing reads it, nothing refills it, and it is
left in place rather than dropped. A mandate's whole filter is one `jsonb` column on `app_lm_strategy`
(V30 explains why). `app_lm_project_candidate` (V36) is the people half: `project_id` is the mapping and
`triage_company_id` is nullable with **ON DELETE SET NULL** beside a snapshotted `company_name`, so
removing a company from a mandate unmaps its executives rather than deleting them; career history and
languages are one `profile` jsonb column for V30's reasons. `app_lm_position` and its six owned-list
tables are the brief (V7, grown by V39): every list a step edits is a child table replaced wholesale by
its step's write, so the aggregate keeps one idiom rather than mixing rows and jsonb.
`app_lm_position_org_node` is the org chart — a tree of seats with exactly one flagged
`mandate_seat`, so "reports to" is that seat's parent and "direct reports" are its children, both
derived rather than stored twice.
`app_lm_project_custom_column` (V42) is the columns a mandate added to its own grid, and V43 puts their
values in a `custom_fields` jsonb bag on both row tables: a column per tenant in real DDL would be
unmigratable and would need the runtime role to hold the `CREATE` privilege `harden.sql` revokes, so
the definitions are rows and the values are a document. `field_key` is slugged once and never
rewritten — every stored value points at it — while `label` is the header a user renames.
V43 also adds `'CSV'` to the triage company's `source` CHECK, the spelling V36 had already reserved on
the candidate side.
`app_lm_position_template` (V42) is the role-template library — the identity a picker lists as columns,
the drafted brief as one `jsonb` body (V30's idiom, not V39's child tables: a template is a
heterogeneous document read and written whole), and the match keywords as a child table because they
are the catalog's lookup key. `workspace_id` is nullable: NULL is the shared library, non-null is one
firm's own, and every read filters on both.
`app_lm_position_document` holds the attached position description inline (`bytea`) — one small file per
mandate, read back only by its own download endpoint. Everything else (roles, hardening, grants) →
`db-ops` skill.

## Conventions (the short form)

- Names carry intent; every type name must read standalone.
- **Comments are the exception, not the habit.** Default to none: a well-named function needs no
  preamble, and a paragraph justifying an ordinary decision is noise a reader has to wade through.
  Write one only where the logic is genuinely hard to follow, or where the *why* is invisible from
  the code — a trap, a security boundary, a non-obvious ordering. Never restate what the line does,
  never narrate alternatives that were not taken, and keep a class doc to a line or two.
  The exception that stays: **inline comments documenting shipped bugs are load-bearing, never strip
  them** — they are why the bug has not come back.
- Errors: RFC 9457 via `GlobalExceptionHandler`; the frontend switches on `code`, never `detail`.
- Java/Lombok/architecture detail → `java-spring-development` skill. React detail → `react` skill.

Review will be done by fable or codex
