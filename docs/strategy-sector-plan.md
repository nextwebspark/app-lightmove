# Strategy — Sector Scope (frontend + backend)

## Context

The Strategy page exists only as a placeholder route. This session builds its first real section — **Sector Scope**: the user picks direct sectors for a mandate, the system auto-suggests **adjacent sectors** (static Claude-authored map) and **inferred tags** (live co-occurrence over `app_lm_companies.industry_tags`), all rendered as toggleable chips with a live "N companies match this scope" estimate. Everything else on the Strategy page (size, ownership, location, lists, lock flow) is out of scope.

**Confirmed decisions** (user): editable state only, no lock/unlock; typeahead adds Direct sectors only; full two-column shell with other nav sections disabled; live estimate banner included.

**Data reality** (queried live): `app_lm_companies` has 54,044 rows, **523 distinct `primary_industry`** values (= "sectors", btree-indexed), **1,702 distinct `industry_tags`** (GIN-indexed), 9 null sectors. No app-layer read path for companies exists yet. Next migration = **V11**. Mockup source of truth: `claude-design/Project.dc.html` lines 286–360 (markup) and 1041–1106 (chip/group spec) — it shows the *locked* state; we build the editable equivalent with identical visuals.

## 1. Migration — `apps/api/src/main/resources/db/migration/V11__strategy.sql`

```sql
CREATE TABLE app_lm_strategy (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id uuid        NOT NULL UNIQUE REFERENCES app_lm_project (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version    bigint      NOT NULL DEFAULT 0
);
CREATE TRIGGER app_lm_strategy_touch BEFORE UPDATE ON app_lm_strategy
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE TABLE app_lm_strategy_sector (
    strategy_id uuid         NOT NULL REFERENCES app_lm_strategy (id) ON DELETE CASCADE,
    sort_order  integer      NOT NULL,
    kind        varchar(16)  NOT NULL
        CONSTRAINT app_lm_strategy_sector_kind_chk CHECK (kind IN ('DIRECT','ADJACENT','INFERRED')),
    label       varchar(160) NOT NULL,
    selected    boolean      NOT NULL DEFAULT true,
    PRIMARY KEY (strategy_id, sort_order)
);
```

- Parent `app_lm_strategy` holds only identity/version now — the 1:1 anchor later sections and lock fields attach to (exact `app_lm_position` shape minus domain scalars).
- One sector table with `kind` column (mirrors `app_lm_position_competency.panel`): single `@OrderColumn` list, service splits by kind.
- Deselected suggestions ARE stored (`selected=false`) — mockup keeps them visible at opacity .65, re-selectable.
- No unique `(strategy_id, kind, label)` index — Hibernate element-collection rewrites hit transient duplicate states mid-flush; dupes rejected in the service instead.

## 2. Adjacency map — static classpath JSON (ALREADY EXISTS)

`apps/api/src/main/resources/data/sector-adjacency.json` — **already authored in a prior session and VERIFIED**: 523 keys = the exact 523 `primary_industry` DB strings (byte-exact, 0 diff both directions), every adjacent value is itself a key (0 orphans), no self-reference. Raw sector strings used as opaque identity — no enum, no slug (final filter is `WHERE primary_industry = ANY(:labels)`, so exact strings drop straight in). Do NOT regenerate.

Classpath JSON (not a migration-seeded table) because: applied migrations are immutable (every re-tune would need a new migration); nothing joins it (lookup = in-memory get); authored judgment wants git diffs. Loaded once at construction by `SectorAdjacency` (Jackson 3 `tools.jackson.*`, `ClassPathResource`). Sectors appearing after a future sync simply miss the map → empty adjacent list, degrades gracefully.

**This session's only map work — guard it, don't build it:**
1. `SectorAdjacencyTest` (unit, no DB): map parses; every adjacent value is itself a key (closed world = every value is a real sector); no self-adjacency. Freezes the invariant the file already satisfies so a future edit can't break it silently.
2. Re-run the byte-exact DB-diff check (keys ⊆ DB distinct, DB distinct ⊆ keys) once before wiring in, to catch any drift since the file was authored. Already passing as of this planning session.

