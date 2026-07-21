# Sector + Company Size scoped filters → Sourcing results

## Context

The Strategy screen (`apps/web/src/features/strategy/`, backed by `V11__strategy.sql` /
`V12__strategy_company_size.sql`) already lets a user build a Sector scope (direct/adjacent/inferred
chips) and a Company Size scope (Employee/Revenue band pills), autosaving both to
`app_lm_strategy_sector` / `app_lm_strategy_company_size`. What this plan built:

1. A **"Go to sourcing"** button on Strategy (present in the mockup, `claude-design/Project.dc.html`
   lines 303–306, was absent from the real `EstimateBanner`/`StrategyPage`).
2. A real **Sourcing screen** — previously `/projects/:projectId/sourcing` rendered
   `ProjectPlaceholderPage`. It now shows the companies matching the project's saved Sector + Company
   Size scope.
3. The backend query capability to actually filter `app_lm_companies` by both scopes together —
   previously `CompanyQueryService.estimate()` only understood sectors/tags; company-size was left out
   on purpose (`StrategyPage.tsx:211-213` flagged this as deferred), and there was no endpoint that
   returned company *rows* at all, only counts.

Per CLAUDE.md, the Sourcing screen stays scoped to what's asked: a plain filtered listing (name, sector,
size, location). No fit score, tiers, or triage actions (add-to-universe/shortlist/decline/comment) —
those read from candidate/pipeline tables that don't exist yet.

**Confirmed decisions** (asked and answered before writing the original plan):
- Company Size axes combine with **AND** when both are selected (employee-band selection AND
  revenue-band selection); bands *within* one axis stay OR'd, matching how sector labels already OR.
- The Strategy live-estimate banner also factors in Company Size (closing the known gap), since the
  same query logic was being built anyway.
- Sourcing shows a **plain filtered list only** in the first pass (fit score/tiers/triage excluded).

**STATUS: implemented and tested** (backend `mvn test` green, frontend `vitest`/`tsc -b`/lint clean).
See "Follow-up" below for the one open item raised after review.

## Why the Sourcing endpoint is project-scoped (not another `CompanyReferenceController` route)

`ProjectAction.WORK_EXECUTE`'s Javadoc already names **sourcing** as content it covers ("Work the
mandate: read its strategy and position brief, sourcing, triage, candidates, notes"), and
`StrategyController`'s own class doc says a mandate's scope is "team content, not browsable to the
whole workspace." `CompanyReferenceController`'s existing routes (`/sectors`, `/estimate`) are gated by
the *workspace*-level `PROJECT_BROWSE` because they take caller-supplied sector/tag lists — they don't
expose which sectors *this project* chose. The Sourcing endpoint resolves company rows from a specific
project's *stored* strategy, so it lives under `/api/v1/projects/{projectId}/...`, gated `WORK_EXECUTE`,
deriving its filters server-side from the saved `Strategy` — never from client-supplied sector/tag
arrays. This also sidesteps any label-count abuse vector: the criteria are already ceiling-bound by
`PutSectorsRequest`/`PutCompanySizeRequest`.

This makes the company-row query itself (`company` feature, shared reference-data access to
`app_lm_companies`) get called from the `project` feature's new Sourcing service — a narrow, deliberate
cross-feature dependency, the same shape as the already-documented exceptions (`AuthResponseAssembler`,
the `rbac/` access services) to the "features don't depend on each other" rule. Called out in
`SourcingService`'s class doc.

## Backend changes (implemented)

No new migration — `app_lm_companies` already had everything needed indexed: `primary_industry`,
`employee_count`, `revenue_usd` (btree), `industry_tags` (GIN).

### 1. `company/service/CompanyQueryService.java` — extended the query engine

- Extracted the sector/tag WHERE-fragment building into a shared private `buildWhere` helper used by
  both the count query and the new row query.
- Added band-range filtering via a public `Range(Long min, Long max)` record — `company` stays agnostic
  of `project`'s `EmployeeBand`/`RevenueBand` enums; callers resolve a band to numeric bounds first.
  `axisClause` builds `(column >= :min AND column <= :max)`-style OR'd clauses per axis (employee
  inclusive `[min,max]`, revenue half-open `[min,max)`), AND'd across axes, AND'd with the sector/tag
  scope. Either axis is omitted entirely when its band list is empty.
- `estimate(sectors, tags, employeeRanges, revenueRanges)` extended with the new params. Added
  `List<CompanyRow> search(sectors, tags, employeeRanges, revenueRanges, page, size)` (`ORDER BY name`,
  `LIMIT`/`OFFSET`), reusing the same WHERE builder. `CompanyRow` is a new public record
  (id, name, domain, primaryIndustry, hqCountry, hqCity, employeeRange, revenueRange).

### 2. `company/controller/CompanyReferenceController.java` — wired bands into `/estimate`

Added repeated `employeeBand`/`revenueBand` params. Resolved via small private static
`EMPLOYEE_BANDS`/`REVENUE_BANDS` lookup maps duplicated in the controller (not imported from
`project.constant`, keeping `company` standalone) — unknown band value → `VALIDATION_FAILED` (400).

### 3. `project` feature — the actual Sourcing endpoint

- **`project/dto/SourcingDtos.java`**: `CompanyResultDto(id, name, domain, sector, employeeRange,
  revenueRange, location)`, `SourcingResponse(companies, totalCount, page, size)`.
- **`project/service/SourcingService.java`**: `get(workspaceId, projectId, page, size)` —
  `requireProject` (same pattern as `StrategyService.load()`), loads `Strategy`, derives selected
  sector labels (`DIRECT`+`ADJACENT`, selected) and tags (`INFERRED`, selected) from the entity directly,
  derives selected `EmployeeBand`/`RevenueBand` ranges from `strategy.getSizeBands()`, calls
  `companyQueryService.search()` + `estimate()` (reused directly as the total-count call, no separate
  `searchCount` wrapper), maps to DTOs. No scope at all → empty result without touching the database.
- **`project/controller/SourcingController.java`**: `GET /api/v1/projects/{projectId}/sourcing`,
  `@PreAuthorize("@projectAuth.can(principal, #projectId, 'WORK_EXECUTE')")`, `page`/`size` params
  (default `0`/`25`, `size` capped at 100 in the service).

## Frontend changes (implemented)

### 1. Strategy screen — button, and bands wired into the estimate

- `companiesApi.ts`: `getEstimate`/`ESTIMATE_KEY` extended with `employeeBands`/`revenueBands`.
- `StrategyPage.tsx`: estimate query now includes `draft.employee`/`draft.revenue`; stale "does not yet
  narrow" comment removed. Added `useNavigate()` → `onGoToSourcing` prop.
