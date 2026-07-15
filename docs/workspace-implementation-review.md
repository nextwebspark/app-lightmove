# Workspace Implementation Review

**Context.** Reviews the work on `feat/workspace-creation`: the projects feature package, workspace
management (roster / settings / invitations), the cross-feature detachment seam, migration V5, and the
React shell + pages that replace the old placeholder `WorkspacePage`. Scope ~60 files / ~4k lines. Goal:
catch correctness, security, tenancy, and convention issues before it lands.

**Overall verdict: solid, ready to commit after considering the findings below.** No blocker. The
tenancy model, the authorization re-read, and the one-lead invariant are all implemented the way
`CLAUDE.md` demands. Findings are one low-probability robustness gap and a handful of nits/confirmations.

> Suites were **not** re-run for this review. The "86 backend / 35 frontend green" and "tsc + build
> clean" claims come from the build session, not verified here. Run `./mvnw test` + `npx vitest` + `tsc`
> before commit.

---

## What holds up well (spot-checked, correct)

- **Tenant isolation** — every service takes `workspaceId` from `principal.requireWorkspaceId()`
  (controllers never read it from a path/body); every repository finder is `...AndWorkspaceId`-scoped.
  `WorkspaceAccess` is the single re-read-from-DB authz gate, never trusting the JWT role claim.
