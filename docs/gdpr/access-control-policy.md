# Access control policy

_Draft for counsel, 2026-11-19, unreviewed. Requested by Alac Partners (gap analysis §5b, item 5: roles, permissions,
MFA, separation of client workspaces; internal, ISO 27001 style). Facts are read from the code and the repository
documents named in each row. **[Owner to confirm]** marks what the repository cannot tell; "not built" marks a control
that does not exist._

## 1. Purpose and scope

Who may reach what in Uncava: customers' users inside a workspace, Uncava staff in the application, and Uncava staff
in the infrastructure. It sits under [information-security-policy.md](information-security-policy.md). Policy owner:
[Owner to confirm].

## 2. Principles

| Principle | Mechanism |
|---|---|
| Identity is a work email; membership is by invitation only | The one second door: a staff member naming a client representative. A domain does not own a workspace. |
| Authorise by action, never by role | `@PreAuthorize` over actions with `@workspaceAuthorizer` and `@projectAuthorizer`; the guards re-read the database on every check; the JWT `roles` claim is never trusted for a decision (it can be up to 15 minutes stale). |
| A session is in exactly one workspace | The `wsId` claim; changed only by `POST /auth/switch-workspace` (404 unless an active member) or a web refresh whose membership has ended (audited `WORKSPACE_SWITCHED`, reason `MEMBERSHIP_ENDED`). |
| Fail closed and vague | A miss on a protected surface is a 404; password-login failures give one sentence and one timing. |

## 3. Workspace roles

A member holds a **set** of roles. A member holding `CLIENT` beside a staff role is staff; only a pure client is kept
off staff surfaces. A workspace always keeps at least one `ADMIN` (`LAST_ADMIN`).

| Role | Who | What it is for (examples of actions it carries) |
|---|---|---|
| `ADMIN` | firm administrators | `WORKSPACE_MANAGE`, `MEMBER_MANAGE`, `MEMBER_INVITE`, `PROJECT_CREATE`, `PROJECT_BROWSE`, `CLIENT_RECORD_MANAGE`, `CANDIDATE_POOL_MANAGE`, `PERSON_ERASE`, `PERSON_SUBJECT_ACCESS` (suppression list and retention settings ride `WORKSPACE_MANAGE`); a standing bypass on project seats |
| `MEMBER` | consultants, researchers | `CANDIDATE_POOL_MANAGE` (the people pool), `CLIENT_RECORD_MANAGE` (the client registry), `PROJECT_CREATE`, `PROJECT_BROWSE`; no member or workspace management |
| `CLIENT` | a hiring-company representative | read-only, only on the positions they are seated on |

Actions that data subjects' rights depend on ([use-cases.md](use-cases.md) §1): restriction needs
`CANDIDATE_POOL_MANAGE` (Admin and Member); export, erasure and retention deletes need `PERSON_SUBJECT_ACCESS` and
`PERSON_ERASE` (Admin only). A client representative can do none of them.

## 4. Project seats

Each seat holds one project role (a seat holds no more than one staff role); a position always has at least one `LEAD`.

| Seat | Actions |
|---|---|
| `LEAD` | owns the position; `WORK_VIEW`, `WORK_EXECUTE`, `PROJECT_EDIT`, `TEAM_MANAGE`, `CLIENT_ACCESS_MANAGE` |
| `RESEARCHER` | `WORK_VIEW`, `WORK_EXECUTE`; no `CLIENT_ACCESS_MANAGE` |
| `CLIENT` | `WORK_VIEW` only |

A position's content (strategy, brief, candidates) is seat-gated on `WORK_VIEW` / `WORK_EXECUTE`, not on the
workspace's `PROJECT_BROWSE`. Notes, documents, tasks, assessments, AI readings and contacts are staff-only and never
appear on `CandidateResponse`, which a client seat reads.

## 5. Client access: two tiers, two decisions

| Tier | Decision | Action | Held by |
|---|---|---|---|
| Registry | create a client record and name a representative on it (shows that person nothing) | `CLIENT_RECORD_MANAGE` | `ADMIN`, `MEMBER` |
| Mandate | give a representative a position (map, create-and-attach, detach) | `CLIENT_ACCESS_MANAGE` | `LEAD` only, plus the standing workspace-admin bypass |

Deliberately not part of `PROJECT_EDIT`: admitting an outsider to a search and changing its target date are different
decisions. Consent to share a person with a client is a separate, per-position control (`consent-requests-1267.md`).

## 6. Authentication, sessions and MFA