- `EstimateBanner.tsx`: new optional `onGoToSourcing` prop rendering the mockup's "Go to sourcing →"
  button (`Project.dc.html:303-306`) in the same banner row; banner copy updated to match the mockup.

### 2. New `features/sourcing/` folder (mirrors `features/strategy/`)

- `api/types.ts` / `api/sourcingApi.ts`: `CompanyResult`, `SourcingResponse`,
  `getSourcingCompanies(projectId, page, size)`, `SOURCING_KEY(...)`.
- `pages/SourcingPage.tsx`: reads `project` via `useOutletContext`; paginated `useQuery`; renders as a
  plain `<table>` (Company/Sector/Employees/Revenue/Location); Prev/Next pager; "Edit criteria in
  Strategy" link back; empty state when `totalCount === 0`.
- `pages/SourcingPage.test.tsx`: mocked `sourcingApi`, asserts rows render, pager requests the next
  page, empty state and its link, edit-criteria link.

### 3. Routing

`routes.tsx`: `/projects/:projectId/sourcing` now renders `SourcingPage` instead of
`ProjectPlaceholderPage`.

## Tests (implemented, all passing)

- `CompanyReferenceIntegrationTest`: `/estimate` band-param cases — AND across axes, OR within an axis,
  open-ended top band, unknown band → 400.
- `SourcingFlowIntegrationTest`: no-scope → empty page without a query; sector+bands AND'd correctly;
  pagination slices in stable name order with correct `totalCount`; cross-tenant project masked (404).
- `SourcingAuthorizationIntegrationTest`: unseated member → 403; RESEARCHER/LEAD/project-ADMIN → 200;
  workspace-admin bypass → 200. No RBAC catalog change (`WORK_EXECUTE` reused), so `RbacCatalogTest`
  untouched.
- `StrategyPage.test.tsx`: extended with a case asserting `getEstimate` is called with the toggled
  employee band, and a case asserting the "Go to sourcing" button navigates.
- `SourcingPage.test.tsx`: as described above.

Verification run: `cd apps/api && ./mvnw test` (Docker/Testcontainers, all green) and
`cd apps/web && npx vitest run` (105/105 passing) + `npx tsc -b` (clean) + `npx oxlint` (clean).

---

## Follow-up: add Card view to Sourcing (matching the mockup default)

**Why this came up:** the shipped `SourcingPage.tsx` only has the mockup's **List** view. The mockup's
Sourcing screen actually defaults to a **Card** view (`Project.dc.html:819`, `srcView: 'card'`) with a
Card/List toggle (lines 391-398); its card layout (lines 408-469) additionally shows a Fit score, a
Direct/Adjacent/AI-Inferred tier badge, and a "Criteria Met" column — all tied to Position-criteria
matching and triage state that are out of scope (confirmed: sector + size only, no fit/tiers/triage).
Rather than build a partial version of that richer card, the first pass shipped List-only without
surfacing the Card option at all — asked the user about it after they noticed, and they confirmed they
want Card view (and the toggle) added, using only the fields already available.

**Change**, all in `apps/web/src/features/sourcing/pages/SourcingPage.tsx` (no backend or API shape
changes — same `CompanyResult` data already fetched):

- Add local `view` state, `useState<"card" | "list">("card")` — Card is the default, matching the mockup.
- Add a small toggle control near the existing "Edit criteria in Strategy" link (mockup lines 391-398:
  two icon buttons, active one highlighted) calling `setView("card")` / `setView("list")`.
- Keep the existing `<table>` rendering as the **List** branch (`view === "list"`).
- Add a **Card** branch (`view === "card"`, the default): a `grid grid-cols-[repeat(auto-fill,minmax(360px,1fr))]`
  of cards (mockup line 409), one per company:
  - An initials tile (small helper deriving initials from `company.name`, e.g. first letters of the
    first two words — mirrors the mockup's `this.initials(name)` at line 413/1034; no existing frontend
    helper to reuse since Sourcing is new).
  - Name, with a meta line "`location · sector`" underneath (mockup line 419/1035).
  - A compact two-row "Employees / Revenue" snapshot (subset of the mockup's Scale Snapshot, dropping
    Region/Sector there since they're already in the meta line just above).
  - No Fit box, tier badge, Criteria Met column, description, or the four triage icon buttons — all
    explicitly out of scope.
- Empty state (`totalCount === 0`) is shared by both views, unchanged.

**Tests**: extend `SourcingPage.test.tsx` —
- A card renders by default (company name + meta line visible without clicking anything).
- Clicking the List toggle switches to the existing table rendering (column headers appear); clicking
  Card switches back.
- Existing pagination/empty-state/edit-criteria-link tests keep passing unchanged (pagination controls
  stay visible under both views).

**Verification**:
1. `cd apps/web && npx vitest run src/features/sourcing/pages/SourcingPage.test.tsx`
2. `npx tsc -b` for the whole frontend.
3. Manual: open a project's Sourcing tab with a scope that matches companies — confirm Card view renders
   by default, the toggle switches to List and back, and both reflect the same data/pagination.

**STATUS: implemented and tested.**

---

## Follow-up 2: Card view parity — match tier badge, full Scale Snapshot, placeholder triage buttons

**Why:** after seeing the Card view, the user flagged it's still missing several things the mockup's card
has: which sector-scope bucket each company matched through (the Direct/Adjacent/AI-Inferred badge next
to the name), the full four-row Scale Snapshot (Revenue, Employees, Region, Sector — today's card only
shows Revenue+Employees), and the row of triage icon buttons. Confirmed scope with the user:
- The badge is the mockup's real Direct/Adjacent/Inferred classification (not just re-showing the sector
  string), which needs a small backend change — today `SourcingService` merges Direct+Adjacent sector
  labels into one list before querying, so which bucket actually matched a given returned company isn't
  tracked past that point.
- Scale Snapshot gets all 4 rows, literal mockup parity, even though Region/Sector repeat the meta line.
- The three triage buttons — **comment, add to universe, shortlist** (mockup's fourth button, decline, is
  explicitly NOT wanted) — render as inert placeholders. They depend on candidate/pipeline tables that
  don't exist yet (per CLAUDE.md), so they're disabled with a tooltip, not wired to anything.
- Scoped to the **Card view only** — the List/table view is unchanged.

### Backend