## 3. Backend — company read path (new `app.lightmove.api.company` package)

No entity/repository — every query is an aggregate over reference data; use Spring's auto-configured `JdbcClient` (already on classpath via data-jpa).

| File | Purpose |
|---|---|
| `company/service/CompanyQueryService.java` | JdbcClient read-only aggregates |
| `company/service/SectorAdjacency.java` | loads classpath JSON once (Jackson 3 — `tools.jackson.*`, NOT `com.fasterxml`) |
| `company/controller/CompanyReferenceController.java` | `/api/v1/companies/...` |
| `company/dto/CompanyDtos.java` | records |

All endpoints `@PreAuthorize("@workspaceAuth.can(principal, 'PROJECT_BROWSE')")` — shared reference data. No new RBAC action.

- **`GET /api/v1/companies/sectors`** → `SectorsResponse(List<SectorCount>)`, `record SectorCount(String name, long count)`. `SELECT primary_industry, count(*) ... WHERE primary_industry IS NOT NULL GROUP BY 1 ORDER BY count DESC`. Client caches with `staleTime: Infinity`.
- **`GET /api/v1/companies/sectors/suggestions?sector=a&sector=b`** → `SuggestionsResponse(List<String> adjacent, List<TagCount> inferredTags)`.
  - Adjacent: merge ranked lists from `SectorAdjacency` per requested sector, dedupe, drop requested sectors themselves, cap 10.
  - Inferred: `SELECT tag, count(*) FROM app_lm_companies, unnest(industry_tags) tag WHERE primary_industry = ANY(:sectors) GROUP BY tag ORDER BY count DESC LIMIT 30`, then Java-filter tags case-insensitively equal to requested sectors/adjacents, return top 8.
  - Empty param → empty response, no DB hit. `@RequestParam(required=false)` + null-check (the `[""]` list-binding trap from CLAUDE.md).
- **`GET /api/v1/companies/estimate?sector=a&tag=x`** → `EstimateResponse(long count)`. `SELECT count(*) WHERE primary_industry = ANY(:sectors) OR industry_tags && CAST(:tags AS text[])`; omit empty side; both empty → 0 without query; >100 params → `VALIDATION_FAILED`.

## 4. Backend — strategy feature (inside `app.lightmove.api.project`, beside Position)

