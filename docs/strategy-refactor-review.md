# Strategy refactor — code review

Branch `claude/strategy-refactor-review-krpcif` against `origin/main`, commits `a3434bb`, `f34ddfe`,
`624fb8e`. 244 files, +11,349 / −15,726.

## Scope and verdict

The branch rewrites the search story: the brightdata warehouse and the Sourcing screen are gone,
`strategy` becomes the market side over `app_lm_apollo_companies`, and a new `triagecompany` feature
holds one project↔company decision per row. Reviewed for correctness, security, SOLID, naming,
maintainability and dead code.

**The shape is right.** One universe instead of two, a flat pass-or-fail filter instead of the
criteria model, the mandate's scope resolved server-side and never from a request parameter, sort
resolved through a closed enum so no caller string reaches an `ORDER BY`, every user value bound as a
named parameter, and every project read scoped by `(id, workspaceId)` taken from the principal. The
new integration tests are genuinely load-bearing — `CompanySearchAuthorizationIntegrationTest` in
particular pins a gate nothing else held.

What follows is what should change before this is finished. Five items are defects; the rest are
places where the new code contradicts either itself or the conventions in `CLAUDE.md`.

---

## 1. Correctness and security

### 1.1 Bulk add can violate its own unique index

`apps/api/.../triagecompany/service/TriageCompanyService.java:142-153`

`addAllInScope` reads which companies the mandate already holds
(`findByProjectIdAndApolloAccountIdIn`), filters them out, then `triaged.saveAll(taken)` — a plain
batch insert. Between the read and the write nothing holds
`app_lm_project_triage_company_uk (project_id, apollo_account_id)`.

`V32__saved_searches_and_project_triage.sql:68` states the opposite in so many words:

> This is what makes "Add all to Universe" re-runnable: the bulk insert is ON CONFLICT DO NOTHING

It is not. Two concurrent "Add all" clicks, or an "Add all" racing the single-row `add` at
`TriageCompanyService.java:110`, fail the whole batch on a constraint violation. `add` has the same
check-then-insert shape and the same race, and its comment promises idempotency it cannot deliver.

**Fix.** Give `TriageCompanyRepository` a native
`INSERT … ON CONFLICT (project_id, apollo_account_id) DO NOTHING` returning the inserted count, and
use it from `addAllInScope`; have `add` catch `DataIntegrityViolationException` and re-read the
existing row. Test: inserting the same id twice succeeds and yields one row.

### 1.2 The report's revenue caveat misses Custom Range

`apps/api/.../project/service/ReportService.java:103`

```java
return !scope.revenueBands().isEmpty()
        && !scope.revenueBands().contains(RevenueBand.R_UNKNOWN.value());
```

Only the band list is inspected. But bands and `revenueRange` are the two modes of one axis, and a
custom range is *also* a numeric predicate — `annual_revenue BETWEEN …` excludes every row where the
figure is null, which is 64,690 of 71,822. So a mandate in Custom Range mode gets a report measuring
a tenth of the market with no caveat beside it, which is precisely the failure `ScopeCaveatsDto`
exists to prevent.

**Fix.** The caveat is also true when `scope.revenueRange() != null`. Cover it in
`ReportIntegrationTest`.

### 1.3 `harden.sql` no longer takes ownership of the universe

`ops/cloudsql/harden.sql:36-41`

The new guarded block does `REVOKE ALL … FROM lm_app` then `GRANT SELECT`, but drops the
`ALTER TABLE … OWNER TO postgres` that the old `app_lm_companies` block had — and which the
`app_lm_companies` block at line 49 still has.

In Postgres, ownership is not a privilege. An owner can re-grant itself anything a `REVOKE` took
away, and can `TRUNCATE`, `ALTER` or `DROP` the table regardless of the grant table. Where `lm_app`
owns `app_lm_apollo_companies` — which is the case on any database where the app created it — the
property this file's own comment states ("the application only reads it… would let a SQL-injection
foothold in the app rewrite every company a consultant sees") is not actually enforced. The comment
acknowledges the table is "typically already owned by the human who loaded it", but nothing checks.

**Fix.** Inside the existing guard, reassign to `postgres` when the connected role can, and
`RAISE NOTICE` naming the current owner when it cannot, so a database where the property does not
hold says so instead of being assumed.

### 1.4 A client representative's Strategy tab dead-ends

