# RBAC & Authorization Architecture — Current State

> **Purpose of this document.** You want to refactor the authorization model and eventually add
> Postgres Row-Level Security (RLS). Before that, you want the Spring method-level security
> (`@PreAuthorize`) layer to be bulletproof. This document maps *exactly* what exists today — the
> user tiers, how permissions are computed, where the gates are, and every loophole worth closing —
> so the refactor starts from ground truth, not from the README's aspirations.

---

## 1. The mental model (what LightMove's authorization actually is)

The system is **action-based RBAC over two nested scopes**. It never asks "what role is this user?"
It asks "may this member perform this *action*?" A member's permissions are the **union of the
actions granted to the roles they hold**. Role *names* only surface where the role itself is the
subject of a rule (the last-admin invariant, and the inert `CLIENT` exclusion).

Two scopes, nested:

```
Workspace (the tenant)
├── WorkspaceMember  → holds a SET of WORKSPACE roles  {ADMIN | MEMBER | CLIENT}
└── Project (a search mandate)
    └── ProjectMember (a "seat") → holds a SET of PROJECT roles {ADMIN | LEAD | RESEARCHER | CLIENT}
```

Key facts that shape everything:

- **A user has at most one ACTIVE workspace membership.** Enforced by a partial unique index on
  `app_lm_workspace_member (user_id) WHERE status = 'ACTIVE'`. Note it constrains `user_id`, not
  `workspace_id` — a workspace holds many members.
- **A project seat references the *membership* (`member_id` → `app_lm_workspace_member.id`), not the
  user.** So a project team physically cannot contain someone from another workspace.
- **Roles are held as sets, permissions are the union.** A project creator starts as `{ADMIN, LEAD}`
  — the same person is both admin and lead, exactly as you described.
- **Tenant isolation is application-enforced only.** Every query carries `workspace_id` from the
  signed token. There is no database-level isolation yet (this is the RLS gap — see §7).

This matches your description: workspace has ADMIN/MEMBER (and CLIENT groundwork); anyone with
`PROJECT_CREATE` (ADMIN or MEMBER) can create a project; inside a project there are ADMIN/LEAD/
RESEARCHER; everyone can do the day-to-day work (`WORK_EXECUTE`); locking/unlocking the position
brief ("locking the universe") is gated to ADMIN via `POSITION_UNLOCK`.

---

## 2. The user / role taxonomy

### Workspace roles (`WorkspaceRole` enum + `app_lm_role` seed, scope=WORKSPACE)

| Role | Actions granted (the union) | Meaning |
|---|---|---|
| **ADMIN** | `WORKSPACE_MANAGE`, `MEMBER_MANAGE`, `MEMBER_INVITE`, `PROJECT_CREATE`, `PROJECT_BROWSE`, `CLIENT_RECORD_MANAGE` | Governance: settings, billing, membership. **Implicit project-admin everywhere** (bypasses project gates). |
| **MEMBER** | `PROJECT_CREATE`, `PROJECT_BROWSE`, `CLIENT_RECORD_MANAGE` | Staff. Creates projects (becoming their project ADMIN+LEAD), works the ones they are seated on. |
| **CLIENT** | *(none — seeded with zero actions)* | Hiring-company contact. Groundwork only. **Currently inert — no path mints it, no action grants it.** |

### Project roles (`ProjectRole` enum + `app_lm_role` seed, scope=PROJECT)

| Role | Actions granted (the union) | Meaning |
|---|---|---|
| **ADMIN** | `PROJECT_EDIT`, `TEAM_MANAGE`, `WORK_EXECUTE`, `POSITION_UNLOCK` | Owns the mandate: team, roles, archival, and the unlock decision. Creator holds it from the start. |
| **LEAD** | `PROJECT_EDIT`, `WORK_EXECUTE` | Runs the search day-to-day. A project may have several. |
| **RESEARCHER** | `WORK_EXECUTE` | Executes: sourcing, triage, candidates, notes. |
| **CLIENT** | *(none — seeded with zero actions)* | Hiring-company seat. Groundwork only, inert. |

### Actions (the permission atoms)

**Workspace-scope** (`WorkspaceAction`): `WORKSPACE_MANAGE`, `MEMBER_MANAGE`, `MEMBER_INVITE`,
`PROJECT_CREATE`, `PROJECT_BROWSE`, `CLIENT_RECORD_MANAGE`.

**Project-scope** (`ProjectAction`): `PROJECT_EDIT`, `TEAM_MANAGE`, `WORK_EXECUTE`, `POSITION_UNLOCK`.