Lives in `project` package (Position precedent; needs `ProjectRepository` for workspace-scoped 404-masking load; features must not depend on each other's internals).

- `project/constant/StrategySectorKind.java` — `DIRECT, ADJACENT, INFERRED`
- `project/model/Strategy.java` — extends `BaseEntity`; `@ElementCollection @CollectionTable("app_lm_strategy_sector") @OrderColumn("sort_order") List<StrategySector>`; `forProject(UUID)`, `replaceSectors(...)` (clear+addAll)
- `project/model/StrategySector.java` — `@Embeddable`: kind (`@Enumerated(STRING)`), label, selected
- `project/repository/StrategyRepository.java` — `findByProjectId(UUID)`
- `project/service/StrategyService.java` — mirrors `PositionService`: workspace-scoped project load first, lazy-seed empty strategy on first GET, replace-list write, duplicate-label-per-kind → `ApiException(VALIDATION_FAILED)`, audit
- `project/controller/StrategyController.java`, `project/dto/StrategyDtos.java`
- `core/audit/constant/AuditEventType.java` — add `STRATEGY_UPDATED`

Endpoints (pattern per `PositionController.java`):
- `GET /api/v1/projects/{projectId}/strategy` — `PROJECT_BROWSE` → `StrategyResponse`
- `PUT /api/v1/projects/{projectId}/strategy/sectors` — `PROJECT_EDIT` → snapshot replace (autosave target)

```java
record ChipDto(@NotBlank @Size(max = 160) String label, boolean selected) {}
record StrategyResponse(List<ChipDto> direct, List<ChipDto> adjacent, List<ChipDto> inferred) {}
record PutSectorsRequest(@NotNull @Size(max = 15) List<@Valid ChipDto> direct,
                         @NotNull @Size(max = 30) List<@Valid ChipDto> adjacent,
                         @NotNull @Size(max = 30) List<@Valid ChipDto> inferred) {}
```

Service concatenates direct→adjacent→inferred into the one ordered collection on write, splits by kind on read. No new ErrorCode.

## 5. Suggestion-merge logic — frontend, pure function

`lib/mergeSuggestions.ts`, applied to adjacent + inferred lists whenever the suggestions query returns:
1. Each newly suggested label (ranked order): existing chip → keep with **current** `selected` flag (preserves explicit deselection); new → append `{label, selected: true}`.
2. Previously present but no-longer-suggested: keep **selected** ones (user opted in — don't silently shrink scope), append after suggested set; drop **deselected** ones.
3. Direct chips never touched by merge; suggestions recompute from *selected* direct labels only.

Backend stays a dumb snapshot PUT — same as Position autosave.

## 6. Frontend — new `apps/web/src/features/strategy/`

```
api/types.ts             Chip, Strategy{direct,adjacent,inferred}, SectorCount, Suggestions, Estimate
api/strategyApi.ts       STRATEGY_KEY(projectId); getStrategy, putSectors
api/companiesApi.ts      SECTORS_KEY; SUGGESTIONS_KEY(sortedDirect); ESTIMATE_KEY(sectors, tags);
                         getSectors, getSuggestions, getEstimate (repeated-param query builders)
lib/fuzzy.ts             rankSectors(query, sectors): exact-prefix > word-boundary prefix > substring
                         > subsequence/bigram; highlight ranges; ties broken by count desc
lib/mergeSuggestions.ts  §5 rules
components/StrategyNav.tsx      sticky 250px left nav; "Scope filters"/"Lists" groups; Sector Scope
                                active (amber icon + selected-count badge); other 5 rows disabled
components/SectorPanel.tsx      title + sub + SectorCombobox + three ChipGroups
components/ChipGroup.tsx        mono uppercase amber label + text3 desc + wrapping pills; selected =
                                sky border + check (stroke 2.4); deselected = line border, plus icon,
                                opacity-[.65]; onToggle(label)
components/SectorCombobox.tsx   net-new typeahead: opens ≥1 char, top-8 fuzzy matches w/ highlight +
                                count badge, excludes already-added; ArrowUp/Down + Enter + Escape,
                                ARIA combobox/listbox/option; no debounce (filters cached in-memory list)
components/EstimateBanner.tsx   panel2 card, mono 26px amber count + "companies match this scope"
pages/StrategyPage.tsx          orchestrator
pages/StrategyPage.test.tsx
```

**StrategyPage wiring** (mirror [PositionPage.tsx](apps/web/src/features/position/pages/PositionPage.tsx)):
- `useOutletContext<ProjectOutletContext>()`; `useQuery(STRATEGY_KEY, getStrategy)`; inner editor keyed by `project.id` holds draft state.
- Sectors list: `useQuery(SECTORS_KEY, { staleTime: Infinity })`.
- Suggestions: `useQuery(SUGGESTIONS_KEY(selectedDirect), { enabled: selectedDirect.length > 0 })`; merge in a `useEffect` depending **only on suggestions data** (loop guard: no-op check before setState); merge change → set draft + `autosave.schedule(snapshot)`.
- **Hoist `useAutosave`** from `features/position/lib/` to `src/lib/` (two-line change in position feature), reuse here. `persist` = `queryClient.setQueryData(STRATEGY_KEY, response)` on success, toast `messageFor(error)` on failure.
- Estimate: `useQuery(ESTIMATE_KEY(selectedSectors, selectedTags), { placeholderData: keepPreviousData })`; both empty → skip query, show 0.
- Layout: header ("Strategy" — NO "Universe locked" badge, no green footer), EstimateBanner, `grid grid-cols-[250px_1fr] gap-4 items-start`. Tokens from tokens.css only.
- Route: swap [routes.tsx:69](apps/web/src/app/routes.tsx#L69) from `ProjectPlaceholderPage` to `StrategyPage` (placeholder stays for other tabs).

### Mockup fidelity (Project.dc.html:286-360 + 1041-1106 — read directly, inline styles → Tailwind tokens)

Match the design's editable equivalent. **Translate inline styles to token utilities** (`--panel2`→`bg-panel2`, `--line-soft`→`border-line-soft`, `--amber`→`text-amber`, `--sky`, `--text/text2/text3`, `--line`, `--panel`, `font-mono`, `font-sans`). Exact specs:

- **Estimate banner**: `bg-panel2 border border-line-soft rounded-[10px] px-[18px] py-3 flex items-center gap-[14px]`; number `font-mono font-bold text-[26px] text-amber min-w-[52px]` (em-dash while loading); label "companies match this scope" `font-semibold text-[13px]`; sub `font-mono text-[11px] text-text3` → reword to editable ("updates as you refine scope"), NOT the mockup's "locked at sign-off" copy.
- **Left nav** (`StrategyNav`): `bg-panel2 border border-line-soft rounded-[10px] p-2 sticky top-0`. Group header `font-mono font-semibold text-[10px] tracking-[0.12em] uppercase text-text3 px-[10px] pt-3 pb-1.5`. Item button `flex items-center gap-[9px] w-full px-[10px] py-[9px] rounded-lg text-[13px] font-medium text-left`. Active (Sector Scope): `text-text bg-panel`, icon `text-amber`, badge `text-amber border-amber`. Inactive/disabled (other 5): `text-text2 bg-transparent`, icon currentColor, badge `text-text3 border-line`, PLUS `opacity-50 cursor-not-allowed` + no onClick. Badge pill: `font-mono font-semibold text-[10.5px] bg-panel border rounded-full px-[7px] py-px`. Count = selected chip count in that section (sector = selected direct+adjacent+inferred). Sector icon path `M12 2a10 10 0 1 0 .01 0M12 8a4 4 0 1 0 .01 0`; other icons per mockup lines 1063-1069. Nav groups: "Scope filters" (Sector Scope, Company Size, Ownership Type, Location) + "Lists" (Target List Seeding, Off-limits) — from a local constant.
- **Panel** (`SectorPanel`): `bg-panel2 border border-line-soft rounded-[10px] px-5 py-[18px] min-h-[360px]`. Title row: `font-semibold text-[13px]` "Sector Scope" — **DROP the "Locked" tag** (editable). Sub `font-mono text-[11.5px] text-text3`: "Direct is must-have · Adjacent and Inferred widen the pool".
- **ChipGroup**: label+desc row `flex items-baseline gap-2 mt-4 mb-2`; label `font-mono font-bold text-[10.5px] tracking-[0.06em] uppercase text-amber`; desc `font-mono text-[11.5px] text-text3`. Groups: Direct ("Core sector — must-have"), Adjacent ("AI-suggested — transferable experience"), Inferred ("AI-suggested — wider talent pool"). Chip row `flex flex-wrap gap-2`.
- **Chip** (button, toggles on click — NOT `cursor-default`/lockedToast): `inline-flex items-center gap-[7px] px-[13px] py-[7px] rounded-full border bg-panel font-medium text-[13px]`. Selected: `border-sky text-text` + check icon `text-sky` (svg 14×14 stroke-width 2.4). Deselected: `border-line text-text2 opacity-[.65]` + plus icon `M12 5v14M5 12h14` `text-text3`.
- **SectorCombobox** (net-new, NOT in mockup): render inside/above the Direct group as a dashed "+ add sector" input (dashed-border pill styled like [PackageCard.tsx](apps/web/src/features/position/components/PackageCard.tsx) `BenefitsField`), backed by a real typeahead dropdown (fuzzy top-8 + count badge). Keeps visual language of the design while adding the editable affordance the locked mockup lacks.
- **OMIT** all locked chrome: header "Universe locked" sky pill (line 290-293), panel "Locked" tag (line 320), green "Company universe locked" footer (line 353-358).

## 7. Tests (behavior-driven only, per user preference)

Backend (Testcontainers, model on Position integration tests):
- `StrategyFlowIntegrationTest` — first GET seeds empty strategy; PUT round-trips 3 groups w/ order + flags; second PUT replaces; duplicate label in a group → 400; >160-char label → 400; other-workspace project → 404.
- `StrategyAuthorizationIntegrationTest` — browse-only member GETs but cannot PUT; PROJECT_EDIT seat can PUT.
- `CompanyReferenceIntegrationTest` — seed rows via JdbcTemplate (identity col: insert without `id`): sectors distinct+counts, null excluded; suggestions ranked, excludes requested sectors; estimate OR-count no double-count; anonymous → 401.
- `SectorAdjacencyTest` (unit) — parses; closed world (every value is a key); no self-adjacency.

Frontend (vitest): `fuzzy.test.ts` (ranking order, word-boundary, subsequence, count tie-break); `mergeSuggestions.test.ts` (§5 rules incl. selected-survivor kept, deselected-nonsurvivor dropped); `StrategyPage.test.tsx` (render groups; toggle → PUT after debounce; combobox add → suggestions fetch → pre-selected chips; estimate renders).

## 8. Implementation order

0. Copy this plan file to `docs/strategy-sector-plan.md` (user request; plan mode blocked the write).
1. `sector-adjacency.json` already exists + verified — just re-run the byte-exact DB-diff check, then write `SectorAdjacencyTest`.
2. `V11__strategy.sql` + entities/repository (boot API once; test profile `ddl-auto: validate` catches drift).
3. Company package (`SectorAdjacency` loads the existing JSON) + tests.
4. Strategy service/controller/DTOs + audit constant + tests.
5. Frontend libs + unit tests.
6. API modules, components bottom-up, route swap, page test.
7. Verify: `npm run dev` → project → Strategy tab → typeahead-add sectors → adjacent/inferred appear pre-selected → deselect one, remove a direct sector, deselection survives recompute → estimate updates → reload persists → browse-only member sees but can't edit. Then `cd apps/api && ./mvnw test`, `cd apps/web && npx vitest`.

## 9. Risks / edge cases

- Upstream sector rename orphans stored labels (chip shows, counts 0 in estimate) — accepted; later session can badge zero-count chips.
- Merge-effect loop — effect depends on suggestions data only + no-op guard.
- Concurrent editors — last-write-wins snapshot (same trade as Position); optimistic `version` surfaces true races via autosave toast.
- Empty companies table (tests/fresh env) — combobox renders "no matches" gracefully.
- Jackson 3 imports (`tools.jackson.*`) in the adjacency loader — `com.fasterxml` compiles then fails at runtime.
- Taxonomy dupes upstream ("IT Services and IT Consulting" vs "Information Technology & Services") — both are real keys; adjacency map cross-links them.

## Key reference files

- [PositionService.java](apps/api/src/main/java/app/lightmove/api/project/service/PositionService.java) — service/lazy-seed/audit template
- [Position.java](apps/api/src/main/java/app/lightmove/api/project/model/Position.java) — element-collection + replace-list pattern
- [V7__position.sql](apps/api/src/main/resources/db/migration/V7__position.sql) — migration idiom
- [PositionPage.tsx](apps/web/src/features/position/pages/PositionPage.tsx) — draft-state + useAutosave + persist wiring
- [PackageCard.tsx](apps/web/src/features/position/components/PackageCard.tsx) — pill styling reference
- claude-design/Project.dc.html:286-360, 1041-1106 — UI source of truth