**`company/service/CompanyQueryService.java`**:
- `CompanyRow` gains a `matchTier` field (`String`: `"DIRECT" | "ADJACENT" | "INFERRED"`).
- `search(...)` signature changes from one merged `sectors` list to separate `directSectors` and
  `adjacentSectors` lists (plus the existing `tags`/ranges/page/size) — internally concatenates them for
  the existing `buildWhere` sector/tag OR clause (unchanged filtering semantics), and additionally adds a
  `CASE WHEN primary_industry IN (:directSectors) THEN 'DIRECT' WHEN primary_industry IN (:adjacentSectors)
  THEN 'ADJACENT' ELSE 'INFERRED' END AS match_tier` to the SELECT list. Each `WHEN` branch is only added
  when its list is non-empty (mirrors `buildWhere`'s existing "never pass an empty list to `IN`" style,
  rather than relying on Spring's empty-collection `IN` handling). The final `ELSE 'INFERRED'` is safe
  because the WHERE clause already guarantees a row here matched on sector-or-tag, so anything not caught
  by the two `WHEN`s must have matched via a tag.
- `estimate(...)` is untouched (still takes one combined sectors list — total count doesn't need a tier).

**`project/service/SourcingService.java`**:
- Replace the single `labelsOf(strategy, DIRECT, ADJACENT)` call with two: `directSectors` and
  `adjacentSectors` separately. Pass both into `companies.search(...)`; for the `estimate(...)` total-count
  call, concatenate them back into one list exactly as before (no behavior change there).
- `toDto` copies `row.matchTier()` across.

**`project/dto/SourcingDtos.java`**: `CompanyResultDto` gains a `matchTier` field, same three values.

### Frontend

**`apps/web/src/features/sourcing/api/types.ts`**: `CompanyResult.matchTier: "DIRECT" | "ADJACENT" | "INFERRED"`.

**`apps/web/src/features/sourcing/pages/SourcingPage.tsx`**, Card branch only:
- A small tier badge next to the company name: "Direct" (sky), "Adjacent" (amber), "AI Inferred"
  (muted/text3) — same three labels/colors as the mockup (`Project.dc.html:1036-1038`).
- Expand the Scale Snapshot from the current 2-item grid to 4 stacked label/value rows, in the mockup's
  order: Revenue, Employees, Region, Sector (`Project.dc.html:1049-1054`) — Region and Sector reuse the
  same `location`/`sector` fields already shown in the meta line.
- A row of three icon buttons at the card's bottom — Comment, Add to universe, Shortlist (mockup icons at
  lines 452-460, dropping the fourth/Decline button) — each `disabled` with a `title` tooltip ("Not
  available yet"), no `onClick` handler: they're visible placeholders, not dead-looking dark patterns, but
  they must not silently no-op on a real click either.

### Tests

- **Backend**: extend `SourcingFlowIntegrationTest` with a case seeding one company matching via a Direct
  sector, one via an Adjacent sector, and one via an Inferred tag (selecting all three in the PUT calls),
  then asserting each returned company's `matchTier` in the JSON response.
- **Frontend**: extend `SourcingPage.test.tsx`'s mock companies with `matchTier` values and add cases
  asserting: the tier badge text renders for each company, the Scale Snapshot shows Region and Sector rows
  in addition to Revenue/Employees, and the three placeholder buttons render and are disabled.

### Verification

1. `cd apps/api && ./mvnw test -Dtest=SourcingFlowIntegrationTest,SourcingAuthorizationIntegrationTest`
   then the full `./mvnw test`.
2. `cd apps/web && npx vitest run src/features/sourcing/pages/SourcingPage.test.tsx`, then full
   `npx vitest run` + `npx tsc -b` + `npx oxlint`.
3. Manual: open Sourcing for a project with Direct, Adjacent, and Inferred criteria all contributing
   matches — confirm each card shows the right tier badge, all four Scale Snapshot rows, and the three
   disabled placeholder buttons with tooltips.

**STATUS: implemented and tested.** Full backend `mvn test` green (including the new
`matchTierReflectsWhichBucketMatched` case); frontend `vitest run` 19/19 files, 109/109 tests, clean
`tsc -b` and `oxlint`.

---

## Follow-up 3: "Criteria Met" as scope-filter checkmarks (not a Position fit score)

**Why:** the user asked why the card had no "Scale Snapshot" header (a plain omission, fixed inline) and
no "X of 6 met" — the latter would need Position-brief fit scoring, which doesn't exist. Talking it
through, the user proposed a different, buildable reading: since only 2 of the Strategy's 6 scope
categories are implemented (Sector, Company Size), the count should reflect *those*, and they want each
card to show, with a checkmark, exactly which sector matched and which employee/revenue band matched —
not a fixed "of 6". Landed on: count only the categories actually active for this query (so "2 of 2" if
just Sector + Employee are set, "3 of 3" if Revenue is set too — employee and revenue counted as separate
rows since that's what's literally shown), always fully met by construction (a company only appears in
results because it already satisfies every active category — this is a scope readout, not a fit score).

**Backend**: `SourcingDtos.AppliedFilters(boolean sector, boolean employee, boolean revenue)` added to
`SourcingResponse`; `SourcingService.get()` computes each flag from whether that category's resolved
lists/ranges are non-empty. `CompanyReferenceIntegrationTest`/`SourcingFlowIntegrationTest` extended:
`appliedFilters` reflects exactly what was PUT to Strategy, independent of any given company's own data.

**Frontend**: `SourcingPage.tsx` card body becomes a two-column grid mirroring the mockup — **Criteria
Met** (left: a `{n} of {n} met` header, then one checkmarked row per active category using the company's
own sector/employeeRange/revenueRange) and **Scale Snapshot** (right: unchanged, all 4 raw rows
regardless of what's active). `api/types.ts` gained `AppliedFilters`.

**Tests**: `SourcingPage.test.tsx` covers the full-3-active case and the "revenue not applied" case
(no Revenue checkmark row, but Scale Snapshot's own Revenue row is unaffected).

**STATUS: implemented and tested.** Backend `mvn test` green; frontend `vitest run` 19/19 files,
111/111 tests, clean `tsc -b`/`oxlint`.

---

## Follow-up 4: verify the `main` merge (644f334) didn't break Sourcing/Strategy

**Why:** the user merged `main` into this branch (bringing in a separately-developed Geography +
Ownership Type strategy scope, per `git log`: PR #12 `feature/project-strategy-geography-ownership`) and
asked to confirm no breaking changes, plus run the full backend/frontend integration suites.

**Findings so far (direct read, not yet confirmed by the dispatched Explore agents):**
- `StrategyPage.tsx` merged cleanly with this session's Sourcing wiring — `onGoToSourcing`, the
  `useNavigate` import, and the `getEstimate`/`ESTIMATE_KEY` calls with `draft.employee, draft.revenue`
  are all intact alongside the new `markets`/`structures`/`putGeography`/`putOwnership`/`ScopeChipPanel`
  code. No conflict markers, nothing obviously severed.
- Found one stale comment: `StrategyPage.tsx` lines 260-263 now read "the count query still reads
  sectors/tags only" — false; the query on line 198-200 already passes `draft.employee, draft.revenue`
  (this session's own work). The comment is a leftover from the pre-merge geography/ownership branch,
  which didn't know about the size-band wiring already landed here. Needs a wording fix, not a code fix.
- `apps/web/src/features/strategy/api/types.ts`'s `Strategy` interface gained `markets: string[]` and
  `structures: string[]`. The Sourcing feature doesn't import this type at all (its own
  `CompanyResult`/`SourcingResponse` types are independent), so no direct frontend coupling risk there.

**Backend verdict (confirmed by Explore agent — full findings, not re-summarized here): clean, no
breaking changes.**
- The merge (644f334 = this session's Sourcing tip `5caa591` + main's PR #12 `b795801`,
  "feature/project-strategy-geography-ownership") touched 9 backend files, all net-new additions:
  `GeographyMarket.java`, `OwnershipStructure.java` (new constants), changes to `Strategy.java`
  (new `marketNames`/`structureNames` `@ElementCollection` fields + `replaceMarkets`/`replaceStructures`),
  `StrategyController`/`StrategyService`/`StrategyDtos` (new `PUT .../geography` and `PUT .../ownership`
  endpoints, both reusing the existing `PROJECT_EDIT` gate — no new RBAC action), a new migration
  `V13__strategy_geography_ownership.sql` (two new tables, `app_lm_strategy_geography` /
  `app_lm_strategy_ownership`, no changes to any table Sourcing touches), and two brand-new Strategy test
  files.
- **Zero overlap** with any Sourcing/company file (`CompanyQueryService`, `CompanyReferenceController`,
  `CompanyDtos`, `SourcingService`, `SourcingController`, `SourcingDtos`, or either Sourcing test) — none
  of them appear in the merge diff at all, so nothing here needed a conflict resolution.
- `SourcingService.java` compiles and behaves identically post-merge: it only calls
  `strategy.getSectors()`/`getSizeBands()`, both untouched. No RBAC catalog changes, so `RbacCatalogTest`
  is unaffected.
- **Scope gap, not a bug**: Sourcing still only filters by sector + size — it does not yet filter by the
  new market/ownership scope. This is explicitly deliberate per `OwnershipStructure`'s own Javadoc ("the
  mapping belongs to the session that builds the sourcing filter"), i.e. the geography/ownership PR
  intentionally left sourcing-side filtering for later. Not something to fix as part of this verification
  — flag it to the user as a known, intentional gap only.

**Frontend verdict (confirmed by Explore agent): clean, no breaking changes.**
- The merge's frontend diff is confined entirely to `apps/web/src/features/strategy/**`: new
  `putGeography`/`putOwnership` in `strategyApi.ts`; `markets`/`structures` added to the `Strategy`
  interface; a new generic `ScopeChipPanel.tsx` component + `catalogOption.ts` type; new
  `geographyMarkets.ts`/`ownershipStructures.ts` catalogs (+ their own tests); `StrategyNav`'s enabled-set
  gained `ownership`/`location`. `StrategyPage.tsx`/`.test.tsx` were the two files the merge commit itself
  flagged as conflicted — confirmed cleanly resolved: this session's "Go to sourcing" button/wiring
  survived intact alongside main's new Ownership/Location panels, nothing dropped or duplicated.
- **Zero overlap with Sourcing**: `features/sourcing/**` imports nothing from `features/strategy/**` (no
  `Strategy` type, no `markets`/`structures` anywhere in it), and neither `features/sourcing/**` nor
  `app/routes.tsx` appear anywhere in the merge's changed-file list. Nothing here needs a code change to
  compile or pass.
- Confirms the same product gap as the backend agent, from the frontend side: `AppliedFilters` (both
  `SourcingDtos.java` and `sourcing/api/types.ts`) only knows `sector`/`employee`/`revenue` — Geography/
  Ownership selections don't narrow Sourcing results or appear in its Criteria Met rows yet. Explicitly
  called out in `StrategyPage.tsx`'s own comment ("Wiring the band bounds, market codes and ownership
  structures into it is the sourcing session"). **Confirmed out of scope for this verification pass** —
  it's a known, pre-existing gap the geography/ownership PR left for later, not something the merge broke.

## Overall verdict: no breaking changes from the merge

Both backend and frontend merges were clean unions (only `StrategyPage.tsx`/`.test.tsx` had real
conflicts, both correctly resolved). Nothing in the Sourcing feature — API, service, controller, DTOs,
frontend page, API client, or tests, on either side of the stack — was touched by, or needs to change
because of, the merge. The only follow-up item is cosmetic: a stale code comment.

## Remaining steps

1. Fix the stale comment in `apps/web/src/features/strategy/pages/StrategyPage.tsx` (currently around
   where the `estimate` query is defined) — it claims the estimate "still reads sectors/tags only" and
   doesn't mention company-size, which is inaccurate now that `draft.employee`/`draft.revenue` are already
   wired into it (this session's own earlier work). Reword to say sector + company-size are wired in, and
   geography/ownership are the pieces still left for a later sourcing pass.
2. Run the full backend integration suite: `cd apps/api && ./mvnw test` (Docker/Testcontainers required —
   confirm Docker is up first).
3. Run the full frontend suite: `cd apps/web && npx vitest run`, then `npx tsc -b` and `npx oxlint` for
   good measure given the merge touched several Strategy files.
4. Report pass/fail plainly; fix anything a suite surfaces (not expected, given the analysis above, but
   verify rather than assume).

**STATUS: complete.** Stale comment fixed. Full backend `mvn test` green (all suites, including the two
new geography/ownership Strategy tests main brought in, alongside every Sourcing test). Full frontend
`vitest run` 21/21 files, 119/119 tests green; `tsc -b` clean; `oxlint` clean (only pre-existing warnings
in unrelated files — `only-export-components` in `ui/index.tsx`, `Toast.tsx`, `Badge.tsx`, `Icon.tsx`,
`Stepper.tsx`, `AuthProvider.tsx`, `ProjectsTable.tsx` — none touched by this session or the merge).
No breaking changes from the merge.

---

## Follow-up 5: verify a second `main` merge (04f99b4) with real conflicts

**Why:** the user merged `main` again — this one brought PR #13
(`feature/project-strategy-target-offlimit`: Target List Seeding + Off-limits company lists, plus a
generic company name-search/browse endpoint for the picker) — and this time it had **real conflicts**,
resolved via commit `4bbd449` ("resolving merge conflicts") before the merge commit `04f99b4`. The
conflicted files, per the merge commit message itself: `CompanyReferenceController.java`,
`CompanyQueryService.java`, `CompanyReferenceIntegrationTest.java`, and `StrategyPage.tsx` — the first
three are files this session built/extended directly (the Sourcing query engine), so this needed careful
verification, not just a scan.

**Findings**: the conflict resolution was substantively correct — every one of this session's additions
(`Range`, `CompanyRow` incl. `matchTier`, `estimate(...)`, `search(directSectors, adjacentSectors, tags,
...)`, `matchTierExpression`, `buildWhere`, `axisClause`, the `/estimate` band params + `EMPLOYEE_BANDS`/
`REVENUE_BANDS` lookup) survived intact, correctly interleaved with main's new company picker code
(`browse(...)`, `search(String query, int limit)` — a distinct overload, no signature clash, `refsByKeys`,
`CompanySearchOrder`, `/companies/search` endpoint). Confirmed via full reads of all three conflicted
backend files plus `StrategyPage.tsx`. Found and fixed pure **merge-resolution leftovers** (none were
functional bugs — `mvnw compile` succeeded before the cleanup too, since Java tolerates a duplicate
identical import and unused private members are only a style issue, not an error):
- `CompanyQueryService.java`: a duplicated `import java.util.ArrayList;` line, and the class Javadoc had
  two copies of the same "tenant isolation" sentence stitched together by the resolution — removed the
  duplicate import, de-duplicated the doc comment.
- `CompanyReferenceController.java`: an unused `MAX_ESTIMATE_LABELS` constant and its now-dead
  `lombok.RequiredArgsConstructor` import (main's merge replaced the hardcoded limit with a configurable
  `estimateConfig.maxLabels()` and switched the class to an explicit constructor) — removed both; also
  fixed one import-order slip (`LightMoveProperties` had landed between `CompanyQueryService` and
  `CompanyQueryService.Range`).
- `StrategyPage.tsx`: no fix needed — this session's earlier stale-comment fix (Follow-up 4) and the
  "Go to sourcing" wiring both survived this second merge correctly, alongside the new
  `CompanyListPanel`/targets/offLimits code.

`SourcingService.java`/`SourcingController.java`/`SourcingDtos.java` were **not** in the conflict list and
remained byte-for-byte unaffected; `Strategy.java` gained `targetCompanies`/`offLimitsCompanies` fields
(via a new `StrategyCompanyRef` embeddable) alongside the untouched `sectors`/`sizeBands` fields
`SourcingService` actually reads, so no behavior change there either.

**Verification**: `mvnw compile`/`test-compile` clean before *and* after the cleanup edits (confirming
the leftovers were cosmetic, not errors) — then the full suites:
- Backend `mvn test`: green, including this session's Sourcing tests, the two Strategy geography/
  ownership tests, and all of the new target-list/off-limits/company-search tests from PR #13.
- Frontend: `vitest run` 22/22 files, 131/131 tests green; `tsc -b` clean; `oxlint` clean (same
  pre-existing unrelated warnings as before, nothing new).

**STATUS: complete.** Conflicts were resolved correctly; the only issues found were cosmetic merge
leftovers (duplicate import, dead constant, duplicated doc sentence), now cleaned up. No breaking changes
to the Sourcing feature.

---

## Follow-up 6: extend Sourcing to Geography, Ownership, Target Seeding, Off-limits + revenue-desc sort

**Why:** the two merges (Follow-ups 4 and 5) brought in four more Strategy scope categories that Sourcing
still ignores — `Strategy.marketNames`/`structureNames` (Geography/Ownership, single-valued fixed
catalogs, same shape as the sector/size scopes Sourcing already reads) and `Strategy.targetCompanies`/
`offLimitsCompanies` (`StrategyCompanyRef` snapshots — manually-picked companies to force-include or
force-exclude). The user also wants Sourcing's results sorted by revenue, highest first.

### Confirmed decision

**Ownership Type has no clean mapping to any `app_lm_companies` column** — `OwnershipStructure`'s own
Javadoc explicitly says none of `org_type`/`ownership`/`ipo_status`/`is_public` line up with its 5 values
and punts the decision. Rather than guess, **Ownership is tracked but not filtered on yet** — same honest
"known gap" treatment already used elsewhere in this codebase (the estimate-banner comment this session
already wrote). No `AppliedFilters.ownership` field, no Ownership row in Sourcing's Criteria Met column,
no narrowing in the query. Revisit once real column values are confirmed against the live database.

### Geography → `hq_country` (+ `markets`), confirmed mapping

`GeographyMarket.value()` is the exact ISO-3166 alpha-2 code stored in `app_lm_companies.hq_country`
(confirmed by both the enum's Javadoc and the `V13` migration's own comment — "the join key the sourcing
filter will run on"). The `ScopeChipPanel` copy for this section reads "Headquarters **or major
operating base** in region" — which maps HQ to `hq_country` and "operating base" to the GIN-indexed
`markets text[]` column. So a company matches a selected market when
`hq_country IN (:markets) OR markets && ARRAY[:markets]::text[]`, mirroring the existing sector/tag OR
pattern. Wired into both `/companies/estimate` (Strategy's live banner, closing the gap the current
comment already flags) and Sourcing.

### Target Seeding / Off-limits → force-include / force-exclude by `(source, source_id)`

The `CompanyListPanel` copy already states the intended semantics precisely: Target companies are
"included in results directly, **bypassing Required filters**"; Off-limits companies are "excluded from
sourcing" outright. So the row-inclusion logic becomes:

```
(scope-match OR is-a-target) AND NOT is-off-limits
```

where `scope-match` is today's existing AND of (sector-or-tag OR-group) × employee × revenue × geography
— unchanged in shape, geography just joins the other two as one more optional AND'd axis. A project with
no sector/tag scope at all but a populated target list still shows those targets (matching "bypasses
required filters" literally); an empty scope **and** an empty target list still shows nothing, unchanged
from today. This only touches `search()`/`estimate()` — `refsByKeys`/`StrategyService`'s existing
target/off-limits validation on the write side is untouched.

A target-seeded company needs its own **`matchTier` value, `"TARGET"`** — the existing `CASE` only
classifies rows that matched via the sector-or-tag path (Direct/Adjacent/Inferred), but a row that's here
purely because it's on the target list didn't necessarily match any sector/tag at all. The target check
becomes the first `WHEN` in the `CASE`, ahead of Direct/Adjacent, so a company that's both a sector match
*and* a target still reads as `TARGET` (its provenance is the more informative fact here).

### Revenue-desc sort

`search()`'s `ORDER BY` changes from `name` to `revenue_usd DESC NULLS LAST, name` — the same convention
`CompanyQueryService.browse()` already uses for its default order, just applied to Sourcing's row query
too. Postgres can `ORDER BY` a column that isn't in the `SELECT` list, so `CompanyRow`/`CompanyResultDto`
don't need a new numeric revenue field — `revenueRange` stays the only revenue value shown, sorting just
changes the order the already-fetched page arrives in.

### Backend changes

**`company/service/CompanyQueryService.java`**:
- New `record ScopeFilter(List<String> directSectors, List<String> adjacentSectors, List<String> tags, List<Range> employeeRanges, List<Range> revenueRanges, List<String> markets, List<CompanyKey> targetKeys, List<CompanyKey> offLimitsKeys)` — the current 7-parameter `search()`/4-parameter `estimate()` signatures were already getting unwieldy; bundling them into one object here is a genuine readability fix given the growth, not a speculative abstraction. `estimate(ScopeFilter)` and `search(ScopeFilter, int page, int size)` replace the current positional-parameter versions.
- `buildWhere` reworked: still returns `null` (→ empty result, no query) only when there is truly nothing to show — no sector/tag/employee/revenue/geography scope **and** no targets. Otherwise builds `(scopeMatch OR targetClause)` (either half omitted if absent) `AND NOT offLimitsClause` (omitted if the off-limits list is empty). The target/off-limits clauses use the same `(source, source_id) IN (SELECT * FROM unnest(ARRAY[:sources]::text[], ARRAY[:sourceIds]::text[]))` pattern `refsByKeys` already uses.
- `matchTierExpression` gains the `TARGET` `WHEN` (checked first, same key-pair `IN unnest` test), otherwise unchanged.
- `search()`'s SQL: `ORDER BY revenue_usd DESC NULLS LAST, name` instead of `ORDER BY name`.

**`company/controller/CompanyReferenceController.java`**: `/estimate` gains a repeated `market` param,
validated against a small local `Set<String>` of the six ISO codes (same "duplicate the catalog locally,
don't import `project`'s enum" convention `EMPLOYEE_BANDS`/`REVENUE_BANDS` already use) → 400 on an
unrecognized code. Builds a `ScopeFilter` with empty `targetKeys`/`offLimitsKeys` (this endpoint is
workspace-generic, not project-scoped — it has no strategy to read lists from).

**`project/service/SourcingService.java`**: resolve `strategy.getMarketNames()` (stored as enum *names*,
like the size bands — `GeographyMarket.valueOf(name).value()` to the wire ISO code, mirroring the
existing `EmployeeBand.valueOf(...)` pattern) and build `CompanyKey` lists from `getTargetCompanies()`/
`getOffLimitsCompanies()` (`StrategyCompanyRef` already carries `source`/`sourceId`). Assemble one
`ScopeFilter`, pass it to both `search()` and `estimate()`. `AppliedFilters` gains `geography` (true iff
`markets` non-empty — same "is the list non-empty" convention `employee`/`revenue` already use, not
"did it actually narrow anything"). No `ownership` field per the confirmed decision above.

**`project/dto/SourcingDtos.java`**: `AppliedFilters` gains `boolean geography`.

### Frontend changes

**Strategy screen** (closing its own "not yet wired" comment for geography):
- `companiesApi.ts`: `getEstimate`/`ESTIMATE_KEY` gain a `markets: string[]` param.
- `StrategyPage.tsx`: estimate query passes `draft.markets` too; the comment at the estimate-panel switch
  is reworded once more to say geography is wired, ownership is the one remaining gap.

**Sourcing screen**:
- `api/types.ts`: `MatchTier` gains `"TARGET"`; `AppliedFilters` gains `geography: boolean`.
- `SourcingPage.tsx`: `TIER_META` gains a `TARGET` entry (a distinct color from Direct/Adjacent/Inferred —
  proposing green, signaling "manually included" rather than "matched"); `criteriaRowsFor` adds a
  `Region` row (reusing the existing `company.location` field, same as Scale Snapshot's own Region row)
  when `appliedFilters.geography` is true.
- No off-limits UI needed — those companies are simply never returned, nothing to display.

### Tests

- **Backend**: extend `SourcingFlowIntegrationTest` — geography narrows results (`hq_country` match and
  `markets` array match, each independently sufficient); a target company appears even with no sector
  scope at all; an off-limits company is excluded even though it matches the scope; a company that is
  both a scope match and a target still reports `matchTier: "TARGET"`; revenue-desc ordering (plus a
  same-revenue tiebreak on name) replaces the existing alphabetical-order pagination test's fixture
  (seed distinct revenues so the expected order is unambiguous). Extend `CompanyReferenceIntegrationTest`
  for `/estimate`'s new `market` param (valid code narrows, unknown code → 400).
- **Frontend**: extend `SourcingPage.test.tsx` for the `TARGET` badge and the new Region criteria row;
  extend `StrategyPage.test.tsx`'s estimate-call assertion to include `markets`.

### Verification

1. `cd apps/api && ./mvnw test` — full suite.
2. `cd apps/web && npx vitest run` + `npx tsc -b` + `npx oxlint` — full suite.
3. Manual: a project with a Direct sector, a Geography market, a Target company outside that sector, and
   an Off-limits company inside it — confirm Sourcing shows the target (badge `TARGET`), excludes the
   off-limits company, shows a Region checkmark row, and orders everything by revenue descending.

**STATUS: implemented and tested.**
- Backend: `CompanyQueryService.ScopeFilter` record replaces the old positional params on
  `estimate`/`search`; `buildWhere` now builds `(scopeMatch OR targetClause) AND NOT offLimitsClause`,
  geography via `hq_country IN (:markets) OR markets && ARRAY[:markets]`, `matchTierExpression` checks
  target membership first (`'TARGET'`) ahead of Direct/Adjacent, `search()` orders by
  `revenue_usd DESC NULLS LAST, name`. `CompanyReferenceController`'s `/estimate` gained a `market` param
  validated against a local ISO-code set. `SourcingService` resolves `strategy.getMarketNames()` (enum
  names → ISO codes via `GeographyMarket.valueOf(...).value()`) and builds `CompanyKey` lists from
  `getTargetCompanies()`/`getOffLimitsCompanies()`. `AppliedFilters` gained `geography`; no `ownership`
  field, per the confirmed decision to leave Ownership unfiltered until real column values are checked.
- Frontend: `companiesApi.getEstimate`/`ESTIMATE_KEY` gained `markets`; `StrategyPage.tsx` passes
  `draft.markets` and its comment now says geography is wired, Ownership is the one remaining gap.
  `sourcing/api/types.ts`: `MatchTier` gained `"TARGET"`, `AppliedFilters` gained `geography`.
  `SourcingPage.tsx`: `TIER_META` gained a green `TARGET` entry; `criteriaRowsFor` adds a `Region` row
  (reusing `company.location`) when geography is applied.
- Tests: backend — geography narrows by `hq_country` or `markets` array (either sufficient), a target
  company appears with no sector scope at all, an off-limits company is excluded despite matching scope,
  a company that's both a scope-match and a target reports `TARGET`, revenue-desc ordering with a
  same-revenue name tiebreak (nulls last), `/estimate`'s new `market` param (valid narrows, unknown → 400).
  Frontend — Region checkmark row appears/count updates when geography is applied, `TARGET` badge renders.
  Full backend `mvn test` green; frontend `vitest run` 22/22 files, 134/134 tests, clean `tsc -b`/`oxlint`
  (only the same pre-existing unrelated warnings as before).

---

## Follow-up 7: why Sourcing's Criteria Met caps at 4, not 6

**Why this came up:** the user pointed at the Strategy sidebar (Sector Scope 7, Company Size 4, Location 3,
Ownership Type 3, Target List Seeding 2, Off-limits 1 — six populated categories) and asked why Sourcing's
"Criteria Met" card indicator never reads "6 of 6". Looked like a regression given Follow-up 6 above claims
Geography/Target/Off-limits were all wired in and "implemented and tested."

**Root cause — confirmed by direct code read, independently re-confirmed by an Explore agent: not a bug,
the count is architecturally capped at 4 by decisions already recorded above.**

- `SourcingDtos.AppliedFilters` (`apps/api/.../project/dto/SourcingDtos.java:24`) has exactly 4 boolean
  fields — `sector`, `employee`, `revenue`, `geography`. No field exists for ownership, target-seeding, or
  off-limits, so there's no room for a 5th or 6th flag in the type itself.
- `SourcingService.get()` (`SourcingService.java:81-82`) populates those 4 booleans from sectors/tags,
  employee bands, revenue bands, and markets. It does read `strategy.getTargetCompanies()`/
  `getOffLimitsCompanies()` (lines 71-72) — but only to build the query's `ScopeFilter` (which rows come
  back), never to set an applied-filter flag. Ownership (`strategy.getStructures()`) isn't read in this
  file at all.
- `SourcingPage.tsx`'s `criteriaRowsFor` (`SourcingPage.tsx:27-34`) can therefore only ever produce up to 4
  rows, and the card header (`SourcingPage.tsx:169`) is literally `{criteriaRows.length} of
  {criteriaRows.length} met` — self-referential, always "fully met" by construction, capped at 4 no matter
  what data comes back. It cannot render "6 of 6" under any input.

This is exactly what Follow-up 3 and Follow-up 6 above already decided, not a regression from either:
- Follow-up 3 scoped the count to "only the categories actually active for this query" among the
  categories implemented as company-row filters — never a fixed "of 6".
- Follow-up 6 explicitly deferred Ownership Type end-to-end ("tracked but not filtered on yet... No
  `AppliedFilters.ownership` field, no Ownership row in Sourcing's Criteria Met column") because
  `OwnershipStructure`'s Javadoc says none of `org_type`/`ownership`/`ipo_status`/`is_public` cleanly map to
  its 5 values — no real mapping was ever confirmed. `StrategyPage.tsx:348-351` carries the matching
  comment.
- Target List Seeding and Off-limits were never meant to produce a "criteria met" row at all — they're
  inclusion/exclusion overrides, not matched scope criteria. A target company can appear having matched
  *no* scope filter whatsoever (the whole point — "bypassing Required filters"), and an off-limits company
  is simply never returned, so there's nothing to check off for either one. Counting them as "met" would
  misrepresent what actually matched.

So today's true ceiling is **4 of 4** (Sector + Employees + Revenue + Region), reached only when all four
of those axes are populated on the Strategy. Ownership, Target Seeding, and Off-limits contribute to the
sidebar's "6 categories with data" but were never designed to add a Criteria-Met row — by prior, explicit
product decisions, not oversight.

**Decision (asked and confirmed with the user): document as expected, no code change.** Ownership stays
deferred until real column values are checked against the live database; Target/Off-limits stay
structurally different from "matched criteria" and won't get rows. `AppliedFilters`, `SourcingService`, and
`SourcingPage.tsx`'s `criteriaRowsFor`/count all stay exactly as they are.

**STATUS: complete.** Documentation-only — no backend or frontend files changed, nothing to test.

## Follow-up 8: Consolidate employee/revenue band catalogs; filter on range strings, not computed numeric bounds

## Context

The user found two related problems in the company-search backend while checking it against the live
`app_lm_companies` data (54,031 rows):

1. **Triplicated band catalogs, nothing pinning them together.** `EmployeeBand`/`RevenueBand`
   (`project/constant`) are the source of truth for Strategy's save/validate path and for
   `SourcingService` (which resolves a saved band to numeric bounds). But
   `CompanyReferenceController`'s `/companies/estimate` endpoint uses its own hand-duplicated
   `EMPLOYEE_BANDS`/`REVENUE_BANDS` maps — same wire values and bounds, typed out by hand a second time,
   with no test failing if the two ever drift (Strategy's live estimate banner and Sourcing's actual
   results would then silently disagree).
2. **Real data loss: 5,045 companies (9.3%) have `employee_count = 0` but `employee_range = '1-10'`.**
   Filtering computes numeric bounds (`employee_count BETWEEN 1 AND 10`) instead of matching the
   `employee_range`/`revenue_range` text columns directly — so a real "1-10 employees" company with an
   unset/zero raw count is silently dropped from that band, about 16% of the band gone. The band catalogs
   already match the warehouse's `employee_range`/`revenue_range` strings verbatim (confirmed against live
   data), so there's no need to compute anything — a plain `employee_range IN (:bands)` is both correct
   and simpler.

**Decision (asked and confirmed):** move `EmployeeBand`/`RevenueBand`/`CompanySizeAxis` from
`project/constant` into `company/constant` (next to `CompanySearchOrder`). These enums are really a
catalog of `app_lm_companies`' own columns, not a `project` concept — `company` should own them.
`project` already depends on `company` in the sanctioned direction (`SourcingService`, `StrategyService`
→ `CompanyQueryService.refsByKeys`), so `project` importing `company`'s enums needs no new architecture
exception; it removes the existing duplication rationale instead of reversing it. This is a pure
package move — the DB persists `StrategySizeBand.band`/`axis` as plain strings/enum-name text either
way (`@Enumerated(EnumType.STRING)` on `axis`, plain `String` on `band`), so there is no migration.

## Backend changes

### 1. Move the enums: `project/constant` → `company/constant`

- `EmployeeBand.java`, `RevenueBand.java`, `CompanySizeAxis.java` relocate as-is (package declaration
  only) to `apps/api/src/main/java/app/lightmove/api/company/constant/`.
- Delete the now-dead `minCount()`/`maxCount()` (`EmployeeBand`) and `minUsd()`/`maxUsd()` (`RevenueBand`)
  accessors — confirmed their only caller is `SourcingService`'s numeric-`Range` builder, which is being
  deleted in step 3. `value()`/`fromValue()` are what's left, and are all any caller needs once filtering
  is string equality.
- Fix the enums' own Javadoc, which currently says `value()` is "what a later count query filters
  `employee_count`/`revenue_usd` against" — that was already slightly aspirational and becomes actively
  wrong once the fix lands; reword to say it filters `employee_range`/`revenue_range` directly.
- Update every importer: `project/service/StrategyService.java`, `project/service/SourcingService.java`,
  `project/model/StrategySizeBand.java`, `project/dto/StrategyDtos.java` (Javadoc mention only) now import
  from `company.constant` instead of `project.constant`.

### 2. `company/service/CompanyQueryService.java` — string equality, no more computed bounds

- Delete the `Range` record and the `axisClause` method entirely — dead once filtering is string
  equality (nothing else uses numeric bounds; confirmed via grep, only `axisClause`/`ScopeFilter`
  referenced `Range`).
- `ScopeFilter`'s `employeeRanges: List<Range>` / `revenueRanges: List<Range>` become
  `employeeBands: List<String>` / `revenueBands: List<String>` — the raw wire-format strings
  (`"1-10"`, `"<5M"`, …) callers already have from `EmployeeBand.value()`/`RevenueBand.value()`.
- `buildWhere`: the two `axisClause(...)` calls become
  `employee_range IN (:employeeBands)` / `revenue_range IN (:revenueBands)`, each still omitted entirely
  when its list is empty (same "never hand an empty list to `IN`" convention already used for
  sectors/tags/markets). OR-within-axis is now just SQL `IN`; AND-across-axes is unchanged (still one
  more clause ANDed into the existing list).
- Update the class-level Javadoc's "callers resolve a band to its numeric bounds and pass a plain
  `Range`" line to describe passing the wire-format string instead.

### 3. `company/controller/CompanyReferenceController.java` — drop the duplicate maps

- Delete `EMPLOYEE_BANDS`/`REVENUE_BANDS` and the `Range`-typed `resolveBands` helper.
- Import `EmployeeBand`/`RevenueBand` (now same-feature, `company.constant`) and validate each
  `employeeBand`/`revenueBand` request value via `EmployeeBand.fromValue(value) != null` /
  `RevenueBand.fromValue(value) != null`, throwing the same `VALIDATION_FAILED` 400 on an unknown value.
  Pass the raw validated strings straight into `ScopeFilter`.
- `MARKET_CODES`/`GeographyMarket` has the identical duplication pattern but was **not** part of this
  request — leave it untouched; flag as a follow-up candidate only if the user wants it later.

### 4. `project/service/SourcingService.java` — resolve to wire strings, not `Range`

- Delete `rangesOf`, `employeeRange(EmployeeBand)`, `revenueRange(RevenueBand)` and the `Range` import.
- Replace with a `bandsOf(strategy, axis)` that collects the strategy's selected `StrategySizeBand`s for
  that axis and resolves each stored enum *name* to its wire string — `EmployeeBand.valueOf(band.getBand()).value()`
  for `EMPLOYEE`, `RevenueBand.valueOf(...).value()` for `REVENUE` — mirroring the exact pattern
  `StrategyService.employeeValues`/`revenueValues` already use for the same lookup.
- `ScopeFilter` construction passes these two string lists as `employeeBands`/`revenueBands`.

## Tests

- **`CompanyReferenceIntegrationTest.java`**: `companyWithSize(sector, employeeCount, revenueUsd)` seeds
  only the numeric columns today; since filtering now reads `employee_range`/`revenue_range`, the helper
  must seed those text columns too (independently of the numeric ones — reflecting that in real data
  they're separate, sometimes-inconsistent facts, which is exactly the bug). Update the existing
  size-band tests (`sizeBandsNarrowEstimateAndedAcrossAxes`, `bandsWithinAxisOr`,
  `openEndedTopBandHasNoUpperBound`) to seed the matching range string per row.
  **Add one new regression test** seeding a company with `employee_count = 0` but
  `employee_range = '1-10'`, asserting `employeeBand=1-10` still returns it — pinning the exact
  5,045-row bug the user found so it can't silently reappear.
- **`SourcingFlowIntegrationTest.java`**: same issue in `companyWithTags`/`company`/`companyWithTag`/
  `companyWithKey` — these seed `employee_count`/`revenue_usd` only. Update them to also set
  `employee_range`/`revenue_range` matching the band each test intends to hit.
  `resultsOrderByRevenueDescending` and `companyWithNullRevenue` are about `revenue_usd` **ordering**,
  not band-filtering — those stay as they are (revenue_usd is still the sort key; only band *filtering*
  changes to string equality).
- No RBAC catalog impact (no action changes), no frontend changes at all: the wire contract (querystring
  param names, JSON response shapes) is identical — this is a backend-only correctness + consolidation
  fix.

## Verification

1. `cd apps/api && ./mvnw test` — full suite, in particular
   `CompanyReferenceIntegrationTest`, `SourcingFlowIntegrationTest`, `SourcingAuthorizationIntegrationTest`,
   and `RbacCatalogTest` (unaffected, confirming no accidental catalog drift).
2. Confirm compilation picks up the package move cleanly across `project/service/StrategyService.java`,
   `project/service/SourcingService.java`, `project/model/StrategySizeBand.java`,
   `project/dto/StrategyDtos.java`, `company/controller/CompanyReferenceController.java`,
   `company/service/CompanyQueryService.java`.
3. Manual sanity check (optional but directly validates the bug fix): query
   `/api/v1/companies/estimate?sector=<something>&employeeBand=1-10` against the real `brightdata`-synced
   data before/after and confirm the count increases by roughly the 5,045 previously-dropped rows for
   that band (adjusted for whatever sector/tag scope is applied).

