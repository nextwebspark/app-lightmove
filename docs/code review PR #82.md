# Review: PR #82 (feature/stratagy-makeover)

## Context

This is a large restructuring: the old `company`/`project.Strategy`/`project.Sourcing` code is deleted and
replaced by two new modules, `strategy` (the market-side filter/search/facets over the
71,822-row Apollo universe) and `triagecompany` (the mandate's triage decisions), plus a new
`Triage` screen and a reworked `Strategy` screen on the frontend. 244 files changed
(+11,349/-15,726).

## Verified findings, most severe first

1. **`TriageCompanyService.addAllInScope` doesn't dedupe via `ON CONFLICT DO NOTHING`**
   ([TriageCompanyService.java:132-160](apps/api/src/main/java/app/lightmove/api/triagecompany/service/TriageCompanyService.java#L132-L160)),
   despite migration V32's own comment stating it does
   ([V32...sql:68-70](apps/api/src/main/resources/db/migration/V32__saved_searches_and_project_triage.sql#L68-L70)).
   It does an in-memory select-then-filter-then-`saveAll` instead. A race (double-click, two
   tabs, or a bulk add racing a single `add()` for the same company) trips the
   `app_lm_project_triage_company_uk` unique index inside the one `@Transactional` method,
   rolling back the *entire* batch, not just the colliding row. **CONFIRMED.**

2. **`TriageCompanyService.add()` never checks the off-limits list**
   ([TriageCompanyService.java:101-125](apps/api/src/main/java/app/lightmove/api/triagecompany/service/TriageCompanyService.java#L101-L125)).
   It resolves the company via `ApolloCompanyQueryService.byAccountIds`, which is explicitly
   scope-agnostic ("the named companies, whatever the scope") and hardcodes `off_limits =
   false`. `addAllInScope` correctly excludes off-limits companies via `StrategyScope`/
   `CompanyScope.offLimitsAccountIds()`, but `add()` (`POST /projects/{id}/triage`, gated only
   by `WORK_EXECUTE`) does not. The current frontend (`strategyApi.addToUniverse`) only calls
   this from rows already filtered out of view when off-limits, so it isn't reachable through
   normal UI clicks today — but nothing at the API layer stops a direct call from re-adding a
   barred company, contradicting `OffLimitsFilter.tsx`'s documented invariant ("dropped from
   every filtered read... no show-anyway toggle"). **CONFIRMED** (real gap; currently
   UI-mitigated, not API-enforced).

3. **`ReportService.excludesUnknownRevenue` ignores `revenueRange`, only checks `revenueBands`**
   ([ReportService.java:99-106](apps/api/src/main/java/app/lightmove/api/project/service/ReportService.java#L99-L106)).
   A custom revenue-range filter (`ApolloCompanyQueryService.rangeClause`, line 339-351) emits
   a plain `annual_revenue >= :min` with no `OR annual_revenue IS NULL`, silently dropping the
   ~90% of companies with null revenue. Because the caveat check never looks at
   `revenueRange()`, the "excludes unknown revenue" disclosure banner never renders for this
   case, so the Reports page shows confidently low counts with no warning. **CONFIRMED.**

4. **`ClientService.domainOf` doesn't strip a trailing port**
   ([ClientService.java:177-186](apps/api/src/main/java/app/lightmove/api/project/service/ClientService.java#L177-L186)).
   `host.split("[/?#]", 2)` never splits on `:`, so `http://example.com:8080/about` stores
   `example.com:8080`. **CONFIRMED**, but low-impact — nothing downstream matches/uniques on
   this field; it's a cosmetic bad value only.

5. **`StrategyService.toResponse` and `StrategySearchService.toDto` duplicate the
   `StrategyFilterDto` mapping** independently
   ([StrategyService.java:295-302](apps/api/src/main/java/app/lightmove/api/strategy/service/StrategyService.java#L295-L302),
   [StrategySearchService.java:121-127](apps/api/src/main/java/app/lightmove/api/strategy/service/StrategySearchService.java#L121-L127)).
   Both hand-build the same 7-arg positional constructor call with nothing enforcing they stay
   in sync if `StrategyFilter` changes shape. **CONFIRMED** (code-quality / reuse, not a bug
   today).

6. **`TriageCompanyService.list()` makes 4 sequential DB round trips** (3×
   `countByProjectIdAndStatus` + 1 paged query) instead of one grouped aggregate
   ([TriageCompanyService.java:76-99](apps/api/src/main/java/app/lightmove/api/triagecompany/service/TriageCompanyService.java#L76-L99)).
   **CONFIRMED**, minor — every call is index-backed and project-scoped, so this is a
   round-trip-count nit more than a real bottleneck.

7. **`ApolloCompanyQueryService.marketSegmentFacets()` issues one `SELECT count(*)` per market
   segment (11 sequential queries) in a loop**
   ([ApolloCompanyQueryService.java:186-199](apps/api/src/main/java/app/lightmove/api/strategy/service/ApolloCompanyQueryService.java#L186-L199)).
   **CONFIRMED**, but the class's own Javadoc explains segments can overlap (a single `GROUP
   BY` would under-report), each query is a cheap indexed probe, and facets are cached 10 min
   client-side — an acknowledged, low-impact tradeoff.

8. **`StrategyPage.tsx` search box re-renders the whole unmemoized filter sidebar on every
   keystroke** (`apps/web/src/features/strategy/pages/StrategyPage.tsx:77`). Plausible per the
   skill's analysis but not independently re-verified line-by-line; likely low-severity since
   `FilterAccordion` only mounts the currently-open panel.