`WORK_EXECUTE` is held by **every** project role — it is the gate for reading team-only project
content (strategy, position brief, sourcing). `POSITION_UNLOCK` is deliberately *not* folded into
`PROJECT_EDIT`: reopening a locked brief is an ADMIN-only decision because the locked brief is the
scoring benchmark.

---

## 3. How a permission decision is made (the runtime path)

Everything is **data-driven and re-read from the DB on every request**. The JWT carries roles too,
but that claim is treated as up to 15 minutes stale and is **never** trusted for a real decision.

```
HTTP request
  │
  ▼
JwtPrincipalConverter.convert(Jwt)                  core/security/jwt
  │   builds AuthPrincipal from SIGNED claims:
  │     subject → userId, "wsId" → workspaceId, "email", "emailVerified", "roles"
  │   roles here = "coarse route material only" (ROLE_ADMIN / ROLE_MEMBER authorities + SCOPE_VERIFIED)
  ▼
SecurityFilterChain (apiChain, @Order(3))           core/security/config/SecurityConfig
  │   /api/v1/**  → .access(verified)  (authenticated AND SCOPE_VERIFIED)
  ▼
Controller method with @PreAuthorize                @EnableMethodSecurity on SecurityConfig
  │   e.g.  @PreAuthorize("@projectAuth.can(principal, #projectId, 'PROJECT_EDIT')")
  ▼
Guard bean: WorkspaceAuth.can(...) / ProjectAuth.can(...)   core/security/rbac
  │   ALWAYS returns true to SpEL; ENFORCES by THROWING ApiException on denial
  ▼
WorkspaceAccess / ProjectAccess.requireAction(...)  core/security/rbac
  │   1. re-read membership row from DB     (NOT the JWT)
  │   2. compute action union via JPQL:  member → roles → actions
  │   3. throw ApiException if the action is absent
  ▼
Controller body runs only if no exception was thrown
```

### The two guard beans (`@PreAuthorize` targets)

`WorkspaceAuth` (bean `workspaceAuth`) and `ProjectAuth` (bean `projectAuth`). Their contract is the
single most important design decision to understand:

```java
// WorkspaceAuth
public boolean can(AuthPrincipal principal, String action) {
    access.requireAction(principal.userId(), principal.requireWorkspaceId(),
            WorkspaceAction.valueOf(action));
    return true;                       // ← only reachable if requireAction did NOT throw
}

// ProjectAuth
public boolean can(AuthPrincipal principal, UUID projectId, String action) {
    access.requireAction(principal.userId(), principal.requireWorkspaceId(), projectId,
            ProjectAction.valueOf(action));
    return true;
}
```

**They never return `false`.** They return `true` or throw. Why: returning `false` collapses every
denial into a generic Spring 403 with no code. Throwing `ApiException` preserves the precise error
code and the **404-masking** (a non-member gets `NOT_A_MEMBER` → HTTP 404 "Workspace not found", so
probing an id reveals nothing). Spring rethrows the runtime exception unwrapped from the SpEL bean
call, and it lands in `GlobalExceptionHandler`.

Because the action string is resolved through `WorkspaceAction.valueOf` / `ProjectAction.valueOf`, a
typo in an annotation fails the very first request loudly instead of silently granting nothing.

### The permission computation (the union)

The "union of role actions" is a JPQL projection run **outside any transaction** (it executes inside
`@PreAuthorize`, before the controller):

```java
// WorkspaceMemberRepository
@Query("select a.name from WorkspaceMember m join m.roles r join r.actions a where m.id = :memberId")
Set<String> findActionNames(UUID memberId);
```

`ProjectMemberRepository.findActionNames(seatId)` mirrors it for a project seat.

### `WorkspaceAccess.requireAction` — workspace tier

```java
public WorkspaceMember requireAction(UUID userId, UUID workspaceId, WorkspaceAction action) {
    WorkspaceMember member = requireActiveMember(userId, workspaceId);        // 404-masks non-members
    if (!members.findActionNames(member.getId()).contains(action.name())) {
        throw new ApiException(ErrorCode.FORBIDDEN, "Requires the " + action.name() + " action");
    }
    return member;
}
```

### `ProjectAccess.requireAction` — project tier (the 5-rung ladder)

Order matters, and is deliberate:

```java
public void requireAction(UUID userId, UUID workspaceId, UUID projectId, ProjectAction action) {
    WorkspaceMember member = workspaceAccess.requireActiveMember(userId, workspaceId);   // 1

    projects.findByIdAndWorkspaceId(projectId, workspaceId)                              // 2
            .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));                    //   scoped to THIS tenant, BEFORE the admin bypass

    if (workspaceAccess.isAdmin(member)) {                                               // 3
        return;                                                                          //   workspace-ADMIN implicit project-admin
    }

    ProjectMember seat = seats.findByProjectIdAndMemberId(projectId, member.getId())     // 4
            .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "Not on this project's team"));

    if (!seats.findActionNames(seat.getId()).contains(action.name())) {                  // 5
        throw new ApiException(ErrorCode.FORBIDDEN, "Requires the " + action.name() + " action");
    }
}
```

Rung 2 runs **before** rung 3 on purpose: a workspace admin must never authorize against a
foreign-tenant project. The admin bypass exists so a departed mandate owner can't strand a search.

---

## 4. The full authorization surface (every `@PreAuthorize` today)

28 annotations, 8 controllers, method security enabled by `@EnableMethodSecurity` on `SecurityConfig`.
Every method reads the workspace from the **principal**, never the path.

### Workspace tier (`@workspaceAuth`)

| Controller | Endpoint | Gate |
|---|---|---|
| `InvitationsController` | `GET /invitations`, `POST /invitations`, `POST /{id}/resend`, `DELETE /{id}` | `can(principal, 'MEMBER_INVITE')` |
| `MembersController` | `GET /members` | `staff(principal)` |
| `MembersController` | `PATCH /members/{id}`, `DELETE /members/{id}` | `can(principal, 'MEMBER_MANAGE')` |
| `WorkspaceController` | `GET /workspace` | `staff(principal)` |
| `WorkspaceController` | `PATCH /workspace`, `DELETE /workspace` | `can(principal, 'WORKSPACE_MANAGE')` |
| `ClientsController` | `GET /clients`, `POST /clients` | `can(principal, 'CLIENT_RECORD_MANAGE')` |
| `CompanyReferenceController` | `GET /companies/sectors`, `/suggestions`, `/estimate` | `can(principal, 'PROJECT_BROWSE')` |
| `ProjectsController` | `GET /projects` | `can(principal, 'PROJECT_BROWSE')` |
| `ProjectsController` | `POST /projects` | `can(principal, 'PROJECT_CREATE')` |

### Project tier (`@projectAuth`, takes `#projectId`)

| Controller | Endpoint | Gate |
|---|---|---|
| `ProjectsController` | `PATCH /projects/{projectId}` | `can(principal, #projectId, 'PROJECT_EDIT')` |
| `ProjectsController` | `PUT/DELETE /projects/{projectId}/members/{memberId}` | `can(principal, #projectId, 'TEAM_MANAGE')` |
| `StrategyController` | `GET /projects/{projectId}/strategy` | `can(principal, #projectId, 'WORK_EXECUTE')` |
| `StrategyController` | `PUT .../sectors`, `/company-size`, `/geography`, `/ownership` | `can(principal, #projectId, 'PROJECT_EDIT')` |
| `SourcingController` | `GET /projects/{projectId}/sourcing` | `can(principal, #projectId, 'WORK_EXECUTE')` |
| `PositionController` | `GET /projects/{projectId}/position` | `can(principal, #projectId, 'WORK_EXECUTE')` |
| `PositionController` | `PUT /position`, `/criteria`, `/competencies`, `POST /lock` | `can(principal, #projectId, 'PROJECT_EDIT')` |
| `PositionController` | `POST /unlock` | `can(principal, #projectId, 'POSITION_UNLOCK')` |

**Read vs write pattern:** project *content reads* (`GET strategy/sourcing/position`) gate on
`WORK_EXECUTE` (any team member); *writes* gate on `PROJECT_EDIT` (ADMIN or LEAD); the *unlock* gates
on `POSITION_UNLOCK` (ADMIN only). This is the "anyone can update, only lead/admin locks the
universe" rule, expressed as actions.

---

## 5. Where authorization is NOT a `@PreAuthorize` (imperative checks)

Three controllers carry **no** method-security annotations. This is intentional but is the fragile
part of the surface — there is no annotation backstop, so the check lives in a service and can be
forgotten when a new method is added.

- **`OnboardingController`** (`/api/v1/onboarding`) — the only authenticated area a user with *no
  workspace* can reach. Every method calls `CurrentUser.require()` and derives the workspace from the
  principal. Writes are gated imperatively downstream: `OnboardingService.requireAdmin(...)` and
  `InvitationService` do their own `access.requireAdmin(...)`.

