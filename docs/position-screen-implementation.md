# Position Screen — Implementation

> **Update (2026-07-17, V8):** two refinements after first ship — see
> [Post-ship refinements](#post-ship-refinements-v8) at the end.
> **Update (2026-07-17, V9):** package/reporting field refinements — see
> [Package & reporting field refinements](#package--reporting-field-refinements-v9).

**Status: shipped (2026-07-17).** The Position page — the default tab of the project workspace per
`claude-design/Project.dc.html` — is built end to end: a new `app_lm_position` table, its REST
surface under `/api/v1/projects/{projectId}/position`, and the deep-linkable `/projects/:projectId`
shell that hosts it in the SPA. One project is one mandate is one position; before this work the
database held only `app_lm_project.position_title`, and the frontend had no project-detail route at
all (project detail was a drawer).

This document is the record of what was built and why. It supersedes nothing in
[role-model-proposal.md](role-model-proposal.md); it consumes the RBAC layer that shipped there.

---

## Scope

**In:** the Position page and the project workspace shell needed to host it. One position per
project, seeded at creation from a role-template library, edited by debounced autosave, lockable
once its criteria and competency weighting are the benchmark, unlockable by a project admin.

**Out (placeholders only):** Strategy, Sourcing, Candidates, Outreach, Reports. Their tabs exist in
the shell and render a "not built yet" empty state — we do not build ahead of the mockups. No
pipeline tables, no AI drafting.

## Product decisions (owner-confirmed this session)

1. **Smart-fill = deterministic templates, not AI.** A new position brief arrives *drafted*, not
   blank: a role-template library matches the position title (CFO, CEO, COO, CTO/CIO, CHRO, CMO,
   CRO, or a generic executive fallback) and seeds the narrative, candidate criteria (flagged
   `from_brief`), and both competency panels pre-balanced to exactly 100%. No LLM dependency — it
   works offline and is fully testable.

2. **AI draft-from-brief is a later phase, explicitly not this session.** The seam is left ready:
   the `from_brief` flag on criteria, the narrative field, and the single seed path
   (`PositionService.seedFor`) are exactly what an AI drafter will populate later *instead of* the
   static templates. Nothing else changes when it lands.

3. **Locking freezes the whole brief, and only a project ADMIN can unlock.** Every write — scalars,
   package, benefits, confidentiality, criteria, competencies — rejects with `409 POSITION_LOCKED`
   while locked. Unlock is a separate ADMIN-only action, not part of `PROJECT_EDIT`, because a
   locked brief is the reference downstream candidate scoring rests on and reopening it invalidates
   that benchmark.

---

## Backend

Package `app.lightmove.api.project` (the feature template), plus small additions to `core`.

### Database — `V7__position.sql`

A separate 1:1 table keeps the project-list query lean; the brief is a large, sparse document only
one screen reads. The three child lists are *owned ordered values* (replace-list writes), so they
carry no identity beyond their slot — composite PK `(position_id, sort_order)`, `ON DELETE CASCADE`,
no timestamps.

| Table | Holds |
|---|---|
| `app_lm_position` | The scalar brief: 1:1 `project_id` (unique FK), `mandate_reason` (CHECK enum), `internal_context`, `narrative`, org fields (`reports_to`, `direct_reports`, `team_size`, `location`, `employment_type`, `start_target`), package (`salary_min/max bigint`, `currency`, `notice`, `bonus_target`, `ltip`), `confidential`, `locked_at`/`locked_by`, plus the `BaseEntity` trio (`created_at`/`updated_at`/`version`) and the shared touch trigger. |
| `app_lm_position_benefit` | `label varchar(80)`, ordered. |
| `app_lm_position_criterion` | `text varchar(300)`, `mode` (CHECK `REQUIRED`/`PREFERRED`), `from_brief`, ordered. |
| `app_lm_position_competency` | `panel` (CHECK `TECHNICAL`/`BEHAVIOURAL`), `name`, `weight integer` (CHECK 0–100), ordered — one list for both panels; `panel` is a field, `sort_order` spans the whole collection. |

The migration also seeds the new RBAC action: `INSERT` `('PROJECT','POSITION_UNLOCK', …)` into
`app_lm_action` and grants it to the PROJECT `ADMIN` role only in `app_lm_role_action`. `integer`
(not `smallint`) for `weight`/`sort_order` so the test profile's `ddl-auto: validate` matches Java
`int` exactly.

### Model layer

- Constants: `MandateReason`, `CriterionMode`, `CompetencyPanel` (in `project/constant`).
- Embeddables: `PositionCriterion`, `CompetencyRow` (`@Embeddable`, `@Enumerated(STRING)`).
- `Position extends BaseEntity` — three `@ElementCollection(fetch = LAZY)` lists with
  `@CollectionTable` + `@OrderColumn(name = "sort_order")`. No public setters; intent-named mutators
  only: `forProject(...)` factory, `apply(PositionDetails)`, `replaceCriteria(...)`,
  `replaceCompetencies(...)`, `lock(userId, at)`, `unlock()`, `isLocked()`.
- `PositionDetails` — a record for the scalar snapshot, shared by the update path and the template
  seed.
- `PositionRepository` — `findByProjectId(UUID)`. No workspace-scoped finder: a position is only
  ever reached through its project, which the service has already scoped to the caller's workspace.

### Template library — `PositionTemplates`

`static TemplateSeed forTitle(String positionTitle)` does a case-insensitive keyword match over a
small curated catalog (most-specific first, generic executive fallback). Each template supplies a
narrative skeleton, 2–3 required + 1 preferred criteria all `fromBrief = true`, both panels
pre-balanced to exactly 100, a `reportsTo` suggestion, and `employmentType`/`currency` defaults. The
CFO template is the mockup's own content verbatim. `PositionService.seedFor` combines the template
with the client's `hqCountry` to prefill `location`. Deterministic, no AI.

### Service + controller

`PositionService` — every method opens with `requireProject(projectId, workspaceId)` (the same
404-masking, principal-scoped lookup as `ProjectService`; the workspace id is never taken from the
path).

- `get` — find-or-lazy-seed. The lazy seed covers projects created before V7 without a SQL
  backfill, which would otherwise have to duplicate the Java template-matching logic in SQL.
- `update` / `putCriteria` / `putCompetencies` — all reject with `POSITION_LOCKED` when locked
  (whole-brief freeze); replace-list semantics for the two lists. No 100%-total check on writes —
  autosave must be free to persist half-balanced panels mid-typing; only `lock` validates.
- `lock` — validates both panels sum to 100 **and** ≥1 `REQUIRED` criterion, else
  `POSITION_NOT_READY`; `409 POSITION_LOCKED` if already locked.
- `unlock` — `400` if not locked; otherwise clears the lock.
- `seedFor(Project)` — called by `ProjectService.create` (which already loads the client) so a
  brand-new project's brief is drafted from birth. No dependency cycle: `PositionService` never
  references `ProjectService`.

`PositionController` — `@RequestMapping("/api/v1/projects/{projectId}/position")`:

| Endpoint | Authorisation |
|---|---|
| `GET` | `@workspaceAuth.can(principal, 'PROJECT_BROWSE')` — whoever sees the project list may read its brief |
| `PUT`, `PUT /criteria`, `PUT /competencies`, `POST /lock` | `@projectAuth.can(principal, #projectId, 'PROJECT_EDIT')` |
| `POST /unlock` | `@projectAuth.can(principal, #projectId, 'POSITION_UNLOCK')` — ADMIN-only via the catalog, plus the standing workspace-admin bypass |

### Constants touched in `core`

- `ErrorCode`: `POSITION_LOCKED` (409), `POSITION_NOT_READY` (409).
- `AuditEventType`: `POSITION_UPDATED`, `POSITION_LOCKED`, `POSITION_UNLOCKED`.
- `ProjectAction`: `POSITION_UNLOCK` — and `RbacCatalogTest`'s grant map updated so the enum↔seed
  drift guard stays honest.

### API surface — why snapshot PUTs

Writes are whole-section PUTs, not field-diff PATCHes. The screen always holds the complete
document and autosaves whole sections, so a debounced flush of the section is simpler and
idempotent, and it sidesteps PATCH's null-ambiguity (you cannot clear `startTarget` when `null`
means "unchanged"). `@Version` on the entity guards torn concurrent writes.

---

## Frontend

New feature folder `apps/web/src/features/position/`, plus the shell in `components/layout`.

### API + pure libs

- `api/types.ts`, `api/positionApi.ts` — `POSITION_KEY(projectId)`, `getPosition`, `putPosition`,
  `putCriteria`, `putCompetencies`, `lockPosition`, `unlockPosition` over the shared `request<T>()`.
- `lib/rebalance.ts` — pure port of the mockup's proportional slider rebalance (redistributes the
  delta across the other rows, keeps the total constant, lands rounding drift so it is exact).
