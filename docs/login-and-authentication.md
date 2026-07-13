# Login & Authentication

Reference for everything the auth module does — backend and frontend — as built in the session of
**13 July 2026**. Written so a future session can pick this up without re-reading the code.

The heart of it is **[Scenarios](#scenarios)**: every route a person can take from "no account" to
"inside a workspace". Everything else in this document exists to serve those.

---

## The model in one paragraph

A **Workspace** is the tenant. It holds **Members** with roles `ADMIN` / `CONSULTANT` / `RESEARCHER`.
A person signs up with a **work email** — consumer domains are rejected — and the email domain is what
tells us which firm they work at. **A domain does not own a workspace**: one firm may run several, so
`email_domain` is deliberately *not* unique. But **a user belongs to at most one workspace**, enforced
by a partial unique index. How a user gets *into* a workspace is the whole story, and it has three
endings: they create one, they ask to join one and an admin approves, or an admin invites them.

---

## Scenarios

Every path a user can take. This is the section to read first.

### S1 — First person from a firm signs up

Nobody from `acme.com` is on LightMove yet.

1. `POST /auth/signup` → user row, `status = PENDING_VERIFICATION`, verification email sent, **201**
   with an access token and a refresh cookie.
   The token carries **no `wsId` claim** — they have an account and no workspace. The filter chain
   therefore admits them to onboarding endpoints and nothing else.
2. SPA lands on `/signup/workspace`. It calls `GET /onboarding/workspaces` → **`[]`**. Nobody to join,
   so the form goes straight to "About your organization".
3. `POST /onboarding/workspace` → workspace created; the creator is its **`ADMIN`**, membership
   `ACTIVE`. **No approval — creating a workspace is the decision.**
4. SPA lands on `/signup/invite` (step 3, skippable).
5. Verification link → `POST /auth/verify` → user `ACTIVE`, `email_verified_at` set.

**Until step 5 they can reach no workspace data.** `require-verified-email: true` means every
workspace-scoped route 403s for an unverified user, even the admin of the workspace they just made.
The SPA shows an amber banner saying so, with a resend link, rather than a silently dead screen.

### S2 — Second person from the same firm signs up, and asks to join

`acme.com` already has a workspace.

1. `POST /auth/signup` — identical to S1.
2. `GET /onboarding/workspaces` → **the workspaces on their domain**. The SPA shows the fork:
   **Ask to join** (primary) or **Create a separate workspace instead** (secondary).
3. They choose join → `POST /onboarding/join-requests` → membership row,
   **`status = PENDING_APPROVAL`**, `joined_at` null.
4. **They have access to nothing.** The access token still carries no `wsId`. SPA sends them to
   `/signup/pending`.
5. An admin sees them at `GET /members/pending` and calls `POST /members/{id}/approve` with a role.
   Membership → `ACTIVE`, `joined_at` set, `decided_by` / `decided_at` recorded.
6. The new member's **next token refresh** (≤ 15 min, or immediately on reload) mints a token carrying
   `wsId` and `role`. They're in.

> **The role they asked for is a suggestion, not a grant.** `requestedRole` is stored; the approving
> admin passes `grantedRole` and that is what is written. Sharing an employer's email domain is
> evidence someone works there — it is not a decision that they should see an executive-search
> pipeline.

### S3 — Second person from the same firm creates their own workspace

Same as S2 up to the fork, then they pick **Create a separate workspace instead**. They become `ADMIN`
of a *second* workspace on `acme.com`. This is legitimate and intentional — a firm may run several —
and it is why `app_lm_workspace.email_domain` has no unique constraint.

### S4 — Admin invites a colleague ⚠️ **incomplete**

1. Admin calls `POST /onboarding/invitations` (signup step 3, or later from Team).
   Invitation row, token hashed, email sent containing
   `{baseUrl}/auth/accept-invite?token=…` — see `InvitationService.java:140`.
2. Invitee clicks the link → **the SPA has no `/auth/accept-invite` route.** The catch-all sends them
   to `/`, they have no workspace, they get bounced to login. **The token is silently discarded.**
3. `POST /onboarding/invitations/accept` exists and works. **Nothing calls it.**

**Consequence:** an invited person signs up as a stranger and lands in the approval queue — the exact
queue the invitation was supposed to let them skip.

**What the flow is supposed to do:** an invited user skips the queue entirely and lands **`ACTIVE`**
with the role the admin chose. An admin naming a colleague *is* the approval, made up front. The
backend honours this; the frontend never asks it to.

**To finish it:** add an `AcceptInvitePage` at `/auth/accept-invite`. It must handle the invitee having
no account yet — carry the token through account creation, then call `accept` — as well as the case
where they are already signed in.

### S5 — Returning user logs in

`POST /auth/login` → access token in memory + refresh cookie. Failures increment
`failed_login_attempts`; 5 within the window locks the account for 15 minutes.

**"Invalid email or password" covers wrong password, unknown account, and Google-only account.** The
audit log records which (`reason: no_such_user` / `bad_password` / …); the client is told only that the
pair did not match. Anything else is a free account-enumeration oracle.

### S6 — Page reload

The access token lives in **JS memory only** and is gone. On boot the SPA calls
`GET /auth/csrf` then `POST /auth/refresh`; the httpOnly cookie survived the reload, so a new access
token is minted and the session is restored. An anonymous visitor gets a 401 here — expected, not an
error; the SPA falls through to the login screen.

### S7 — Google OAuth *(built, not configured)*

`GET /oauth2/authorization/google` → success handler links or creates the user, mints **our** JWTs,
sets our cookie, redirects to `/auth/callback`. Needs a GCP OAuth client with redirect URI
`http://localhost:8080/login/oauth2/code/google`. Until then `GET /auth/providers` returns
`{"google": false}` and the SPA hides the button — a button leading to a 404 is worse than no button.

### S8 — Refresh token theft

A refresh token that has already been rotated away should never be seen again. If one is, either an
attacker stole it, or they stole *and used* it and this is the real user arriving with a now-stale
token. Indistinguishable from here, and in both cases someone unauthorised holds a live credential —
so **the entire token family is revoked** and both parties sign in again.

---

## Endpoints

All under `/api/v1`.

| Method | Path | Auth | Notes |
|---|---|---|---|
| `POST` | `/auth/signup` | — | 201 + session. No workspace yet |
| `POST` | `/auth/login` | — | Rate-limited, lockout-aware |
| `POST` | `/auth/refresh` | **refresh cookie + CSRF** | Rotates; reuse revokes the family |
| `POST` | `/auth/logout` | refresh cookie | Idempotent |
| `POST` | `/auth/verify?token=` | — | Anonymous by design — the person clicking a link in their inbox may not be signed in |
| `POST` | `/auth/verify/resend` | — | Always 202, even for an unknown address |
| `GET` | `/auth/me` | bearer | SPA calls on boot to rehydrate |
| `GET` | `/auth/csrf` | — | **Writes the XSRF cookie.** See the trap below |
| `GET` | `/auth/providers` | — | `{"google": false}` unless configured |
| `GET` | `/onboarding/workspaces` | bearer | Workspaces on your email domain → the join/create fork |
| `POST` | `/onboarding/workspace` | bearer | Creates workspace; caller becomes `ADMIN` |
| `POST` | `/onboarding/join-requests` | bearer | → `PENDING_APPROVAL` |
| `POST` | `/onboarding/invitations` | bearer, ADMIN | Bulk invite |
| `POST` | `/onboarding/invitations/accept` | bearer | **Exists; the SPA never calls it — see S4** |
| `GET` | `/members/pending` | bearer, ADMIN, **verified** | The approval queue |
| `POST` | `/members/{id}/approve` | bearer, ADMIN | Admin passes the role |
| `POST` | `/members/{id}/reject` | bearer, ADMIN | |

---

## Tokens

| | |
|---|---|
| **Access** | RS256 JWT, **15 min**, claims `sub`, `email`, `emailVerified`, `wsId`, `role`, `jti`. **JS memory only** |
| **Refresh** | Opaque 256-bit random, **30 days**, SHA-256 at rest, **rotates on every use** |
| **Cookie** | `lm_refresh` — httpOnly, Secure, SameSite, **path-scoped to `/api/v1/auth`** |

**The access token is never in `localStorage`.** One compromised npm dependency would otherwise walk
away with a 30-day credential to a product holding executive-candidate PII.

`wsId` and `role` are signed claims, so a caller cannot alter them. The cost is staleness: revoking
someone's admin rights does not reach an already-minted token. That window is bounded by the 15-minute
access-token TTL, because the next refresh re-reads the membership from the database.

**Nothing is stored raw.** Refresh, verification and invitation tokens are 256-bit random values; only
their SHA-256 hash is persisted. Passwords are BCrypt(12).

> A consequence worth knowing: **if a verification email fails to send, the link is unrecoverable.**
> The plaintext existed only in that email. Use `/auth/verify/resend` — there is nothing to dig out of
> the database.

---

## Schema — `V1__auth_core.sql`

Eight tables, all `app_lm_` prefixed. **Hibernate never touches the schema** (`ddl-auto: none`);
migrations are hand-written SQL applied by Flyway. Never edit an applied migration — add a new one.

| Table | Purpose |
|---|---|
| `app_lm_user` | `email` unique + `CHECK (email = lower(email))`, nullable `password_hash` (Google-only users), `status`, `email_verified_at`, `failed_login_attempts`, `locked_until`, `terms_accepted_at` |
| `app_lm_user_identity` | Federated logins. Unique `(provider, provider_user_id)` |
| `app_lm_workspace` | The tenant. `email_domain` **NOT unique** — one firm may run several |
| `app_lm_workspace_member` | `role`, `status`, `joined_at` (null while pending), `decided_by`, `decided_at` |
| `app_lm_invitation` | `token_hash`, `role`, `status`, `expires_at` |
| `app_lm_verification_token` | `token_hash`, `purpose`, `expires_at`, `consumed_at` |
| `app_lm_refresh_token` | `token_hash`, `family_id`, `replaced_by_id`, `revoked_reason` — powers rotation and theft detection |
| `app_lm_audit_event` | **Append-only** — a trigger blocks `UPDATE`/`DELETE`/`TRUNCATE`, even for the app's own role |

The index that enforces one-workspace-per-user:

```sql
CREATE UNIQUE INDEX app_lm_workspace_member_single_org_per_user_uk
    ON app_lm_workspace_member (user_id)
    WHERE status = 'ACTIVE';
```

It constrains **`user_id`, not `workspace_id`** — a workspace holds as many members as it likes.

### Tenant isolation

Every workspace-scoped query filters by the `workspace_id` **from the authenticated principal**, never
from a request parameter. `AuthPrincipal.requireWorkspaceId()` is the only supported way to get it.

---

## Email

`EmailSender` is a **port** with two adapters:

- **`LogEmailSender`** — prints the message to the console. **The default**, so a fresh clone is fully
  testable with no provider account.
- **`ResendEmailSender`** — production.

The domain layer does not know which is live.

**Resend needs a verified domain.** Until one is verified, Resend delivers **only to the address the
Resend account is registered under**, and refuses everything else with a 403 — regardless of the `from`
address. `onboarding@resend.dev` is Resend's shared sandbox sender; it does not lift that restriction.
To send real mail: verify the domain at resend.com/domains, add their DNS records, then set
`from-address` to an address **on that domain**.

A failed send does **not** roll back the signup. Email is best-effort; the account is not.

### Signup email validation

Layered, all free, before any send: RFC 5322 syntax → **DNS MX lookup** on the domain → disposable-domain
blocklist → **consumer-domain blocklist** (gmail, outlook, …).

Configurable via `lightmove.email.validation.block-public-domains`, `.public-domains`,
`.extra-public-domains`, `.block-disposable-domains`, `.mx-check-enabled`.

Turning the consumer block off has a consequence: the email domain is what groups colleagues, and
`gmail.com` groups the entire world. LightMove therefore never offers a Gmail user "the workspaces on
your domain" — that list would be every unrelated customer who also used Gmail. Consumer signups always
create a fresh workspace.

---

## Frontend

```
src/
├── app/routes.tsx              /login /signup /auth/verify /auth/callback
│                               /signup/workspace /signup/invite /signup/pending  /
├── lib/apiClient.ts            access token in memory · single-flight refresh · CSRF double-submit
├── lib/cn.ts                   clsx + tailwind-merge — see the trap below
├── components/ui/index.tsx     Button · Field · Input · Select · Card · Logo · FormError · Notice
└── features/
    ├── auth/  AuthProvider · schemas (zod) · api/ · pages/
    └── workspace/pages/WorkspacePage.tsx   placeholder + admin approval queue + unverified banner
```

**Session rehydration without `localStorage`**: on boot, `GET /auth/csrf` then `POST /auth/refresh`.

**Single-flight refresh** (`apiClient.ts`): concurrent 401s must not each rotate the token — the second
rotation would present an already-rotated token and trip **theft detection**, revoking the family and
logging the user out. All callers await one in-flight refresh.

**Dev requests are proxied** through Vite (`localhost:5173/api/…` → `:8080`) so the SPA and API share an
origin and the `SameSite` cookie is accepted.

---

## Compliance & monitoring

- **Audit events** (22 types): `USER_SIGNED_UP`, `LOGIN_SUCCEEDED`, `LOGIN_FAILED`, `ACCOUNT_LOCKED`,
  `TOKEN_REFRESHED`, `TOKEN_REUSE_DETECTED`, `TOKEN_FAMILY_REVOKED`, `EMAIL_VERIFIED`,
  `WORKSPACE_CREATED`, `MEMBER_INVITED`, `INVITATION_ACCEPTED`, `JOIN_REQUESTED`, `JOIN_APPROVED`,
  `JOIN_REJECTED`, `MEMBER_ROLE_CHANGED`, `MEMBER_REMOVED`, `OAUTH_*`, `PASSWORD_*`, `LOGOUT`.
  Written async, append-only, with a correlation id.
- **Rate limits** (Bucket4j, keyed by IP **and** email): login 10/min, signup 5/hr, verify-resend 3/hr.
- **Lockout**: 5 failed attempts → 15 minutes.
- **Errors**: RFC 9457 `ProblemDetail` with a stable `code`. The frontend switches on `code`, never on
  `detail`.
- **GDPR**: `terms_accepted_at`, `privacy_policy_version` captured at signup.
- Actuator + Micrometer `/prometheus`; structured logs with a correlation id.

---

## Tests

| Suite | Count | What |
|---|---|---|
| `AuthFlowIntegrationTest` | 17 | Testcontainers, real Postgres 16. Signup → verify → login → refresh → logout; token reuse revokes the family; lockout; rate limiting; tenant isolation |
| `EmailAddressValidatorTest` | 8 | Consumer/disposable domain rules, including the `[""]` regression |
| `apiClient.test.ts` | 7 | Single-flight refresh, CSRF, anonymous restore |
| `SignupPage.test.tsx` | 4 | Validation rules and server-error mapping |
| `ui/index.test.tsx` | 3 | Caller's `className` beats the component default |

```bash
cd apps/api && ./mvnw test    # needs Docker (Testcontainers)
cd apps/web && npx vitest
```

---

## Traps — every one of these shipped, looked correct, and did nothing

- **`@Async` / `@Transactional` are proxy-based.** A method calling another **on itself** bypasses the
  proxy and the annotation is inert. `AuditService` delegates to a separate `AuditEventWriter` bean for
  exactly this reason — otherwise the audit insert ran in the caller's transaction and rolled back the
  signup it was recording.
- **Spring rolls back on any unchecked exception, including `ApiException`.** `login()` and `rotate()`
  are `@Transactional(noRollbackFor = ApiException.class)`. Without it, the failed-login counter and the
  token-family revocation are rolled straight back out — **silently disabling account lockout and
  refresh-token theft detection entirely.**
- **Spring Security loads the CSRF token lazily.** An endpoint that returns 204 without calling
  `csrfToken.getToken()` writes no cookie, so the SPA has nothing to echo back and every refresh 401s.
  See `AuthController.csrf` — the apparently redundant `token.getToken()` is the entire method.
- **`@DefaultValue("")` on a `List<String>` binds to `[""]`, not `[]`.** Treating that as "the operator
  supplied an override" emptied the consumer-domain blocklist and let Gmail signups through.
- **Every auth route needs `JwtPrincipalConverter`.** With Spring's default converter the principal is a
  raw `Jwt`, `CurrentUser` finds no `AuthPrincipal`, and the endpoint 401s on a valid token.
- **Tailwind resolves conflicting utilities by stylesheet order, not attribute order.** A component that
  hardcoded `w-full` and appended the caller's `w-[130px]` rendered full-width — the invite row's role
  select blew out the layout while the JSX read exactly as intended. Every primitive now routes
  `className` through `cn()` (`clsx` + `tailwind-merge`).

### Not a bug in this codebase, but it will cost you an hour

**Cookies on `localhost` are shared across every port and every project.** Other dev servers you have
run pile cookies into the same jar, and once the `Cookie` header exceeds **8 KB**, Tomcat rejects
*every* request at the connector with a bare **400** — before Spring, before routing, before any
validation. The symptoms are baffling: a body-less `GET` returns 400, login and signup fail
identically, and **nothing appears in the application log or the audit table**, because no application
code ever runs.

Clear the `localhost` cookies. To diagnose it, turn on the Tomcat access log — it is the only place
these rejections are visible:

```bash
SERVER_TOMCAT_ACCESSLOG_ENABLED=true \
SERVER_TOMCAT_ACCESSLOG_DIRECTORY=/dev \
SERVER_TOMCAT_ACCESSLOG_PREFIX=stdout \
SERVER_TOMCAT_ACCESSLOG_SUFFIX= \
SERVER_TOMCAT_ACCESSLOG_ROTATE=false \
npm run dev
```

A durable fix would be to serve the SPA from `http://lightmove.localhost:5173` — a distinct host, and
therefore a cookie jar no other project can pollute.

### Config precedence

`EMAIL_PROVIDER=log` does **nothing** if `application-local.yml` sets `provider: resend` literally: the
env var only feeds the `${EMAIL_PROVIDER:log}` placeholder in `application.yml`, and the profile file
outranks the base file. The override that works is **`LIGHTMOVE_EMAIL_PROVIDER=log`**, which binds
straight to the property and outranks both.

---

## Known gaps

| Gap | Status |
|---|---|
| **Invite-accept flow** | Backend done, **SPA route missing** — see S4. The one real hole |
| Google OAuth | Built, not configured. Needs a GCP OAuth client |
| Resend | Key wired; needs a **verified domain** before mail reaches anyone |
| Password reset | `VerificationToken` has a `PASSWORD_RESET` purpose. No endpoint or screen |
| `ops/cloudsql/harden.sql` | Written, **not run** — it revokes `cloudsqlsuperuser` from `lm_app`, which also blocks Flyway. Needs a separate `lm_migrate` role first |
| Cloud SQL `bright-gcc` | **Backups disabled**, `sslMode: ALLOW_UNENCRYPTED_AND_ENCRYPTED`. Both must change before this holds real candidate PII |
| Rate limiter | In-memory Bucket4j — swap for Redis before running more than one instance |
