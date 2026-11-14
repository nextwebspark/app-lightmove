# GDPR: the terms tick, re-acceptance and the DPA record (#928)

Step 6 of epic #917, for Uncava's own users rather than the people a firm holds. Until now acceptance was implied:
the password signup sent a hard-coded `termsAccepted: true`, the invited signup asked nothing, and a first Google or
LinkedIn sign-in created the account with the terms "accepted" at the moment of verification. Now every door asks for
an unticked box, a change of terms asks again, and a workspace's founder accepts the data processing agreement for the
firm where the deployment names one.

None of it sits behind `DATA_PROTECTION_ENABLED`: an account's acceptance is not a people control. The DPA tick is
gated by its own configuration.

| Piece | Where | Migration |
|---|---|---|
| The terms tick on the password signup | `/signup` | — |
| The terms tick on the invited signup | `/auth/accept-invite` | — |
| The terms step of a first provider sign-in | `/signup/terms` | V221 |
| Re-acceptance when the version changes | a sheet over every signed-in screen | — |
| The DPA tick when founding a workspace | `/signup/workspace`, Settings → Workspaces → New workspace | V221 |

## Every signup door asks

- **Password signup.** `SignupRequest.termsAccepted` was always `@AssertTrue`; the SPA now sends what the box says
  instead of `true`.
- **Invited signup.** `AcceptInvitationSignupRequest` gains `termsAccepted` (`@NotNull @AssertTrue`): an invitee who
  has not ticked gets `VALIDATION_FAILED` on that field and nothing is created; the token still admits once they do.
- **A first sign-in with a provider.** `OAuth2LoginSuccessHandler` no longer creates the account. It still refuses
  what the provider sent (reserved registration ids, no address, an unproven address) and the work-email rule, so a
  refused address is told so before being asked to agree to anything; then, for an identity and address nobody holds,
  it parks the provider's word in `app_lm_pending_registration` and redirects to `/auth/callback?challenge=terms`
  with the `lm_pending_registration` cookie — httpOnly, SameSite as the refresh cookie, path
  `/api/v1/auth/oauth/registration`, ten minutes, stored as its SHA-256, single use. The popup reports `termsRequired`
  to the opener, as it reports `secondFactor`, and no credential crosses between the windows.
  - `GET /api/v1/auth/oauth/registration` answers the provider id, the address and the name for the step to show.
  - `POST /api/v1/auth/oauth/registration` `{termsAccepted}` spends the hold under its row lock and creates the
    account with the tick's time, then signs in through `SignInCompletion` like every door. It keeps the auth chain's
    double-submit check: the cookie is what creates an account.
  - An identity or address that gained an account while the step was open (another tab, a password signup) signs in
    or links exactly as the callback would have — an unverified claimant is still wiped first
    (`UnverifiedAccountWipe`). A second sign-in by the same identity replaces the hold, so two tabs cannot both
    register. Spent, replaced, unknown and late holds all answer `REGISTRATION_EXPIRED` (410).
  - Linking into an existing account and every later sign-in are untouched: they ask nothing.

`ProviderSignInService` holds what the handler used to do inside its transaction (find by subject, link by address,
register, complete), so the callback and the terms step share one path. Nothing in it names a provider.

## Re-acceptance

`PrivacyPolicy.CURRENT_VERSION` is the version every door records. `/me` answers `termsUpdateRequired` (the stored
`privacy_policy_version` is not the current one, or there is none) and `termsVersion`. The SPA's `TermsUpdateSheet`
covers every signed-in screen — the signup steps, the invitation and the extension's pairing included, booking and
privacy pages and the popups' landings excepted — until the person ticks it or signs out.

`POST /api/v1/auth/me/terms-acceptance` `{version}` stores the time and the version. A version that is no longer the
current one is refused (`TERMS_VERSION_OUTDATED`, 409): the sheet was opened before a deploy, and the person must read
the new terms. Accepting is audited `USER_TERMS_ACCEPTED` with the version only, on the account (no workspace); a second
tick of the same version records nothing.

**The version bump itself is not in this PR.** It ships with the counsel-reviewed terms on the website; until then
every account's stored version is current and no one sees the sheet. When it is bumped, point the sheet's "What
changed" link at the page that says what changed (it opens the Terms today).

## The data processing agreement

`lightmove.legal.dpa-version` and `dpa-url` (`LEGAL_DPA_VERSION`, `LEGAL_DPA_URL`), set together or neither. Blank,
nothing is asked and nothing is recorded. Set, both doors that found a workspace — signup's `POST /onboarding/workspace`
and Settings' `POST /workspaces`, through `OnboardingService.createWorkspace` — require `dpaAccepted: true`
(`DPA_ACCEPTANCE_REQUIRED`, before the creation budget is spent) and store `dpa_accepted_at`, `dpa_version` and
`dpa_accepted_by` on `app_lm_workspace`; `WORKSPACE_CREATED` gains `dpaVersion`. `GET /onboarding/legal` tells the form
what to show. Signup's Back (`PATCH /onboarding/workspace`) never asks again.

The three columns are a contract record: the purge's tombstone keeps them, and an erased founder leaves the time and
version with a null `dpa_accepted_by`.

## Personal data

`app_lm_pending_registration` holds a provider subject, an address, a name and a picture link for ten minutes; a new
hold first deletes every expired one, and `RetentionSweeper` (`RETENTION_PENDING_REGISTRATIONS_AFTER`, a day past
expiry) clears what is left. It is global, as the accounts it becomes are, so row-level security never reaches it. The
account export already carries `termsAcceptedAt` and `privacyPolicyVersion` (`ExportedConsents`).

## Left out ⚖️

- **The version bump** — with the counsel-reviewed terms and website (above).
- **The DPA for workspaces that already exist** — nothing asks a founder after the fact; counsel to decide whether a
  sheet like the terms one is wanted, or a countersigned DPA outside the product.
- **Marketing opt-in** — Uncava sends no marketing email, so there is nothing to opt into yet.
