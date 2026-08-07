# Coresignal Multi-source Company API — runtime universe expansion

End-to-end analysis and implementation plan for filling company-universe gaps with Coresignal's
Multi-source Company API, queried at runtime on user demand and cached into our own database —
instead of buying a bulk dataset upfront.

**Status: analysis only. No code has been changed.** Every claim about Coresignal below was
researched against their documentation (July 2026); verification level is marked where it matters:
**[V]** verified against a primary doc page, **[U]** unverifiable publicly / must be confirmed.

---

## 1. The problem

The company universe (`app_lm_companies`, ~54k rows) is a copy of the `brightdata` warehouse
projection, synced by `ops/cloudsql/sync-companies.sh`. After applying our strategy criteria
(sector/category, size bands, GCC geography), many companies that should match are missing:

- Whole sectors under-deliver against consultant expectations (the motivating issue).
- 5,045 rows (9.3%) have `employee_count = 0` (with a defaulted `employee_range = '1-10'`)
  — documented in `docs/strategy-sourcing-company-filter.md`.
- Jordan and Egypt appear in the design mockups but have zero rows
  (`GeographyMarket` javadoc notes they are deliberately omitted).
- Ownership Type is captured in the strategy but never applied as a filter, because none of
  `org_type` / `ownership` / `ipo_status` / `is_public` maps cleanly today.

Growing the brightdata pipeline means paying upfront for scraping/coverage we may never search.
The alternative analysed here: query Coresignal **at runtime**, scoped to what a project's
strategy actually asks for, and persist only what we fetch — spend scales with real demand.

## 2. Current state (what the integration must fit into)

### 2.1 The read path

- `company/service/CompanyQueryService` is the only read path over the universe. It uses
  `JdbcClient` (no JPA — the table is ETL-owned). Methods: `sectors()`, `suggestionsFor()`,
  `estimate(ScopeFilter)`, `search(ScopeFilter, page, size)`, `browse()`, name-typeahead
  `search(query, limit)`, and `refsByKeys(keys)` (the seam the strategy write path uses to
  validate target/off-limits picks).
- `project/service/SourcingService` resolves the **stored** strategy (never client input) into a
  `ScopeFilter`: direct+adjacent sectors, inferred tags, employee bands, revenue bands, markets,
  target keys, off-limits keys.
- Filter semantics in `buildWhere()`: sector anchor required
  (`primary_industry IN (…) OR industry_tags && ARRAY[…]`), then AND-narrowed by
  `employee_range IN (…)`, `revenue_range IN (…)`, geography
  (`hq_country IN (…) OR markets && ARRAY[…]`), minus targets/off-limits by `(source, source_id)`.
- Band catalogs (drift-tested against frontend mirrors):
  - `EmployeeBand`: `1-10, 11-50, 51-200, 201-500, 501-1000, 1001-5000, 5001-10000, 10000+`
  - `RevenueBand`: `<5M, 5M-25M, 25M-100M, 100M-500M, 500M-1B, 1B-5B, 5B+`
  - `GeographyMarket`: `AE SA KW QA BH OM` (GCC only)

### 2.2 The write constraints

- `app_lm_companies` is **read-only to the app**: `harden.sql` reassigns it to `postgres` and
  leaves `lm_app` with `SELECT` only. This is deliberate (SQL-injection blast-radius control) and
  is not negotiable for this feature.
- The rebuild-stable identity everywhere is **`UNIQUE (source, source_id)`** — never `id`.
  Strategy target/off-limits rows already carry `(source, source_id)` plus write-time snapshot
  columns, so they survive upstream churn.
- The sync script's "gone upstream" report anti-joins staging against the whole table — a second
  writer into `app_lm_companies` would corrupt that report every run. New data needs its own table.

### 2.3 Existing tables — full inventory and reuse decisions

All 30 tables were checked (migrations V1–V17; V16 is taken by a sibling branch, so the next
free slot is **V18**). Reuse-first outcome:

| Existing table(s) | Decision for this feature |
|---|---|
| `app_lm_companies` | **Reused unchanged** as the base universe. Never written by the app. |
| `app_lm_strategy`, `_sector`, `_company_size`, `_geography`, `_ownership` | **Reused unchanged** — they already define the scope we translate to a Coresignal query. |
| `app_lm_strategy_target_company`, `_off_limits_company` | **Reused unchanged** — they key by `(source, source_id)` + snapshots, so a Coresignal-sourced company can be targeted/off-limited with **zero schema change** once `refsByKeys` reads the union view. |
| `app_lm_audit_event` | **Reused** for the security/spend trail — new event types are enum constants (`AuditEventType` is extended in code, not schema). |
| `app_lm_action`, `app_lm_role`, `app_lm_role_action` | **Reused** — the new `UNIVERSE_EXPAND` permission is seed `INSERT`s into these catalogs (the designed extension path; `RbacCatalogTest` enforces enum↔seed alignment). |
| `app_lm_project`, `_member`, `_member_role`, workspace/user/auth tables | Untouched; existing guards (`@workspaceAuth` / `@projectAuth`) are reused for gating. |
| Everything else (position, client registry, invitations, tokens…) | Not involved. |