- `lib/readiness.ts` — `readiness()` (panel totals, `hasRequired`, `ready`) for the lock checklist,
  and `completion()` (0–100) for the hero's completion chip.
- `lib/useAutosave.ts` — debounce (~700 ms), flush-on-unmount, flush-on-demand, exposing
  `status: idle | saving | saved`.
- `lib/errorCodes.ts` extended with copy for the two new codes.

All three libs are unit-tested (`rebalance.test.ts`, `readiness.test.ts`).

### Shell + routes

- `components/layout/ProjectLayout.tsx` (following the `SettingsLayout` precedent): breadcrumb
  topbar `Projects / {client} / {position title}`, a sidebar with the "All projects" back link, the
  stage badge, and the mockup's nav groups — **Mandate** (Position, Strategy), **Companies**
  (Sourcing), **People** (Candidates, Outreach, Reports). The project is resolved from the cached
  `PROJECTS_KEY` list query by `useParams`; a deep link shows a spinner while the list loads and
  only redirects to `/` once the id is confirmed absent. It hands the routed page the resolved
  project via router `Outlet` context.
- `Sidebar` gained an optional `header` slot (the stage badge); `Topbar` gained `ProjectBreadcrumb`;
  `Icon.tsx` gained the six mockup nav glyphs.
- `routes.tsx`: `/projects/:projectId` (index = `PositionPage`) plus five placeholder tab routes,
  all under `RequireWorkspace` + `ProjectLayout`.
