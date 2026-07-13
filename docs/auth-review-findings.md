# Auth review — findings & fixes

Review of the login/signup/OAuth/onboarding auth module built against `login-and-authentication.md`
and the `claude-design/*.dc.html` mockups. Hand this to an implementing session: each item has a
location, what's wrong, and the fix. Scope agreed: **security + correctness + design fidelity**, and
**add the member-approval role picker**.

The core is sound — every documented trap (self-invoked `@Transactional`, `noRollbackFor`, lazy CSRF
cookie, entity identity, token hashing, enumeration-safe errors) is handled correctly. Items below are
the real gaps, none of which the doc already covers except where noted under "Not in scope."

---

## A. Backend security

### A1 — HIGH · unverified user can create a workspace and claim a company domain
The whole trust model is "email domain = proof of org." Today an unverified signup can create a
workspace bound to a domain it never proved it owns.

- **Where:** [SecurityConfig.java:139](../apps/api/src/main/java/app/lightmove/api/auth/infrastructure/SecurityConfig.java#L139)
  gates `/onboarding/**` on `.authenticated()` only, *ahead* of the verified-email check at line 145.
  [OnboardingService.createWorkspace:83](../apps/api/src/main/java/app/lightmove/api/workspace/application/OnboardingService.java#L83)
  has no verified check.
- **Exploit:** `POST /auth/signup` as `victim@realfirm.com` (mailbox never proven) →
  `POST /onboarding/workspace` → ADMIN of a workspace bound to `realfirm.com`. Note the inconsistency:
  invite-*accept* already requires verified (`InvitationService.java:189-191`), workspace-*create* and
  join-request do not.
- **Fix:** Require `SCOPE_VERIFIED` on onboarding **writes** (`POST /onboarding/workspace`,
  `/onboarding/join-requests`, `/onboarding/invitations`) while keeping the **read**
  `GET /onboarding/workspaces` open (needed to show the join/create fork before verifying). Extract the
  `VERIFIED_AUTHORITY` predicate already inline at SecurityConfig.java:146-151 into a shared
  `AuthorizationManager` field and apply it to a `POST /onboarding/**` matcher above line 139.
  Defense in depth: also assert `user.isEmailVerified()` inside `createWorkspace` and `requestToJoin`
  ([:117](../apps/api/src/main/java/app/lightmove/api/workspace/application/OnboardingService.java#L117)),
  throwing `ApiException(ErrorCode.EMAIL_NOT_VERIFIED, …)`.
- **Test:** unverified user gets 403 on `POST /onboarding/workspace` (`AuthFlowIntegrationTest`).

### A2 — MEDIUM · `/actuator/**` guarded by the tenant `ROLE_ADMIN`
[SecurityConfig.java:131](../apps/api/src/main/java/app/lightmove/api/auth/infrastructure/SecurityConfig.java#L131)
uses `hasRole("ADMIN")` — the same `ROLE_ADMIN` every workspace creator gets. Any customer can scrape
`/actuator/prometheus` and `/metrics`. **Fix:** split ops from tenant role — restrict `/actuator/**`
(beyond `health`/`info`) to a config-driven ops credential (basic auth) or a separate management port;
do not let a workspace role double as system-admin.

### A3 — MEDIUM · spoofable `X-Forwarded-For`
`clientIp` takes the leftmost (client-supplied) hop in three places — `RateLimitGuard`,
`AuditService.Builder`, and
[TokenService.clientIp:209](../apps/api/src/main/java/app/lightmove/api/auth/application/TokenService.java#L209).
A caller sending its own `X-Forwarded-For` gets a fresh per-IP rate-limit bucket each request (defeats
per-IP login/signup budget) and poisons the audit `ip_address`. **Fix:** one `ClientIpResolver` in
`common/`, configured with a trusted-proxy count, selecting the rightmost-untrusted hop (or
`getRemoteAddr()` when none configured). Replace all three call sites. Add
`lightmove.security.trusted-proxy-count` (default 0).

### A4 — MEDIUM · refresh-token rotation race
`RefreshToken` has no `@Version`/row lock, so two concurrent refreshes of one token both read it
un-revoked, both mint successors in the same family, and theft detection never fires
([TokenService.rotate:88-132](../apps/api/src/main/java/app/lightmove/api/auth/application/TokenService.java#L88)).
**Fix:** make read-then-rotate atomic — `@Lock(PESSIMISTIC_WRITE)` on a `findByTokenHashForUpdate`
repository method used in `rotate`, or a conditional `UPDATE … WHERE revoked_at IS NULL` returning
affected-rows (0 rows ⇒ reuse). Prefer `SELECT … FOR UPDATE`. Add a concurrent-refresh test.

### A5 — MEDIUM · ephemeral JWT keys on missing files
`RsaKeyProvider` generates throwaway in-memory keys with only a `WARN` when the configured key files are
absent, so a prod misconfig boots on per-restart keys and invalidates every token on redeploy. **Fix:**
fail-fast — if the active profile is not `dev`/`test` and either key resource is missing, throw at
startup instead of generating.

---

## B. Frontend correctness

All in [WorkspacePage.tsx](../apps/web/src/features/workspace/pages/WorkspacePage.tsx) unless noted.

### B1 — Member-approval role picker (replaces hardcoded RESEARCHER)
[WorkspacePage.tsx:87](../apps/web/src/features/workspace/pages/WorkspacePage.tsx#L87) hardcodes
`"RESEARCHER"` while the comment claims "the admin decides" — contradicts the S2 model. `requestedRole`
is on `PendingMember` (`api/types.ts:58`) but never shown; `approveMember` already accepts a role
(`authApi.ts:103`). **Fix:** per pending row, show `requestedRole` and add a `Select` (reuse the `Select`
primitive) defaulting to the requested role, options CONSULTANT / RESEARCHER / ADMIN; pass the chosen
role to `approveMember`. Delete the now-false comment.

### B2 — Approve/Decline double-submit
The two `Button`s ([:82,:94](../apps/web/src/features/workspace/pages/WorkspacePage.tsx#L82)) fire async
handlers with no `loading`/`disabled`; a double-click sends duplicate `POST /approve`. **Fix:** convert
to `useMutation` (no mutations exist yet; CLAUDE.md wants server state via Query) so the acting row
disables both buttons while pending, and `invalidateQueries(["pending-members"])` on success instead of
the imperative `refetch()`.

### B3 — Resend banner hangs on failure
`UnverifiedBanner.resend` ([:144](../apps/web/src/features/workspace/pages/WorkspacePage.tsx#L144)) has no
try/catch, so a 429 (`RateLimitGuard.checkVerificationResend`) or network error leaves the button on
"Sending…" forever. **Fix:** add a `catch` → `"error"` state showing "Couldn't send — try again" and
re-enable the button.

### B4 — Pending "Check again" strands approved user
`PendingApprovalPage` "Check again" (`PendingApprovalPage.tsx:33`) reloads `user` but nothing navigates
once `user.workspace` becomes non-null; `RequireAuth` lets a workspaced user sit there. **Fix:** effect
— when `user?.workspace` is present, `navigate("/", { replace: true })`.

---

## C. Design fidelity (vs claude-design mockups)

Tokens are a verbatim lift and the auth primitives are faithful. Drift:

### C1 — Back navigation in the signup wizard
Mockup has Back on steps 2 and 3 (`Signup.dc.html:91,117`) and a clickable stepper; implementation has
neither, so a user on step 2/3 cannot go back.
- The `Stepper onGoBack` prop exists (`Stepper.tsx:35`) but is unwired at all three call sites
  (`SignupPage.tsx:74`, `WorkspaceStepPage.tsx:53`, `InviteStepPage.tsx:71`). Wire it for safe
  transitions only (3→2). **Do not** wire back into step 1 — the account is already created there.
- Add an explicit **Back** button (`Button variant="secondary"`) beside Continue on step 2
  ([WorkspaceStepPage.tsx:271](../apps/web/src/features/auth/pages/WorkspaceStepPage.tsx#L271)) and step 3
  (`InviteStepPage.tsx:132`), matching the mockup's `flex:none` + `flex:1` layout.

### C2 — Dark mode unreachable
Full dark palette exists (`tokens.css:55-76`) but nothing ever adds the `.dark` class. **Fix:** theme
toggle (mockup persists `localStorage` key `lm-theme`, toggles `document.body.classList`). The sidebar
that houses it isn't built, so put the toggle in the `WorkspacePage` header for now; apply saved theme
(or `prefers-color-scheme`) on boot. Small `useTheme` hook + header button.

### C3 — Empty-state drift (WorkspacePage)
Match `Workspace.dc.html:99-111`: briefcase icon (not folder), tile 52px/`rounded-[14px]`, heading 19px,
the mockup's body copy verbatim, and add the `Brief → Universe → Mapping → Shortlist` process rail below
the CTA. Keep "New project" disabled (no Project model yet).

### C4 — Small drifts
- Login "Continue with Google" still wears the mockup's SSO **padlock** icon (`LoginPage.tsx:132`) → swap
  for the Google G mark.
- Company-size defaults to `COMPANY_SIZES[1]` ("11–50") but mockup defaults to the first option
  ([WorkspaceStepPage.tsx:193](../apps/web/src/features/auth/pages/WorkspaceStepPage.tsx#L193)) →
  `COMPANY_SIZES[0]`.
- Signup step-2 grid has double margin above Continue (`Field` `mb-4` stacks with grid `mb-5`) → remove
  one.

### C5 — Accessibility (low-cost)
- `Field` error `<span>` (`ui/index.tsx:112`) → add `aria-live="polite"` and `aria-describedby` so screen
  readers announce per-field errors (only the form-level `FormError` announces today).
- Join-workspace radios are `sr-only` with no `focus-visible` on the label
  (`WorkspaceStepPage.tsx:133`) → add a visible focus ring so keyboard users see selection.

---

## D. Cleanup (cheap, do alongside)
- Dead code: `inviteSchema`/`InviteValues` (`schemas.ts:43-59`) unused — `InviteStepPage` does no
  client-side email validation at all (no `<form>`, no schema); either wire the schema in or delete it.
- `AuthProvider.adopt` (`AuthProvider.tsx:127`) is never called; `OAuthCallbackPage` double-round-trips
  `me()` (`setAccessToken` + `reload`). Wire `adopt` into the callback and drop the redundant `me()`.
- `refresh_token.ip_address`: entity says `inet` (`RefreshToken.java:68`), DDL says `varchar(45)` — make
  consistent (new Flyway migration, or fix the `columnDefinition`).

---

## Not in scope (documented decisions — leave as-is)
- Doc "Known gaps": invite-accept SPA route (S4), Google OAuth config, Resend verified domain, password
  reset. The dead `/forgot-password`, `/terms`, `/privacy` links follow from password-reset/legal pages
  not existing — same bucket.
- Full workspace app shell (46px header, workspace-switcher, 240px collapsible sidebar, panel chrome) —
  needs the Project model. WorkspacePage stays a placeholder; only its empty-state fidelity (C3) is fixed.
- `claude-design/_ds/` is a **different product's** design system (ALAC Global Talent Map — Montserrat,
  `#2563eb`, shadcn) and contradicts the `.dc.html` files. Implementation rightly followed the `.dc.html`
  mockups and ignored `_ds/`. Flag to the design owner; do not follow `_ds/`.

---

## Verification
1. **Backend:** `cd apps/api && ./mvnw test` (needs Docker/Testcontainers). New: unverified user 403 on
   `POST /onboarding/workspace` (A1); concurrent refresh trips theft detection (A4). Manually confirm
   `/actuator/prometheus` is unreachable with a workspace-ADMIN JWT (A2).
2. **Frontend:** `cd apps/web && npx vitest`. New: role picker sends chosen role (B1); approve disables
   while pending (B2); resend error re-enables (B3). Run `npm run dev`, sign up two users on one domain,
   request-to-join, approve with a chosen role, confirm the second user lands in the workspace after
   "Check again" (B4).
3. **Design:** eyeball signup steps 2/3 Back nav (C1), dark-mode toggle (C2), and the empty-state against
   `Workspace.dc.html` (C3) side by side.