**Genuinely new (three tables + one view, one migration) — each with a single job:**

1. `app_lm_companies_ext` — normalized Coresignal companies, **same column shape as
   `app_lm_companies`** so ingestion stays a dumb mapper and the union stays trivial. Cannot be
   avoided by reuse: the app cannot write `app_lm_companies`.
2. `app_lm_coresignal_raw` — the **full collect payload, verbatim jsonb**, accumulating forever.
   Insurance for the future: a better normalizer, new columns, or new features replay this table
   for free; re-collecting from Coresignal costs credits.
3. `app_lm_universe_coverage` — the **query cache brain**: which (sector/tag × market) cells have
   already been fetched, when, and how completely. This is what makes a repeat or subset query
   serve from our DB with **zero API calls** (§4.5).
4. View `app_lm_companies_all` — `UNION ALL` of base + ext, which lets **every existing query
   and screen work unchanged** by retargeting one table name in `CompanyQueryService`.

Explicitly **not** added (reuse instead):

- ~~`app_lm_universe_expansion` run-ledger table~~ — the daily spend cap can be enforced by
  counting `app_lm_coresignal_raw` rows with `fetched_at >= date_trunc('day', now())`, and the
  who/when/outcome trail lives in `app_lm_audit_event`. A dedicated per-run credit ledger only
  becomes worth it in Phase 2 if invoice reconciliation demands it.

## 3. Coresignal research findings

### 3.1 API mechanics [V]

Base `https://api.coresignal.com/cdapi/v2/`, auth header `apikey`, source slug
`company_multi_source`:

| Endpoint | Shape | Use here |
|---|---|---|
| `POST …/search/es_dsl` | Elasticsearch DSL body → **array of company IDs only**, ≤1,000/page, cursor via `x-next-page-after` header (+ `x-total-results`) | Find matching companies for a strategy scope; the total is the "how many exist" estimate |
| `POST …/search/es_dsl/preview` | Top matches with a small field set: `id, company_name, professional_network_url, website, unique_domain, size_range, employees_count, industry, hq_country, company_logo, _score` | Instant results display + **pre-collect dedup** against our universe. Credit cost undocumented [U] — assume 2 *search* credits (it is a Multi-source endpoint and the credits page lists no free endpoints); it never touches the collect pool. Verify on the trial key via `x-credits-remaining` before/after a call (Phase 0 step 4). |
| `GET …/collect/{id}` or `/collect/{linkedin_shorthand}` | Full record (`?fields=` to trim) | Fetch the companies we keep |
| `GET …/enrich?website={url}` | Lookup by domain | One-off resolution of a specific company (picker fallback, Phase 2) |
| `POST /v2/data_requests/company_multi_source/collect` | Async bulk job → files | Large backfills only (Phase 2, if ever) |

- The simpler "filters" search endpoint exists only for **Base** APIs — Multi-source search is
  **ES DSL only** [V].
- **No webhooks for companies** [V] — freshness is pull-based: TTL re-collect, or an ES DSL range
  query on `last_updated_at` scoped to held IDs.
- Rate limits [V]: search 18/s, collect 54/s, enrich 18/s. Average response ~176 ms (their FAQ).

### 3.2 Credits and pricing

- **2 credits per successful (200) request** on Multi-source (Base = 1). Search credits and
  collect credits are **separate pools**; failed requests are not charged; balance comes back in
  the `x-credits-remaining` response header. [V]
- Self-service tiers (their pricing page): **free trial** 200 collect + 400 search credits,
  7 days, no card; Starter from **$49/mo** (≥250 collects); Pro from **$800/mo** (≥10k); Premium
  from **$1,500/mo** (≥50k). Annual −20%. Monthly credits don't roll over.
- Effective unit cost: **≈ $0.06–$0.16 per company collected**. A search page (up to 1,000 IDs)
  costs 2 search credits — negligible next to collects. Exact per-tier allocations and overage
  rates are quote-only [U].
- Implication: a 500-company expansion for one under-covered sector ≈ $30–$80 — demand-priced,
  exactly the economics the runtime approach is meant to buy.

### 3.3 Data model fit

Multi-source record (500+ fields claimed) maps well onto our columns:

| Coresignal field | Our column | Note |
|---|---|---|
| `company_name` | `name` | |
| `website` / `unique_domain` | `website` / `domain` | domain lowercased at ingest |
| `professional_network_url` | `linkedin_url` | shorthand slug parsed out as a secondary identity |
| `company_logo` | `logo` | |
| `industry` | `primary_industry` | LinkedIn-style string — same family as our 523 values; overlap rate measured in Phase 0 |
| `categories_and_keywords` | `industry_tags` | capped (default 25) |
| `sic_codes` / `naics_codes` | `sic_codes` | NAICS is a bonus we don't store today |
| `employees_count` (+ `employees_count_inferred`, `size_range`) | `employee_count` + derived `employee_range` | band derived from the **same bounds our filters use** |
| `revenue_annual` (+ `revenue_annual_range`) | `revenue_usd` + derived `revenue_range` | same-bounds derivation |
| `hq_country_iso2`, `hq_city` | `hq_country`, `hq_city` | `markets` = `[hq_country]` when GCC, v1 |
| `founded_year`, `description`, followers | `founded`, `description`, `followers` | |
| `is_public`, `ownership_status`, `parent_company_information` | `is_public`, `ownership`, `org_type` | **better ownership data than we have** — the key to enabling the deferred ownership filter in Phase 2 |
| `last_updated_at` | stored as `coresignal_updated_at` | drives Phase-2 TTL refresh |

### 3.4 The three real risks

1. **Licensing [U] — the gating item.** Coresignal's public T&Cs explicitly carve data purchases
   out into a separate signed agreement. The rights this plan depends on — (a) storing collected
   records in our DB, (b) displaying them to our SaaS end-users, (c) retention after termination
   — are **not publicly verifiable** and must be obtained in writing before production caching.
   (Third-party commentary says embedded/derivative SaaS use is commonly granted; that is
   secondary evidence only.) Fallback if terms disappoint: People Data Labs has the most
   permissive published license of the alternatives (Crunchbase = enterprise/custom;
   Apollo/ZoomInfo effectively prohibit cache-and-serve).
2. **Coresignal company-ID stability is undocumented [U].** Same class of problem as "upstream
   ids are re-minted" in our warehouse. Mitigation is our existing convention: key on
   `(source='coresignal', source_id)` and keep **secondary match keys** (`domain`,
   `linkedin_shorthand`) on every cached row.
3. **GCC / sector coverage is unverified [U].** No published geo/sector breakdown, and one
   third-party review warns small/inactive company records can be 3–4 months stale. The free
   trial (200 collects) exists precisely to test our gap sectors empirically before any money or
   code is committed. This is Phase 0 and it is the go/no-go gate.

## 4. How it fits the existing strategy flow

The runtime flow reuses the strategy scope end-to-end — nothing about how consultants define
scope changes. The governing principle: **the app always reads from our database; Coresignal is
only ever a fill step.** An external call happens exclusively when the coverage ledger says a
part of the scope has never been fetched (or has gone stale). Repeat queries, and any *narrower*
query, hit the database alone:

```
Strategy (stored)
  sectors/tags ──┐
  size bands   ──┼─► ScopeFilter
  markets      ──┘      │
                        ▼
              ┌─ coverage check (app_lm_universe_coverage) ─┐
              │ all cells fresh?                             │
              ▼                                              ▼
        YES: 0 API calls                    NO: fetch ONLY the missing/stale cells
              │                                              │
              │                    search es_dsl (per cell) ─► IDs   (2 search credits/page)
              │                        │  drop IDs already held (free)
              │                    preview ─► drop domain/slug dupes vs base (free of collects)
              │                        │
              │                    parallel collect survivors (bounded, ≤54/s, capped)
              │                        │            │
              │                 raw jsonb INSERT    │  (full payload, kept forever)
              │                 app_lm_coresignal_raw
              │                        │            │
              │                    normalize ─► upsert app_lm_companies_ext
              │                        │        ON CONFLICT (source, source_id)
              │                    mark cell covered (cursor, counts, fetched_at)
              ▼                        ▼
        CompanyQueryService reads app_lm_companies_all (view)
                        │
                        ▼
        Sourcing list / estimate / picker — existing screens, unchanged
```

### 4.1 Scope → ES DSL translation

Every `ScopeFilter` dimension has a direct ES DSL counterpart. Band strings become numeric
ranges using bounds that already exist conceptually in `EmployeeBand` / `RevenueBand` (the enums
gain explicit min/max fields so the translator and the normalizer can never disagree):

```json
{ "query": { "bool": { "must": [
    { "bool": { "minimum_should_match": 1, "should": [
        { "terms": { "industry": ["<direct + adjacent sectors + curated aliases>"] } },
        { "terms": { "categories_and_keywords": ["<inferred tags>"] } } ] } },
    { "terms": { "hq_country_iso2": ["AE", "SA", "…selected GCC markets"] } },
    { "bool": { "minimum_should_match": 1, "should": [
        { "range": { "employees_count": { "gte": 51, "lte": 500 } } } ] } },
    { "bool": { "minimum_should_match": 1, "should": [
        { "range": { "revenue_annual": { "gte": 5000000, "lte": 25000000 } } } ] } }
] } } }
```