- `ProjectDrawer` gained an **"Open project →"** button navigating into the shell (the drawer
  stays — it is still the quick view from the list).

### PositionPage

`pages/PositionPage.tsx` loads the brief, then hands a `PositionEditor` a snapshot to draft against
(remounted via `key` on project + lock-state so it resyncs after a lock, including one made in
another tab). There is no Save button anywhere: each section's draft autosaves as a snapshot PUT —
scalars, criteria and competencies independently — and the hero shows the collective Saving…/Saved
state. Cards, all built from the existing UI kit over `tokens.css` (so light and dark come free):

- `PositionHero` — title/client/reports-to/location, chips (Seniority N-1, comp range, target
  start, employment type), confidentiality toggle (instant flush), Draft/✓ Locked badge, the live
  save indicator, and the **completion %** chip.
- `MandateContextCard` — reason select + internal-context textarea, "Internal only" badge.
- `IdealProfileCard` — narrative textarea.
- `ReportingStructureCard` — the org row (reports-to → this role → direct reports) + inline field
  grid; target start is a native date input.
- `PackageCard` — thousands-formatted salary inputs, a **currency select**, notice/bonus/LTIP,
  benefit chips (Enter adds, ✕ removes).
- `CriteriaCard` — inline rows, "From brief" tag, Required/Preferred segmented toggle, add/remove.
- `CompetencyPanel` (×2, technical = sky, behavioural = amber) — name/number/slider with
  `rebalance()`; total badge green only at exactly 100.
- **Locked = whole page read-only**: a single `<fieldset disabled>` freezes every control at once;
  the Unlock button lives outside it and is shown only when the current user is a workspace or
  project admin (the server enforces regardless).