- **Security fix is real** — `GET /members/pending` is now admin-gated
  ([OnboardingService.java:347-352](../apps/api/src/main/java/app/lightmove/api/workspace/service/OnboardingService.java#L347-L352)).
  Previously any active member could enumerate applicants' names/emails.
- **One-lead invariant** — `changeLead` demote → `flush()` → promote
  ([ProjectService.java:137-155](../apps/api/src/main/java/app/lightmove/api/project/service/ProjectService.java#L137-L155))
  so the `app_lm_project_member_lead_uk` partial index never sees two `LEAD` rows in one flush. Correct.
- **Cross-feature seam has no cycle** — `MemberDetachment` declared in `workspace`, implemented by
  `project/ProjectMemberDetachment`; project → workspace only. Matches the arch rule. The core→feature
  violation (`ErrorCode` importing `workspace.model.Workspace`) is removed.
- **FK `project_member.member_id → workspace_member.id`** makes a cross-tenant team physically
  impossible — the right place to enforce it.
- **Entity state guards** — `WorkspaceMember.changeRole/remove`, `Invitation.revoke`, `Workspace.delete`
  throw on illegal transitions. Good defensive layer, consistent with the "traps" philosophy.
- **Error contract** — frontend `ApiErrorCode` union + `messageFor()` mirrors the backend enum; all five
  new codes (`LAST_ADMIN`, `MEMBER_LEADS_PROJECTS`, `CLIENT_ALREADY_EXISTS`, `PROJECT_LEAD_REQUIRED`,
  `WORKSPACE_NAME_MISMATCH`) are present. DTO records ↔ TS interfaces line up field-for-field. Trigger
  function `app_lm_touch_updated_at` exists from V1. No stale refs to the deleted `WorkspacePage`/authApi.

---

## Findings

### 1. [Low — robustness] DB unique constraints surface as 500, not the clean business error the code comments promise

`GlobalExceptionHandler` has no `@ExceptionHandler(DataIntegrityViolationException.class)`, so any
constraint violation falls through to the catch-all `Exception` handler → **500 INTERNAL_ERROR**
([GlobalExceptionHandler.java:139-142](../apps/api/src/main/java/app/lightmove/api/core/error/handler/GlobalExceptionHandler.java#L139-L142)).

Three code paths lean on a unique index as "the belt behind" a pre-check, but the belt is a 500:

- `ClientService.create` — pre-checks name, comments *"the case-insensitive unique index is the belt
  behind it"* ([ClientService.java:20-56](../apps/api/src/main/java/app/lightmove/api/project/service/ClientService.java#L20-L56)).
  On a concurrent create, `app_lm_client_workspace_name_uk` fires → 500, **not** `CLIENT_ALREADY_EXISTS`.
  The frontend's "409 silently selects existing" path
  ([NewProjectModal.tsx:46-53](../apps/web/src/features/projects/components/NewProjectModal.tsx#L46-L53))
  never triggers.
- `ProjectService.addMember` — concurrent double-PUT → `app_lm_project_member_uk` → 500.
- `ProjectService.changeLead` — concurrent lead swaps on one project → `app_lm_project_member_lead_uk` → 500.

All three need concurrency, so real-world probability is low — but they log an ERROR stack trace and lie
to the client. **Fix:** add a `DataIntegrityViolationException` handler that maps by constraint name
(e.g. `app_lm_client_workspace_name_uk` → `CLIENT_ALREADY_EXISTS`, else a generic `CONFLICT`), or
translate in the service. This makes the comments true.

### 2. [Low — confirm intended] Workspace-removing a member deletes their lead seat on *done* projects, leaving those projects leaderless

`ProjectMemberDetachment.assertRemovable` only counts leads on **non-done** stages
([ProjectMemberDetachment.java:21-31](../apps/api/src/main/java/app/lightmove/api/project/service/ProjectMemberDetachment.java#L21-L31)),
but `detach` bulk-deletes **all** their seats
([`deleteByMemberId`](../apps/api/src/main/java/app/lightmove/api/project/repository/ProjectMemberRepository.java#L33-L34)).
So a member who leads only `DELIVERED`/`CLOSED` projects is removable, and those projects end up with no
`LEAD` row. The partial unique index enforces *at most* one lead, not *at least* one, so nothing breaks
and the read path handles a leaderless project fine — this is only a concern if a done mandate is meant
to always show who ran it. Likely intended; flagging to confirm.

### 3. [Nit — copy vs behavior] "Permanently removes" describes a soft delete

`WorkspaceSettingsService.delete` flips `workspace → DELETED`, frees members, revokes invitations — but
project/client/project_member rows persist (correctly unreachable, since every query is scoped to the
active workspace)
([WorkspaceSettingsService.java:59-81](../apps/api/src/main/java/app/lightmove/api/workspace/service/WorkspaceSettingsService.java#L59-L81)).
The danger-zone copy says *"Permanently removes all projects, candidates and client records"*
([SettingsGeneralPage.tsx:104-106](../apps/web/src/features/settings/pages/SettingsGeneralPage.tsx#L104-L106)).
Functionally fine; the wording overstates it. Consider softening the copy (or leave as-is — the user
never sees the retained rows).

### 4. [Nit] `NewProjectModal` 409-resolve only works if the existing client is already in local cache

The recovery finds the existing client in the `clients` prop by name
([NewProjectModal.tsx:47-51](../apps/web/src/features/projects/components/NewProjectModal.tsx#L47-L51)). If
another user created that client and this client's cache is stale, `existing` is `undefined` and the
error surfaces instead of resolving. Acceptable degradation; note it. (Also compounded by finding #1 if
it manifests as a 500 rather than the code it switches on.)

### 5. [Nit — convention] `InvitationService` import block is disordered

([InvitationService.java:1-2](../apps/api/src/main/java/app/lightmove/api/workspace/service/InvitationService.java#L1-L2))
`import ...InviteCommand;` sits directly after the package line, ahead of an ungrouped/unsorted block.
Cosmetic, but the reviewer (fable/codex) will flag it — worth a quick reorder.

### 6. [Nit] `sortProjects` "client" sort is case-sensitive

([filtering.ts:59-61](../apps/web/src/features/projects/lib/filtering.ts#L59-L61)) raw JS `<`/`>` on the raw
`clientName` orders uppercase before lowercase (`"Zeta"` before `"apple"`). Use `localeCompare` for the
string branch if mixed-case client names are expected.

---

## Follow-up (not part of this diff, but owed)

- **`CLAUDE.md` is now stale** — "Built so far: auth only" and "Projects are designed but not modelled"
  are both false after this lands. Update the intro + the Architecture tree (`project/` package now
  exists) in the same commit or a follow-up.
- Run the three verification passes before commit (see the note at the top).

## Verification to close this out

```bash
cd apps/api && ./mvnw test          # needs Docker (Testcontainers); confirms the 86 + tenancy suites
cd apps/web && npx vitest run       # 35 tests
cd apps/web && npx tsc --noEmit      # contract drift between DTOs and TS types
npm run dev                          # manual pass vs claude-design mockups, light + dark (not yet done)
```