`apps/web/src/features/strategy/components/FilterSidebar.tsx:101-114`,
`apps/web/src/components/layout/ProjectLayout.tsx:68`,
`apps/api/.../strategy/controller/CompanySearchController.java:46`

`GET /api/v1/companies/facets` is gated `PROJECT_BROWSE` and 403s a pure client representative. That
is deliberate and well tested. But a *project* CLIENT seat holds `WORK_VIEW`, so
`GET /projects/{id}/strategy` and `/strategy/companies` both succeed
(`StrategyController.java:51,82`), and the project sidebar is static — every seat sees the Strategy
tab.

`FilterSidebar` renders `<ChipSkeleton />` whenever `facets` is `undefined`, which includes the
error case, and `StrategyPage` never reads `facets.isError`. The result for a client representative
is a filter rail that pulses forever with no message, beside a table that loaded fine.

**Fix.** Thread `facets.isError` into `FilterSidebar` and render one line in place of the skeletons,
or hide the rail for a caller who cannot read the facets. Add a vitest case for the 403 path.

### 1.5 A read that writes

`apps/api/.../strategy/service/StrategyService.java:94-97`

`get` is `@Transactional` (not `readOnly`) and goes through `load(...)`, which seeds and saves a
`Strategy` row when none exists. Since the endpoint is `WORK_VIEW`, a client representative opening
the tab performs an INSERT.

`ReportService.java:67` refuses to do this, in a comment, for exactly this reason:

> an unsaved strategy is not seeded here: a report is a read, and writing a row to answer one would
> make a client representative's page load a write

Two reads of the same aggregate, opposite rules.

**Fix.** Make `get` `@Transactional(readOnly = true)` and fall back to a transient
`Strategy.forProject(projectId)` — the same thing `companies` and `scopeOf` already do at
`StrategyService.java:146,160`. Only the write paths seed.

---

## 2. Dead code

| What | Where |
|---|---|
| `CompanyFacet` enum — no callers anywhere; its only references are an unused import and a javadoc mention | `strategy/constant/CompanyFacet.java`, `ApolloCompanyQueryService.java:3`, `dto/FacetsResponse.java:20` |
| `SELECT %s, false AS off_limits` — the row mapper never reads the column, so the projection is inert and the mapper's javadoc at `:451` describes behaviour that does not exist | `ApolloCompanyQueryService.java:106,123` |
| Self-package import (`strategy.service` importing `strategy.service.ApolloCompanyQueryService`) | `StrategyService.java:29` |
| `filterRef` — assigned on every render, read nowhere | `StrategyPage.tsx:86-87` |
| `STRATEGY_WRITE_KEY` — a `mutationKey` no `useIsMutating` observes; its doc describes a coordination mechanism that was never built | `strategyApi.ts:19`, `StrategyPage.tsx:110` |
| `ICONS.sourcing` — orphaned when Sourcing became Triage | `components/layout/Icon.tsx` |
| `website` / `linkedinUrl` — selected in SQL, mapped through `CompanyRow` → `CompanyResultDto` → the TS type, and rendered by no column; the mockup has no Website or LinkedIn column | `CompanyResultDto.java`, `strategy/api/types.ts:97-98`, `company_linkedin_url` in `ROW_COLUMNS` |

`website` must stay on `CompanyRow` — `ClientService.fromUniverse` derives the client's domain from
it. It is the *list DTO* carrying it to nowhere.

**Worth keeping rather than losing.** `apps/web/src/features/sourcing/lib/externalUrl.ts` was
deleted with the feature. It is the `javascript:` href guard, and its own doc calls itself "a
security boundary rather than formatting". Nothing renders a company link today, so nothing is
broken — but the next link column will need it, and it is better re-homed to
`apps/web/src/lib/externalUrl.ts` with its test than rediscovered.

---

## 3. Duplication

### 3.1 Pagination has three sources of truth

`CompanyListSettings` (config, `default-page-size: 25`, `max-page-size: 100`),
`TriageCompanyService.java:49` (`public static final int MAX_PAGE_SIZE = 100`), and
`TriageCompanyController.java:44-45` (`@RequestParam(defaultValue = "25")`). `StrategyService` reads
the config; triage hard-codes both halves of the same numbers. Retuning
`COMPANY_LIST_MAX_PAGE_SIZE` in a deployed environment moves one screen and not the other.

Same shape at `StrategyService.java:72`: `MAX_QUERY_LENGTH = 100` duplicates
`lightmove.company.search.max-query-length`, which `CompanySearchController.java:67` reads from
config.

