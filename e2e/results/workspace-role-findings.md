# Workspace RBAC end-to-end findings

Run date: 2026-08-12 · LightMove branch `bug/auth-fix-as-per-end-to-end-testing`
API Spring Boot 4.1 on :8080 · SPA Vite on :5173 · throwaway `postgres:16-alpine` on :55432
(Flyway V1–V19 clean on a virgin schema) · email provider forced to `log`, nothing mailed.

**240 checks executed. 4 failed, and all four are the same bug (B1).**

> **Resolved 2026-08-13.** B1, C2 and the `VALIDATION_FAILED` gap are fixed; C1 was decided as designed
> and is now written down. C3 stays open by choice. See **Resolution** at the foot of this document —
> including three factual slips in the text below that survived into the fixes.

Everything in this run happened against the Docker database. The shared Cloud SQL dev instance was
never contacted. No source file was changed — the only thing this session produced is the `e2e/`
suite and this document.

| Phase | Script | Checks |
|---|---|---|
| Fixtures — the cast | `e2e/api/fixtures.sh` | 9 |
| Role × endpoint grid | `e2e/api/09-workspace-roles.sh` | 66 |
| Escalation, stale claims, invariants | `e2e/api/10-role-invariants.sh` | 46 |
| Client registry and portal access | `e2e/api/11-client-access.sh` | 46 |
| Tenant isolation and workspace delete | `e2e/api/12-tenant-isolation.sh` | 31 |
| SPA under each role | `e2e/spa/roles.mjs` | 42 (4 failing) |

The flow under test, end to end: an admin invites a member → the member signs up → the member creates
a client and invites its representative → the representative signs up and logs in directly → and then
every one of them is pointed at every endpoint they should not reach.

---

## Headline

**The API's authorisation is sound.** 198 API checks, zero failures. No role can escalate itself, the
`roles` claim is genuinely re-read on every request in both directions, tenant masking is complete,
and every invariant holds. I tried to break it from six different directions and did not.

**The SPA has one real hole**, and it is a routing gap rather than an authorisation one: two staff
pages have no client-side guard, so a client-portal guest who types the URL is served the firm's
internal staff screen. No data escapes — the API refuses every call the page makes — but the guest is
shown internal tooling, an actionable form that cannot work, and a false statement about the firm.

---

## B1 · A client-portal guest can open the firm's staff pages by typing the URL

**Severity B** · `S2.3`, `S2.3b`, `S2.3c`, `S2.4` in `e2e/spa/roles.mjs`

### Reproduce

```bash
cd e2e && ./stack/up.sh && bash api/fixtures.sh    # builds a pure client
# sign in to http://localhost:5173 as the CLIENT_EMAIL in results/current/cast.env
# then navigate directly to:
#   http://localhost:5173/clients
#   http://localhost:5173/team
```

| | |
|---|---|
| Expected | redirected to `/`, as `/settings/*` does for a non-admin |
| Actual | both pages render in full |

On `/clients` the guest sees the Clients heading, the empty state **"Add your first client"**, and
**two "New client" buttons**. Clicking one opens the create drawer with the company-database search
field. The page also states **"0 clients"** — the workspace has two.

Screenshots: `e2e/spa/screenshots/roles-client-clients-deeplink.png`,
`roles-client-team-deeplink.png`, `roles-client-newclient-drawer.png`.

### Why it is not worse than it looks

The server holds. From inside that same browser session, with that user's own token:

```
GET /api/v1/clients          -> 403 FORBIDDEN     (S3.6)
GET /api/v1/members          -> 403 FORBIDDEN     (S3.5)
GET /api/v1/companies/search -> 403 FORBIDDEN     (S3.8)
POST /api/v1/projects        -> 403 FORBIDDEN     (S3.9)
```

So no client record, no roster, no company data reaches them. The `0 clients` count is the API's 403
rendering as an empty list, not a real count.

### Root cause

`apps/web/src/app/routes.tsx:67` — `/clients` (`:70`) and `/team` (`:71`) sit inside
`<RequireWorkspace>` only. The one role guard in the router is `RequireAdmin` (`:173-181`), applied
solely to the settings branch (`:87`).

The nav is filtered correctly — `components/layout/WorkspaceLayout.tsx:23` uses `isPureClient(roles)`
and builds a portal-only sidebar — so the pages are simply unreachable by clicking. Only the URL
exposes them.

### Fix