| Topic | Rule today | Source |
|---|---|---|
| Password | BCrypt strength 12; lockout after 5 failed attempts for 15 minutes, one email when it arms; the counter clears only on a successful login or reset; login rate limit 10 a minute by default | `application.yml` |
| Sign-in providers | Google and LinkedIn, each a yml block; no branch on a provider name | CLAUDE.md |
| Access token | 15 minutes, held in browser memory only | `application.yml` |
| Refresh token | 30 days, httpOnly, Secure, SameSite=Strict, host-only cookie; rotates on every use; a replay revokes the family (`TOKEN_REUSE_DETECTED`); the SPA and API share one origin | `application.yml`; `lightmove-domain` skill |
| Extension | its own refresh family, 14 days, revocable from Settings; stays in the workspace it was paired into | `application.yml` |
| One-time links | verification 24 hours; password reset 30 minutes; invitation 7 days | `application.yml` |
| MFA | TOTP and recovery codes, belonging to the person, asked at every sign-in door (password, reset, verification link, OAuth); a wrong code counts as a wrong password; turning it on needs the current password; secret sealed with Tink. **Behind `MFA_ENABLED` (default false)**; a person who already has a factor is asked whatever the switch says. Whether it is on in production: [Owner to confirm] | `lightmove-domain` skill |
| What MFA does not have | no per-workspace enforcement, no SAML/OIDC SSO, no SCIM (gap G6; `data-protection-plan.md` items 2.1 and 2.7) | |
| Account status | checked at login and at refresh; a suspended user keeps a working access token for up to 15 minutes; nothing sets that status today | `lightmove-domain` skill |
| Public API keys | reach only `/api/v1/public/**` (and `/api/v1/mcp` with `mcp:use`); read-only; never more than scopes ∩ the owner's live permissions, re-read every call; default life 90 days, maximum 365 | `application.yml` |
| AI apps over MCP | their own signing key and audience; access token 1 hour, refresh 30 days; no token is forwarded to another service | `application.yml`; CLAUDE.md |

## 7. Separation of customer workspaces

| Layer | Control |
|---|---|
| Application | every workspace-scoped query filters by `AuthPrincipal.requireWorkspaceId()`, never a request parameter |
| Database | forced row-level security on 79 tenant tables (V184, V190, V192); every pooled connection carries the caller's workspace; with none set it sees no rows; `TenantTableGuard` refuses a boot with an unprotected tenant table, a superuser or `BYPASSRLS` runtime role, or a pooled connection left in maintenance mode |
| Jobs and doors with no user | a job, webhook or worker names its workspace (`WorkspaceScope`); a door that knows only a key resolves it first through V189's `TenantResolvers` |
| Deliberately outside | `app_lm_workspace_member` and `app_lm_refresh_token` (read before a workspace is chosen); vendor caches and the company universe, which are not tenant data |
| What RLS does not stop | it is a backstop against application bugs, not against SQL injection or a database login: `lm_app` still owns the tables, and any SQL session can set `app.workspace_id` or `app.rls_maintenance = 'on'` until #256 moves ownership (`row-level-security.md` §3) |

## 8. Uncava staff

| Access | Rule today | Source |
|---|---|---|
| `SUPER_ADMIN` platform role | gates `/api/v1/platform/**` only (the position-template library) and opens nothing inside a workspace; granted only by ops script (`ops/cloudsql/grant-platform-role.sh`), never by an endpoint; never in the JWT, re-read on every check; a miss is a 404. Current holders: [Owner to confirm] | `lightmove-domain` skill |
| Production database | **Gap G1.** `ops/cloudsql/grant-db-user.sh` gives any Google account, a consumer one included, read or write on `app_lm_*` under its own identity, with no approval step and outside the application's audit trail. With row-level security a plain session sees no tenant rows until it sets `app.workspace_id` or `app.rls_maintenance`, which any holder can do. Who holds a grant today: [Owner to confirm] | `data-protection-plan.md` G1 |
| Cloud administration | IAM on the Google Cloud project; holders [Owner to confirm] | |
| Audit of that access | no Data Access audit logs on Cloud SQL, Cloud Storage or Secret Manager; only Admin Activity logs. Cross-tenant SQL for a candidate request is run by [named person] with a second person's approval, written in the request log (`subject-requests-procedure.md` §4) | `breach-runbook.md` §2 |
| Target (not built) | no standing human access; named person, reason, time-boxed IAM grant with second-person approval; Data Access audit logs with an alert on human principals; then customer-approved support access (`data-protection-plan.md` 1.1, 1.2, 3.3) | |

## 9. Joiners, movers, leavers and review

| Item | Position |
|---|---|
| Customer member removed | membership ends through the roster; their API keys and AI-app connections end with it; whether open sessions end at once is [Owner to confirm]; the membership row is kept for a rejoin |
| Customer account deleted by its owner | all memberships end, sessions, sign-ins and second factor are deleted ([retention-schedule.md](retention-schedule.md)) |
| Uncava staff joiner, mover, leaver (cloud IAM, GitHub, database grants, platform role) | no written checklist: [Owner to confirm] |
| Review of platform roles and database grants | [Owner to confirm: quarterly]; no record exists. A query to run: `SELECT` on `app_lm_user_platform_role`; `gcloud projects get-iam-policy hak-talent-mapping` |
| Review of customers' own admins | each workspace's admins see their roster; no Uncava-run review |
| Where access events are recorded | `app_lm_audit_event` (sign-in, session, role changes `MEMBER_ROLE_CHANGED`, `API_KEY_CREATED`, `OAUTH_GRANT_CREATED`, `PLATFORM_ROLE_GRANTED`, exports); read in Settings → Security → Audit log |

## 10. Gaps

1. Standing human access to every tenant's data (G1); no break-glass, no approval, no time limit.
2. No Data Access audit logs; no alert on any audit event; alerts go to personal inboxes.
3. Row-level security does not bind anyone who can run SQL as `lm_app` or set the maintenance flag (#256).
4. MFA behind a flag, no per-workspace enforcement, no SSO or SCIM.
5. No suspension surface: account status is not checked per request.
6. No staff joiner/mover/leaver checklist and no access review cadence or record.
7. Holders of `SUPER_ADMIN`, database grants and cloud IAM are not listed in the repository.
8. No customer-visible record of Uncava staff access to a workspace (G11).