### 3.2 List-query parsing exists twice, behaving differently

`StrategyService.companies` rejects an over-large `size` with a 400; `CompanySearchController.search`
silently clamps `limit` with `Math.clamp` (`:76`). Same concern, two answers. `resolveSort`,
`resolveDirection` and `normaliseQuery` are a fourth copy of validation that belongs beside them.

`CompanySearchController.java:66` also carries a dead branch: `q` is `@RequestParam(name = "q")` with
`required = true`, so `query == null` cannot happen — Spring 400s first.

**Fix.** One `CompanyListQuery` value in `strategy` owning page/size/sort/direction/q resolution
against `CompanySettings`, used by both. Reject rather than clamp: a silently clamped page is a wrong
answer to a stated request.

### 3.3 Icon paths re-declared beside the registry that exists to prevent it

`components/layout/Icon.tsx` opens with "Mockup glyphs, named. One place so two screens cannot draw
'team' differently", and already carries `search`, `plus`, `close`, `chevronDown`. The new code
declares them again:

- `IndustryFilter.tsx:8-10` — `SEARCH`, `CHECK`, `PLUS`
- `OffLimitsFilter.tsx:5` — `CLOSE`
- `FilterAccordion.tsx:6` — `CHEVRON_OPEN`, identical to `ICONS.chevronDown`
- `companyColumns.tsx:144`, `SaveSearchMenu.tsx:102` — raw path strings inline

And `TriagePage.tsx` draws **two different stars in one file**: `ICONS.star` in the tab strip at
`:41`, a different path for the Shortlist button at `:211` — exactly the drift the registry exists to
stop.

**Fix.** Add `check`, `sparkle`, `chevronRight` and `undo` to `ICONS`; every new file uses `ICONS.*`.

### 3.4 Two identical JSON-resource loaders

`SectorTaxonomy` and `MarketSegments` differ only in the filename and one validation. Both hold the
same `ClassPathResource` / `TypeReference<LinkedHashMap<String, List<String>>>` / `IllegalStateException`
body. Extract the loader; keep the file-specific check.

### 3.5 The company snapshot exists twice, with transposed arguments

`StrategyCompanyRef.of(id, name, industry, city, country, logo)` versus
`TriageCompany.taken(projectId, addedBy, id, name, industry, country, city, …)`.

Two adjacent `String` parameters in **opposite order** between two factories that snapshot the same
company from the same `CompanyRow`. Both call sites are correct today. The next one is a
transposition away from filing every company under the wrong country, and the compiler cannot see it.
`TriageCompany.taken` takes eleven positional parameters, nine of them `String`.