Rules mirroring `buildWhere()`: sector/tag anchor required (empty scope → no API call); each
block omitted when its list is empty; contiguous selected bands merged into one range. When the
strategy names no market, v1 still constrains to the full GCC set — an unbounded worldwide
search is a credit bomb. Sector strings: exact match first (both sides are LinkedIn-style);
misses get entries in a curated `data/coresignal-sector-aliases.json` (same pattern as the
existing `sector-adjacency.json`), fed by Phase 0 measurements.

### 4.2 Record normalization

The collect payload is normalized into exactly our column shape so **existing filters match the
new rows with no query changes**: `employee_range` / `revenue_range` derived from the shared
band bounds (fallbacks: `employees_count_inferred`, then an explicit LinkedIn
`size_range`-string→band map; all-null stays null, same as today's 9.3%); `primary_industry`
taken verbatim (`sectors()` is data-driven and absorbs new values); tags lowercased and capped;
the raw JSON payload is kept verbatim in `app_lm_coresignal_raw` so an improved normalizer can
be re-run for free — re-collecting would cost credits.

### 4.3 Where fetched rows live — three simple tables, one view

Data structure kept deliberately simple: the normalized table **mirrors `app_lm_companies`
column-for-column** (dumb ingestion, trivial union), the raw table is just id + jsonb (the full
Coresignal dataset, saved as-is for the future), and the coverage table is the query cache:

```sql
-- V18__company_universe_expansion.sql (sketch — V16 taken by sibling branch)

-- 1. Normalized companies — SAME shape as app_lm_companies, nothing clever
CREATE TABLE app_lm_companies_ext (
    id        bigint GENERATED ALWAYS AS IDENTITY (START WITH 100000000) PRIMARY KEY,
    source    text NOT NULL DEFAULT 'coresignal',
    source_id text NOT NULL,
    linkedin_shorthand text,            -- secondary identity: Coresignal id stability undocumented
    -- …the same reference columns as app_lm_companies (name, domain, primary_industry,
    --  industry_tags, employee_count/_range, revenue_usd/_range, hq_country, markets, …)
    suppressed_duplicate boolean NOT NULL DEFAULT false,
    fetched_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX … ON app_lm_companies_ext (source, source_id);
-- mirror V3's index set (country, industry, employees, revenue, GIN markets/tags) + domain

-- 2. Raw archive — the full Coresignal record, verbatim, accumulating forever.
--    Replaying this table is free; re-collecting from the API costs credits.
CREATE TABLE app_lm_coresignal_raw (
    source_id  text PRIMARY KEY,
    payload    jsonb NOT NULL,
    coresignal_updated_at timestamptz,           -- their last_updated_at
    fetched_at timestamptz NOT NULL DEFAULT now(),  -- freshness + the daily-budget counter
    refetched_at timestamptz                     -- last refresh, null until first re-collect
);

-- 3. Coverage ledger — one row per fetched (anchor × market) cell; the serve-from-DB decision.
--    The envelope columns record WHAT PREDICATE the cell was fetched under, so the planner can
--    answer containment questions ("asked ≥5M, covered ≥2M → already have it") — see §4.5.
CREATE TABLE app_lm_universe_coverage (
    anchor       text NOT NULL,                  -- one sector or tag label
    anchor_type  text NOT NULL,                  -- SECTOR | TAG
    market       text NOT NULL,                  -- ISO country code (AE, SA, …)
    -- covered predicate envelope; NULL = unbounded on that side.
    -- Default fetch mode is band-free → all four NULL → covers every band query forever.
    revenue_covered_min  bigint,
    revenue_covered_max  bigint,
    employee_covered_min integer,
    employee_covered_max integer,
    search_total bigint,                         -- x-total-results at last search
    collected    integer NOT NULL DEFAULT 0,     -- how many we hold from this cell
    next_cursor  text,                           -- x-next-page-after; null = cell exhausted
    fetched_at   timestamptz NOT NULL,           -- freshness clock for the whole cell
    PRIMARY KEY (anchor, anchor_type, market)
);

CREATE VIEW app_lm_companies_all AS
    SELECT <shared columns> FROM app_lm_companies
    UNION ALL
    SELECT <shared columns> FROM app_lm_companies_ext WHERE NOT suppressed_duplicate;
```

Design points, each forced by an existing constraint:

- **Identity seeded at 100,000,000** — both tables mint their own `id`; without the offset the
  union view would emit colliding row keys to the frontend. (Correctness never depends on `id` —
  strategy references key on `(source, source_id)`.)
- **Dedup is a flag, not deletion and not a view-time anti-join**: duplicates against the base
  universe are detected at ingest (normalized domain / LinkedIn shorthand) and suppressed via
  `suppressed_duplicate`. Pre-collect preview dedup means most duplicates never even cost collect
  credits; the flag is the backstop. Policy stays in Java (re-runnable), reads stay cheap, and a
  paid-for record is never thrown away.
- **Rejected alternatives**: granting `lm_app` INSERT on `app_lm_companies` (defeats the
  hardening, breaks the sync report, couples app releases to ETL ops) and ETL-side ingestion
  (not runtime — consultants need companies now, not after the next ops run; a pipeline backfill
  can still adopt `source='coresignal'` rows later because the keying already allows it).
- **One deploy caveat**: the view reads `app_lm_companies`, owned post-hardening by `postgres`,
  while migrations run as `lm_migrate` — so `lm_migrate` needs `GRANT SELECT ON app_lm_companies`
  **before** V18 runs in production (one line in the ops grant flow; verify on staging first).

### 4.4 Runtime orchestration — the two APIs, in parallel, store-and-show

One expansion request (`CompanyUniverseExpansionService`) runs this sequence:

1. **Resolve scope** from the stored strategy → `ScopeFilter` (existing `SourcingService` logic).
2. **Decompose into cells.** The scope's anchors (each direct/adjacent sector, each inferred
   tag) × selected markets = the cell list. Cells are the unit of fetching, caching, and
   freshness — e.g. `(Oil and Gas, AE)`, `(Oil and Gas, SA)`, `(grocery retail, AE)`.
