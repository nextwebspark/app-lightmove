# Strategy — Company Size Scope (frontend + backend)

## Context

The Strategy page shipped its first section, **Sector Scope** (see `strategy-sector-plan.md`). This
session adds its second section, **Company Size**: two axes of toggleable band pills — **Employee**
(headcount) and **Revenue** — that the consultant selects/deselects, rendered under Sector Scope in the
same left-nav shell. Both are multi-select; a band is either in scope or not.

Unlike sectors (free-text snapshots + AI suggestions), the bands are a **fixed catalog** mirroring the
distinct `employee_range` / `revenue_range` values of the company universe (`app_lm_companies`) — so no
typeahead, no suggestions, and no persisted "rejected" rows.

**Confirmed decisions** (user):
- **In-page section switch** — one `/strategy` route; the left nav swaps the panel via local state
  (matches the mockup: one screen, shared universe). Not separate routes.
- **Backend enums are the source of truth** (validation + numeric bounds), **plus a frontend static
  mirror** of the catalog so the pills paint instantly on mount without waiting for the GET.
- Bands **will** feed the strategy-page company count later; the query is **next session**, but the enums
  carry numeric bounds now so it drops in cleanly. `CompanyQueryService.estimate` is untouched this session.

**Data reality** (queried live against `app_lm_companies`, ~54k rows):
- **Employee** (`employee_range`): `1-10`, `11-50`, `51-200`, `201-500`, `501-1000`, `1001-5000`,
  `5001-10000`, `10000+`
- **Revenue** (`revenue_range`): `<5M`, `5M-25M`, `25M-100M`, `100M-500M`, `500M-1B`, `1B-5B`, `5B+`

The mockup's bands (`500–1K`, `$1B–2B`, …) were **placeholder** and do not match the data — the real DB
values win, because they are what the count query will filter on. Migration = **V12**.

---

## 1. Migration — `apps/api/src/main/resources/db/migration/V12__strategy_company_size.sql`

```sql
CREATE TABLE app_lm_strategy_company_size (
    strategy_id uuid        NOT NULL REFERENCES app_lm_strategy (id) ON DELETE CASCADE,
    sort_order  integer     NOT NULL,
    axis        varchar(16) NOT NULL
        CONSTRAINT app_lm_strategy_company_size_axis_chk CHECK (axis IN ('EMPLOYEE', 'REVENUE')),
    band        varchar(32) NOT NULL,     -- enum name, e.g. B_51_200 / R_5M_25M
    PRIMARY KEY (strategy_id, sort_order)
);
```

- Second child off the **existing** `app_lm_strategy` 1:1 parent (V11) — no new parent table.
- **Only selected bands are stored** (presence = selection). No `selected` column: the full catalog
  renders from the enums, so there is no need to persist "off" rows (sectors keep those only because
  their rejected suggestions must stay visible).
- `band` holds the enum **name**, not the display label or raw range string; `axis` splits the one
  collection into its two dimensions, mirroring `kind` on `app_lm_strategy_sector`.
- **No unique `(strategy_id, axis, band)` index** — same Hibernate element-collection mid-flush trap V11
  documents; duplicates are rejected in the service (and cannot arise, the values being enums).

## 2. Band enums — `project/constant/` (source of truth)

- `CompanySizeAxis { EMPLOYEE, REVENUE }`.
- `EmployeeBand` — 8 constants `(value, minCount, maxCount)`, e.g. `B_51_200("51-200", 51, 200)`,
  `B_10000_PLUS("10000+", 10000, null)`. `value` == `employee_range` verbatim; bounds feed the future
  `employee_count` count query (null upper = open-ended top band). `fromValue(String)` for validation.
- `RevenueBand` — 7 constants `(value, minUsd, maxUsd)` in whole USD, e.g.
  `R_5M_25M("5M-25M", 5_000_000L, 25_000_000L)`, `R_5B_PLUS("5B+", 5_000_000_000L, null)`.

Constructor-injected final fields, accessors named as the field, no Lombok — matches `ErrorCode`.

## 3. Persistence — `project/model/`

- `StrategySizeBand` — `@Embeddable { CompanySizeAxis axis; String band }` (band = enum name),
  static `of(axis, band)`. Mirrors `StrategySector`.
- `Strategy` — a second `@ElementCollection List<StrategySizeBand> sizeBands`
  (`@CollectionTable(name = "app_lm_strategy_company_size")`, `@OrderColumn(name = "sort_order")`) plus
  `replaceSizeBands(...)` (clear + addAll).

## 4. DTOs — `project/dto/StrategyDtos.java` (additive)

- `StrategyResponse` gains `List<String> employee, List<String> revenue` — the **selected band values**
  per axis (range strings), ordered by enum declaration. The client renders the full catalog from its
  mirror and marks these in scope; no full-catalog DTO on the wire. Existing sector fields untouched.
- `PutCompanySizeRequest(@NotNull @Size(max=8) List<String> employee, @NotNull @Size(max=7) List<String> revenue)`
  — the selected values per axis.

