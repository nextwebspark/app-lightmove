# Role Model Proposal — Workspace Roles, Project Roles, and Client Access

**Status: Phase 1 implemented (2026-07-16).** The staff tiers shipped with four decisions that
supersede parts of the proposal below:

1. **Membership is invitation-only.** The join-request path (§2's approval queue, the signup
   domain listing) was removed outright — signup always creates a workspace; an admin naming you
   is the only way into an existing one.
2. **RBAC is DB-driven and multi-role.** Roles and actions are catalog tables (`app_lm_role`,
   `app_lm_action`, `app_lm_role_action`, seeded in V6); a membership or project seat holds a
   *set* of roles and its permissions are the union of their actions. Adding a role or action is
   an INSERT migration plus an enum constant (`RbacCatalogTest` keeps them aligned). All RBAC code
   lives in `core/security/rbac`; controllers declare authorisation with `@PreAuthorize` over
   actions, and the guard beans re-read the database on every check.
3. **Leads are plural.** No one-lead index; a seat may hold several roles at once, and the project
   creator is seeded `{ADMIN, LEAD}` — literally admin and lead both. The invariant is ≥1 ADMIN
   seat per project (the workspace last-admin rule, mirrored).
4. **Client is groundwork only.** CLIENT roles are seeded granting no actions, invitations carry a
   `project_id` column with the client⇔project CHECK, and every staff path refuses to mint CLIENT.
   The portal itself is Phase 2.
5. **Onboarding UX (follow-up, 2026-07-17).** Signup no longer asks for a self-role — the creator is
   always ADMIN, so the wizard's "Your role" field is gone. And an invited user skips email
   verification: they set a password on the accept screen (`POST /onboarding/accept-invitation-signup`)
   and land ACTIVE at once, because clicking the invite link already proved their mailbox.

The research and the client-access design below remain the reference for Phase 2.

**Date: 2026-07-16**

---

## 1. Why this document

We need to decide how authority is split between the **workspace** (the tenant — the search
firm) and the **project** (a single search mandate), and how an external **client contact**
(a person at the hiring company) gets a controlled window into a project.

The requirements as stated:

- Workspace level: a user is **Admin or Member**.
- Project level: **Admin, Lead, Researcher** — and **Client**.
- Every internal role can execute a search mandate.
- Whoever starts a project is its Admin; project Admins appoint the Lead, Researchers, and
  the Client.
- The Client mostly views, but can **change candidate status** and **write notes** for the
  researchers.

This document reconciles those requirements with what the codebase already has, checks the
design against how comparable products solve the same problem, and proposes a concrete
target model plus the path to it.

---

## 2. Where the codebase is today

The exploration turned up an important fact: **a two-tier role model already exists in the
schema — it just isn't enforced.**

### Workspace tier (exists, half-used)

- `WorkspaceRole` = `ADMIN | CONSULTANT | RESEARCHER` (`workspace/constant/WorkspaceRole.java`).
- **Only `ADMIN` is ever checked.** `WorkspaceAccess.requireAdmin(...)` gates settings,
  membership, invitations, and approvals. `CONSULTANT` and `RESEARCHER` are never
  distinguished anywhere — a workspace RESEARCHER can today create projects, reassign leads,
  and create clients exactly like a CONSULTANT. The distinction is decorative.
- The JWT carries `wsId` + `role` claims, and `JwtPrincipalConverter` mints `ROLE_*`
  authorities — which nothing consumes. Authorization is imperative, in services, via
  `WorkspaceAccess` re-reading the DB (deliberate: the claim can be 15 minutes stale).

### Project tier (exists, data-only)

