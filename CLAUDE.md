# LightMove

Multi-tenant SaaS for executive search and talent mapping.

A **Workspace** is the tenant. It holds **Members** whose membership carries a *set* of workspace roles
(`ADMIN` / `MEMBER` / `CLIENT`) who run **Projects** — search mandates for client companies — where each
seat holds **one** staff project role (`LEAD` / `RESEARCHER`) — the project tier has no admin; `LEAD`
owns the mandate. The assignment table still models a *set*, so re-admitting a second staff role costs
no migration, but the service and the HTTP contract permit only one. `CLIENT` is the exception, and a
hiring-company representative: read-only, scoped to the mandates they're attached to, granted by
attaching a representative and never by the team table, so it sits *alongside* a staff role on the
seat of someone who is both. It is not a fence —
a member may hold `CLIENT` **alongside** a staff role and is then treated as staff. A *pure* client (only
`CLIENT`) is the one kept out of staff surfaces. The workspace `CLIENT` role grants nothing; access is the
project `CLIENT` seat, which grants `WORK_VIEW` (read a mandate's content, never edit).

**Built so far: auth, workspace management, minimal projects, and the RBAC layer.** Signup (3 steps),
login, OAuth sign-in (Google and LinkedIn), invitations, the roster, the projects/clients screens, a project's Team & access
tab (staff seats with their role, plus the client contacts on the mandate), and the client registry
with representative invites and their scoped read-only project access.
The Project screen's own tables (candidates, pipeline) don't exist yet. Don't build ahead of the
mockups: if a screen isn't being built this session, its tables and entities don't exist yet.

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

**Signup asks for a work email**, because LightMove is sold to firms and the email domain is what tells
us which firm someone works at — it becomes the workspace's `email_domain` and it is what an invited
colleague's address is expected to look like.

That is a *signal*, not an enforced gate. Signup **can** reject consumer domains (gmail, outlook, …) via
`lightmove.email.validation.block-public-domains`, with `.public-domains` / `.extra-public-domains` as
the list — but it **defaults to `false`**, so a stock build accepts them. The test profile turns it on.
The address is still checked for shape, for disposable domains, and (via `mx-check-enabled`, on by
default) for a domain that exists and accepts mail: a non-existent domain and RFC 7505's "no mail here"
are both refused, while a resolver that times out fails open — our outage must not block a signup.

**A domain does not own a workspace.** One firm may run several — so `email_domain` is *not* unique.

**Membership is invitation-only.** Signup always creates a workspace (the creator is its `ADMIN`); the
only way into an existing one is an admin's invitation, and accepting lands `ACTIVE` immediately — an
admin naming someone *is* the decision. **One second door, deliberately:** any staff member may name a
client representative (`CLIENT_RECORD_MANAGE` is granted to `MEMBER` as well as `ADMIN`), and that
person becomes a `CLIENT`-role member without an admin involved. A consultant who owns a mandate owns
its client contacts, and the reach they are admitting to is narrow — see the two tiers below. There is no join request and no approval queue; a colleague
whose firm is already here asks their admin for an invite. A new invitee sets a password on the accept
screen and is in at once: `POST /onboarding/accept-invitation-signup` (public — token + name + password)
creates their account *already verified* and issues a session carrying the workspace, with **no separate
email-verification step**. The invite token, mailed only to the invited address, is the mailbox proof
verification would otherwise collect; the account's email is taken from the invitation, **never the
request body**, so the token can only ever mint the identity it was addressed to (that binding, plus the
`existsByEmail` guard that sends an already-registered address to log in, is the security of this path).
An invitee who *already* has an account is routed server-side instead: `/me` carries `pendingInvitation`
and the signed-in `POST /onboarding/accept-invitation` redeems it token-lessly.

**A user belongs to at most one workspace.** Enforced by a partial unique index on
`app_lm_workspace_member (user_id) WHERE status = 'ACTIVE'`. Note it constrains `user_id`, *not*
`workspace_id` — a workspace holds as many members as it likes.

**Verification is not cosmetic.** An unverified address is an unproven claim, so `require-verified-email`
is on and an unverified user reaches no workspace data. It gates the *creator* path — someone who typed
their own address into signup. An invited user skips it: the invitation link already proved the mailbox,
because the emailed token is the same proof a verification email exists to collect.

The verification link is not the *only* thing that proves it. A **password reset also verifies** the
address and materialises a held workspace — `PasswordResetService` publishes `EmailVerifiedEvent` on
purpose, because a reset link proves the same mailbox the verification link would. An unverified creator
who resets a password comes out verified, with their workspace built. What is gated is the *proof*, not
which email carried it.

A **held wizard is redeemed whenever its owner proves the mailbox**, however late. `PendingOnboarding`
carries an `expires_at`, but `materialise` does not consult it: the row holds the workspace name, size,
region and invitees the user typed, and refusing to honour it protects nothing while losing all of it.
The column is for a future cleanup job.

### Tenant isolation

Every workspace-scoped query filters by the `workspace_id` **from the authenticated principal**, never
from a request parameter. `AuthPrincipal.requireWorkspaceId()` is the only supported way to get it.

### Authorisation asks for an action, never a role

RBAC is data (`core/security/rbac`): `app_lm_role` / `app_lm_action` / `app_lm_role_action` are seeded
catalogs, memberships and project seats hold role **sets** via assignment tables, and permissions are
the union of the roles' actions. Adding a role or action = an INSERT migration + an enum constant;
`RbacCatalogTest` fails the build if the two drift. Controllers declare the gate with `@PreAuthorize`
over actions (`@workspaceAuth.can(principal, 'MEMBER_INVITE')`, `@projectAuth.can(principal,
#projectId, 'TEAM_MANAGE')`); the guard beans **re-read the database** on every check and enforce by
throwing `ApiException`, so denials keep their codes and the 404 masking. The JWT's `roles` claim is
coarse material only — up to 15 minutes stale, never trusted for a role-sensitive decision.
Annotations live on **controllers only**: services reachable outside a request's SecurityContext
(everything `PendingOnboardingMaterialiser` calls with its synthetic principal) keep imperative checks.
Invariants that need loaded state stay imperative too — a workspace keeps ≥1 holder of the workspace
`ADMIN` role (`LAST_ADMIN`) and every project ≥1 holder of `LEAD` (`PROJECT_LAST_LEAD`), and a project
seat holds no more than one staff role.

**Roles are re-read every request; account *status* is not.** It is checked at login and at refresh
(`TokenService.rotate`), so a user whose status turns `SUSPENDED` keeps a working access token for up to
15 minutes. Nothing sets that status today — there is no suspension surface, only the enum. Whoever
builds one must call `tokens.revokeAllSessions(userId, …)` there to close the refresh path, and accept
the ≤15-minute window on the access token or add a per-request status check to the guard beans.

**Client access is two tiers, and they are not the same decision.** The **registry** — client records
and who exists as a contact on them — is workspace `CLIENT_RECORD_MANAGE`, held by `ADMIN` *and*
`MEMBER`; minting a representative there names a person and shows them nothing. Giving that person a
**mandate** — mapping one the registry already holds, or creating and attaching in a single step, or
detaching — is project `CLIENT_ACCESS_MANAGE`, held by `LEAD` alone (plus the standing workspace-admin
bypass). Deliberately not part of `PROJECT_EDIT`: admitting an outsider to a search and moving its
target date are different decisions, and folding them together would hand the first away the moment the
second was widened to `RESEARCHER`.

A project's **content** reads (its strategy, position brief, and future tables) are seat-gated on the
project action `WORK_VIEW` (held by every seated role including CLIENT; workspace-admin bypasses),
**not** workspace `PROJECT_BROWSE` — a mandate's scope and brief are team-only. `WORK_EXECUTE` is the
write half, held by the staff roles and never CLIENT, so read and write access can be granted apart.
The project *list* rides any active membership (`@workspaceAuth.member`; the service scopes a pure
client to the mandates they're seated on), and shared reference data (`CompanyReferenceController`)
rides `PROJECT_BROWSE`: existence isn't secret, content is.

### An identity provider is configuration, not code

Adding Google, LinkedIn, or anything else that speaks OIDC is a `spring.security.oauth2.client`
registration block and **nothing else** — no enum constant, no migration, no SPA change. The
registration id *is* the provider: it names the button, the authorisation path
(`/oauth2/authorization/{id}`), Spring's callback (`/login/oauth2/code/{id}`), and the value stored
on the identity row (uppercased). `UserIdentity.provider` is therefore a plain `String` and V24
dropped the CHECK that used to enumerate them; `LOCAL` is the one non-provider value, meaning our own
password hash. `/auth/providers` returns the configured ids and the SPA renders a button per id, with
a generic mark for one it has no icon for. **Never branch on the provider in
`OAuth2LoginSuccessHandler`** — it reads standard OIDC claims only (`sub`, `email`, `email_verified`,
`name`, `picture`), which is the whole reason a provider nobody wrote code for can sign someone in.

**Where a provider departs from the spec, that too is configuration** — `lightmove.auth.oauth`
holds `pkce-unsupported-registrations` and `nonce-unsupported-registrations`, applied by
`PkceAwareAuthorizationRequestResolver`, and LinkedIn is in both. Per-registration on purpose: Google
keeps PKCE and the nonce, and a test pins that so one provider's shortcomings can never quietly
weaken another's. Dropping the nonce costs the id_token→browser binding; what remains is the code
exchange itself (single-use, server-to-server over TLS with our secret) plus `state` for CSRF.

Five things this cost an afternoon each to learn:

- A provider Boot ships no `CommonOAuth2Provider` preset for (LinkedIn) needs its endpoints — in the
  shared `provider:` block in `application.yml`, pinned rather than discovered from an `issuer-uri`,
  because discovery makes a provider outage into an application that will not boot — **and an
  explicit `redirect-uri`**, without which startup fails outright. LinkedIn additionally needs
  `client-authentication-method: client_secret_post`; its token endpoint rejects HTTP Basic.
- **`invalid_client` from LinkedIn is usually not about the client.** It is what it answers when
  Spring replays a `code_verifier` at a provider that never implemented PKCE. The credentials are
  fine; re-copying the secret finds nothing. To tell them apart, POST `grant_type=client_credentials`
  with the id and secret: a *valid* pair answers `access_denied` ("not allowed to create application
  tokens"), an invalid one answers `invalid_client`.
- **`invalid_nonce` arrives only after a successful token exchange** — it is id_token validation, not
  authentication. LinkedIn does not echo the nonce and does not list it in `claims_supported`.
- **`email_verified` is optional, and absence is not consent.** Spring coerces anything *present* to
  a Boolean, so the only ambiguous answer is the claim being missing entirely — which is what LinkedIn
  does, and why demanding `Boolean.TRUE` refused every LinkedIn login. Silence is trusted only for
  registrations listed in `email-verified-optional-registrations`, never by default: this handler
  links a provider identity into an *existing* account on a matching address, so "the provider did
  not say" must never read as "the provider said yes" for an IdP nobody vetted.
- **A provider's `picture` is a URL the whole workspace fetches**, and on many IdPs it is a
  user-editable field. It is stored only when it is `https` and under 2 KB — otherwise a member could
  point it at a host they control and harvest a request from every colleague who opens the roster.

**A failed OAuth login needs `OAuth2LoginFailureHandler`.** Spring's default redirects to
`/login?error` on the *API's* host, which in development is the API and answers 404 JSON — so the
real error never reaches anyone. Ours logs the provider's wording (configuration detail, useless to
the person signing in) and sends the browser to the SPA with `OAUTH_FAILED`.

The profile picture is the provider's CDN URL, copied to `app_lm_user.avatar_url`, and **owned by
whoever supplied it** (`avatar_source`, V25). That source may re-stamp it on every sign-in — LinkedIn's
URLs expire within weeks — and anyone else may only fill an empty one. Without the ownership rule the
last provider used always won, so signing in with an account that has no photo replaced a real picture
with a generated monogram; providers send a monogram, not nothing. A null source is a row older than
the column and is claimed once. The name follows the same shape and is only backfilled when blank — it
is editable here, and a provider must not overwrite what someone typed. `Avatar` falls back to initials
when the image fails to load, which is the designed end state for a user who stops signing in, not a
bug.

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

"Invalid email or password" covers **every** password-login failure: wrong password, unknown account,
Google-only account, **locked account, suspended account**. The audit log records which; the client is
told only that the pair did not match. Anything else is a free account-enumeration oracle — a distinct
`423 ACCOUNT_LOCKED` was reachable only for an address that exists, so five wrong guesses confirmed an
account, more cheaply and more reliably than any timing attack. (`ACCOUNT_SUSPENDED` still exists and is
still thrown by the OAuth and password-reset paths; only password login stopped using it.)

Sameness is about the clock too. Every refusal that skips the password check pays for one BCrypt
comparison anyway — `PasswordPolicy.equaliseFailureCost`, against a decoy hash derived at startup.
Without it an unknown address answered in 26 ms and a real one in 276 ms, which told anyone who asked
whether an address is a customer.

A locked-out user therefore learns it **by email**, sent once when the lock arms (not on each later
attempt, or an attacker's dictionary run would flood the owner's inbox). The mailbox already proves
ownership, so it can carry what the login response must not. Note the lockout counter does not decay:
five failures lock for 15 minutes, and after the lock lapses the counter is still five, so the next
wrong password re-locks immediately. Only a successful login or a password reset clears it — deliberate,
since a decaying counter gives an attacker a free retry budget every window.

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
- **BCrypt measures the password in bytes; `String.length()` counts characters.** 41 accented characters
  is 83 bytes: it passed a 72-*character* policy and then threw inside `encode`, 500ing signup, password
  reset and invited signup alike. `PasswordPolicy` measures UTF-8 bytes, and the message no longer
  promises a character count it cannot keep.
- **Bean Validation runs before the service ever sees the request.** Jakarta `@Email` rejected a pasted
  address with a trailing space while the normaliser that would have trimmed it sat one layer down,
  unreached. Address fields carry `@JsonDeserialize(converter = EmailAddressNormaliser.class)` so
  canonicalisation happens at binding — never do this to a password, where trimming changes the secret.
- **A revoked refresh token is not automatically a stolen one.** Branch on `RevokeReason`: `ROTATED` and
  `REUSE_DETECTED` are theft, `LOGOUT` and `PASSWORD_CHANGED` are how a session is *supposed* to end.
  Testing only `isRevoked()` declared theft on every ordinary logout — alarming the user and firing the
  one alert meant to page a human, which made it worthless. `ROTATED` must stay theft: that is the
  actual attack signature.
- **A caught `NamingException` is not proof of an outage.** The MX check swallowed `NameNotFoundException`
  — the resolver's answer that the domain does not exist — as "inconclusive" and let it through, so the
  typo'd domain it exists to catch was the case it passed. Fail open on timeouts, not on answers.
- **Deleting before deciding.** `materialise` deleted the held wizard, *then* checked whether it was
  usable, so the path that refused it was also the path that destroyed it. Decide first, delete after.
- **A refused read is not an empty list.** `useQuery` with `data: rows = []` renders the *empty state*
  on a 403, so `/clients` told a portal guest the firm had "0 clients" and offered a New client button
  that could never work. Branch on `isError` before `rows.length === 0` on every list that can be
  refused — the count is the tell, because it states as fact a number the caller was not allowed to read.
- **`OAuth2AuthorizationRequest.from()` carries the rendered URI across.** Rebuilding a request to
  drop `code_challenge` produced a clean parameter map and a redirect URL that still carried it,
  because `authorizationRequestUri` is a field copied verbatim. Build the request field by field so
  `build()` renders the URI from the parameters you actually kept.
- **`String.valueOf(x)` where `x` comes from a generic `<T> T` getter binds to the `char[]` overload.**
  `OAuth2AuthorizationRequest.getAttribute` is generic, so inference picks `valueOf(char[])` and the
  call dies at runtime with a `ClassCastException` — a 500 on every authorisation request. Assign to
  a `String` first. (A non-generic getter returning `Object`, like `HttpServletRequest.getAttribute`
  or `Map.get`, is safe: that binds `valueOf(Object)`.)
- **`Set.copyOf(…).contains(null)` throws.** An immutable set answers a null lookup with a
  `NullPointerException` rather than `false`, so a nullable key needs its own guard.
- **`@ConditionalOnBean` on user configuration silently never matches.** A resolver bean conditioned
  on `ClientRegistrationRepository` was never created — the repository is auto-configured *after*
  user config — so Spring used its default and the override looked inert. Build such things where the
  bean is already in hand.
- **A route the nav hides is still reachable by URL.** The sidebar filtered pure clients out of
  `/clients` and `/team` and nothing else did, so typing the path served the firm's internal screens.
  Guard the *route*; the nav is presentation.

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
    constant/   TokenPurpose, UserStatus
    model/      User, UserIdentity, VerificationToken, AuthPrincipal,
                EmailVerifiedEvent, SignupCommand, AuthenticatedSession
    repository/ UserRepository, UserIdentityRepository, VerificationTokenRepository
    service/    AuthService, VerificationService, PasswordPolicy,
                OAuth2LoginSuccessHandler, OAuth2LoginFailureHandler,
                ProviderQuirkAwareRequestResolver, CurrentUser, ClientIpResolver
    config/     SecurityConfig
    controller/ AuthController, AuthResponseAssembler
    dto/        AuthDtos
    jwt/        JwtConfig, JwtPrincipalConverter, RsaKeyProvider          (flat concern pkg)
    token/      RefreshToken, RefreshTokenRepository, TokenService, TokenPair,
                RevokeReason, RefreshCookieFactory, Tokens                (flat concern pkg)
    rbac/       Role, Action, RoleRepository, ActionRepository, RoleScope,
                WorkspaceRole, ProjectRole, WorkspaceAction, ProjectAction,
                RbacService, WorkspaceAccess, ProjectAccess,
                WorkspaceAuth, ProjectAuth                                (flat concern pkg)
  email/       model/(EmailMessage)  service/(EmailSender, EmailAddressValidator, …)  config/
  audit/       constant/(AuditEventType, AuditOutcome)  model/(AuditEvent)  repository/  service/
  error/       constant/(ErrorCode)  model/(ApiException)  service/(Problems)
               handler/(GlobalExceptionHandler, ProblemAccessDeniedHandler)
  ratelimit/   service/(RateLimiter, Bucket4jRateLimiter, RateLimitGuard)
  persistence/ model/(BaseEntity)
  logging/     service/(CorrelationId, CorrelationIdFilter)
  config/      LightMoveProperties, SpaResourceConfig      (cross-cutting; no type split)

workspace/                 # feature template — project / strategy / candidate copy this
  constant/   MemberStatus, WorkspaceStatus, InvitationStatus
  model/      Workspace, WorkspaceMember, PendingOnboarding, Invitation,
              CreateWorkspaceCommand, InviteCommand
  repository/ service/ controller/ dto/(WorkspaceDtos)
```

Role enums live in `core/security/rbac`, not in the features — they are catalog mirrors, and both
tiers' access services need them.

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

**Flat concern packages** are the one exception to type-only grouping: inside `core/security`, `jwt/`,
`token/` and `rbac/` group everything for their concern regardless of type — so `RefreshToken` (an
entity) and `RevokeReason` (an enum) live in `token/`, and `Role` (an entity) next to `WorkspaceAction`
(an enum) in `rbac/`. This applies only to those three.

**Dependency rule:** features depend on `core`, never on each other's internals. `core` does not depend on
a feature — the deliberate exceptions are `AuthResponseAssembler` (`core/security/controller`), which reads
workspace repositories to build the `/me` response (`AuthDtos.UserResponse` embedding
`WorkspaceDtos.WorkspaceSummary` is the same seam), and the `rbac/` access services, which read the
workspace/project repositories because authorisation is answered from membership rows. One
feature→feature seam is sanctioned: `project`'s `StrategyService` calls `company`'s
`CompanyQueryService.refsByKeys` to resolve strategy-list company snapshots at write time — the
universe lookup lives with the universe rather than being duplicated SQL in `project`, and the seam
is a public service method plus the `company/model` records it returns, never `company` internals.
A second seam is sanctioned for client representatives: `project`'s `ClientRepresentativeService`
calls `workspace`'s `InvitationService.onboardClientRepresentative` to grant membership (a representative
is a CLIENT-role workspace member, and membership is the workspace's to grant). That call chooses the
path: an email that is **already an active member** gains the `CLIENT` role on their existing membership
plus an informational email — no invite, because a user is unique to a workspace and this person is in;
a **stranger** gets the ordinary invitation, and *acceptance* flows back as a
`ClientRepresentativeAcceptedEvent` the project side listens for — so `workspace` announces the accept in
primitives and never depends on `project` (mirrors `EmailVerifiedEvent`). Attaching a representative to a
mandate is a plain project seat (`ProjectService.attachRepresentative`), no seam. This is a deliberate
trade of the old ports/adapters layering for a uniform, type-based shape, so
`EmailSender`/`RateLimiter` are plain `service` interfaces rather than declared ports.

Ports worth knowing: `EmailSender` (`core/email/service`; `LogEmailSender` prints the verification link to
the console — the default, so a fresh clone is fully testable with no provider account; `ResendEmailSender`
for prod) and `RateLimiter` (`core/ratelimit/service`; in-memory Bucket4j — swap for Redis before running
more than one instance).

## Conventions

- Java: constructor injection only. `record` for DTOs. Immutable where you can be.
- **Names carry intent.** Variables, methods, classes, enums, and constants get meaningful, logical
  names — the name alone must make the purpose clear. No abbreviations, single letters (except loop
  indices), or vague names (`data`, `info`, `tmp`, `doStuff`, `handle`, `flag`). Methods read as verbs
  (`resolveWorkspaceId`), booleans as predicates (`isVerified`, `hasActiveSeat`), classes/enums as nouns.
  If a name needs a comment to explain what it holds, rename it — same rule as the Comments line below.
- **Lombok.** Use Lombok for all standard boilerplate: `@RequiredArgsConstructor` for constructor
  injection, `@Slf4j` for the logger. Entities use `@Getter` + `@NoArgsConstructor(access = PROTECTED)`
  + selective `@Setter`, and **never** `@Data`, `@EqualsAndHashCode`, or `@Builder` — `BaseEntity`
  explains why identity equality is hand-written. A hand-written constructor is allowed **only** when it
  *derives* a value (e.g. a nested config record, `this.config = properties.auth()`); one that contains
  nothing but `this.x = x` assignments is dead weight, however many dependencies it takes — use
  `@RequiredArgsConstructor`. Config is `lombok.config` at the module root.
- Errors: RFC 9457 `ProblemDetail`, produced centrally in `GlobalExceptionHandler`. The frontend switches
  on `code`, never on `detail`. `ApiException` has **two message channels**: the constructors take an
  *internal* detail that reaches the log and never the response (so a rule may quote the request), while
  `userFacing` / `withField` opt a **fixed** sentence into the body — the latter as `fieldErrors`, the
  same shape Bean Validation produces. Never hand `userFacing` anything interpolated from input.
- Comments explain *why*, not *what*. Every class carries a class-level doc; the inline comments flagged in
  "Traps" are load-bearing — they document bugs that shipped, and must not be stripped. If a line needs a
  comment to say what it *does*, rename something.
- React: feature folders. Server state via TanStack Query — don't mirror it into `useState`.
- Styling: Tailwind utilities over the tokens in `apps/web/src/styles/tokens.css`, which are lifted
  verbatim from the mockups. Change a colour there, not inline.


Review will be done by fable or codex