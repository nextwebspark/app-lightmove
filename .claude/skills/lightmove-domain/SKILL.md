---
name: lightmove-domain
description: LightMove's security and domain invariants, with full rationale. Load before ANY work touching auth, signup, login, OAuth/SSO, JWT, refresh tokens, sessions, workspace, membership, invitations, roster, onboarding, verification, password reset, RBAC, roles, permissions, clients, client representatives, or tenant isolation.
---

# LightMove domain rules — the full rationale

CLAUDE.md carries these as one-line invariants; this file is why each one holds. These paragraphs
are load-bearing — they document decisions that were expensive to learn. Don't act against them,
and don't strip them when editing.

## Identity is a work email; the organization is a workspace

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
its client contacts, and the reach they are admitting to is narrow — see the two tiers below. There is
no join request and no approval queue; a colleague whose firm is already here asks their admin for an
invite. A new invitee sets a password on the accept screen and is in at once:
`POST /onboarding/accept-invitation-signup` (public — token + name + password) creates their account
*already verified* and issues a session carrying the workspace, with **no separate email-verification
step**. The invite token, mailed only to the invited address, is the mailbox proof verification would
otherwise collect; the account's email is taken from the invitation, **never the request body**, so the
token can only ever mint the identity it was addressed to (that binding, plus the `existsByEmail` guard
that sends an already-registered address to log in, is the security of this path). An invitee who
*already* has an account is routed server-side instead: `/me` carries `pendingInvitation` and the
signed-in `POST /onboarding/accept-invitation` redeems it token-lessly.

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

## Tenant isolation

Every workspace-scoped query filters by the `workspace_id` **from the authenticated principal**, never
from a request parameter. `AuthPrincipal.requireWorkspaceId()` is the only supported way to get it.

## Authorisation asks for an action, never a role

RBAC is data (`core/security/rbac`): `app_lm_role` / `app_lm_action` / `app_lm_role_action` are seeded
catalogs, memberships and project seats hold role **sets** via assignment tables, and permissions are
the union of the roles' actions. Adding a role or action = an INSERT migration + an enum constant;
`RbacCatalogTest` fails the build if the two drift. Controllers declare the gate with `@PreAuthorize`
over actions (`@workspaceAuthorizer.can(principal, 'MEMBER_INVITE')`,
`@projectAuthorizer.can(principal, #projectId, 'TEAM_MANAGE')`); the guard beans **re-read the
database** on every check and enforce by throwing `ApiException`, so denials keep their codes and
the 404 masking. The JWT's `roles` claim is coarse material only — up to 15 minutes stale, never trusted
for a role-sensitive decision.
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
The project *list* rides any active membership (`@workspaceAuthorizer.member`; the service scopes a
pure client to the mandates they're seated on), and shared reference data
(`CompanyReferenceController`) rides `PROJECT_BROWSE`: existence isn't secret, content is.

## An identity provider is configuration, not code

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
`ProviderQuirkAwareRequestResolver`, and LinkedIn is in both. Per-registration on purpose: Google
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
the person signing in) and sends the browser to the SPA with `OAUTH_FAILED`. Both handlers route
refusals through `LoginErrorRedirector`.

The profile picture is the provider's CDN URL, copied to `app_lm_user.avatar_url`, and **owned by
whoever supplied it** (`avatar_source`, V25). That source may re-stamp it on every sign-in — LinkedIn's
URLs expire within weeks — and anyone else may only fill an empty one. Without the ownership rule the
last provider used always won, so signing in with an account that has no photo replaced a real picture
with a generated monogram; providers send a monogram, not nothing. A null source is a row older than
the column and is claimed once. The name follows the same shape and is only backfilled when blank — it
is editable here, and a provider must not overwrite what someone typed. `Avatar` falls back to initials
when the image fails to load, which is the designed end state for a user who stops signing in, not a
bug.

## Tokens are never stored raw

Refresh, verification and invitation tokens are 256-bit random values; only their SHA-256 hash is
persisted. Passwords are BCrypt(12).

The **refresh token** is httpOnly + Secure + SameSite, scoped to `/api/v1/auth`, and **rotates on every
use**. Presenting an already-rotated token is treated as theft and revokes the whole family. The access
token lives in **JS memory only** — never `localStorage`, because one compromised npm dependency would
otherwise walk away with a 30-day credential to a product holding executive-candidate PII.

## The SPA and the API are one origin

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

## Auth errors are deliberately vague

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