The predicate already exists and is already used in three places
(`apps/web/src/features/auth/roles.ts:8`, plus `ProjectsPage.tsx:25` and `TeamAccessPage.tsx:38`).
Add a `RequireStaff` guard beside `RequireAdmin` in `routes.tsx` that redirects when
`isPureClient(user.workspace.roles)`, and wrap `/clients` and `/team`.

**What the fix must preserve:** `isPureClient` is "holds CLIENT **and no staff role**". A member who
holds `CLIENT` alongside `MEMBER` is staff and must keep both pages — `S1.11`–`S1.13` and
`S3.11`–`S3.13` cover exactly that and must stay green.

**Consider also:** an empty list that is really a 403 should not render the "Add your first client"
empty state. That pattern will mislead on any surface where the read is refused rather than genuinely
empty.

---

## C1 · Every staff member can create clients and invite their representatives

**Severity C — a design decision, not a defect** · `fixtures.3`, `fixtures.5`, `R4.3` in
`09-workspace-roles.sh`

`CLIENT_RECORD_MANAGE` is granted to workspace `MEMBER` as well as `ADMIN`
(`V6__invite_only_and_rbac.sql`, pinned by `RbacCatalogTest`). Verified live: a plain member creates a
client (`201`), invites a representative (`201`), and that representative becomes a workspace member
holding `CLIENT`.

So **any member can grant an outsider a login to this workspace**, without an admin involved. The
representative's reach is genuinely narrow — one mandate, read-only — but the act of admitting an
external person is not an admin decision today.

This may well be intended: it matches the flow you described, and a consultant who owns a mandate
plausibly owns its client contacts. Recording it because it is the one place where the invitation-only
model has a second door, and `CLAUDE.md`'s membership section ("the only way into an existing one is
an admin's invitation") reads as though it does not.

**If it should change:** move `CLIENT_RECORD_MANAGE` to ADMIN only, or split representative invitation
out of it into its own action. Both are an INSERT migration plus an enum constant.

**If it should not:** worth a sentence in `CLAUDE.md`, because the current text says otherwise.

---

## C2 · A portal guest is shown the firm's internal identity

**Severity C** · `C5.5`–`C5.8` in `11-client-access.sh`, `S4.4` in `roles.mjs`

`/auth/me` for a pure client returns the full `WorkspaceSummary`:

```
id, name, slug, logoMark, emailDomain, roles
```

