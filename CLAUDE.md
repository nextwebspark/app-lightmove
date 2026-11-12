# LightMove

Multi-tenant SaaS for executive search and talent mapping. Product name on screen is **Uncava**; code,
packages, persisted keys and JWT issuer keep `lightmove` (deliberate split). Screens say **Position** /
**Business unit** / **Hiring manager** where code, routes and tables say `project` / `client`; an agency
workspace (V84 `mode`) says Client / Client contact — every such label via `useWorkspaceVocabulary`, never a literal.

- **Workspace** = tenant. **Members** hold a *set* of workspace roles (`ADMIN` / `MEMBER` / `CLIENT`).
- **Projects** (positions/mandates): each seat holds one project role (`LEAD` / `RESEARCHER`); `LEAD` owns it.
- `CLIENT` = hiring-company rep, read-only, scoped to attached mandates via the project `CLIENT` seat (`WORK_VIEW`).
  A member holding `CLIENT` beside a staff role is staff; only a *pure* client is kept off staff surfaces.
- **Person** (`app_lm_person`) is the workspace's human; a mandate's `Candidate` row maps them (V91). Notes,
  documents, tasks, assessments, AI readings and contacts are **staff-only** — never on `CandidateResponse`,
  which a client seat reads.
- A user may belong to several workspaces; a session is in exactly one (`wsId`).

