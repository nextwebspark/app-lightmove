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

`ops/cloudsql/create-database.sh` creates the database, the app user, and registers the IAM principals.
Set `DB_IAM_USER` and Flyway's V2 grants that principal read access, so a human can query the database
with their Google identity and no password.

## Architecture

**Package by feature, layered inside**: `api/` (HTTP only) → `application/` (services, transactions) →
`domain/` (entities; depends on nothing), with `infrastructure/` supplying adapters for ports the
application declares. If you're importing an adapter into `application`, define a port instead.

Ports worth knowing: `EmailSender` (`LogEmailSender` prints the verification link to the console — the
default, so a fresh clone is fully testable with no provider account; `ResendEmailSender` for prod) and
`RateLimiter` (in-memory Bucket4j — swap for Redis before running more than one instance).

## Conventions

- Java: constructor injection only. `record` for DTOs. Immutable where you can be.
- Errors: RFC 9457 `ProblemDetail`, produced centrally in `GlobalExceptionHandler`. The frontend switches
  on `code`, never on `detail`.
- Comments explain *why*, not *what*. If a line needs a comment to say what it does, rename something.
- React: feature folders. Server state via TanStack Query — don't mirror it into `useState`.
- Styling: Tailwind utilities over the tokens in `apps/web/src/styles/tokens.css`, which are lifted
  verbatim from the mockups. Change a colour there, not inline.
