# Code Review — Role Model, Invite-Only Onboarding & Cleanup

**Reviewed:** unpushed work on `feat/workspace-creation` (6 commits + working tree) vs
`origin/feat/workspace-creation` — ~3800 insertions across `apps/api`, `apps/web`, migration V6, tests.
**Against:** [docs/role-model-proposal.md](role-model-proposal.md) (Phase 1 + §10 onboarding UX).
**Date:** 2026-07-17. **Method:** one independent human pass over the security-critical backend +
five parallel review agents (RBAC, invite/onboarding, project tier, frontend, tests/cleanup).

---

## Verdict

**Ship-ready. No CRITICAL or HIGH defects found.** The implementation faithfully realises the design
doc. Every finding below is defence-in-depth, a test-coverage gap, or polish — none blocks merge.

What is genuinely well built (verified, not assumed):

- **The invited-signup trust model holds.** The created account's email comes from the resolved
  invitation, never the request body — `AcceptInvitationSignupRequest` has no email field, so the flow
  *cannot* be handed one. `existsByEmail` refuses a pre-existing identity before anything is created.
  One plain `@Transactional` with real rollback; the cross-bean calls (`createVerifiedLocalUser`,
  `tokens.issue`) keep the proxy honest. See [InvitationService.java:250-266](../apps/api/src/main/java/app/lightmove/api/workspace/service/InvitationService.java#L250-L266),
  [AuthService.java:146-176](../apps/api/src/main/java/app/lightmove/api/core/security/service/AuthService.java#L146-L176).
- **Guards re-read the DB every check; the JWT is coarse-only.** `WorkspaceAccess`/`ProjectAccess`
  resolve permissions from `findRoleNames`/`findActionNames` on every call. Only `wsId` + workspace role
  names enter the token — **project roles never do**. Verified-skip is scoped to the new-account path
  only; both authenticated accept paths re-check `isEmailVerified()`.
- **Throw-from-SpEL contract works and is regression-tested.** Guard beans return `true` and enforce by
  throwing `ApiException`, so denials keep their code and the 404 masking
  (`ProjectAuthorizationIntegrationTest.denialKeepsItsProblemShape`).
- **CLIENT cannot be minted** — refused in four places (invite, roster change, project seat, DB CHECK)
  and seeded with zero actions.
- **Migration V6 is sound** — dropped objects all exist in prior migrations; the multi-LEAD backfill
  can't collide with the still-present one-lead index (it constrained the old column); creator
  `{ADMIN,LEAD}` backfill + lead-promotion repair establish the `PROJECT_LAST_ADMIN` invariant.
- **Cleanup is complete** — every deleted symbol (`WorkspaceSummaries`, `pendingInvite.ts`,
  `PendingApprovalPage`, old `WorkspaceRole`/`ProjectRole` enums, join-request audit events/error codes,
  `jobTitle`) has zero dangling references. `tsc --noEmit` clean.

---

## Findings

### MEDIUM

**M1 — `RbacCatalogTest` does not validate the role→action grant map.**
[RbacCatalogTest.java:24-50](../apps/api/src/test/java/app/lightmove/api/core/security/rbac/RbacCatalogTest.java#L24-L50)
asserts the role and action catalogs match the enums, and that CLIENT grants nothing — but never checks
`app_lm_role_action`. The grant-map INSERT
([V6:103-122](../apps/api/src/main/resources/db/migration/V6__invite_only_and_rbac.sql#L103-L122)) is an
inner JOIN on string literals; a typo (`'PROJECT_BROWS'`) seeds **zero rows, not an error** — compiles,
seeds, passes the test. A future migration that fat-fingers a grant silently strips a permission and the
build stays green. CLAUDE.md and the test's own doc claim it "fails the build if the two drift" — drift
in *what a role grants* is exactly what it misses. Fails safe (denies), but silently.
**Fix:** assert the seeded grants against a code-side expected map, or at least that every non-CLIENT
role has ≥1 action.

**M2 — `AcceptInvitePage` doesn't gate on auth-loading; flashes a create-account form.**
[AcceptInvitePage.tsx:39-52](../apps/web/src/features/auth/pages/AcceptInvitePage.tsx#L39-L52). This is
the one route with no `RequireAuth`/`AnonymousOnly` wrapper, and it never checks `useAuth().loading`.
During the session-restore window `user === null`, so a signed-in invitee (wrong account / already in a
workspace) opening an emailed link briefly sees the anonymous full-name/password create form before it
corrects. No security breach (a submit during the flash 409s before any token installs), but it shows an
authenticated user a create-account form. Every other route avoids this by rendering `<Booting/>` while
`loading`. **Fix:** render a loading state while `loading` in `AcceptInvitePage`.

**M3 — TOCTOU race in the last-admin guards can drop below one admin.**
[ProjectService.java:163-166](../apps/api/src/main/java/app/lightmove/api/project/service/ProjectService.java#L163-L166)
(mirrored in [MemberService.java:102-108](../apps/api/src/main/java/app/lightmove/api/workspace/service/MemberService.java#L102-L108)).
The guard counts admin seats then acts on a *different* row in the same tx; `@Version` guards only the
same row. Two concurrent requests demoting/removing two *different* admins both observe count = 2, both
proceed → zero admins. Real but low-probability (needs simultaneous requests on a 2-admin project). The
workspace guard carries the identical race, so it may be an accepted limitation — flagged because the
invariant is stated as absolute. **Fix (if desired):** `SELECT … FOR UPDATE` on the project/workspace
row, or a serializable boundary, around the count+mutate.

**M4 — The `INVITATION_EXPIRED` path is untested.**
[InvitationService.java:319-322](../apps/api/src/main/java/app/lightmove/api/workspace/service/InvitationService.java#L319-L322)
returns a *distinct* code for a lapsed token vs an unknown/consumed one. No test exercises it —
`InvitedSignupIntegrationTest` covers only bogus and consumed tokens; no `INVITATION_EXPIRED` assertion
exists in the suite. A regression that inverts the ternary or drops the `expiresAt` check ships green.
**Fix:** add an expired-token case (issue an invite with a past `expiresAt`, assert 410/`INVITATION_EXPIRED`).

### LOW

**L1 — `ProjectAccess` workspace-admin bypass returns before the project is scoped to the workspace.**
[ProjectAccess.java:40-42](../apps/api/src/main/java/app/lightmove/api/core/security/rbac/ProjectAccess.java#L40-L42)
(flagged independently by two agents). For a workspace admin, `requireAction` returns *before* the
`findByIdAndWorkspaceId` check, so `@projectAuth.can(principal, #projectId, …)` authorises a workspace
admin for any `projectId`, foreign included. Harmless today — every service method re-scopes via
`requireProject(projectId, workspaceId)` → 404. But any *future* project endpoint that trusts the gate
alone (e.g. a `GET /projects/{id}`) would act cross-tenant. **Fix:** move the existence/scope check
above the admin bypass so the guard is self-sufficient.

**L2 — Invitation token travels in the URL query string.**
[InvitationService.java:149](../apps/api/src/main/java/app/lightmove/api/workspace/service/InvitationService.java#L149)
(emailed link) and [OnboardingController.java:151-155](../apps/api/src/main/java/app/lightmove/api/workspace/controller/OnboardingController.java#L151-L155)
(`GET …/preview?token=`). The 256-bit credential lands in access logs, browser history, and `Referer`
headers — places the hash-at-rest design was meant to keep it out of. The account-minting endpoint
correctly takes the token in the POST body, so this is confined to the read-only preview and the email
link itself (largely inherent to invite links). **Fix (optional):** POST the token to preview.

**L3 — The "vice versa" half of the staff/CLIENT separation is unguarded.**
[ProjectService.putMember:119-123](../apps/api/src/main/java/app/lightmove/api/project/service/ProjectService.java#L119-L123)
rejects a requested `CLIENT` role, but seating a workspace-`CLIENT` member with staff roles is not
blocked — `requireActiveMemberRow` asserts active membership, not non-CLIENT. Latent only (no CLIENT
members can exist yet); becomes real the moment the portal seeds them. **Fix (Phase 2):** a
`requireStaff`-style check on the target member.

**L4 — `app_lm_invitation.role_id` FK is not scope-pinned to WORKSPACE.**
[V6:211](../apps/api/src/main/resources/db/migration/V6__invite_only_and_rbac.sql#L211) references
`app_lm_role(id)` with no scope constraint — unlike the assignment tables, whose composite
`(role_id, role_scope)` FK makes cross-scope assignment physically impossible. Schema could hold a
PROJECT-scope role on an invitation; unreachable via the API (service only resolves `WorkspaceRole`).
**Fix (optional):** mirror the composite-FK + CHECKed-scope pattern.

**L5 — `weakPasswordRollsBackCleanly` doesn't exercise the rollback it names.**
[InvitedSignupIntegrationTest.java:117](../apps/api/src/test/java/app/lightmove/api/workspace/InvitedSignupIntegrationTest.java#L117)
uses `"onlyletters"`, which the DTO `@Pattern` rejects at the controller boundary *before* the service
transaction starts. The user-facing contract (bad password → nothing created → token still works) is
genuinely verified; the *transactional* cleanup inside the service is not. A future change moving
password enforcement into the service without `@Transactional` wouldn't be caught. **Fix:** trigger a
failure that reaches the service (e.g. a password that passes the DTO pattern but fails `PasswordPolicy`).

**L6 — Two AcceptInvite UX dead-ends** (frontend polish):
an unverified-then-invited account routes to `/auth/accept-invite` and sees only a static "check your
inbox" notice — no resend button, no auto-redirect; and after `EMAIL_ALREADY_REGISTERED`, the filled
password fields stay on screen above the "Log in to accept" link. Both cosmetic.

### NITs

- **`MEMBER_MANAGE` action is dead** — seeded and granted to ADMIN but no gate consumes it;
  `MembersController` gates on the ADMIN *role*. Either wire it or drop it.
- **`InvitationsController` write methods carry no `@PreAuthorize`** — only `pending()` is annotated,
  yet the class doc says the writes are gated on `MEMBER_INVITE`; they rely solely on the service's
  imperative `requireAdmin`. Not a hole, but the protection is invisible at the controller and
  inconsistent with the read.
- **No-op `putMember` PUT isn't idempotent in side effects** — an unchanged role set still calls
  `changeRoles` and writes a `PROJECT_TEAM_CHANGED` audit event + version bump. An
  `granted.equals(seat.getRoles())` early-skip would honour the doc's "changes nothing" claim.
- **Stale error code name** — the project sole-admin block throws `MEMBER_LEADS_PROJECTS`
  ([ProjectMemberDetachment.java:31](../apps/api/src/main/java/app/lightmove/api/project/service/ProjectMemberDetachment.java#L31));
  the invariant is now "sole admin," so the name misleads.
- **`job_title` column left in the DB unmapped** — intended (§10.4), tolerated by `ddl-auto: validate`.
  Fine; noted for the record.
- **`redeem()` doesn't re-assert the invitation role is non-CLIENT** — safe today (invite refuses it +
  DB CHECK), defence-in-depth for Phase 2.

---

## Design suggestions (opt-in — Phase 2 prep)

The model is well-shaped; these only tidy edges the doc already anticipates.

1. **Make the last-admin invariant enforced by the DB, not just by a read-then-write guard.** M3's race
   is the one place an "absolute" invariant is only advisory. A partial approach: pessimistic lock on the
   parent row during the guard. This also future-proofs the workspace guard.
2. **Close the staff↔CLIENT symmetry now, cheaply.** L3 + L4 + the `redeem` nit are the same latent
   gap: CLIENT membership is refused *forward* everywhere but not *reverse*. A single `requireStaff`
   check on any team-seat target, plus the scope-pinned invitation FK, would make the invariant
   structural before the portal ships and CLIENT rows first exist — cheaper than retrofitting under it.
3. **Consider preview-by-POST** so the invite token never sits in a URL (L2) — small, closes a real
   log/referer exposure, and keeps the whole invite credential body-only.

---

## Test coverage

Strong and behaviour-driven. `InvitedSignupIntegrationTest` proves the invitee lands ACTIVE with no
verification email, already-registered → 409 with an unchanged roster, consumed/bogus token rejected,
endpoint reachable unauthenticated. `ProjectFlowIntegrationTest` proves the last-admin guard on both
demote and remove; `MemberManagementIntegrationTest` proves CLIENT unreachable both ways;
`ProjectAuthorizationIntegrationTest` locks the `@PreAuthorize` matrix and the problem-shape masking.
The ~200-line drop in `AuthFlowIntegrationTest` is legitimate dead-path removal (the join-request feature
is gone), not coverage weakened to green a build. **Gaps to close:** M1 (grant-map), M4 (expired token),
L5 (real service-rollback trigger).
</content>
</invoke>