**Fix.** One `@Embeddable CompanySnapshot` with `CompanySnapshot.of(CompanyRow)`, embedded by both
entities (`@AttributeOverride` where the two tables' columns differ). No positional String lists at
the call sites, and no schema change.

### 3.6 Other reuse misses

- `TriagePage.tsx:152-180` hand-rolls Previous/Next while
  `strategy/components/PaginationBar.tsx` exists and does the same job.
- `PAGE_SIZE = 25` is declared separately in `StrategyPage.tsx:21` and `TriagePage.tsx:14`.
- `strategyApi.addToUniverse` (`:82`) and `addAllInScope` (`:86`) call `/projects/{id}/triage*` from
  the strategy feature, and `BulkAddResult` lives in `strategy/api/types.ts` while `triage/api/types.ts`
  exists. `addToUniverse` is also typed `Promise<unknown>` where `Promise<TriageCompany>` is known.

---

## 4. SOLID

### 4.1 SRP — `ApolloCompanyQueryService` is 529 lines doing six jobs

WHERE-clause building, band `CASE` construction, facet aggregation, paged list reads, typeahead,
report breakdowns, and row mapping — plus a `static final RowMapper` declared at `:461`, below the
methods that use it. Every consumer takes the whole thing.

**Suggested split**, keeping the SQL text byte-identical:

| Class | Holds |
|---|---|
| `CompanyScopeSql` (package-private) | `buildWhere`, `rangeClause`, the band clauses, `arrayLiteral`, `escapeLikePattern`, `bind` |
| `CompanyRowMapper` | the hand-written mapper (keep the `founded_year`/`Number` trap comment verbatim) |
| `CompanyFacetQueryService` | the five accordions and the band `CASE` builders |
| `ApolloCompanyQueryService` | `count`, `search`, `byAccountIds`, `typeahead`, the three breakdowns |

Minor, in the same file: `List.of(EmployeeBand.values()).stream()` at `:231` and `:241` allocates a
list to stream an array — `Arrays.stream(...)` says it directly.

### 4.2 ISP / DIP — consumers depend on more than they use

After 4.1, `CompanySearchController` takes facets + typeahead, `ReportService` takes breakdowns, and
neither sees the rest.

Separately, `TriageCompanyService` and `ReportService` both inject the concrete `StrategyService` for
one method, `scopeOf`. `ReportService`'s comment already frames it as "deliberately one method wide"
— make that structural: a `MandateScopeProvider` interface in `strategy`
(`CompanyScope scopeOf(UUID workspaceId, UUID projectId)`) implemented by `StrategyService`, with the
two consumers depending on the interface.

### 4.3 `CompanyScope` restates all seven `StrategyFilter` components

`CompanyScope` is `StrategyFilter`'s seven fields plus `offLimitsAccountIds` and `nameQuery`. Adding
one axis today means editing `StrategyFilterDto`, `StrategyFilter`, `CompanyScope` and
`StrategyScope` — four files to add one concept, and three chances to forget one.

**Fix.** `CompanyScope(StrategyFilter filter, List<String> offLimitsAccountIds, String nameQuery)`
with delegating accessors, so `buildWhere` reads unchanged.

### 4.4 One finder trusts its caller

`StrategySearchService.list(UUID projectId)` (`:55`) is a public method on a Spring bean that takes no
workspace id. It is safe today because its one caller resolved the project first, and the javadoc
says so — but it is the only entry point in the feature where tenant scoping is a convention rather
than a signature. Give it `workspaceId` (already in hand at the call site) or make it
package-private.

Also at `:65`: `findByProjectIdOrderByNameAsc(projectId).size()` loads every saved search to compare a
count against 50. `countByProjectId` is one query and no entities.

### 4.5 URL parsing inside a client service

`ClientService.domainOf` (`:177`) hand-rolls scheme stripping with regex and keeps anything after the
host that is not a path — `https://acwapower.com:8080/` becomes `acwapower.com:8080`, and
`https://user@host.com` keeps the userinfo. It is also the backend twin of the frontend guard that
was just deleted (§2). `:185` reaches for `java.util.Locale.ROOT` fully qualified where every other
file in the package imports it.

**Fix.** A `core` text utility parsing with `java.net.URI`.

### 4.6 Facets are recomputed on every request

`GET /companies/facets` runs roughly fifteen aggregates over 71,822 rows per call — one `GROUP BY` for
industries, one per market segment (eleven), one for countries, and one each for the two band
`CASE`s. `ApolloCompanyQueryService`'s own class doc calls this "one cacheable read that no filter
invalidates"; nothing caches it. The client sets a 10-minute `staleTime`, which helps a browser and
not the server.

**Fix.** `@Cacheable` on the facet methods with a fixed TTL — the counts change only when the pipeline
reloads.

---

## 5. Naming and smaller things

- **Three names for one idea.** `StrategyScope` (a static translator), `CompanyScope` (the value it
  builds), `StrategyService.scopeOf` (the caller-facing name). `StrategyScope` reads like an entity
  and is not one. Fold its two methods into `CompanyScope.from(strategy, nameQuery)` and delete the
  class.
- `TriageCompanyService.toDto` (`:213`) is package-private among all-private siblings, with no caller
  outside the class.
- `Client.UNIVERSE_SOURCE` (`:71`) is declared between the instance fields and the factory methods.
  Constants belong at the top.
- `EmployeeBand.B_10000_PLUS` (`:38`) is labelled `10000+` but its lower bound is 10,001 — a company
  with exactly 10,000 staff falls in `5001-10000`. The bands are right; the label states a boundary
  the filter does not honour. `10001+`, or rebase the band.
- `OffLimitsFilter.tsx:45` still tells the user companies are "excluded from your active **sourcing**
  search results". Sourcing no longer exists in the product; the same sentence is quoted as
  justification in `CompanyRow` and `CompanyResultDto` javadoc.
- `ProjectLayout.tsx:17,27` keeps two overlapping lists of route suffixes (`/strategy` is in both) for
  two related layout facts. One map keyed by suffix would not need keeping in sync.
- `ReportResponse.sectorsInScope` is now `scope.industries().size()` (`ReportService.java:76`). An
  untouched filter is the whole universe, so the report reads "71,822 companies, 0 sectors in scope" —
  true of the filter, misleading as a figure.
- `RangeFilter`'s `BoundInput` accepts unbounded digits and sends `Number(raw)`; past `Long.MAX_VALUE`
  the server answers with a JSON parse error rather than a validation message.

---

## 6. Comment style

`CLAUDE.md` is explicit:

> **Comments are the exception, not the habit.** … Never restate what the line does, never narrate
> alternatives that were not taken, and keep a class doc to a line or two.

Most new classes open with 20–30 lines doing exactly the thing that is ruled out —
`ApolloCompanyQueryService` (27 lines before the first field), `CompanyScope` (38), `StrategyFilter`,
`EmployeeBand`, `RevenueBand`, `MarketSegments`, `FilterSidebar`, `CompanyResultsTable`,
`CompanySearchController`, and the rest. Several narrate the rejected option at length ("Not a single
`String[]` parameter, which is the obvious way and is wrong here…"), and two of them have already
drifted from the code: the row mapper's `off_limits` paragraph (§2) and `StrategyService`'s reference
to a package named `company` that is now `triagecompany`.

The volume is the problem: a reader wading through a page of prose per class stops reading them, and
that is when the ones that matter get missed.

**If this is trimmed, these must survive verbatim** — they are traps and shipped bugs, which
`CLAUDE.md` marks as load-bearing:

- `founded_year` read through `Number`, not `(Short)` — `ApolloCompanyQueryService.java:451-460`
- The `ARRAY[…]::text[]` cast and why a bound `String[]` breaks it — `:479-496`
- V11's "no unique index: Hibernate rewrites an `@OrderColumn` collection in place" — `V31`'s footer
- `flyway.baseline-version: 0` and its counterpart note in `ops/dev/db.sh`
- `@JsonIgnoreProperties` on `StrategyFilter` — old documents must stay readable
- `NULLS LAST` / `NULLIF` on `CompanySortField`
- The migration headers V30–V32, which are the design record

---

## 7. Suggested commit sequence

If these are actioned, they divide cleanly:

1. `fix(triage): make bulk add idempotent under the unique index` — §1.1
2. `fix(strategy): caveat a custom revenue range; stop seeding on read` — §1.2, §1.5
3. `fix(web): surface refused facets instead of an endless skeleton` — §1.4
4. `chore(ops): reassign the Apollo universe to postgres in harden.sql` — §1.3
5. `refactor(strategy): split the Apollo query service; narrow its consumers` — §4.1, §4.2, §4.6
6. `refactor: one company snapshot, one list query, one config for paging` — §3.1, §3.2, §3.5, §4.3
7. `chore: remove dead code and re-home the triage API calls` — §2, §3.3, §3.6, §5
8. `docs: trim class comments to house style` — §6

Verification for any of them: `cd apps/api && ./mvnw test` (Testcontainers, needs Docker),
`cd apps/web && npx vitest && npm run build` (the build is the real typecheck), then `npm run dev`
with `npm run dev:db:apollo` and a walk through Strategy → save search → Add all → Triage → Reports
as an ADMIN and as a project CLIENT seat — that seat is what §1.4 and §1.5 are about.

---

# Verification pass — 22 Aug 2026

Re-read against `feature/stratagy-makeover` at `d9ff2c8`, six commits after the report above
(`3968e70`, `e69c0f3`, `041c316`, `77eda1e`, `74f6564`, `d9ff2c8`). Every finding was checked against
the code as it now stands, not against the commit messages.

**Checks run.** Backend compiles clean (271 sources, `mvnw compile`). Web builds — the real typecheck —
and the full frontend suite is green: 36 files, 197 tests. The backend suite could not run here:
Testcontainers needs Docker and this container has none, so the new integration tests were read rather
than executed.

## Addressed, and correctly

All five defects are fixed, and the fixes are better than the report asked for in two places.

| § | Finding | How it was settled |
|---|---|---|
| 1.1 | Bulk add races its own unique index | `TriageCompanyWriter` — a real multi-row `INSERT … ON CONFLICT DO NOTHING`, every value bound, the row template generating placeholder names rather than interpolating. Both `add` and `addAllInScope` go through it. The audit event now fires only when the insert actually wrote, so a losing racer gets the company without a phantom event; `addAllInScope` dropped the read-then-filter entirely and takes its `added` count from the statement. V32's header is now true. |
| 1.2 | Report caveat missed Custom Range | `excludesUnknownRevenue` leads with `revenueRange != null`. Covered by `ReportIntegrationTest`. |
| 1.3 | `harden.sql` left the universe writable by its owner | Reads the current owner from `pg_class`, reassigns to `postgres` where the connected role can, and `RAISE NOTICE`s the owner's name where it cannot — so a database on which the property does not hold says so instead of being assumed. Stronger than the report's suggestion. |
| 1.4 | Client rep's filter rail skeletons forever | `FacetsUnavailable`, threaded through `FilterSidebar` on `facets.isError`, with a line that also tells the reader the results beside it are unaffected. Covered by a `StrategyPage` test named for the case. |
| 1.5 | A `WORK_VIEW` GET performed an INSERT | `get` is `readOnly` and falls back to a transient `Strategy.forProject`. Only the write paths seed. |

Also settled: `CompanyFacet` deleted, the inert `off_limits` projection and its false javadoc,
the self-package import, `filterRef`, `ICONS.sourcing`, `linkedinUrl`; triage now reads
`CompanyListSettings` instead of its own `MAX_PAGE_SIZE` and hard-coded `defaultValue = "25"`;
`ClasspathVocabulary` behind both JSON loaders; `StrategyCompanyRef.of(CompanyRow)` with
`TriageCompany.taken` gone, which removes the transposed city/country hazard by removing the
positional constructors entirely; `PaginationBar` shared from `components/ui`, `PAGE_SIZE` from
`lib/paging`, triage calls moved to `triageApi` and typed; `StrategySearchService.list` takes a
workspace id and counts with `countByProjectId`; `WebsiteDomain` parsing with `URI`, with tests for
the port and userinfo cases; `toDto` private; `UNIVERSE_SOURCE` at the top of `Client`;
`EmployeeBand` relabelled `10001+`; the "sourcing" copy gone from the UI; `BoundInput` capped at 15
digits.

## What the fix pass caught that this review missed

`3968e70` — **a saved Custom Range could never be read back.** Jackson read `NumericRange.isEmpty()`
as a bean property, wrote `"empty"` into the `filter` jsonb, and then refused to deserialise the
document it had just written. Saving a custom range on either axis left the mandate permanently
unreadable: Strategy, its results, the report and bulk add all 500ing on every subsequent request,
with the same shape stored on every saved search.

This review read `StrategyFilter`'s `@JsonIgnoreProperties` and took the serialisation path as
settled; it never checked the nested record for derived accessors. A round-trip test now guards it.
Worth recording as the class of bug reading alone does not find — the annotation was present and
correct one level up, and only exercising the write-then-read caught it.

## Still open — filed as issues

| Issue | § | What |
|---|---|---|
| #84 | 4.6 | `/companies/facets` recomputes ~15 aggregates per request; nothing caches what only the pipeline changes |
| #85 | 4.1–4.3, 5 | `ApolloCompanyQueryService` at 527 lines doing six jobs; consumers on the concrete `StrategyService`; `CompanyScope` restating `StrategyFilter`; `StrategyScope` unfolded |
| #86 | new | `COMPANY_BULK_ADD_LIMIT` above ~7,280 exceeds Postgres's 65,535 bind-parameter ceiling — a latent limit the 1.1 fix introduced |
| #87 | 2, 3.3, 5 | `STRATEGY_WRITE_KEY`, `website` on the list DTO, the un-rehomed `externalUrl` guard, two raw icon paths, the two `ProjectLayout` lists, `sectorsInScope` |
| #88 | 3.2 | List-query validation still written three times, though the three now behave alike |
| #89 | 6 | Class docs at 20–38 lines against the two-line rule, with the trap comments that must survive a trim |

Issue #55 — duplicated SQL binding helpers between `CompanyQueryService` and
`ApolloCompanyQueryService` — is obsolete: `CompanyQueryService` went with the warehouse.

## Assessment

The five defects are closed and the two riskiest fixes — the conflict-ignoring insert and the
ownership reassignment — are done properly rather than papered over. What is left is structural
(#85), a cache (#84), and tidying (#87, #88, #89); none of it blocks the branch. #86 is worth a look
before anyone tunes the bulk-add limit upward.

The one caveat on this pass: the backend integration tests were read, not run. They should be run
somewhere with Docker before merge — particularly `TriageFlowIntegrationTest.insertIgnoresAHeldCompany`
and `StrategyFlowIntegrationTest.customRangeSurvivesTheRoundTrip`, which guard the two bugs most
likely to reappear.
