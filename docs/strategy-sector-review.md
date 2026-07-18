# Code Review — Strategy: Sector Scope

Reviewed: `feature/project-stategy-sector` working tree (no PR open). Scope = the strategy
sector-scope feature: backend `project`/`company` packages, migration `V11`, and the
`apps/web/src/features/strategy` screen. Plan: [strategy-sector-plan.md](strategy-sector-plan.md).

## Overview

- **Migration V11** — `app_lm_strategy` (1:1 project, cascade delete, touch trigger, `@Version`) and
  `app_lm_strategy_sector` as an `@OrderColumn` element collection; `kind` CHECK constraint, deselected
  chips kept `selected=false`.
- **Strategy feature** (`project`) — `GET /strategy` (PROJECT_BROWSE, lazy-seeds empty),
  `PUT /strategy/sectors` (PROJECT_EDIT, whole-scope snapshot), audit `STRATEGY_UPDATED` with actor.
- **Company reference** (`company`) — `JdbcClient` read path over `app_lm_companies`, no entity:
  `/sectors`, `/sectors/suggestions`, `/estimate`. `SectorAdjacency` loads the curated JSON; inferred
  tags computed live from tag co-occurrence.
- **Frontend** — `StrategyPage` orchestrator (draft + autosave + live estimate), net-new
  `SectorCombobox`, `ChipGroup`, `StrategyNav`, `EstimateBanner`; `useAutosave` hoisted to `src/lib/`.

Overall: clean, convention-adherent, well-tested. No correctness or security blockers found. Items
below are minor.

## Strengths

- **Conventions honored throughout** — type-subpackage layout, `record` DTOs, constructor injection,
  class-level docs, Jackson 3 (`tools.jackson`), Boot-4 starters, no `@Data`/`@Builder`.
- **No SQL injection** — every `JdbcClient` query binds named params; list params expand safely
  (`IN (:sectors)`, `ARRAY[:tags]::text[]`).
- **Tenant isolation correct** — strategy load scopes through `findByIdAndWorkspaceId` from the
  principal; a foreign project 404s before any strategy row is touched. Cross-tenant test covers it.
- **List-binding trap avoided** — `orEmpty()` normalises a missing repeated param to `[]`, dodging the
  `@DefaultValue("")` → `[""]` trap called out in CLAUDE.md.
- **Duplicate guard lives in the service, not a unique index** — correctly reasoned: an element
  collection is rewritten in place mid-flush, so a non-deferrable index could fire on a transient state.
- **Estimate is a true union** (`count(*)` over `OR`, no double count) — test confirms.
- **Strong test coverage** — flow, authorization matrix, company reads, adjacency invariant (build-time
  closed-world check), plus frontend `fuzzy` / `mergeSuggestions` / `StrategyPage` tests.

## Findings (minor)

### 1. `/sectors/suggestions` has no label cap, unlike `/estimate`
[CompanyReferenceController.java:39](../apps/api/src/main/java/app/lightmove/api/company/controller/CompanyReferenceController.java#L39)

`estimate` guards with `MAX_ESTIMATE_LABELS` (100); `suggestions` accepts an unbounded `sector` list
that flows straight into `IN (:sectors)` and the co-occurrence aggregate. A member could pass hundreds
of sectors. Low risk (shared reference data, authenticated), but the two read endpoints should be
consistent — apply the same cap.

### 2. Snapshot PUT carries no version → silent last-write-wins
[StrategyDtos.java:34](../apps/api/src/main/java/app/lightmove/api/project/dto/StrategyDtos.java#L34)

`BaseEntity` has `@Version`, but the read-modify-write happens on the client (draft → PUT snapshot) and
the DTO surfaces no version, so optimistic locking never engages across the round trip. Two leads
editing the same project's scope concurrently clobber each other with no conflict. Acceptable for the
single-editor autosave model as designed; worth a note in the plan as a known limitation for when the
brief lock lands.

### 3. Read-only member gets an editable UI that 403s on every keystroke
[StrategyPage.tsx:107](../apps/web/src/features/strategy/pages/StrategyPage.tsx#L107)

A PROJECT_BROWSE-only member (no PROJECT_EDIT seat) sees fully interactive chips/typeahead; each edit
updates the local draft, autosaves, and the PUT 403s → error toast. The server gate is correct (auth
test confirms), but the client doesn't reflect the read-only state. Consider passing an `canEdit` flag
down to disable toggles/typeahead. UX nit, not a security issue.

### 4. Live estimate query is not debounced
[StrategyPage.tsx:100](../apps/web/src/features/strategy/pages/StrategyPage.tsx#L100)

Every chip toggle changes `selectedSectors`/`selectedTags` and fires a new `/estimate` request.
`keepPreviousData` smooths the UI, and toggles aren't rapid, so this is fine in practice — but rapid
refinement produces a request per toggle. A short debounce (mirroring the 700 ms autosave) would trim
chatter. Optional.

### 5. `@Size(max=160)` validates the raw label before `StrategySector.of` trims
[StrategyDtos.java:22](../apps/api/src/main/java/app/lightmove/api/project/dto/StrategyDtos.java#L22)

A 160-char label with leading/trailing whitespace is rejected pre-trim, then would have fit after trim.
Trivial edge; labels come from the taxonomy typeahead and won't hit it. No action needed.

## Notes (no change requested)

- **`GET /strategy` writes on read** — lazy-seeds a row inside a non-readonly `@Transactional`, so a
  PROJECT_BROWSE read issues an INSERT. Intended and documented; flagging only for visibility.
- **Merge effect autosaves suggestions** — picking a direct sector triggers the suggestions query,
  whose response merges into adjacent/inferred and autosaves. Guarded by `sameChips` against a loop and
  reads the draft through a ref to avoid a dependency cycle — correctly done.
- **`useAutosave` hoist** — moved to `src/lib/` and re-imported by `PositionHero`/`PositionPage`; the
  old `position/lib/useAutosave.ts` is deleted. Clean, shared-utility placement.

## Verdict

Ship-ready. None of the findings block. Address #1 (cap parity) as a small follow-up; record #2 and #3
as known limitations or quick follow-ups.
