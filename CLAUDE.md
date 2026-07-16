# LightMove

Multi-tenant SaaS for executive search and talent mapping.

A **Workspace** is the tenant. It holds **Members** (roles: `ADMIN` / `CONSULTANT` / `RESEARCHER`) who
run **Projects** — search mandates for client companies.

**Built so far: auth only.** Signup (3 steps), login, Google OAuth, and a placeholder screen behind the
login wall. Projects are designed but not modelled — they arrive with the Project screen. Don't build
ahead of the mockups: if a screen isn't being built this session, its tables and entities don't exist yet.

## Layout

| Path | What |
|---|---|
| `apps/api` | Spring Boot 4.1 (Java 21, Maven) |
| `apps/web` | React 19 SPA (Vite 8, TypeScript, Tailwind v4) |
| `claude-design/` | HTML mockups — **the source of truth for all UI**. Read the relevant `*.dc.html` before building a screen. |
| `ops/cloudsql/` | Database bootstrap and hardening scripts |

## Commands

```bash
npm run dev                  # api (:8080) + web (:5173)
npm test                     # both suites
cd apps/api && ./mvnw test   # backend — needs Docker (Testcontainers)
cd apps/web && npx vitest    # frontend
```

First run: `cp apps/api/src/main/resources/application-local.yml{.example,}` and fill in the DB password.
The Cloud SQL connector authenticates as you — `gcloud auth application-default login`.

## The rules that shape everything

### Identity is a work email; the organization is a workspace

Signup **rejects consumer email domains** (gmail, outlook, …). LightMove is sold to firms, and the email
domain is what tells us which firm someone works at. Configurable via
`lightmove.email.validation.block-public-domains` and `.public-domains` / `.extra-public-domains`.

**A domain does not own a workspace.** One firm may run several — so `email_domain` is *not* unique. At
signup we show the user the workspaces already on their domain, and they either:

- **create their own** — they become its `ADMIN`; or
- **ask to join one** — they land `PENDING_APPROVAL` with **no access to anything** until an admin
  approves them, and the admin picks their role (what you asked for is a suggestion, not a grant).

An **invited** user skips the queue entirely and lands `ACTIVE` — an admin naming them *is* the decision.

**A user belongs to at most one workspace.** Enforced by a partial unique index on
`app_lm_workspace_member (user_id) WHERE status = 'ACTIVE'`. Note it constrains `user_id`, *not*
`workspace_id` — a workspace holds as many members as it likes.

**Verification is not cosmetic.** An unverified address is an unproven claim, so `require-verified-email`
is on and an unverified user reaches no workspace data.

### Tenant isolation

Every workspace-scoped query filters by the `workspace_id` **from the authenticated principal**, never
from a request parameter. `AuthPrincipal.requireWorkspaceId()` is the only supported way to get it.

### Tokens are never stored raw

Refresh, verification and invitation tokens are 256-bit random values; only their SHA-256 hash is
persisted. Passwords are BCrypt(12).

The **refresh token** is httpOnly + Secure + SameSite, scoped to `/api/v1/auth`, and **rotates on every
use**. Presenting an already-rotated token is treated as theft and revokes the whole family. The access
token lives in **JS memory only** — never `localStorage`, because one compromised npm dependency would
otherwise walk away with a 30-day credential to a product holding executive-candidate PII.

### The SPA and the API are one origin

That cookie is `SameSite=Strict` and **host-only** (no `domain`), and the SPA calls a relative `/api/v1`.
A browser therefore returns it only to the host that served the page. So the two are served together —
Vite proxies `/api` in dev; in production Spring serves the built bundle from `static/` and ships as one
Cloud Run container.

Don't split them across hosts. It looks free and isn't: **Firebase Hosting strips every cookie but
`__session`** on a rewrite (we need two), and any CDN that drops a `Set-Cookie` on the way back breaks
rotation — the next refresh looks like theft and revokes the family. Splitting means weakening the cookie
to `SameSite=None`, which is a real downgrade, not a config detail. The upgrade path is a load balancer
in front of two services, which keeps one origin.

`SpaResourceConfig` serves the bundle and the history fallback (`/auth/verify` is opened cold from an
email link, so it must not 404). `SecurityConfig`'s SPA chain matches by *exclusion* — anything outside
`/api/`, Actuator, and the OAuth2 redirects is public. **Every endpoint lives under `/api/v1`. Keep it
that way**; `SpaSecurityTest` holds the line.

### Auth errors are deliberately vague

"Invalid email or password" covers wrong password, unknown account, and Google-only account. The audit
log records which; the client is told only that the pair did not match. Anything else is a free
account-enumeration oracle.

## Traps this codebase has already fallen into

Each of these shipped, looked correct, and did nothing. They are all covered by tests now — don't
reintroduce them.

- **`@Async` / `@Transactional` are proxy-based.** A method calling another method *on itself* bypasses
  the proxy and the annotations are inert. `AuditService` delegates to a separate `AuditEventWriter`
  bean for exactly this reason.