- **`AuthController`** (`/api/v1/auth`) — auth entry points, governed by filter-chain request
  matchers, not method security. `/me` exposes only the caller's own data.

- **`PendingOnboardingMaterialiser`** — a `@Component` event listener, **not** a controller. It runs
  outside any request's SecurityContext (fires on `EmailVerifiedEvent`), so method security cannot
  apply. It **synthesizes an admin principal** to flush held invitations:

  ```java
  AuthPrincipal principal = new AuthPrincipal(
          event.userId(), null, workspace.getId(), Set.of(WorkspaceRole.ADMIN), true);
  invitations.invite(principal, commands, event.request());
  ```

  This is the **one place an `AuthPrincipal` is built from something other than a signed token.** It's
  safe only because the event fires solely for a genuinely-verified user who is the workspace's own
  creator/admin — but it is a trust boundary worth keeping in view during the refactor.

**Why these can't just adopt `@PreAuthorize`:** `InvitationService` is called both from a controller
(real request) *and* from the materialiser (synthetic principal, no SecurityContext). Method security
would evaluate the wrong (anonymous) authentication in the second case. So the service keeps its own
imperative `access.requireAdmin(...)`.

Also note: `MemberService` re-reads the target row (`access.requireActiveMemberRow(memberId,
workspaceId)`) *even though* the controller already has `@PreAuthorize` — object-level scoping so a
member id from another workspace resolves to nothing (a foreign id → 404, not 403).

---

## 6. What makes the current design strong (keep these in the refactor)

1. **Actions, not roles, at the gate.** Adding a capability is an INSERT into `app_lm_role_action` +
   an enum constant — no controller edits, no redeploy to re-grant.
2. **DB is the source of truth, re-read every request.** The 15-minute-stale JWT roles claim never
   makes a real decision. Role changes take effect immediately, not after token expiry.
3. **404-masking is uniform.** Non-members and foreign-tenant ids get `NOT_FOUND`, never a 403 that
   would confirm existence. Enforced by the throw-don't-return-false contract.
4. **Scope is pinned in the schema.** Composite FK `(role_id, role_scope)` + a CHECK make attaching a
   PROJECT role to a workspace membership physically unrepresentable.
5. **Catalog drift is a red build.** `RbacCatalogTest` fails if the enums and the SQL seeds diverge,
   including the exact per-role grant map.
6. **Tenant id comes only from the signed token.** `AuthPrincipal.requireWorkspaceId()` is the single
   supported accessor and throws (→ 404) rather than letting a null reach a WHERE clause.

---

## 7. Loopholes & gaps to close before/with the refactor

Ordered roughly by importance for a "bulletproof method security then RLS" goal.

### 7.1 No database-level tenant isolation (the RLS gap) — biggest structural hole

Tenant isolation is **100% application code**. Every repository finder carries `workspace_id`, and the
class comments beg "an unscoped lookup on tenant data must not exist" — but nothing *forces* it. One
forgotten `findById`, one hand-written JPQL without the workspace clause, one native query, and a
tenant boundary is silently crossed. There is no `ENABLE ROW LEVEL SECURITY`, no policy, no
`workspace_id` session GUC anywhere (confirmed by grep; `harden.sql` is coarse role-level grants, not
RLS).

**For the RLS rollout, note the shape problem:** `app_lm_project_member` (the seat table) has **no
`workspace_id` column** — tenancy rides indirectly through `member_id → app_lm_workspace_member`. An
RLS policy there needs a join/subquery, or you denormalize `workspace_id` onto the seat table. Decide
this early; it affects the migration.

Plan direction: set `app.workspace_id` per connection (`SET LOCAL` in a transaction-bound
interceptor, sourced from `AuthPrincipal.requireWorkspaceId()`), then `CREATE POLICY` on
`app_lm_project`, `app_lm_client`, `app_lm_workspace_member`, `app_lm_position*`, `app_lm_strategy*`,
etc. RLS becomes defense-in-depth *under* the existing app checks, not a replacement.

### 7.2 Imperative checks have no annotation backstop (§5)

`OnboardingController` and `InvitationService` enforce authorization only in service code. If someone
adds a new onboarding mutation and forgets the downstream `requireAdmin`, nothing catches it — no
`@PreAuthorize` on the controller, no test forcing one. **Mitigation options:** a deny-by-default
`@PreAuthorize("denyAll()")` baseline on controllers with explicit opt-outs, or an ArchUnit/test that
asserts every controller mutation either has `@PreAuthorize` or is on an allowlist.

### 7.3 The synthetic ADMIN principal is an unsigned trust boundary (§5)