## 5. Service — `project/service/StrategyService.java`

- `putCompanySize(userId, workspaceId, projectId, request, httpRequest)`: resolve each value via
  `EmployeeBand.fromValue` / `RevenueBand.fromValue` (unknown → `VALIDATION_FAILED`), reject intra-axis
  duplicates, build one ordered `List<StrategySizeBand>` (storing enum names), `replaceSizeBands(...)`,
  audit `STRATEGY_UPDATED` with `detail("section", "companySize")` (reused event type), return `toResponse`.
- `toResponse` extended: emits `employee`/`revenue` as selected values, ordered by enum declaration
  (empty selection ⇒ empty lists).

## 6. Controller — `project/controller/StrategyController.java`

- `PUT /api/v1/projects/{projectId}/strategy/company-size`, gated `@projectAuth.can(principal,
  #projectId, 'PROJECT_EDIT')` — same write gate as `/sectors`. GET already returns the whole strategy,
  now including the bands. No new RBAC action.

---

## 7. Frontend — `apps/web/src/features/strategy/`

- **`lib/companySizeBands.ts`** (new) — the static mirror: `EMPLOYEE_BANDS` / `REVENUE_BANDS` arrays of
  `{ value, label }`. `value` must equal the backend enum's `value()` verbatim; `label` is display-only
  (e.g. `"51–200"`, `"$5M–25M"`). This is the instant-paint catalog.
- **`lib/companySizeBands.test.ts`** (new) — **drift guard**: asserts the exact value sets/order against
  the backend enums (verified against the live DB). A backend add/rename → red test before a PUT silently
  400s on an unknown value.
- **`api/types.ts`** — `Strategy` gains `employee: string[]; revenue: string[]` (selected values).
- **`api/strategyApi.ts`** — `putCompanySize(projectId, employee, revenue)` → `PUT …/company-size`.
- **`pages/StrategyPage.tsx`** — `activeKey` state switches Sector ↔ Company Size in place; a **dedicated
  company-size autosave** (`putCompanySize`) independent of the sector autosave; `toggleBand(axis, value)`
  adds/removes a value from `draft[axis]`.
- **`components/StrategyNav.tsx`** — Sector Scope + Company Size now enabled/clickable with an `onSelect`
  and per-section count badges; the rest stay disabled.
- **`components/CompanySizePanel.tsx`** (new) — two band groups (Employees, Revenue) rendered from the
  static catalog, `selected` computed by membership, toggling on band `value`.
- **`components/ChipGroup.tsx`** — extracted the pill button as a shared exported `Pill`
  ({label, selected, onToggle}) used by both Sector chips and Company Size bands.

---

## Deliberately deferred / known trade-offs

- **Count/estimate unchanged this session.** Selected bands do not yet affect the "N companies match"
  banner. Next session wires `employee_count` / `revenue_usd` band bounds into `CompanyQueryService`;
  the enum bounds exist for exactly this.
- **Cross-section autosave race.** Sectors and Company Size debounce independently; editing both within
  ~700ms could 409 on the `Strategy` optimistic lock (surfaces as a toast, recovers on next edit). Low
  risk, matches the per-section snapshot model; serialize if it proves annoying.
- **Bands stored only when selected** — deselecting all clears the axis; the catalog always re-renders
  from the enum/constant, so nothing is lost.

## Files

| Change | Path |
|---|---|
| Migration (new) | `apps/api/src/main/resources/db/migration/V12__strategy_company_size.sql` |
| Enums (new) | `apps/api/…/project/constant/{CompanySizeAxis,EmployeeBand,RevenueBand}.java` |
| Embeddable (new) | `apps/api/…/project/model/StrategySizeBand.java` |
| Aggregate (edit) | `apps/api/…/project/model/Strategy.java` |
| DTOs (edit) | `apps/api/…/project/dto/StrategyDtos.java` |
| Service (edit) | `apps/api/…/project/service/StrategyService.java` |
| Controller (edit) | `apps/api/…/project/controller/StrategyController.java` |
| Backend tests (edit) | `apps/api/…/project/Strategy{Flow,Authorization}IntegrationTest.java` |
| Band catalog (new) | `apps/web/…/strategy/lib/companySizeBands.ts` (+ `.test.ts`) |
| Types / API (edit) | `apps/web/…/strategy/api/{types,strategyApi}.ts` |
| Page / Nav (edit) | `apps/web/…/strategy/pages/StrategyPage.tsx`, `components/StrategyNav.tsx` |
| Panel (new) | `apps/web/…/strategy/components/CompanySizePanel.tsx` |
| Pill (edit) | `apps/web/…/strategy/components/ChipGroup.tsx` |

## Verification (this session)

- `cd apps/api && ./mvnw test -Dtest=Strategy*IntegrationTest` → **15/15 pass**; boot under
  `ddl-auto: validate` confirms the new entity matches V12.
- `cd apps/web && npx vitest run src/features/strategy` → **29/29 pass**; `npx tsc --noEmit` clean.