**Feature detail lives outside this file.** Before touching a feature, grep
`docs/architecture/features.md` (the full product/feature narrative) and `docs/architecture/database.md`
(every migration/table note) for its class, route, table or V-number, then read its own doc:
`docs/candidate-crm.md`, `candidate-documents.md`, `outreach-conversations.md`, `notetaker.md`,
`tasks-notifications.md`, `email-signatures.md`, `spreadsheet-import.md`, `position-extraction.md`,
`workspace-modes.md`, `assistant-tools.md`, `public-api.md`, `mcp.md`, `llm-guardrails.md`,
`security/row-level-security.md`, `gdpr/*`, `integrations/*`, `glossary.md` (on-screen words),
`validation-matrix.md` (every form field's rules).

**UI mockups are the source of truth** and live in the separate private repo
`nextwebspark/uncava-design`, cloned beside this one (`gh repo clone nextwebspark/uncava-design ../uncava-design`).
Read the relevant `../uncava-design/*.dc.html` before building a screen. Don't build ahead of the mockups.

## Layout

| Path | What |
|---|---|
| `apps/api` | Spring Boot 4.1 (Java 21, Maven), one package per feature under `app.lightmove.api` |
| `apps/web` | React 19 SPA (Vite 8, TypeScript, Tailwind v4) |
| `apps/extension` | LightMove Capture — Chrome extension (MV3, React 19, Vite 8). Shares no code with `apps/web`. |
| `e2e/` | End-to-end matrix |
| `ops/` | Cloud SQL bootstrap/hardening, GCP, eval scripts |
| `docs/` | Feature docs, contracts read by tests (`public-api/openapi.json`, `mcp/tools.json`, `gdpr/retention-schedule.md`, `gdpr/subject-access-export.md`, `eval/*`) |

Feature dependency directions (nothing depends back):
- `position` (the brief) → `project`; `positiontemplate` is read by `position`; shared vocabulary in `common/constant`.
- `strategy` = market side (filters, saved searches, Apollo reads); `triagecompany` = a mandate's decision per company.
- `candidate` (people side) → `triagecompany`; `customcolumn` ← `triagecompany`, `candidate`.
- `dataimport`, `dataexport` → `triagecompany`, `candidate`, `customcolumn`; `pairing` pairs a stage's companies with executives.
- `outreach` → `candidate`; `notetaker` → `outreach`, `candidate`, `position`; `task`/`notification` answer `candidate`'s seams.
- `publicapi` and `mcp` read through the screens' services and narrow to public DTOs.

## Commands

```bash
npm run dev                  # docker postgres (:55433) + api (:8080) + web (:5173)
npm run dev:stop             # free :8080 and :5173 when Ctrl+C left them held (Git Bash's own window)
npm run dev:db:reset         # drop the local database; next boot re-runs every migration from V1
npm run dev:db:psql          # psql shell in the local container
npm run dev:db:apollo        # copy the Apollo company universe down from Cloud SQL into it
npm run dev:db:seed-report   # demo companies + executives for the Reports tab, local database only
npm run dev:cloud            # api + web against the SHARED Cloud SQL dev database
npm test                     # all three suites: api, web, extension
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
Apollo universe: `npm run dev:db:apollo` pulls the 100,631 rows down once (that step needs gcloud), and
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
| `chrome-extension` | any work in `apps/extension`, on `/api/v1/auth/extension`, or on the SPA's `/extension/connect` |
| `db-ops` | migrations, grants, `harden.sql`, `ops/cloudsql` scripts, the Apollo company universe |
| `pr-cleanup` | addressing PR review feedback |
| `verify` | running the app end-to-end |

## Security invariants (one line each — full rationale in `lightmove-domain`)

- Identity is a **work email**; the domain signals the firm but is **not** unique — a domain does not own a workspace.
- **Membership is invitation-only**; the one second door is a staff member naming a client representative.
- A user may hold **several** active memberships (V81 dropped V1's partial unique index), but a session is in exactly **one** workspace — the `wsId` claim, chosen at sign-in from `last_workspace_id`, remembered per refresh-token family, and changed by `POST /auth/switch-workspace` (a 404 unless the caller is an active member of the target) or by a web refresh whose membership has ended (audited `MEMBERSHIP_ENDED`; `/me` and the extension never fall through). The signup wizard's `POST /onboarding/workspace` founds a *first* workspace only (`ALREADY_IN_WORKSPACE`); a further one is `POST /workspaces`, staff-gated.
- Verification gates the *proof of mailbox*, not the channel — an invite token or a password reset proves it too.
- **Tenant isolation:** every workspace-scoped query filters by `AuthPrincipal.requireWorkspaceId()`, never a request parameter. Forced Postgres row-level security (V184, V190, `core/tenant`) is the backstop on every tenant table, which `TenantTableGuard` reads off the schema and the boot refuses unprotected: every borrowed connection carries the caller's workspace, a job, webhook or worker with no principal names its own with `WorkspaceScope`, and a door that knows only a key (a grant, a bot, a slug, a token's hash) resolves its workspace through V189's `TenantResolvers` first — with none, tenant tables answer nothing (`db-ops`).
- **Authorise by action, never by role** (`@PreAuthorize` + `@workspaceAuthorizer`/`@projectAuthorizer`); guard beans re-read the DB every check; the JWT `roles` claim is never trusted for a decision.
- Client access is **two tiers, two decisions**: registry (`CLIENT_RECORD_MANAGE`, ADMIN+MEMBER) vs mandate (`CLIENT_ACCESS_MANAGE`, LEAD only). Project content is seat-gated `WORK_VIEW`/`WORK_EXECUTE`, not `PROJECT_BROWSE`.
- **A platform role sits above every tenant and inside none**: `SUPER_ADMIN` gates `/api/v1/platform/**` (`@platformAuthorizer`) and nothing else, is granted by ops script only, and never rides in the JWT.
- **An identity provider is a yml block** — never branch on a provider name anywhere.
- **Tokens are never stored raw** (SHA-256) — the exceptions a profile photo's link and an email picture's (`app_lm_email_image.token`, which a sent email names), which every colleague's screen draws and which opens that one image; the refresh cookie rotates on every use; the access token lives in JS memory only.
- **The SPA and API are one origin**; every endpoint lives under `/api/v1`. Don't split hosts.
- **An MCP token's `aud` is the MCP endpoint and it is signed by its own key**; the session decoder checks our issuer and refuses any token carrying an `aud`, so neither token opens the other's routes.
- **No token passthrough:** an MCP tool calls our services in-process as its `McpCaller`; a caller's token is never forwarded to another service, ours or a provider's.
- **An API key reaches only `/api/v1/public/**`**, and `/api/v1/mcp` once it holds `mcp:use`; read-only, and never more than its scopes ∩ its owner's live permissions — re-read every call; a session token opens neither.
- **Auth errors are deliberately vague** — one sentence, one timing, for every password-login failure.
- **A log line names a person by id, never by address** — the deployed JSON encoder masks emails, LinkedIn profile links and `+`-dialled phones in every message and stack trace (`PersonalDataLogMasker`, #927), since Cloud Logging outlives an erasure; local, test and e2e keep the plain console, which e2e reads emailed links from (`docs/production-observability.md`).
- **Two-factor is the person's, and every sign-in door asks `SignInCompletion`** — a right password, reset link or provider sign-in (the OAuth callback answers `?challenge=two-factor`, no token) from a factor holder earns the `lm_mfa_challenge` cookie, never a session; only the right code clears the lock.

## Database

Cloud SQL Postgres 16, instance `bright-gcc`, database `lightmove`. All tables prefixed **`app_lm_`**.
**Hibernate never touches the schema** (`ddl-auto: none`); hand-written Flyway SQL in
`apps/api/src/main/resources/db/migration/`. **Never edit an applied migration; add a new one** (comments
included — they are in the checksum). Branches collide on V-numbers: renumber above main's head before merge.
- `app_lm_apollo_companies` is the **company universe** (~100k rows, ETL-owned, read-only to the app, keyed on
  `apollo_account_id`). Anything storing a company stores that id plus a **write-time snapshot**, never a foreign key.
- Forced row-level security on every tenant table (`core/tenant`, `TenantTableGuard`); a job/webhook/worker with no
  principal opens `WorkspaceScope`, a door knowing only a key resolves its workspace via `TenantResolvers` first.
- Vendor caches (`app_lm_vendor_*`, `app_lm_geocoded_place`) are deliberately not tenant-scoped.
- Per-table / per-migration notes: `docs/architecture/database.md`. Roles, grants, `harden.sql`: `db-ops` skill.

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
- On-screen words follow `docs/glossary.md` wherever no mockup draws the copy; `copyGuard.test.ts` holds the never-on-screen list.
- Every form field's client rule, server rule and error surface is a row of `docs/validation-matrix.md`; a change to one updates its row.
- Java/Lombok/architecture detail → `java-spring-development` skill. React detail → `react` skill.
- **Keep this file short (~12 KB).** Never add feature narrative or migration notes here — put them in the
  feature's `docs/*.md`, `docs/architecture/features.md` or `docs/architecture/database.md`. This file loads into
  every session and every subagent.

Review will be done by fable or codex
