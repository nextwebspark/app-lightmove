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

**Built so far: auth, workspace management, minimal projects, and the RBAC layer.** Signup (3 steps),
login, OAuth sign-in (Google and LinkedIn), invitations, the roster, the projects/clients screens, a
project's Team & access tab, and the client registry with representative invites and their scoped
read-only project access. The Project screen's own tables (candidates, pipeline) don't exist yet.
Don't build ahead of the mockups: if a screen isn't being built this session, its tables and entities
don't exist yet.

## Layout

| Path | What |
|---|---|
| `apps/api` | Spring Boot 4.1 (Java 21, Maven) |
| `apps/web` | React 19 SPA (Vite 8, TypeScript, Tailwind v4) |
| `claude-design/` | HTML mockups — **the source of truth for all UI**. Read the relevant `*.dc.html` before building a screen. |
| `ops/cloudsql/` | Database bootstrap and hardening scripts |

## Commands

```bash
npm run dev                  # docker postgres (:55433) + api (:8080) + web (:5173)
npm run dev:db:reset         # drop the local database; next boot re-runs every migration from V1
npm run dev:db:psql          # psql shell in the local container
npm run dev:cloud            # api + web against the SHARED Cloud SQL dev database
npm test                     # both suites
cd apps/api && ./mvnw test   # backend — needs Docker (Testcontainers)
cd apps/web && npx vitest    # frontend
cd apps/web && npm run build # the real frontend typecheck
```

`npm run dev` needs Docker and nothing else — no gcloud, no `application-local.yml`. Its database is
yours alone, so a migration in your tree applies only to you.

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
| `db-ops` | migrations, grants, `harden.sql`, `ops/cloudsql` scripts, the company-universe sync |
| `pr-cleanup` | addressing PR review feedback |
| `verify` | running the app end-to-end |

## Security invariants (one line each — full rationale in `lightmove-domain`)

- Identity is a **work email**; the domain signals the firm but is **not** unique — a domain does not own a workspace.
- **Membership is invitation-only**; the one second door is a staff member naming a client representative.
- A user holds **at most one active workspace** (partial unique index on `user_id`).
- Verification gates the *proof of mailbox*, not the channel — an invite token or a password reset proves it too.
- **Tenant isolation:** every workspace-scoped query filters by `AuthPrincipal.requireWorkspaceId()`, never a request parameter.
- **Authorise by action, never by role** (`@PreAuthorize` + `@workspaceAuth`/`@projectAuth`); guard beans re-read the DB every check; the JWT `roles` claim is never trusted for a decision.
- Client access is **two tiers, two decisions**: registry (`CLIENT_RECORD_MANAGE`, ADMIN+MEMBER) vs mandate (`CLIENT_ACCESS_MANAGE`, LEAD only). Project content is seat-gated `WORK_VIEW`/`WORK_EXECUTE`, not `PROJECT_BROWSE`.
- **An identity provider is a yml block** — never branch on a provider name anywhere.
- **Tokens are never stored raw** (SHA-256); the refresh cookie rotates on every use; the access token lives in JS memory only.
- **The SPA and API are one origin**; every endpoint lives under `/api/v1`. Don't split hosts.
- **Auth errors are deliberately vague** — one sentence, one timing, for every password-login failure.

## Database

Cloud SQL Postgres 16, instance `bright-gcc`, database `lightmove`. All tables prefixed **`app_lm_`**.
**Hibernate never touches the schema** — `ddl-auto: none`; hand-written Flyway SQL in
`apps/api/src/main/resources/db/migration/`. **Never edit an applied migration; add a new one.**
`app_lm_companies` is a read-only **copy** of the warehouse's universe, keyed `(source, source_id)` —
never its `id`. Everything else (roles, hardening, grants, sync mechanics) → `db-ops` skill.

## Conventions (the short form)

- Names carry intent; every type name must read standalone. Comments explain *why*, not *what* —
  inline comments documenting shipped bugs are load-bearing, never strip them.
- Errors: RFC 9457 via `GlobalExceptionHandler`; the frontend switches on `code`, never `detail`.
- Java/Lombok/architecture detail → `java-spring-development` skill. React detail → `react` skill.

Review will be done by fable or codex
