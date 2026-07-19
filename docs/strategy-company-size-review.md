# Code Review — Strategy: Company Size Scope (frontend + backend)

**Commit reviewed:** `182e896` "company size and revenue changes. first draft" (unpushed, branch `feature/project-strategy-company-size`)
**Scope:** 20 files, +689 / −19. Backend (Spring) adds a company-size axis to the strategy; frontend adds the panel, nav wiring, and a mirrored band catalog.
**Reviewer:** Claude (Opus 4.8)

---

## What it does

Adds a second scope section — **Company Size** — onto the existing 1:1 `app_lm_strategy` row built in V11 for sectors.

- **Two axes:** `EMPLOYEE` (headcount) and `REVENUE`, each a fixed catalog of bands (`EmployeeBand`, `RevenueBand` enums) mirroring the distinct `employee_range` / `revenue_range` values of the company universe.
- **Storage:** `app_lm_strategy_company_size` element collection (V12), one row per *selected* band. Presence is selection — no `selected` column, mirroring the design note in the migration.
- **API:** new `PUT /strategy/company-size` gated by `PROJECT_EDIT` (same gate as `/sectors`); `StrategyResponse` gains `employee` / `revenue` string arrays carrying only selected band values.
- **Frontend:** `CompanySizePanel` renders both axes from a client-side catalog (`companySizeBands.ts`); `StrategyNav` now switches panels in place with per-section live counts; `SectorChip` generalized into a shared exported `Pill`.
- **Tests:** integration (round-trip, replace, unknown/duplicate rejection, auth gate, empty seed), a JS drift guard on the catalog, and two `StrategyPage` behavior tests.

Design mirrors the sector precedent (V11) closely — same replace-list snapshot model, same "reject duplicates in service, not via unique index" rationale.

---

## Strengths

- **Convention-faithful.** Package layout (`constant` / `model` / `dto` / `service`), enum-as-catalog pattern, `@Enumerated(STRING)`, class-level docs explaining *why*, migration prose matching V11's voice — all on-house. `PROJECT_EDIT` gate reuse is correct and tested.
- **Tenant isolation intact.** `putCompanySize` routes through `load(projectId, workspaceId)`; workspace id comes from the principal, never the path. Cross-tenant masking already covered by the existing suite.
- **Transactionality correct.** `get`/`putCompanySize` are `@Transactional`, so lazy `sizeBands` loads inside the session — no `LazyInitializationException`.
- **Server-authoritative ordering.** `employeeValues` / `revenueValues` iterate enum declaration order and filter by the selected set, so response order is stable regardless of storage/request order. Test confirms out-of-order input comes back catalog-ordered.
- **Validation layered well.** DTO `@NotNull` + `@Size` ceilings (8 / 7 = exact catalog sizes) reject malformed shapes at the edge; unknown/duplicate band values rejected in the service against the enums with `VALIDATION_FAILED`.
- **Frontend stale-closure safe.** `toggleBand` reads `draftRef.current`, not the captured `draft`. Two independent autosave debouncers each write section-scoped PUTs; each PUT reads the full row server-side, so no cross-section clobber.
- **Drift guard.** `companySizeBands.test.ts` pins the frontend catalog to the backend enum values — a real regression net given the two-sided mirror.

---

## Findings

### 1. Employee band boundary overlaps at 10,000 (low — latent)
`EmployeeBand.java:44-45`
```java
B_5001_10000("5001-10000", 5001, 10000),   // maxCount inclusive
B_10000_PLUS("10000+", 10000, null);        // minCount 10000
```
`maxCount` is documented inclusive, so **10,000 belongs to both bands**. Harmless today (bounds unused), but the javadoc says the bounds exist precisely so "a later count query drops in without re-deriving them" — that query would double-count a 10,000-employee company if both bands are selected. Should be `B_10000_PLUS(..., 10001, null)`, or make `maxCount` exclusive to match revenue.

### 2. Employee vs revenue bound semantics diverge (low — document it)
`EmployeeBand.maxCount` is **inclusive**; `RevenueBand.maxUsd` is **exclusive** (per its own javadoc, half-open). Two axes carrying the same "bounds for the future count query" with opposite conventions is a trap for whoever writes that query. Either align them, or add a one-line note on `CompanySizeAxis` flagging the asymmetry deliberately.

### 3. Company-size selection does not affect the estimate (observation)
`StrategyPage.tsx:155-160` — `EstimateBanner` counts only sectors/tags; `estimate` query ignores `draft.employee` / `draft.revenue`. Selecting bands changes the nav count but not the "companies matched" banner. Consistent with "first draft" (the enum docs defer the count query to a later session), but the panel visually implies scope narrowing it doesn't yet perform. Worth a TODO or a note so it isn't mistaken for a bug later.

### 4. Shared `Pill` exported from `ChipGroup.tsx` (nit)
`ChipGroup.tsx:89` — `Pill` is now the shared chip across Sector Scope and Company Size but lives in `ChipGroup.tsx`, imported by `CompanySizePanel`. Fine functionally; consider hoisting to its own `components/Pill.tsx` since it's no longer sector-specific. Low priority.

---

## Risk / correctness / security

- **Correctness (shipped path):** none found. Round-trip, replace, ordering, rejection all covered and behave.
- **Security:** no new surface — same gate, same principal-derived workspace scoping, no raw SQL. Band strings never hit SQL (resolved to enum names before persist).
- **Performance:** two lazy collections on one row = two extra selects on read; negligible at this scale. Element-collection replace is a delete-all + re-insert, same as sectors — fine for a small fixed catalog.
- **Migration:** V12 numbering clean, shape parity with V11, `ON DELETE CASCADE` + axis CHECK constraint present. `varchar(32)` comfortably fits the longest enum name (`B_5001_10000`).
- **Test coverage:** strong for behavior. Gap: no test asserts an enum→wire→DB round-trip *edge* (e.g. the open-ended top bands), but the round-trip test with `10000+` / `<5M` effectively exercises them.

---

## Recommendation

**Approve with minor fixes.** Solid, convention-faithful extension of the sector pattern; no shipped-path defect. Address finding #1 (the 10,000 overlap) before the count query lands — cheapest to fix now while the bounds are unused — and add a line documenting the #2 asymmetry. #3 and #4 are non-blocking.

---

## Resolution (applied)

All four findings addressed:

1. **10,000 overlap — fixed.** `EmployeeBand.B_10000_PLUS` now starts at `10001` (the band below owns
   `10000` via its inclusive upper), so the bounds are disjoint. Comment on the constant explains the
   `"10000+"` label is the warehouse range string, not a literal lower bound.
2. **Bound-semantics asymmetry — documented.** `CompanySizeAxis` javadoc now states the deliberate
   convention split (employee inclusive `[min, max]`, revenue half-open `[min, max)`) and warns the
   future count query to apply each axis's convention; `EmployeeBand.maxCount()` cross-references it.
3. **Estimate ignores bands — noted in code.** Added a comment in `StrategyPage.tsx` at the panel render
   flagging that the count query is sectors/tags-only until a later session, so it isn't read as a bug.
4. **Shared `Pill` — hoisted.** Moved to its own `components/Pill.tsx`; `ChipGroup` and `CompanySizePanel`
   import it from there.

Verification after fixes: `apps/web` — `tsc` clean, `vitest` 29/29; `apps/api` — recompiles clean (enum
bound change touches no tested path; integration suite unaffected).