- **Spring rolls back on any unchecked exception, including `ApiException`.** `login()` and `rotate()`
  are `@Transactional(noRollbackFor = ApiException.class)`, because otherwise the failed-login counter
  and the token-family revocation are rolled straight back out — silently disabling account lockout and
  refresh-token theft detection entirely.
- **Spring Security loads the CSRF token lazily.** An endpoint that returns 204 without calling
  `csrfToken.getToken()` writes no cookie, so the SPA has nothing to echo back and every refresh 401s.
  See `AuthController.csrf`.
- **`@DefaultValue("")` on a `List<String>` binds to `[""]`, not `[]`.** Treating that as "the operator
  supplied an override" emptied the consumer-domain blocklist and let Gmail signups through.
- **Every auth route needs `JwtPrincipalConverter`.** With Spring's default converter the principal is a
  raw `Jwt`, `CurrentUser` finds no `AuthPrincipal`, and the endpoint 401s on a valid token.

## Stack notes

Spring Boot **4** renamed the starters — most tutorials online are for Boot 3 and will not compile:

| Boot 3 | Boot 4 |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` |
| `spring-boot-starter-oauth2-client` | `spring-boot-starter-security-oauth2-client` |

Also: **Jackson 3** (`tools.jackson.*`, not `com.fasterxml.*` — the old jars are still on the classpath
and will compile, then fail at runtime with "no ObjectMapper bean"). Spring Security 7 enables CSRF for
APIs by default. `authorizeRequests()` is gone; use `authorizeHttpRequests()`.

## Database

Cloud SQL Postgres 16, instance `bright-gcc`, database `lightmove`. All tables prefixed **`app_lm_`**.

**Hibernate never touches the schema** — `ddl-auto: none`. The schema is hand-written SQL in
`apps/api/src/main/resources/db/migration/`, applied by Flyway. Never edit an applied migration; add a
new one. (`ddl-auto: validate` is set in the *test* profile only, where entity/schema drift becomes a
red build rather than a production surprise.)

**Flyway runs at boot locally, and as a deploy step in production** (`FLYWAY_ENABLED=false` on Cloud Run).
Not for speed — because migrating at boot forces `lm_app`, the *runtime* role on the other end of any SQL
injection we ever ship, to hold `CREATE ON SCHEMA public` forever, which is precisely what `harden.sql`
revokes. In the pipeline it runs as `lm_migrate` instead, so a bad migration fails a deploy and the old
revision keeps serving — rather than crash-looping production, where you can roll back an image but not
a schema.

`ops/cloudsql/create-database.sh` creates the database, the app user, and registers the IAM principals.
Set `DB_IAM_USER` and Flyway's V2 grants that principal read access, so a human can query the database
with their Google identity and no password.

To add *another* human, run `ops/cloudsql/grant-db-user.sh <email> [--write]` — don't copy V2. V2 takes
one principal, and it could grant on the whole schema only because Flyway ran as the owner of every
table; after `harden.sql` neither `lm_app` nor `lm_migrate` owns `app_lm_audit_event` or
`app_lm_companies`, and a non-owner cannot grant. So the grant runs as `postgres`, which makes it an ops
script and not a migration. `--write` covers `app_lm_*` but never those two: the audit trail stays
append-only, and the company universe belongs to the pipeline.

### The company universe is a copy, not a link

The same instance hosts a second database, **`brightdata`** — the ETL warehouse. It holds the scrape
sources (`src_linkedin`, `src_zoominfo`, `supabase_company_dnb`, …) and `app_companies`, a built
projection over them: ~54k companies, the list a consultant actually searches.

`app_lm_companies` is a **copy** of it, refreshed by `ops/cloudsql/sync-companies.sh`. Don't reach for a
second `DataSource` or `postgres_fdw` — Postgres has no cross-database queries, and a company list that
can't be joined to a project is not a company list. It is reference data: the pipeline writes it, the
application only reads it (`harden.sql` reassigns the table to `postgres` and leaves `lm_app` with
`SELECT`).

The sync goes out through GCS — `gcloud sql export csv` → bucket → `gcloud sql import csv` — which looks
like a detour and isn't. `brightdata.app_companies` is owned by `postgres` with **no grants at all**, so
no role you can log in as is able to `SELECT` from it; the export runs server-side as the instance's
service agent and is authorised by your *gcloud* identity, not by any database password. That agent needs
`roles/storage.objectAdmin` on the bucket, granted once (the script's header has the command).

The sync **upserts on `(source, source_id)`**, never on `id`. Upstream ids are re-minted on every
pipeline rebuild, so anything that references a company must reference *our* id — adopt the warehouse's
and the next rebuild silently repoints every project. Rows that vanish upstream are reported, never
deleted.

Eventually the pipeline writes into `lightmove` directly and the sync script retires. Nothing about the
table changes when it does — which is the point of keying it that way now.

## Architecture

**Two tiers, every module laid out by type.** A shared **`core/`** holds the concerns every feature
reuses; each business feature is its sibling. Both use the same type-subpackages, a module keeping only
the ones it needs. The actual tree:

```
core/
  security/                # the whole auth domain
    constant/   AuthProvider, TokenPurpose, UserStatus
    model/      User, UserIdentity, VerificationToken, AuthPrincipal,
                EmailVerifiedEvent, SignupCommand, AuthenticatedSession
    repository/ UserRepository, UserIdentityRepository, VerificationTokenRepository
    service/    AuthService, VerificationService, PasswordPolicy,
                OAuth2LoginSuccessHandler, CurrentUser, ClientIpResolver
    config/     SecurityConfig
    controller/ AuthController, AuthResponseAssembler
    dto/        AuthDtos
    jwt/        JwtConfig, JwtPrincipalConverter, RsaKeyProvider          (flat concern pkg)
    token/      RefreshToken, RefreshTokenRepository, TokenService, TokenPair,
                RevokeReason, RefreshCookieFactory, Tokens                (flat concern pkg)
  email/       model/(EmailMessage)  service/(EmailSender, EmailAddressValidator, …)  config/
  audit/       constant/(AuditEventType, AuditOutcome)  model/(AuditEvent)  repository/  service/
  error/       constant/(ErrorCode)  model/(ApiException)  service/(Problems)
               handler/(GlobalExceptionHandler, ProblemAccessDeniedHandler)
  ratelimit/   service/(RateLimiter, Bucket4jRateLimiter, RateLimitGuard)
  persistence/ model/(BaseEntity)
  logging/     service/(CorrelationId, CorrelationIdFilter)
  config/      LightMoveProperties, SpaResourceConfig      (cross-cutting; no type split)