- `LockFooter` — unlocked shows the live **readiness checklist** (✓/✗ per rule with current totals)
  and the Lock button, disabled until ready; locked shows the green banner + Unlock.

State wiring: `useQuery(POSITION_KEY)` + draft `useState`, three mutations wrapped in `useAutosave`,
`setQueryData` on success, `toast(messageFor(error))` on failure — and on `POSITION_LOCKED` it also
invalidates the query (another tab locked it). `PositionPage.test.tsx` covers seeded render,
debounced criterion autosave, the lock-disabled-until-ready gate, the read-only locked state, and
admin-only Unlock.

---

## Verification

- Backend: `cd apps/api && ./mvnw test` — **111 tests green**, including 10 new
  (`PositionFlowIntegrationTest`, `PositionAuthorizationIntegrationTest`) covering template seeding,
  snapshot round-trips, the lock gate, whole-brief freeze, admin-only unlock, lazy re-seed for
  legacy projects, and cross-tenant 404 masking.
- Frontend: `npx vitest` — **57 tests green** (18 new); `tsc -b` and `vite build` clean.
- End-to-end (real API + SPA, driven live): CFO project seeded the finance template with the
  client's HQ as location; scalar/criteria/competency PUTs round-tripped; lock → 200, post-lock
  writes → `409 POSITION_LOCKED`, unlock → 200. Probes held: unbalanced-panel lock →
  `POSITION_NOT_READY`, lowercase currency → field-level 400, ghost project id → 404,
  unlock-when-unlocked → 400, unrecognised title → generic template (still lockable), weight 150 →
  field-level 400. Browser: logged in through the real login screen, opened `/projects/:id`, saw
  the page render to match the mockup, and watched the narrative edit flip Saving… → Saved.

A local verify recipe was captured at `.claude/skills/verify/SKILL.md` (launch commands, the
`LIGHTMOVE_EMAIL_PROVIDER=log` override that keeps the real Resend key from firing, the CSRF/bearer
flow, and the Playwright handle).

---

## Repo-specific gotchas honoured

- Jackson 3 (`tools.jackson.*`) and Boot 4 starter names throughout.
- `ddl-auto: validate` in the test profile — `@ElementCollection` mappings match the DDL exactly
  (`@OrderColumn` names, `@Column(name="label")` on the benefits `List<String>`, `integer` weights).
- No new *role* — reusing `PROJECT_BROWSE`/`PROJECT_EDIT` — but the new `POSITION_UNLOCK` action is
  an INSERT migration + enum constant + `RbacCatalogTest` grant-map update, exactly as the RBAC
  contract requires.
- `@PreAuthorize` action strings resolve via `ProjectAction.valueOf` — every endpoint is exercised
  in the flow tests so a typo would fail loudly.
- Replace-list PUTs are Hibernate delete-all-reinsert — fine at this scale; do not add per-row
  endpoints without revisiting the embeddable choice.
- Debounced autosave means one `POSITION_UPDATED` audit event per flush, never per keystroke.

---

## Follow-ups

- **AI draft-from-brief** — populate `seedFor`'s output (narrative + `from_brief` criteria +
  competencies) from a pasted brief instead of the static template. Confirmed as a later phase.
- The five placeholder tabs (Strategy, Sourcing, Candidates, Outreach, Reports) — build when their
  mockups are taken on.
- Team avatar stack in the project breadcrumb (mockup shows one; deferred as polish).

---

## Post-ship refinements (V8)

Two owner-requested corrections, shipped as `V8__position_field_cleanup.sql` (added, not an edit to
V7 — V7 was already applied to the dev DB, and editing an applied migration breaks Flyway's
checksum on the next boot).

### 1. Employment type is a fixed set, not free text