so an outside contact at Northwind sees the search firm's workspace name, slug and **`emailDomain`**.
The name is unavoidable — it is the brand they are dealing with, and the SPA renders it in the top
bar. `emailDomain` is the one worth a second look: it is an internal signal (it is what the firm's
colleagues' addresses are expected to look like) and the portal has no use for it.

**Fix if wanted:** narrow the assembler's summary for a pure client, or drop `emailDomain` from the
payload when the caller holds no staff role. Low effort, low risk.

---

## C3 · The portal sidebar offers surfaces that do not exist yet

**Severity C** · `S4.9` in `roles.mjs`

Inside a mandate, a pure client is offered:

```
Position | Strategy | Sourcing | Candidates | Outreach | Reports | Team & access
```

`Candidates`, `Outreach` and `Reports` are placeholder pages today, so nothing leaks. But they are
seat-gated on `WORK_VIEW`, which the project `CLIENT` role holds — so when those tables are built,
**a client will see them by default** unless the gate is reconsidered at that point.

Flagging it now because it is invisible while the pages are empty and expensive to notice later. A
client seeing a sourcing long-list or an outreach log is a different disclosure decision from a client
seeing the position brief.

---

## Verified sound — what held under attack

Listed so a future change knows what it might break. Every item was driven live, not read.

### No path escalates a role · `10-role-invariants.sh`
- A member promoting themselves, a pure client promoting themselves to `ADMIN` **or** to `MEMBER`, a
  dual-role member promoting themselves, a member promoting somebody else — all `403`, and the
  database row is unchanged after each (`E1.1`–`E1.9`).
- `CLIENT` cannot be granted through the roster, alone or smuggled alongside a staff role; an empty
  set, an invented role name, and a **project** role offered at workspace scope are all refused, and
  the target survives every rejected payload unchanged (`E2.1`–`E2.7`).

### The `roles` claim really is coarse material · `E3`, `E5.9`
This is `CLAUDE.md`'s strongest authorisation claim and nothing exercised it against a running server.
Both directions now proven, with the token's own claim confirmed stale throughout:

| | |
|---|---|
| Promote a member, then use their **existing** token | admin-only route succeeds **immediately** (`E3.5`) |
| Demote them, then use the **same** token | refused **at once**, not in 15 minutes (`E3.7`) |
| Remove a member, then use their live token | `404 NOT_A_MEMBER` on every tenant route (`E4.3`, `E4.4`) |

### The last-admin and last-lead invariants · `E5`, `E6`
- The sole admin can neither demote themselves nor leave (`409 LAST_ADMIN`), and is still admin after
  both attempts. Promote a second admin and the first can then do both (`E5.2`–`E5.8`).
- Removing the sole lead of a live mandate is refused (`409 MEMBER_LEADS_PROJECTS`); once the mandate
  is `DELIVERED` the same removal succeeds and the seats are cleaned up (`E6.2`–`E6.5`).

### Tenant isolation is complete · `12-tenant-isolation.sh`
Every id-bearing endpoint, called by a legitimate admin naming another tenant's `memberId`,
`clientId`, `projectId`, `invitationId`, `representativeId` — **`404` in all eleven cases**, never a
`403`. Existence is not confirmable. The workspace-admin project bypass correctly does **not** cross
tenants (`T1.7`, `T1.8`): the tenant check runs before the bypass. Both directions tested, and a pure
client is nothing in a workspace that is not theirs (`T3`).

### The client fence · `11-client-access.sh`
- A pure client reads the brief, strategy and sourcing of the one mandate they are attached to, and is
  refused on every other mandate, including ones created after they joined (`C4.1`–`C4.6`).
- They cannot edit that mandate, write its strategy, touch its team, or attach another representative
  (`C4.7`–`C4.10`).
- They are absent from the roster **body**, not merely refused the call (`R1.4`) — previously untested.
- Detaching them empties their project list and closes the brief in the same instant (`C7.1`–`C7.4`).
- A representative of one client cannot be attached to another client's mandate (`C6.3`); a revoked
  one cannot be re-attached (`C6.8`); a pure client cannot be seated through the staff team table
  (`C6.4`); `CLIENT` cannot be requested through it either (`C6.5`).
- A portal invitation redeemed through the **staff** accept endpoint grants `CLIENT` and nothing more
  — the endpoint does not decide the role, the invitation row does (`C6.1b`, `C6.2b`, `C6.2b2`), and
  it does not surface as a pending staff invitation on `/me` (`C6.2c`).

### The dual-role member is staff · `R1.5`, `R1.6`, `C3.4`–`C3.7`, `S1.11`–`S1.13`, `S3.11`–`S3.13`
A member holding `{MEMBER, CLIENT}` keeps the roster, the registry, workspace settings, mandate
creation, the full staff nav and the unfiltered project list. This was the untested case most likely
to break if `isPureClient` is ever loosened, and it is now covered on both tiers.

### The workspace-delete gate · `T4`
Previously untested in both directions. A member is refused (`403`), a pure client is refused, a wrong
`confirmName` is refused (`400 WORKSPACE_NAME_MISMATCH`), and the correct name succeeds (`204`) —
after which memberships are freed, pending invitations are dead, both users' live tokens stop
reaching a workspace, and `/me` shows them free to start again.

---

## Two smaller observations

**Custom `VALIDATION_FAILED` messages never reach the caller.** An admin who tries to grant `CLIENT`
through the roster gets `{"detail":"One or more fields are invalid"}` — the real message,
*"Clients are invited to a project, not granted through the roster"*, exists in the service and is
only logged at DEBUG (`E2.2`). Codes that carry their own `defaultMessage` do communicate: the
last-lead refusal correctly says *"This member is the only lead on active projects. Hand those over
first"* (`E6.3`). The difference is that `ApiException(VALIDATION_FAILED, msg)` has its message
discarded by `GlobalExceptionHandler`, which always renders `code.defaultMessage()`. This is the same
gap already noted at the end of `auth-findings.md` for the password-length message — one fix closes
both.

**Body parsing runs before authorisation.** A caller with no permission sending a malformed body gets
`400`, not `403` — Bean Validation runs during argument resolution, ahead of method security. Not a
leak (the request still fails), but it means a negative test with a sloppy payload silently proves
nothing. Two of my own assertions were wrong for exactly this reason before I fixed them.

---

## Coverage this run added

No existing Java test asserted any of these; all are now covered by the suite:

- A plain member refused `DELETE /api/v1/workspace`, and the successful delete path.
- A dual-role member retaining every staff surface.
- A pure client excluded from the `GET /members` response **body**.
- The `roles` claim being re-read live, in both directions, on an unrefreshed token.
- A removed member's still-valid access token.
- Tenant masking across all five id types from both sides.
- Every workspace endpoint under all four roles as one grid.

## Suggested order of work

1. **B1** — add `RequireStaff` to `routes.tsx` for `/clients` and `/team`. Small, and it is the only
   thing in this run a user could stumble into.
2. **C1** — decide whether inviting an outsider should be an admin act, then align code or `CLAUDE.md`.
3. The `VALIDATION_FAILED` message gap, jointly with the one left open in `auth-findings.md`.
4. **C2** — drop `emailDomain` from a pure client's `/me`.
5. **C3** — revisit `WORK_VIEW` for clients when Candidates/Outreach/Reports are built, not before.

---

## Resolution · 2026-08-13

Every claim above was re-verified against the source before anything was changed. **The report was
accurate**, with three slips that changed no conclusion:

| Says | Actually |
|---|---|
| `features/projects/ProjectsPage.tsx`, `TeamAccessPage.tsx` | both under `features/projects/pages/`; the line numbers 25 / 38 are exact |
| "Flyway V1–V19" | V21 was current, V18 is a skipped CoreSignal POC slot — the new migration is **V22** |
| "the `/me` query hook" | `/auth/me` is fetched imperatively in `AuthProvider`, into `useState` — not react-query |

Two things the report understated, both now fixed:

- The false-empty-state was on **`/team` as well**, which reported "0 members" for the same reason.
- `emailDomain` was not merely in the payload — `Topbar.tsx:129` **rendered** it in the workspace menu.

### B1 — fixed

`RequireStaff` sits beside `RequireAdmin` in `routes.tsx` and wraps `/clients` and `/team` through a
nested `<Outlet/>`; `/` and `/all` stay open, because a pure client's project list is server-scoped to
their seats. It reuses `isPureClient` rather than restating the rule.

Covered by `apps/web/src/app/routes.test.tsx` — the repo's first router test. It was checked against a
temporarily-removed guard, and both pure-client cases went red. Two traps worth knowing if you extend
it: the pathname probe must be read under `waitFor` (the guards render `Booting` until the session
restore resolves, so an immediate assertion sees the pre-redirect path), and `toHaveTextContent("/")`
passes on `/clients` as a substring — assert the exact string.

**Also fixed, the "consider also" note:** `ClientsPage` and `TeamPage` now branch on `isError` before
the empty state, so a refused read says so instead of reporting a count the caller could not read.

### C1 — as designed, and now written down

Kept. A consultant who owns a mandate owns its client contacts. What was missing was the distinction the
report is really pointing at, so it is now two actions rather than one:

- **Registry** — `CLIENT_RECORD_MANAGE`, workspace `ADMIN` *or* `MEMBER`. Creates client records and
  mints representatives. No project is named and none is granted: a representative who exists but is
  attached to nothing sees nothing.
- **Mandate** — `CLIENT_ACCESS_MANAGE` (new, `V22`), project `LEAD` alone plus the workspace-admin
  bypass. Attach, invite-and-attach, detach.

The rule was already true by arithmetic — those endpoints rode `PROJECT_EDIT`, which `RESEARCHER` does
not hold — but it was one permission with "change the target date", and widening that would have handed
client access away silently. `CLAUDE.md`'s membership section now names the second door, and its RBAC
section the two tiers.

### C2 — fixed

`WorkspaceRole.isStaff(Collection)` is the server twin of `isPureClient`; the assembler sends
`emailDomain` only to staff. Name, slug and mark stay — that is the brand the guest is dealing with.
A `{MEMBER, CLIENT}` member keeps it, which is asserted alongside the pure-client case.

### The `VALIDATION_FAILED` gap — fixed, and it closes `auth-findings.md`'s open item

Not by flipping the handler: 33 sites relied on the old contract and several interpolate raw request
input (`SourcingService`'s sort tokens, `CompanyReferenceController`'s bands, `StrategyService`'s
company labels), which is exactly the reflection the generic message was suppressing. `ApiException`
now has a **second, opt-in channel** — `userFacing(code, message)` and `withField(code, field, message)`
— and the ordinary constructors keep their meaning untouched.

Promoted: the nine registry/project rules (roster, invitation, client, representative, seat). Moved to
`fieldErrors`: `termsAccepted`, and the four `PasswordPolicy.validate()` sites — which is the
*"carry the validate() message through as a `fieldErrors.password` entry"* item left open at the end of
`auth-findings.md`. Left internal: `SourcingService`, `CompanyReferenceController`, `StrategyService`.

### C3 — still open, by choice

`WORK_VIEW` is held by the project `CLIENT` role, so Candidates, Outreach and Reports will be visible
to a client the day they are built. Nothing leaks while they are placeholders, and the disclosure
decision belongs with the screens. Revisit then.

### Verification

`./mvnw test` — **258 pass**, including `V22` applied to a virgin schema under `ddl-auto: validate` and
`RbacCatalogTest` pinning `CLIENT_ACCESS_MANAGE` to `LEAD` and nobody else. New integration coverage:
the two-tier split driven end to end (a researcher mints a representative through the registry, is
refused all three mandate endpoints, and the lead succeeds), and the `/me` narrowing in both directions.

`npx vitest` — **191 pass** across 30 files, 7 of them new.

---

# Run 2 — independent verification of the fixes

Run date: 2026-08-13 · branch `bug/workspace-role-finding-fix` · rebuilt API, **virgin Docker
database** (V1–V22 applied from empty, so the new migration was exercised on a fresh schema rather
than an already-migrated one) · email provider forced to `log`.

**287 e2e checks, 0 failures.** No source file was touched this session — the only additions are
`e2e/api/13-client-access-tiers.sh` and updated assertions in the existing scripts.

| Suite | Checks | Result |
|---|---|---|
| `fixtures.sh` | 9 | pass |
| `09-workspace-roles.sh` | 66 | pass |
| `10-role-invariants.sh` | 48 | pass |
| `11-client-access.sh` | 50 | pass |
| `12-tenant-isolation.sh` | 31 | pass |
| **`13-client-access-tiers.sh`** (new) | **41** | pass |
| `spa/roles.mjs` | 42 | **pass — was 38/4** |
| Java `./mvnw test` | 268 | pass |
| Web `npx vitest` | 191 | pass |

---

## The two-tier rule, verified as stated

> At workspace level an ADMIN **or** a MEMBER can add a client and a client representative.
> At project level **only the LEAD** can map a client to a project.

That is now exactly what the code does. `13-client-access-tiers.sh` drives both halves against roles
that differ *only* by project seat — MEMBER and MEMBER2 are both workspace `MEMBER`, so both hold
`CLIENT_RECORD_MANAGE`; the only thing separating them is that MEMBER2 leads the mandate and MEMBER
sits on it as a RESEARCHER.

**Workspace tier — both roles, both halves** (`V2.1`–`V2.3`, each run as ADMIN and as MEMBER)

| | ADMIN | MEMBER |
|---|---|---|
| `POST /clients` | 201 | 201 |
| `POST /clients/{id}/representatives` | 201 | 201 |

And the tier boundary itself: a representative minted through the registry is seated on **no mandate
at all** (`V2.4`, `V2.5`) — creating a contact shows them nothing, which is the whole point of the
split.

**Project tier — the lead alone** (`V3`, `V4`, `V5`)

| Caller | attach | detach | create-and-attach |
|---|---|---|---|
| Seated RESEARCHER | **403** | **403** | **403** |
| Staff member, no seat | **403** | — | — |
| Portal guest (pure CLIENT) | **403** | **403** | **403** |
| **Mandate LEAD** | **200** | **200** | **200** |
| Workspace ADMIN, no seat | **200** (bypass) | **200** | — |

The RESEARCHER case is the one that matters: they hold `CLIENT_RECORD_MANAGE` and can mint a
representative through the registry all day, and are still refused all three mandate endpoints
(`V3.3`, `V4.1`, `V5.1`). Registry and mandate are genuinely separate decisions now, not the same one
wearing two names.

**The catalog backs it** (`V1.1`–`V1.6`, read from the database after V22 applied to an empty schema):
`CLIENT_ACCESS_MANAGE` exists at PROJECT scope, is granted to **exactly one** role, and that role is
`LEAD`. RESEARCHER holds neither it nor `PROJECT_EDIT`; the project `CLIENT` seat still holds only
`WORK_VIEW`; workspace `CLIENT_RECORD_MANAGE` is still `ADMIN,MEMBER`.

**The bypass survives, and stays inside the tenant.** A workspace admin with no seat can still map and
unmap a client (`V6.2`, `V6.3`) — a search never strands on a departed lead — but reaching another
tenant's mandate is still `404`, not `403` (`V6.4`).

Two behaviours worth recording because they are easy to mistake for bugs:

- Attaching a representative who has **not yet accepted** parks a `app_lm_project_pending_representative`
  row instead of creating a seat, and it is redeemed when they accept (`V3.9`–`V3.11`). Same gate,
  different landing place. My first draft asserted a seat and was wrong.
- `POST /projects/{id}/representatives/invitations` needs **both** actions and is refused if either is
  missing (`V5.1`, `V5.2`). Since every staff member holds the registry half today, the only way to
  fail that gate is to lack the mandate half — which is precisely the RESEARCHER case.

---

## The four findings

**B1 — fixed.** `RequireStaff` in `routes.tsx:177-193` wraps `/clients` and `/team` via an `Outlet`.
A portal guest deep-linking to either is now redirected to `/` (`S2.3`, `S2.4`), is offered **zero**
"New client" buttons (`S2.3b`), and is no longer told the firm has "0 clients" (`S2.3c`). The four
assertions that failed in run 1 all pass.

The regression guard holds too: the dual-role member (`{MEMBER, CLIENT}`) keeps both pages and the
full staff nav (`S1.11`–`S1.13`), and still gets 200 from `/members`, `/clients` and `/workspace`
called directly from their own browser session (`S3.11`–`S3.13`). Reusing the existing `isPureClient`
rather than writing a second predicate is what keeps those two facts from drifting apart.

**C2 — fixed, and better than I proposed.** I suggested narrowing the payload for a pure client.
`WorkspaceRole.isStaff` is the sharper move: the rule lives on the enum next to the roles it reasons
about, and `AuthResponseAssembler:110` asks it from a role set already in hand rather than re-reading
membership. Verified in all three directions — `emailDomain` is `null` for the pure client while name,
slug and mark survive (`C5.7`, `C5.8`), present for a plain member (`C5.9`), and present for the
dual-role member (`C5.10`), who is staff.

**The `VALIDATION_FAILED` message gap — fixed, across both reports.** `ApiException` now has two
explicit channels and `GlobalExceptionHandler:59-66` renders `clientDetail` when one is set. Verified:

- The roster rule now answers *"Clients are invited to a project, not granted through the roster"* as
  the `detail` (`E2.2`), correctly as a **banner** with no field attribution (`E2.3`) — the roster
  editor is a role set, not one input.
- The password rule, left open at the foot of `auth-findings.md`, now reaches the caller as
  `fieldErrors.password` **and** as the detail (`N1.9`, `N1.10`) — *"Use at most 72 characters — fewer
  if they are accented or emoji"*, instead of the generic sentence. One change closed both reports'
  loose ends.

I had expected `withField` for the roster rule and was wrong; `userFacing` is the right channel there,
and the assertion was corrected rather than the code.

**C1 — decided as designed and written down.** Nothing to verify beyond the behaviour, which is
unchanged and still covered (`fixtures.3`, `fixtures.5`, `V2`).

**C3 — open by choice.** Still true: the portal sidebar offers Candidates, Outreach and Reports
(`S4.9`). Placeholders today; the decision lands when the tables do.

---

## Regression sweep

The `ApiException` change touches the shape of *every* error response, so the authentication suite was
re-run whole rather than assumed:

```
01-happy-path            61 pass
02-signup-validation     35 pass
03-login-lockout         29 pass
04-tokens-verification   35 pass
05-session-csrf          32 pass
06-onboarding-edges      36 pass
```

Two scripts failed on the first pass and neither was a regression: `04` and `06` tripped the
password-reset limit, which is 3/hour and is the one budget `application-local.yml` does not raise.
Re-run with `LIGHTMOVE_AUTH_RATE_LIMIT_PASSWORD_RESET_REQUESTS_PER_HOUR=100`, both are clean. Noted in
`e2e/README.md` already.

One harness fact learned and now documented in the script header: **`10-role-invariants.sh` is not
idempotent.** It promotes, demotes and removes people, and its last-admin cases only mean anything
when the workspace starts with exactly one admin. Re-run `fixtures.sh` before re-running it — running
it twice against one cast produces eight failures that look alarming and mean nothing.

---

## Everything still holds

Re-verified from scratch on the rebuilt API, not carried over from run 1: no self-promotion by any
role; the `roles` claim re-read live in both directions on an unrefreshed token; `LAST_ADMIN` and
`MEMBER_LEADS_PROJECTS`; all eleven cross-tenant id cases answering `404`; the pure client scoped to
one mandate, read-only, absent from the roster body, and cut off the instant they are detached; and
the workspace-delete gate in both directions.

## Still open

- **C3** — the portal sidebar, by choice.
- **Google OAuth** — untestable locally, unchanged from `auth-findings.md`.
- Nothing else.