- V5 already created `app_lm_project` and `app_lm_project_member` with
  `ProjectRole = LEAD | MEMBER`, referencing `app_lm_workspace_member.id` (so a project team
  can never contain another workspace's member), and a partial unique index enforcing
  **exactly one LEAD per project**.
- The migration comment already states the philosophy: *"project role (LEAD/MEMBER) is
  orthogonal to the workspace role — a workspace RESEARCHER may lead a project."*
- But project role is **data, not authorization**: any active workspace member can create
  projects, edit any project, and add/remove any team member. The only enforced rule is
  "you can't remove the lead."

### Client (exists only as a company record)

- `app_lm_client` is the hiring **company entity** — a registry record projects inherit
  from. There is **no client user**: no portal, no client login, no client-visible anything.
  Candidate notes in the mockups are explicitly labelled *"Private notes"*.

### The UI already promises this model

Three separate screens in `claude-design/` say **"roles apply per project"** (Signup step 3,
Settings → Members, Workspace team view). The copy has been ahead of the implementation the
whole time. There is, however, **no client-portal mockup at all** — that surface has to be
designed before it can be built.

### Constraints we inherit

- **One active workspace per user**, enforced by a partial unique index on
  `workspace_member(user_id) WHERE status = 'ACTIVE'`. The JWT carries a single `wsId`;
  `activeMembership()` assumes at most one row. This constraint runs deep.
- Signup blocks consumer email domains; **invitations already accept any work domain**
  (deliberately — firms invite external contractors), so inviting a person at the client
  company is not blocked by domain rules.
- The signup "Your role" field (Partner / Consultant / Researcher / Operations) is a **job
  title on the user**, not an authorization role. That separation is already correct — keep it.

---

## 3. What comparable products do

Two research passes: executive-search software specifically, and general collaboration SaaS.

### Executive search / retained recruiting products

| Product | Internal roles | Client access |
|---|---|---|
| **Clockwork** | Admin, Partner, Recruiter, Researcher, External Recruiter — global roles, but everyone except Admin only sees projects they're invited to | Distinct client user class, invited **per project**, unlimited free client seats. Clients see dashboard, long list, strategy; give **thumbs up/down + comments**; add project notes. **Statuses stay firm-controlled.** Per-candidate and per-note visibility toggles; notes hidden from clients unless individually shared. "View as client" preview. |
| **Thrive TRM** | Role-based privileges (names not public) | Branded portal: stages and rankings at a glance, thumbs up/down, comments, mark-as-priority. Recruiters control what is shared; unshared candidates invisible. |
| **Invenias (Bullhorn)** | 5 roles as **editable permission bundles** (SysAdmin, Directors, Managers, Consultants, Researchers) | Portal account is **created at the moment of sharing** an assignment with a client person; per-assignment, per-person. Profile/sharing templates control exposed fields. Clients add feedback; firm can moderate client feedback (its own permission). |
| **Ezekia** | Admin + configurable permission groups | Per-assignment invite (48-hour invite links, access expiry dates). Clients comment (syncs back as feedback notes) and edit only fields explicitly flagged client-updatable. Per-candidate hide/anonymize; custom fields hidden by default. |
| **Loxo** | Admin / non-admin | Hiring-manager portal per job: review, comment, scorecards, **approve/reject as structured input**. Recruiter toggles resume/comp/stage visibility. |
| **Greenhouse / Lever** | Global level (Basic / Job Admin / Site Admin; Lever: 5 fixed roles) **+ per-job grants** with per-job tiers (Standard/Private for sensitive data) | Interviewer-grade limited roles; confidential postings with per-user overrides. |

### General SaaS (org role vs resource role)

- **Everyone converges on the same shape**: a small fixed org-role set (owner/admin/member —
  pure governance: billing, membership, security) plus a per-resource access level
  (GitHub repo Admin→Read; Asana Project admin/Editor/Commenter/Viewer; Jira project roles
  via permission schemes; Notion page access levels).
- **Guests are scope-holders, not role-holders**: Slack single-channel guests, Notion
  page-scoped guests, Linear team-scoped guests, GitHub outside collaborators, Asana guests
  (auto-classified: any email outside the org domain = guest — the same trick LightMove's
  signup already uses).
- **Basecamp is the canonical client model**: clients live in the same project, every item
  carries a `visible_to_clients` bit, **default private**, and the client UI is stripped of
  all machinery (clients never see the flags or that hidden items exist). 37signals arrived
  there after their visible-by-default "Clientside" failed — share-by-default causes
  *"they weren't supposed to see that"* incidents.
- **Admin omniscience is a deliberate choice**: GitHub org owners and Basecamp owners see
  everything; Figma private projects hide even from team owners. Clockwork (our closest
  comp) chose: **Admin sees all, everyone else is invite-gated per project**.
- WorkOS's multi-tenant RBAC guidance says the same thing in design terms: *"roles should
  define broad responsibility, not encode every action on every object — resource-level
  decisions belong at the resource level."* Keep the global model small; push variation to
  the edges.

### The two lessons that matter most for us

1. **No product in this industry lets the client mutate firm-controlled state freely.**
   Clients everywhere are *feedback-writers*: thumbs up/down, comments, scorecards, at most
   approve/reject as a structured decision. Candidate stage/status stays with the firm.
   Our "client can change status" requirement should be narrowed to a **client-decision
   subset** (see §5).
2. **Client-visible is opt-in per item, never default.** Every product that tried
   default-visible walked it back.

---

## 4. Proposed model

Two tiers plus a guest class. Small at the workspace, expressive at the project.

```
WORKSPACE (tenant)                        PROJECT (search mandate)
──────────────────                        ────────────────────────
ADMIN   governance: settings, members,    ADMIN       full control of this project:
        billing, approvals, invites —                 team, lead, client, stage, archive
        implicit admin of every project   LEAD        runs the search day-to-day
MEMBER  staff: can create and work                    (exactly one per project)
        projects they're on               RESEARCHER  executes: sourcing, triage,
                                                      candidates, notes
CLIENT  guest class: exists only through  CLIENT      per-project window: curated view,
        the projects shared with them                 client-decision statuses, shared notes
```

### 4.1 Workspace tier: `ADMIN | MEMBER | CLIENT`

- **ADMIN** — everything `requireAdmin` already gates (settings, member approval, role
  changes, invitations, billing) **plus implicit project-admin on every project** (the
  Clockwork/GitHub-owner choice; right for small firms that need oversight, and it means a
  departed project admin never strands a mandate).
- **MEMBER** — replaces both `CONSULTANT` and `RESEARCHER`, which were never distinguished
  anyway. Members can create projects (creator becomes that project's ADMIN) and work any
  project they're added to. What a person *is* at the firm (Partner, Consultant, Researcher)
  stays where it already lives: the free-text job title on the user.
- **CLIENT** — a guest classification, not a staff grade. A client has **no workspace
  surface at all**: no team page, no member lists, no settings, no company universe. Their
  entire experience is the union of projects explicitly shared with them. (Asana/Figma
  classify guests by email domain automatically; we classify by invitation kind — cleaner,
  since our invitations already cross domains for contractors.)

Why collapse CONSULTANT/RESEARCHER instead of keeping three staff grades: nothing enforces
the distinction today, no mockup gives them different capabilities, and the industry pattern
is explicit that org roles should be governance-only. Keeping them would force every future
endpoint to answer "and what may a workspace-RESEARCHER do here?" for no product benefit.
"Researcher" remains meaningful — as a **project** role, where it actually describes work.

### 4.2 Project tier: `ADMIN | LEAD | RESEARCHER | CLIENT`

Stored in `app_lm_project_member.role` (today `LEAD | MEMBER`).

- **ADMIN** — the mandate owner. Creating a project makes you its ADMIN. Appoints/changes
  LEADs, adds/removes RESEARCHERs, invites/removes the CLIENT, archives the project.
  More than one allowed (so ownership can be shared/handed over); the creator is the first.
- **LEAD** — runs the search: stage transitions, strategy lock/unlock (the mockups already
  say *"edits require unlocking by the project lead"*), shortlist approval, and — together
  with ADMIN — controls what is shared with the client. **One or more per project** — the
  one-lead index was dropped; a mandate can be co-run.
- **RESEARCHER** — executes: company triage, candidate work, notes, outreach prep. Cannot
  reshape the team or the stage gates.
- **CLIENT** — see §5.

A seat holds a *set* of these roles, and the creator's seat starts as **{ADMIN, LEAD}** —
admin and lead both, until they delegate. ADMIN and LEAD stay distinct on purpose: the
partner who owns the client relationship (ADMIN) is often not the consultant running the
search (LEAD), and multi-role seats make the common small-firm case (one person holding
both) the default rather than a special case.

Consistency rules (service-enforced):

- A project-`CLIENT` row requires the person's workspace role to be `CLIENT`, and vice versa
  staff can never hold a project-`CLIENT` role.
- Staff must be an **active** workspace member to be on a team (already guaranteed by the
  FK to `workspace_member.id`).
- A project never loses its **last ADMIN seat** — demotion and removal are both refused
  (`PROJECT_LAST_ADMIN`), mirroring the workspace last-admin rule. Leads carry no such
  invariant; they are plural and freely reassigned.
- Workspace ADMINs don't need a membership row to administer a project — but appear on the
  team only if actually added (oversight ≠ staffing).

### 4.3 Project visibility

- Workspace **ADMIN**: sees all projects (the "All projects" tab in the mockups).
- **MEMBER**: v1 keeps the mockups' behaviour — members can browse all projects, work only
  those they're on. A **Confidential** flag (visible to team + workspace admins only —
  Clockwork's model, padlock and all) is the designed future step, *not* built now: no
  mockup shows it yet.
- **CLIENT**: only their shared projects, only the client-safe surfaces of those.

---

## 5. The client, precisely

The requirement: *"Client can mainly view but will have the ability to change status, and
write notes for the researchers."* Industry practice says shape it like this:

### What a client sees

Only what is deliberately shared. Default hidden (the Basecamp lesson):

| Surface | Client sees? |
|---|---|
| Project header, stage, target date | Yes |
| Candidates **marked client-visible** (normally = shortlisted) | Yes — curated fields; compensation and internal classification hidden by default |
| Full universe / sourcing triage / declined pool | No |
| Outreach activity | No |
| Notes flagged **shared** | Yes |
| Internal notes (the default) | Never |
| Reports published to client | Yes |
| Team identities on the project | Yes (names/roles only) |
| Anything workspace-level | Never |

### What a client can do

- **Change status — narrowed to the client-decision subset.** The candidate status enum in
  the mockups is `Executive / Interested / Not Interested / Off-Limits / Out of Scope`.
  `Off-Limits` and `Out of Scope` are firm classifications with contractual meaning; a
  client must not touch them. The client gets the decisions that are genuinely theirs:
  **Interested / Not Interested** on client-visible candidates (recorded as *client
  decision*, attributed and audited, notifying the team). Every product we studied that
  allows more than comments frames it exactly this way (Loxo approve/reject, Clockwork
  thumbs up/down). If a client needs a status corrected beyond that, they write a note.
- **Write notes** — a client-authored note is always in the **shared** thread (clients have
  no private channel), notifies the project team, and is moderatable by ADMIN/LEAD
  (Invenias makes "edit/delete client feedback" its own permission — same idea).

### How notes carry visibility

Notes gain an `audience` flag: `INTERNAL` (default) | `SHARED`. Staff choose per note;
client notes are always `SHARED`. This is the per-item visibility bit from Basecamp, and it
is the smallest mechanism that prevents the *"they weren't supposed to see that"* incident.

### Identity and lifecycle

- A client is invited **from a project** by that project's ADMIN (or LEAD, or a workspace
  ADMIN) — a normal work-email invitation carrying `role = CLIENT` **and the project id**.
- Accepting creates a normal `app_lm_user` (email verification and all), an **ACTIVE
  workspace membership with role `CLIENT`**, and the `project_member` row with project role
  `CLIENT`. No onboarding wizard: a client must never see create-organization or
  invite-team (this rides on the server-derived `pendingInvitation` routing already planned
  for invitees).
- Adding the same client contact to a second project of the same workspace is just another
  `project_member` row — no new account, no new invite ceremony (Ezekia's "one login,
  invited per project").
- Removing a client from their last project leaves the account with an empty portal;
  workspace admins can revoke the membership entirely from a separate "Client access" list
  (clients never appear in the staff roster).
- **v1 keeps the one-active-workspace-per-user constraint for clients too.** A CHRO engaging
  two LightMove-using firms is real (Clockwork supports cross-firm switching) but relaxing
  it touches the single-`wsId` JWT and `activeMembership()` — future work, §8.

### Why the client is a workspace member at all (and not a separate table)

Modelling the client as a `workspace_member` with role `CLIENT` (rather than a parallel
grant table) keeps every existing mechanism working: the invitation machinery, the tenant
claim in the JWT (`requireWorkspaceId()` still holds — every portal query is still
workspace-scoped *and* project-scoped), audit, and the single-membership invariants. The
cost is discipline: every staff-facing query must now say **staff** (`role <> 'CLIENT'`),
not "active member" — which is exactly why `WorkspaceAccess` grows a `requireStaff(...)`
(§7). This is the Slack/Notion/Linear pattern: the guest *is* a member of the tenant,
restricted structurally.

---

## 6. Permission matrix

WS-ADMIN column = workspace admin acting on any project (implicit). Project columns assume
the person holds that project role.

The rows of this matrix became the seeded **action catalog** (`app_lm_action` + the
`app_lm_role_action` grants): `WORKSPACE_MANAGE`, `MEMBER_MANAGE`, `MEMBER_INVITE`,
`PROJECT_CREATE`, `PROJECT_BROWSE`, `CLIENT_RECORD_MANAGE` at workspace scope;
`PROJECT_EDIT`, `TEAM_MANAGE`, `WORK_EXECUTE` at project scope. Authorisation asks for an
action, never a role — which roles grant which actions can change by INSERT.

| Capability | WS ADMIN | WS MEMBER | P-ADMIN | P-LEAD | P-RESEARCHER | P-CLIENT |
|---|---|---|---|---|---|---|
| Workspace settings, billing, branding | ✅ | — | — | — | — | — |
| Change member roles / remove members | ✅ | — | — | — | — | — |
| Invite staff | ✅ | — | — | — | — | — |
| Create clients (company records) | ✅ | ✅ | — | — | — | — |
| Create a project (→ becomes its ADMIN) | ✅ | ✅ | n/a | n/a | n/a | — |
| Browse all projects (list level) | ✅ | ✅ v1 (Confidential flag later) | — | — | — | — |
| Archive/delete project, transfer ownership | ✅ | — | ✅ | — | — | — |
| Appoint/replace LEADs; add/remove RESEARCHERs | ✅ | — | ✅ | — | — | — |
| Invite / remove the CLIENT | ✅ | — | ✅ | ✅ | — | — |
| Stage transitions, strategy lock/unlock, approve shortlist | ✅ | — | ✅ | ✅ | — | — |
| Mark candidate/note client-visible | ✅ | — | ✅ | ✅ | — | — |
| Sourcing, triage, candidate edits, outreach | ✅* | — | ✅ | ✅ | ✅ | — |
| Write internal notes | ✅* | — | ✅ | ✅ | ✅ | — |
| Write shared notes | ✅* | — | ✅ | ✅ | ✅ | ✅ |
| Set client-decision status (Interested / Not Interested) | — | — | — | — | — | ✅ |
| Set firm statuses (Off-Limits, Out of Scope, …) | ✅* | — | ✅ | ✅ | ✅ | — |
| View client portal rendering ("view as client") | ✅ | — | ✅ | ✅ | — | n/a |

\* workspace ADMIN *can* — implicit project-admin includes working rights — but normally
they staff themselves onto the team if they're actually working it.

---

## 7. How it lands in the codebase

### Schema (V6 — shipped; see `db/migration/V6__invite_only_and_rbac.sql` for the source of truth)

- Roles became **catalog tables**, not CHECK-constrained columns: `app_lm_role` /
  `app_lm_action` / `app_lm_role_action` (seeded), with assignment tables
  `app_lm_workspace_member_role` and `app_lm_project_member_role` whose composite FK pins the
  scope. Data migration: workspace `CONSULTANT/RESEARCHER → MEMBER`, project `MEMBER →
  RESEARCHER`; each project's `created_by` backfilled a seat holding `{ADMIN, LEAD}`.
- The one-LEAD partial index was **dropped** — leads are plural.
- `app_lm_invitation` references `role_id` and gained nullable `project_id` (FK) with a CHECK
  tying the CLIENT role to a project.
- Notes/candidate tables (when they arrive with their screens) carry the `audience` /
  `client_visible` flags from day one — this proposal defines the contract; the tables still
  arrive only with their mockups, per the house rule.

### Backend

- `WorkspaceRole` → `ADMIN | MEMBER | CLIENT`; `ProjectRole` → `ADMIN | LEAD | RESEARCHER | CLIENT`.
- `WorkspaceAccess` gains `requireStaff(userId, wsId)` (active member, role ≠ CLIENT).
  Existing `requireActiveMember` call sites in `ProjectService`/`ClientService`/`MemberService`
  become `requireStaff` — a client must never pass a staff gate.
- New **`ProjectAccess`** service, the project-tier mirror of `WorkspaceAccess`:
  `requireProjectRole(userId, projectId, ProjectRole...)` with the workspace-ADMIN bypass,
  DB-read per request (same staleness reasoning — **project roles never enter the JWT**;
  the token keeps carrying only `wsId` + workspace role).
- `ProjectService` authorization moves from "any active member may do anything" to the
  matrix above; list endpoints split by caller class (admin/staff/client).
- Client portal endpoints live in their own controller namespace (e.g.
  `/api/v1/portal/...`), returning client-safe DTOs only — a separate DTO shape, not a
  filtered staff DTO, so a fat serializer can never leak a private field.
- Invitation flow: client invites carry the project; `accept` materialises membership + team
  row together. Invitee routing reuses the server-derived `pendingInvitation` mechanism.

### Frontend

- `WorkspaceRole` type and role dropdowns shrink to Admin/Member (Settings → Members, signup
  step 3, approval modal).
- Project drawer team panel grows the role picker (Admin/Lead/Researcher) and a separate
  "Client access" section with invite/revoke.
- New client-portal shell: `homeFor(user)` routes `role === 'CLIENT'` to `/portal`;
  clients never see the staff nav. **Requires new mockups first** — no `claude-design/`
  screen exists for the portal today (nor for pending-approval/invite states, noted during
  exploration).

---

## 8. Rollout

**Phase 1 — collapse and enforce (staff only). ✅ Shipped 2026-07-16.**
V6 shipped the RBAC catalog + assignment tables, invite-only membership (join flow deleted),
`WorkspaceAccess.requireAction`/`requireStaff` + `ProjectAccess` guard beans behind
`@PreAuthorize`, multi-role team UI, and the invitee-first onboarding (`pendingInvitation`
on `/me`, token-less accept). The product finally does what its own copy ("roles apply per
project") promises.

**Phase 2 — client access.**
Portal mockups → client invite flow (invitation + project id) → portal shell → shared
notes + client-decision statuses + client-visible flags on candidates. Needs the candidate/
notes tables, so it naturally rides with the Project screen work.

**Phase 3 — later, designed-for now.**
Confidential projects (team + admins only). Field-level visibility templates (Invenias/
Ezekia style). Multi-workspace clients (relax the partial unique index for `CLIENT` rows +
workspace-context switching in the token — the invariant to revisit is single-`wsId`).
Client access expiry dates (Ezekia). "View as client" preview.

---

## 9. Open decisions

Resolved in Phase 1: project-ADMIN and LEAD stayed distinct (as multi-role sets, so one seat
holds both); the project role kept the name ADMIN; members browse all projects (Confidential
flag remains future work); join requests were removed rather than restricted.

Still open, for Phase 2:

1. **Client status power.** Proposal narrows "change status" to Interested/Not-Interested
   client decisions. If clients truly need full status control, say so explicitly — it
   contradicts every comp we studied and weakens the audit story.
2. **Client seats and billing.** Industry norm is unlimited free client seats (Clockwork);
   Billing mockup shows per-seat pricing for staff. Assume clients are free/uncounted?

---

## 10. Invited signup & onboarding UX (implemented 2026-07-17)

A follow-up pass on two onboarding bugs found after the staff tiers shipped. Both were about how a
person *first arrives*: the workspace creator was asked to pick a "role" that meant nothing, and an
invited user was forced through an email verification the invitation had already made redundant.

### 10.1 The two problems

1. **Signup asked the creator for a "role."** Step 2 rendered a *Your role* dropdown
   (Partner / Consultant / Researcher / Operations). It never set authority — the workspace creator is
   always `ADMIN` — it only wrote a free-text job title onto the user. On screen it read as a decision
   the user did not have to make, next to a workspace they were being told they own.
2. **Invited users verified their email twice over.** An invitee had to: create an account → receive a
   *second* email (the verification link) → click it → *then* accept. But the invitation link is itself
   a 256-bit token mailed only to the invited address. Clicking it already proves control of that
   mailbox — the exact thing email verification exists to establish. The second round-trip proved
   nothing new and stranded people at a "check your inbox" wall one step from being in.

### 10.2 Decisions

- **No role at signup.** The *Your role* field is gone; `jobTitle` was removed end to end (it was never
  authority, and the vestigial `app_lm_user.title` write was not worth keeping a form field for). The
  creator is `ADMIN`, full stop.
- **A confirmed password everywhere.** Signup and the invite-accept form both take the password twice
  (`password` + `confirmPassword`, matched client-side). Standard double-entry; it changes no server
  contract.
- **An invited user skips verification.** The invite token is treated as the mailbox proof, so a new
  invitee sets a password on a dedicated screen and is `ACTIVE` in the workspace immediately — no
  verification email, no wizard. This extends the codebase's existing "an emailed token proves the
  address" principle rather than inventing a new one.

### 10.3 The trust model (why skipping verification is safe — and where it is not)

The safety of the new path rests on one invariant: **the created account's email is taken from the
resolved invitation, never from the request body.** The accept-signup request carries only
`{token, fullName, password}` — no email. So possession of the token can only ever mint the one identity
the token was addressed to; it can never be redirected to another address.

Two guards back this up:

- **`existsByEmail`** — if the invited address already has an account, the endpoint refuses
  (`EMAIL_ALREADY_REGISTERED`) and the UI sends that person to log in. The public endpoint can never
  attach to, or overwrite the password of, a pre-existing identity.
- **The email-match guard in `redeem`** — still runs, and is now trivially satisfied because the user's
  email and the invitation's email share one source.

The honest cost: this drops the *shoulder-surf* backstop for **new** accounts. Someone who reads an
invite link off the invited person's screen could claim it with their own password. The old model
stopped that at the verification step (they could not read the invited inbox); the new model treats the
invite click as inbox proof, so it does not. This is the same exposure email verification itself carries
(a verification link read over a shoulder verifies the account too), narrowed to the invite window, and
it was accepted deliberately. The **signed-in** accept paths for people who already have an account are
unchanged — they still require a verified session.

### 10.4 Backend

**New public endpoint** — `POST /api/v1/onboarding/accept-invitation-signup`, body
`AcceptInvitationSignupRequest{token, fullName, password}` (no email — see §10.3). Returns a full
session: access token in the body, refresh token in the httpOnly cookie, exactly like signup/login.
`SecurityConfig` matches it `permitAll` and `POST`-only, placed *before* the `/onboarding/**`
`authenticated` gate and the `/api/**` verified gate so first-match wins.

**Orchestration** — `InvitationService.acceptWithNewLocalUser(...)`, one plain `@Transactional` (full
rollback on any failure — the opposite of `login`/`rotate`, whose `noRollbackFor` exists only to keep a
counter on the failure path; there is no such side effect to protect here):

1. `resolveRedeemable(token)` — find by token hash, check redeemable (`INVITATION_INVALID` /
   `INVITATION_EXPIRED`). Extracted from the old inline block in `accept`/`preview`, now shared by all
   three.
2. `rateLimit.checkSignup(invitation.email, request)` — the same budget as signup.
3. `AuthService.createVerifiedLocalUser(invitation.email, fullName, password, request)` — validates the
   password, rejects an existing address (`EMAIL_ALREADY_REGISTERED`), creates the user, links the local
   identity, then `markEmailVerified(now)`. Mirrors `signup` minus the verification email and minus
   `validateWorkEmail` (the invited address was already vetted when the invite was issued).
4. `redeem(invitation, user, now, request)` — the shared accept tail: email-match guard, verified gate
   (passes — just marked), not-already-a-member, then the `ACTIVE` `WorkspaceMember` holding the invited
   role.
5. `tokens.issue(user, member, request)` — the session carries `wsId`, `roles` and `SCOPE_VERIFIED` from
   the very first token, so the invitee is in with **no** second login and no refresh.

The seam respects the dependency rule: `InvitationService` (feature) → `AuthService` / `TokenService`
(core) is feature→core, the allowed direction, and introduces no bean cycle. `createVerifiedLocalUser`
is a reusable core building block; the workspace orchestration stays in the feature.

**Existing accept paths kept** — the token-based `POST /onboarding/invitations/accept` and the
token-less `POST /onboarding/accept-invitation` still exist and still require a verified session; they
serve the "already have an account / already signed in" invitee.

**`jobTitle` removal** — dropped from `CreateWorkspaceRequest`, `CreateWorkspaceCommand`,
`PendingOnboarding` (field + `toCreate`/`describe` params + the `materialise` replay), and the two
`user.setTitle(command.jobTitle())` calls in `OnboardingService`. **No migration**: the `job_title`
column is left in place, unmapped, which `ddl-auto: validate` tolerates (it fails on a *missing* column,
not an extra one).

### 10.5 The three ways in, after this change

| Arrival | Where | What they do | Verification? |
|---|---|---|---|
| **Create a workspace** | signup wizard steps 1–3 | name/email/password → org details → invite team | **Yes** — a self-typed address is still an unproven claim; the wizard is *held* (`PendingOnboarding`) until they verify, then materialised. |
| **New invitee** (no account) | `AcceptInvitePage`, token in URL | set name + password on the accept screen | **No** — the invite token is the mailbox proof. `ACTIVE` at once. |
| **Existing-account invitee** | `AcceptInvitePage`, signed in (or routed there by `pendingInvitation`) | one-click accept | Uses their existing verified session; an unverified existing account is asked to verify first. |

### 10.6 Frontend

- **`AcceptInvitePage`** rewritten. An anonymous token arrival (the common case) renders a real
  account-creation form: read-only invited email, full name, password, confirm password, "Accept & join"
  → `AuthProvider.acceptInviteSignup` → the new endpoint → `/`. `EMAIL_ALREADY_REGISTERED` swaps in a
  "Log in to accept →" link carrying the address (and nothing else — the workspace name is never
  revealed pre-auth). Signed-in arrivals keep the wrong-account / already-in-a-workspace /
  ready-to-accept states.
- **`AuthProvider.acceptInviteSignup`** and **`authApi.acceptInvitationSignup`** — mirror `signUp`:
  call the endpoint, install the returned access token, set the user.
- **Confirm password** — added to `signupSchema` (with a `.refine` match) and a new `acceptInviteSchema`;
  rendered on both forms.
- **Wizard cleaned** — the dead `pendingInvite` sessionStorage detour (which carried the token through
  the old signup → verify → accept round trip) was deleted outright: gone from `SignupPage`,
  `VerifyEmailPage`, and the `pendingInvite.ts` file itself. Signup is now purely the fresh-creator
  step 1. The server-derived `user.pendingInvitation` routing is *kept* — it is the mechanism that sends
  an existing-account invitee to the accept screen from any tab.

### 10.7 Mockups & tests

- **Mockups** — `Signup.dc.html` lost the *Your role* select and gained a confirm-password field; new
  `AcceptInvite.dc.html` is the invite-accept screen (invited email locked, name + password + confirm,
  "Accept & join").
- **Backend** — `InvitedSignupIntegrationTest` (Testcontainers): a new invitee lands `ACTIVE` with no
  verification email and a first session that already works against a tenant route; an already-registered
  address gets `409` and no membership; only a live token works and a consumed one cannot be reused; a
  weak password is refused and leaves nothing behind (rollback proof); the endpoint is reachable
  unauthenticated. Full suite: 99 green.
- **Frontend** — the accept form renders and calls the endpoint; `EMAIL_ALREADY_REGISTERED` shows the
  login CTA; mismatched passwords are blocked before any request. Full suite: 39 green, `tsc` clean.

### 10.8 Files touched

Backend: `AuthService`, `InvitationService`, `OnboardingController`, `SecurityConfig`, `WorkspaceDtos`,
`CreateWorkspaceCommand`, `PendingOnboarding`, `OnboardingService`. Frontend: `AcceptInvitePage`,
`AuthProvider`, `api/authApi.ts`, `api/types.ts`, `schemas.ts`, `SignupPage`, `WorkspaceStepPage`,
`VerifyEmailPage` (and `pendingInvite.ts` deleted). Mockups: `Signup.dc.html`, `AcceptInvite.dc.html`.
Docs: this section and the `CLAUDE.md` invitation/verification rules.

---

## 11. Sources

Exec search: [Clockwork roles](https://support.clockworkrecruiting.com/article/364-roles-in-clockwork),
[Clockwork client portal](https://support.clockworkrecruiting.com/collection/1069-for-clients),
[Clockwork note visibility](https://support.clockworkrecruiting.com/article/609-client-visibility-on-notes),
[Invenias users/roles](https://kb.bullhorn.com/invenias/Content/Invenias/Topics/usersRolesAndGroups.html),
[Invenias client portal](https://kb.bullhorn.com/invenias/Content/Invenias/Topics/clientPortal.htm),
[Ezekia client portal](https://ezekia.freshdesk.com/support/solutions/articles/101000486642-client-portal),
[Thrive TRM collaboration](https://thrivetrm.com/how-thrive-speeds-up-the-executive-search-life-cycle-with-real-time-collaboration/),
[Loxo HM portal](https://www.loxo.co/products/hiring-manager-portal),
[Greenhouse permission levels](https://support.greenhouse.io/hc/en-us/articles/360039614171-Permission-levels-overview),
[Lever roles](https://help.lever.co/hc/en-us/articles/20087352819229).
General SaaS: [GitHub org roles](https://docs.github.com/en/organizations/managing-peoples-access-to-your-organization-with-roles/roles-in-an-organization),
[GitHub repo roles](https://docs.github.com/en/organizations/managing-user-access-to-your-organizations-repositories/managing-repository-roles/repository-roles-for-an-organization),
[Asana permissions](https://asana.com/features/admin-security/permissions),
[Notion sharing](https://www.notion.com/help/sharing-and-permissions),
[Slack guest roles](https://slack.com/help/articles/202518103-Understand-guest-roles-in-Slack),
[Linear members & roles](https://linear.app/docs/members-roles),
[Basecamp clients](https://5.basecamp-help.com/article/1082-what-clients-can-see-and-do),
[Basecamp client visibility API](https://github.com/basecamp/bc3-api/blob/master/sections/client_visibility.md),
[Figma permissions](https://help.figma.com/hc/en-us/articles/1500007609322-Guide-to-sharing-and-permissions),
[WorkOS multi-tenant RBAC](https://workos.com/blog/how-to-design-multi-tenant-rbac-saas),
[WorkOS on Slack/Notion/Linear](https://workos.com/blog/multi-tenant-permissions-slack-notion-linear).