`PendingOnboardingMaterialiser` builds an `AuthPrincipal` with `{ADMIN}` from an event, bypassing all
role checks by construction. Correct today, but it is the one principal not derived from a signed
token. Any refactor that broadens what the materialiser (or any event listener) can do needs to
re-examine this. Consider a distinct marker type for "system principal" so it can never be confused
with a request principal, and so guard beans can log/audit its use.

### 7.4 Permission queries run outside a transaction, un-cached, per request

`findActionNames` executes on every gated call, no transaction, no caching. Correct (fresh reads), but
each protected request does 2–4 extra queries (member row, project row, action union, seat union).
Not a security hole — a performance and N+1 consideration for the refactor. If you cache, cache with a
very short TTL and invalidate on role change, or you reintroduce the staleness the design fought to
avoid.

### 7.5 `CLIENT` role is inert but present — dead surface

Both `CLIENT` roles are seeded with zero actions and no path mints them; `requireStaff` and
`InvitationService` actively refuse them. Harmless now, but it is unexercised authorization surface.
When the client portal ships, `staff(principal)` vs `can(principal, ...)` gating needs a deliberate
review — today `staff()` is the only thing standing between a (hypothetical) CLIENT and staff reads.

### 7.6 JWT legacy-role tolerance is a small soft spot

`JwtPrincipalConverter.roles()` accepts a legacy single `role` claim for tokens minted before RBAC.
It's bounded (dies with the 15-minute TTL) and only feeds coarse authorities, but it's a compatibility
branch that should be **deleted on a fixed date** so it can't linger as an accepted claim shape.

### 7.7 The `staff()` vs `can()` split is subtle

`GET /workspace` and `GET /members` gate on `staff(principal)` (active, non-CLIENT) rather than a
named action. This is a second, parallel gating style next to action checks. Not wrong, but two
mechanisms are harder to audit than one. Consider modeling "staff read" as an actual action
(e.g. `WORKSPACE_VIEW` / `MEMBER_VIEW`) so the entire surface is uniformly action-based — this would
also make the eventual CLIENT portal gating explicit rather than implicit in `requireStaff`.

---

## 8. Key files (where to work during the refactor)

| Concern | Path |
|---|---|
| Role/Action enums | `core/security/rbac/{WorkspaceRole,ProjectRole,WorkspaceAction,ProjectAction,RoleScope}.java` |
| Catalog entities | `core/security/rbac/{Role,Action}.java` + `{RoleRepository,ActionRepository}.java` |
| Permission math | `WorkspaceMemberRepository.findActionNames`, `ProjectMemberRepository.findActionNames` |
| Access services | `core/security/rbac/{WorkspaceAccess,ProjectAccess,RbacService}.java` |
| Guard beans (`@PreAuthorize`) | `core/security/rbac/{WorkspaceAuth,ProjectAuth}.java` |
| Principal + JWT | `core/security/model/AuthPrincipal.java`, `core/security/jwt/JwtPrincipalConverter.java` |
| Filter chains + method security | `core/security/config/SecurityConfig.java` (`@EnableMethodSecurity`) |
| Imperative-check services | `workspace/service/{InvitationService,OnboardingService,MemberService,PendingOnboardingMaterialiser}.java` |
| Last-admin invariants | `MemberService.requireAnotherAdmin`, `ProjectService.requireAnotherProjectAdmin` |
| Schema seeds | `db/migration/V6__invite_only_and_rbac.sql` (+ `V7__position.sql`) |
| DB hardening (not RLS) | `ops/cloudsql/harden.sql` |
| Anti-drift gate | `test/.../rbac/RbacCatalogTest.java` |
| Auth integration tests | `test/.../project/{ProjectAuthorizationIntegrationTest,ProjectFlowIntegrationTest}.java` |

---

## 9. Invariants enforced outside the action model (don't lose these)

- **Last workspace admin** — `MemberService.requireAnotherAdmin` blocks removing/demoting the final
  workspace ADMIN (`LAST_ADMIN`, HTTP 409).
- **Last project admin** — `ProjectService.requireAnotherProjectAdmin` blocks removing the final
  project ADMIN seat (`PROJECT_LAST_ADMIN`, HTTP 409).
- **One active workspace per user** — partial unique index (§1).
- **Cross-project sole-admin guard** — `countSoleAdminSeatsExcludingStages` prevents removing a
  workspace member who is the sole ADMIN of a live mandate.

These are stateful invariants that need loaded rows, so they stay imperative even in a fully
action-based world. Any refactor must preserve them.