workspace/                 # feature template — project / strategy / candidate copy this
  constant/   WorkspaceRole, MemberStatus, WorkspaceStatus, InvitationStatus, PendingOnboardingKind
  model/      Workspace, WorkspaceMember, PendingOnboarding, Invitation,
              CreateWorkspaceCommand, InviteCommand
  repository/ service/ controller/ dto/(WorkspaceDtos)
```

Invitations are part of `workspace` (membership), not their own feature.

**What goes in each subpackage** (a module includes only the ones it needs):

| subpackage | holds |
|---|---|
| `constant` | **all enums** and fixed constant values |
| `model` | entities, domain events, internal command/result records — **no enums, no HTTP payloads** |
| `dto` | HTTP request/response records only |
| `repository` | Spring Data interfaces |
| `service` | business logic and its interfaces (`EmailSender`, `RateLimiter` live here) |
| `controller` | `@RestController` classes (`@RestControllerAdvice` handlers go in `error/handler`) |
| `config` | `@Configuration` classes |

**Flat concern packages** are the one exception to type-only grouping: inside `core/security`, `jwt/` and
`token/` group everything for their concern regardless of type — so `RefreshToken` (an entity) and
`RevokeReason` (an enum) live in `token/`, not in `model/`/`constant/`. This applies only to those two.

**Dependency rule:** features depend on `core`, never on each other's internals. `core` does not depend on
a feature — the one exception is `AuthResponseAssembler` (`core/security/controller`), which reads
workspace repositories to build the `/me` response (`AuthDtos.UserResponse` embedding
`WorkspaceDtos.WorkspaceSummary` is the same seam). This is a deliberate trade of the old ports/adapters
layering for a uniform, type-based shape, so `EmailSender`/`RateLimiter` are plain `service` interfaces
rather than declared ports.

Ports worth knowing: `EmailSender` (`core/email/service`; `LogEmailSender` prints the verification link to
the console — the default, so a fresh clone is fully testable with no provider account; `ResendEmailSender`
for prod) and `RateLimiter` (`core/ratelimit/service`; in-memory Bucket4j — swap for Redis before running
more than one instance).

## Conventions

- Java: constructor injection only. `record` for DTOs. Immutable where you can be.
- **Lombok.** `@RequiredArgsConstructor` for constructor injection, `@Slf4j` for the logger. Entities use
  `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + selective `@Setter`, and **never** `@Data`,
  `@EqualsAndHashCode`, or `@Builder` — `BaseEntity` explains why identity equality is hand-written. A
  service whose constructor *derives* a nested config record (`this.config = properties.auth()`) keeps its
  hand-written constructor. Config is `lombok.config` at the module root.
- Errors: RFC 9457 `ProblemDetail`, produced centrally in `GlobalExceptionHandler`. The frontend switches
  on `code`, never on `detail`.
- Comments explain *why*, not *what*. Every class carries a class-level doc; the inline comments flagged in
  "Traps" are load-bearing — they document bugs that shipped, and must not be stripped. If a line needs a
  comment to say what it *does*, rename something.
- React: feature folders. Server state via TanStack Query — don't mirror it into `useState`.
- Styling: Tailwind utilities over the tokens in `apps/web/src/styles/tokens.css`, which are lifted
  verbatim from the mockups. Change a colour there, not inline.


Review will be done by fable or codex