3. **Plan** (`UniverseFetchPlanner` over `app_lm_universe_coverage`): containment check of the
   asked predicate against each cell's covered envelope (§4.5). Fully contained + fresh →
   **return immediately, zero API calls** — the sourcing list is already served from the view.
   Otherwise the plan lists only the missing/stale cells with their residual predicates.
4. **Search (API #1)** per uncovered cell: `POST …/search/es_dsl` with that anchor + market —
   deliberately **without band narrowing** (see §4.5 for why) → up to 1,000 IDs + `x-total-results`
   (2 search credits). Cells are few and searches are cheap; run them sequentially or with small
   parallelism under the 18/s limit.
5. **Free dedup before spending**: drop IDs already in `app_lm_coresignal_raw` (we hold them);
   `preview` the remainder and drop candidates whose domain/LinkedIn slug already exist in
   `app_lm_companies` (they'd be duplicates of the base universe).
6. **Collect (API #2) in parallel**: surviving IDs through a bounded executor (8 concurrent,
   comfortably under 54/s), clamped by `maxCollectsPerExpansion` and the daily budget. Each
   response is handled as it lands, in batches:
   - `INSERT` the verbatim payload into `app_lm_coresignal_raw`,
   - normalize → upsert `app_lm_companies_ext ON CONFLICT (source, source_id) DO UPDATE`.
   Storing and showing are the same event: rows become visible to `app_lm_companies_all` the
   moment their batch commits — there is no separate "publish" step.
7. **Mark coverage**: upsert each cell's row — `search_total`, `collected`, `next_cursor`
   (null when the cell is exhausted; otherwise "expand more" resumes the cursor instead of
   re-searching), `fetched_at = now()`.
8. **Respond + audit**: summary counts back to the UI; the Sourcing screen refetches its list,
   which now includes the new rows via the view. Audit event records who spent what.

The whole request is synchronous and bounded (~2–3 s for 50 collects). The user-facing
"parallel" experience is: the preview rows (step 5) can be returned to the UI immediately as a
"found these" optimistic list while step 6 finishes, and the authoritative list arrives on the
refetch — no SSE/job machinery needed at this cap.

### 4.5 Serve-from-DB on repeat and subset queries — the coverage-cell pattern

The requirement: the *second* time a user runs the same query — or any subset of it — no
real-time API call happens; our database answers. This is a **semantic query cache**: instead of
caching responses keyed by the literal query, we record *what data region each past call
covered* and answer new queries by **predicate containment** — the standard
"answering-queries-using-views" idea from database theory, kept deliberately small here.

**The two decision layers, both cheap:**

**Layer 1 — "call search at all?" — the `FetchPlanner`.** A pure, stateless decision component
(`company/service/UniverseFetchPlanner`): input `ScopeFilter` + the scope's coverage rows (one
indexed read, a few dozen rows), output a `FetchPlan` — either *serve locally* or *the minimal
list of (cell, residual predicate) fetches*. Per cell it checks containment of the asked
predicate against the covered envelope:

- Asked `revenue ≥ 5M`, cell covered `revenue ≥ 2M` (or band-free) → **contained → no call.**
  (The user's example: broader was already fetched; the narrower ask is a pure DB filter.)
- Asked `revenue ≥ 2M`, cell covered `revenue ≥ 5M` → **partial → residual fetch**: call
  Coresignal for `[2M, 5M)` only — interval subtraction, not a full re-fetch — then widen the
  cell's envelope to `≥ 2M`. Envelopes only ever widen, so the ledger converges toward
  band-free.
- Cell has no row (new sector/market) → full cell fetch.
- Cell row older than `coverageTtl` → *stale, not uncovered*: delta search with
  `last_updated_at ≥ fetched_at` (§ freshness below), never a blind re-pull.

Containment math is per-dimension interval logic over at most two numeric axes (employees,
revenue) — a few comparisons in Java, microseconds, fully unit-testable with no I/O. Residuals
on **one axis at a time** (planner picks the axis with the gap and keeps the other at the asked
value); if both axes have gaps it falls back to a band-free cell fetch rather than generating a
cross-product of residual boxes — simplicity over cleverness, and it just accelerates the
convergence the design wants anyway.

The **default fetch mode stays band-free** (envelope = unbounded): for GCC-sized cells the extra
collect volume is small, capped, and buys "every future band permutation is free". The envelope
machinery is the controlled fallback for cells the totals show are too big to swallow band-free
(e.g. a huge sector where the user only ever wants `> 100M` revenue) — there the planner fetches
exactly the asked interval and widens incrementally on later residuals.

**Layer 2 — "collect only what we don't hold" — ID-set difference.** When a search does run and
returns N IDs, we never collect blindly: one indexed query —
`SELECT source_id FROM app_lm_coresignal_raw WHERE source_id = ANY(:ids)` (primary-key lookup,
milliseconds for a 1,000-ID page) — splits the page into *held* vs *missing*. The user's
example: 200 IDs return, 101 already held → **99 collects**, not 200. The missing set then
passes the preview dedup (drop rows whose domain/LinkedIn slug already exist in
`app_lm_companies` — those would duplicate the base universe) before any collect credit is
spent. Held IDs cost nothing and stay fresh under the cell's own clock.

```
ScopeFilter ─► FetchPlanner ──► serve locally (0 calls)
                   │ (containment vs coverage envelopes)
                   └──► FetchPlan: [(cell, residual predicate), …]
                              │
                        search es_dsl (residual only) ─► N IDs
                              │
                        ID-set difference vs raw PK ─► missing ⊂ N
                              │
                        preview dedup vs base universe
                              │
                        collect only the survivors ─► raw + ext upsert
                              │
                        widen cell envelope, stamp fetched_at
```

The rest of the original cell reasoning still holds:

- **Same query again** → every cell has a fresh coverage row → step 3 short-circuits. 0 credits.
- **Narrower sector/market selection** → its cells are a subset of already-covered cells → 0
  credits.
- **Added or changed size/revenue bands** → still 0 credits, *because cells are fetched
  band-free*: step 4 deliberately omits `employees_count`/`revenue_annual` ranges from the ES
  DSL, so a cell's cached population contains every size and revenue variant once. Band
  filtering then happens where it always has — in `buildWhere()` over the view. Bands only ever
  narrow, so they can never make a covered cell insufficient. (Cost trade-off: a cell fetch
  collects somewhat more than the banded need of the moment — bounded by the per-request cap and
  cursor resumption — in exchange for every future band permutation being free. For GCC-sized
  cell populations this is the right trade; the cap keeps the worst case fixed.)
- **New sector/tag or new market** → exactly the uncovered cells are fetched; everything else
  still comes from the DB. Delta-fetching falls out of the decomposition — there is no
  "invalidate the whole query" moment.

**Degree of freshness.** The dataset accumulates — rows are never deleted, and every re-fetch
upserts over `(source, source_id)`:

- Each cell carries `fetched_at`; config `coverageTtl` (default 30 days) decides when a cell is
  *stale* rather than *uncovered*.
- Refreshing a stale cell is cheap: re-search it with an added
  `range: { last_updated_at: { gte: <cell.fetched_at> } }` clause — Coresignal-side change
  detection — so only **new or changed** companies come back for collecting; unchanged holdings
  are already ours. Update `fetched_at`, done.
- Row-level freshness is visible too (`app_lm_coresignal_raw.fetched_at`,
  `coresignal_updated_at`), so the UI can badge data age later, and Phase 2's TTL re-collect
  knows exactly which rows are oldest.
- The base universe (`app_lm_companies`) keeps its own sync cadence; the two freshness regimes
  never interact — the view just unions them.

### 4.6 Application surface

Follows the codebase's own patterns — no new frameworks, no SDK dependency:

- **Module layout** (inside the `company` feature — Coresignal serves exactly one feature, so it
  does not belong in `core/`; the HTTP client copies the `ResendEmailSender` pattern:
  `RestClient` built in the constructor, keyless no-op default so a fresh clone runs):
  - `company/service/CompanyDataProvider` (interface) / `CoresignalCompanyDataProvider` /
    `NoOpCompanyDataProvider` (default)
  - `company/service/UniverseFetchPlanner` (pure containment/residual logic — the intelligence
    layer, no I/O), `CoresignalQueryTranslator`, `CoresignalRecordMapper`,
    `CompanyUniverseExpansionService` (orchestrator: plan → search residuals → ID-diff →
    preview dedup → collect → normalize → JdbcClient upsert → widen envelopes → audit)
  - `company/constant/CompanySource` (`BRIGHTDATA`, `CORESIGNAL` — the source strings, typed)
  - config block in `LightMoveProperties`: provider toggle, api key, base URL, timeout,
    `maxCollectsPerExpansion` (default 50), `dailyCollectBudget` (default 500), `creditFloor`,
    `maxIndustryTags`
- **Endpoints** on the existing `SourcingController` (scope is resolved server-side from the
  stored strategy, exactly like sourcing reads — client-supplied scope would leak team-only
  strategy content):
  - `POST /api/v1/projects/{id}/sourcing/expansion-preview` → available / already-held / new
    candidates / estimated credits / budget remaining
  - `POST /api/v1/projects/{id}/sourcing/expansions` → collected / added / suppressed /
    credits spent+remaining
- **Gate**: new workspace action `UNIVERSE_EXPAND` (seeded to workspace ADMIN in v1) combined
  with the project seat — `WORK_EXECUTE and UNIVERSE_EXPAND`. Spending workspace-wide metered
  credits is a genuinely new permission; neither `PROJECT_EDIT` nor `WORK_EXECUTE` alone should
  grant it. One seed INSERT + one enum constant; `RbacCatalogTest` keeps them aligned. Widening
  to MEMBER later is one migration.
- **Sync vs async**: synchronous, capped. Preview ≈ 2 calls (~400 ms); 50 collects with bounded
  parallelism ≈ 2–3 s — a normal request. No job/queue infrastructure exists in the repo and v1
  should not grow one; "expand again" walks deeper into the ID-ordered search results. Async
  bulk is Phase 2 only if real usage demands >100 per action.
- **UX (v1)**: an explicit, staff-gated **"Expand universe"** action on the Sourcing screen —
  preview count and credit estimate shown *before* any collect spend, then confirm. Automatic
  background expansion is rejected for v1: it spends real money on an arbitrary
  "under-delivered" threshold with no human in the loop.
- **Budget enforcement (reuse-first)**: per-request clamp from config; daily cap = count of
  `app_lm_companies_ext` rows `fetched_at >= today` (no new ledger table); refuse below
  `creditFloor` from the `x-credits-remaining` header with a distinct `ErrorCode`; audit events
  (`UNIVERSE_EXPANSION_REQUESTED/COMPLETED/FAILED`) for the trail.

## 5. Step-by-step execution plan

### Phase 0 — validate before building (free trial, zero product code)

The go/no-go gate. Costs nothing but the trial signup.

1. Sign up for the Coresignal free trial (200 collect + 400 search credits, 7 days).
2. Write `ops/coresignal/validate-coverage.sh` (bash + curl + jq, key from env — an ops script
   like `sync-companies.sh`, **not** a test: the build must never spend credits).
3. For each GCC country × our top ~40 sectors **plus the specific under-delivering sectors that
   motivated this**: run count-only ES DSL searches and preview samples; collect ~20 full
   records across the gap sectors.
4. Output a CSV: Coresignal count vs `app_lm_companies` count per (sector, country); plus wire
   facts — exact `industry` strings vs our 523 (feeds the alias file), `size_range` string
   formats, `hq_country_iso2` casing, revenue field population rates for GCC SMEs, and the
   **preview endpoint's actual credit cost** (diff `x-credits-remaining` across two preview
   calls — undocumented, assumed 2 search credits).
5. **In parallel**: contact Coresignal for the data-usage agreement; get caching, end-user
   display, and post-termination retention answered **in writing**.
6. **Exit gate**: coverage materially beats the universe in the gap sectors AND licensing
   permits cache-and-serve. If either fails → stop; evaluate People Data Labs with the same
   script shape.

### Phase 1 — MVP integration (order of work)

1. **Migration `V18__company_universe_expansion.sql`**: `app_lm_companies_ext` +
   `app_lm_coresignal_raw` + `app_lm_universe_coverage` + `app_lm_companies_all` view +
   `UNIVERSE_EXPAND` action seeds. Add the `lm_migrate` SELECT grant to the ops flow; verify
   V18 applies on a hardened staging DB.
2. **Enums gain bounds**: add min/max to `EmployeeBand` / `RevenueBand`; add
   `WorkspaceAction.UNIVERSE_EXPAND`; extend `AuditEventType` permits with a new
   `CompanyEventType`. (`RbacCatalogTest` goes green only when enum and V18 seeds agree.)
3. **Retarget reads**: `CompanyQueryService` queries `app_lm_companies_all` instead of
   `app_lm_companies` (table-name change in `sectors`, tags co-occurrence, `estimate`, `search`,
   browse/typeahead, `refsByKeys`; WHERE logic untouched). Seed integration tests with rows in
   both tables to prove union, suppression, ordering, and cross-source `refsByKeys`.
4. **Provider client**: `CompanyDataProvider` interface + `CoresignalCompanyDataProvider`
   (RestClient; `apikey` header; cursor pagination; `x-credits-remaining` parsing) +
   `NoOpCompanyDataProvider` default + config wiring + `LightMoveProperties.Coresignal` block.
   Tests via `MockRestServiceServer` (ships with spring-test; no new dependency).
5. **Planner + translator + mapper**: `UniverseFetchPlanner` (scope → cells → containment vs
   coverage envelopes → `FetchPlan` with residual predicates, §4.5), `CoresignalQueryTranslator`
   (ScopeFilter/residual → ES DSL, rules from §4.1) and `CoresignalRecordMapper` (payload → our
   columns, rules from §4.2) — all three pure and unit-tested (planner: containment, residual
   subtraction, both-axes fallback, TTL staleness; mapper against real Phase 0 sample payloads;
   band derivations asserted against the enums so drift is a red build). Curate
   `data/coresignal-sector-aliases.json` from Phase 0's overlap CSV.
6. **Orchestrator**: `CompanyUniverseExpansionService` implementing the §4.4 sequence — scope →
   cells → coverage check (fresh cells skipped; fully covered scope returns with zero API
   calls) → per uncovered cell: band-free search → drop already-held IDs → preview-dedup →
   parallel collect up to cap → raw insert + normalize + upsert per batch → coverage upsert
   (cursor, counts, `fetched_at`) → audit event. Budget guard (daily count from raw
   `fetched_at` + credit floor) checked up front. Stale-cell refresh uses the
   `last_updated_at >= fetched_at` delta search (§4.5).
7. **Endpoints**: the two `SourcingController` routes (§4.4) with the combined
   `WORK_EXECUTE + UNIVERSE_EXPAND` gate; thin `project`-side service resolves strategy →
   `ScopeFilter` (extracting the existing private mapping in `SourcingService` for reuse);
   authorization integration tests.
8. **Frontend**: "Expand universe" button + preview/confirm dialog + result summary on the
   Sourcing page (visible only with the gate actions, using the existing capability pattern);
   TanStack Query mutations; Sourcing list refetch on completion.
9. **End-to-end verification**: Testcontainers flow test (expand with a recording fake provider →
   rows land in ext → appear in sourcing results → duplicates suppressed → budget refusal), then
   `npm test`, then a staging run against the real trial key.

### Phase 2 — after MVP proves out

- **Freshness**: TTL re-collect (30–90 days, driven by `coresignal_updated_at`) and/or cheap
  change detection via ES DSL `last_updated_at` range over held IDs.
- **Ownership filter enablement**: map `ownership_status`/`is_public` into the 8
  `OwnershipStructure` buckets — Coresignal's ownership data is the missing piece that deferred
  this filter — then add the clause to `buildWhere` and the 5th `AppliedFilters` flag.
- **Jordan/Egypt**: add `GeographyMarket` constants + frontend catalog + translator geo set
  (the enum's javadoc anticipates exactly this).
- **Sync reconciliation**: after each `sync-companies.sh` run, mark ext rows newly duplicated by
  brightdata arrivals (`suppressed_duplicate = true`).
- **Picker fallback**: `enrich?website=` for one-off "company not in our universe" additions.
- Optional, only if justified by usage/invoices: per-run credit ledger table; async bulk
  expansion for >100-company pulls.

## 6. Cost model summary

| Scenario | Search credits | Collect credits | ≈ Cost (Pro tier) |
|---|---|---|---|
| Preview a scope (no spend commitment) | 2–4 | 0 | < $0.50 |
| One expansion (50 companies) | 2–4 | 100 | ~$8 |
| Filling a gap sector (~500 companies) | ~4–10 | 1,000 | ~$80 |
| Hypothetical full re-buy of a 54k universe | — | 108,000 | ~$6–9k/yr tier territory — exactly what runtime-on-demand avoids |

Suggested start: validate on the free trial (Phase 0), run the MVP on Starter/Pro, revisit tier
only when the daily-budget counter shows sustained demand.

## 7. Open items that block production (not development)

1. **Signed data agreement** covering DB caching, end-user display, retention (§3.4-1). Blocks
   enabling the real provider in production; everything up to staging proceeds on the trial.
2. **Phase 0 coverage results** for the gap sectors in GCC — the empirical go/no-go.
3. Commercial choices exposed as config, decided later: plan tier, `dailyCollectBudget`,
   `maxCollectsPerExpansion`, who beyond workspace ADMIN gets `UNIVERSE_EXPAND`.