V7 made `employment_type` a free-text `varchar(80)` (following the mockup's inline input); it is a
small closed vocabulary in practice, and structured data filters better downstream. Now the enum
`EmploymentType` — `FULL_TIME_PERMANENT`, `FIXED_TERM_CONTRACT`, `PART_TIME`, `INTERIM`,
`RETAINED_ADVISORY` — with a `CHECK` constraint (V8 maps the one seeded value
`'Full-time, permanent'` → `FULL_TIME_PERMANENT` and nulls any hand-typed value). Backend: the enum
constant, `@Enumerated(STRING)` on the entity, and `EmploymentType` through `PositionDetails` / the
DTOs. Frontend: an `EmploymentType` union, `EMPLOYMENT_TYPE_LABELS` (in
`features/position/lib/labels.ts`, shared by the hero chip and the card), and the
`ReportingStructureCard`'s inline input becomes an inline `<select>`.

### 2. One target date, on the project — not two

V7 gave the position its own `start_target`, unlinked from `app_lm_project.target_date` — so the
date entered in the New-project modal never appeared on the Position screen, and the screen's "Target
start" was a second, independent date. Unified on **`project.target_date` as the single source of
truth**: V8 **drops `app_lm_position.start_target`**. The Position screen's "Target start" now reads
and writes the project's date — `PositionService` loads the project alongside the position (a private
`Brief` record), `toResponse` sources `startTarget` from `project.getTargetDate()`, and the scalar
`update` writes `request.startTarget()` to `project.setTargetDate(...)` (same `PROJECT_EDIT` gate as
`PATCH /projects/{id}`). The Position page invalidates `PROJECTS_KEY` after a scalar save so the
list/drawer "Target" column reflects an edit made on the Position screen. Editing the date in either
place now changes the one value. `PositionResponse.startTarget` kept its name (the screen's label is
"Target start"); only its source moved.

Tests updated: the flow test asserts the seeded/round-tripped `employmentType` enum values and that a
`startTarget` set via the position PUT surfaces as the **project's** `targetDate` on
`GET /api/v1/projects` — proving the two are one field. The page test asserts employment type renders
as a labelled select.

---

## Package & reporting field refinements (V9)

An owner review of the Package and Reporting cards turned several free-text fields into typed inputs,
fixed a benefits-not-saving bug, and polished display. Shipped as `V9__position_structured_fields.sql`
(added, not an edit to V7/V8).

- **Team size & Direct reports → integers.** Entity `String → Integer`, DTOs `Integer @Min(0)`, the
  org-chart "Direct reports" box renders the number. Frontend: a new `NumberInput` (digits only, no
  separator — the plain sibling of `PackageCard`'s thousands-formatted `MoneyField`).
- **Notice period → number + unit.** Dropped the free-text `notice`; added `notice_value integer` +
  `notice_unit` (`NoticeUnit` enum, `WEEKS`/`MONTHS`, CHECK-constrained). UI is a `NumberInput` + a
  Weeks/Months inline select.
- **Bonus target → percentage of base.** Dropped free-text `bonus_target`; added `bonus_target_pct
  integer` (CHECK 0–100). UI is a `NumberInput` with a trailing `% of base`, clamped to 100.
- **Target start displays as "15 Sep 2026".** New `FormattedDateField` shows `formatDate(value)` over
  a transparent native `<input type="date">` — the native picker still opens on click, keyboard entry
  still works, and no date library is added.
- **Benefits: bug fix + preset picker.** The add-input previously committed **only on Enter**, so
  typing a benefit and clicking away silently dropped it — the reported "not saving". The new
  `BenefitsField` commits on Enter **or blur**, is backed by a `<datalist>` of common presets
  (`features/position/lib/benefits.ts` — housing/car/schooling/flights/insurance/…), accepts custom
  values, dedupes case-insensitively, and its pills wrap and grow (`whitespace-normal break-words`) so
  a long benefit reads on multiple lines instead of overflowing.

`ltip` stayed free text (not flagged). `completion()` in `lib/readiness.ts` now counts `noticeValue`
and `bonusTargetPct`. Tests: the flow test's scalar PUT sends and asserts the new shapes; a page test
asserts a benefit typed and **blurred** (no Enter) reaches `putPosition` — the guard for the bug fix.
