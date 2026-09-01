# Bright Data as the people source — investigation

**Status: findings, not a decision · Probed live 2026-08-31 against the account key in
`apps/api/src/main/resources/application-local.yml` (`lightmove.vendor.BrightData.api-key`).**

Every number below with a ✅ was measured against the live API on that date, not read from a docs
page. Everything else is their published figure and is marked as such. Re-check before implementing:
this is a vendor's product surface, and the last such note (`CoresignalEmployeeClient`'s class doc)
already carries the same warning for the same reason.

## Why this exists

The sourcing design (`hak-sourcing-pipeline-design.md`) picks Coresignal for stage S2 — who works at
a target company. Coresignal bills search credits to find people and *collect* credits again to read
them, at $0.03–$0.196 per record depending on plan tier. That is the cost the whole §10 unit-economics
argument is fighting. This asks whether Bright Data can do the same job cheaper.

Short answer: yes, by roughly one to two orders of magnitude, and it removes a pipeline stage rather
than replacing it. The catch is freshness, and it is a real one — see [Gaps](#gaps-that-matter).

## The finding that decides it: the join key is already ours

Apollo's `company_linkedin_url` slug **is** Bright Data's people-side company key.

```
app_lm_apollo_companies.company_linkedin_url = http://www.linkedin.com/company/dp-world
                                        slug = dp-world
brightdata people.current_company_company_id = dp-world     ✅ matched
brightdata people.current_company_company_id = 163494       ✅ returned ZERO rows
```

`163494` is DP World's numeric LinkedIn company id, taken from Bright Data's own company dataset. The
people dataset does not use it. **The slug is the key and the numeric id is a decoy** — a filter built
on it fails silently as "no records found", which is indistinguishable from a company with no
employees. Worth an inline comment wherever this filter is built.

Local Apollo coverage ✅:

| | rows |
|---|---|
| total | 71,822 |
| with `company_linkedin_url` | 71,777 (99.94%) |
| with `website` | 61,642 (85.8%) |

Column is `company_linkedin_url`. There is no `linkedin_url` column — a query against that name errors
rather than returning nothing, which is the good failure.

**Consequence: pipeline stage S1 (company resolution) disappears on this path.** No search credits, no
`company_resolution` table, no match-confidence ladder, no slug-vs-domain matching, and no
"subsidiary resolved as its parent group" failure mode — because nothing is being resolved. The
company universe is already keyed on the thing the people dataset joins on.

## The API to use — Filter, not Scraper

Bright Data sells two different things against the same dataset ids. The one that matters here is the
**marketplace Filter API**: it queries their *stored* 115M-profile LinkedIn dataset. Nothing is
scraped on demand, and the whole profile comes back in the matched row.

```
POST https://api.brightdata.com/datasets/filter                          → {"snapshot_id": "..."}
GET  https://api.brightdata.com/datasets/snapshots/{id}                  → status, dataset_size, cost
GET  https://api.brightdata.com/datasets/snapshot/{id}/download?format=json
```

Auth is `Authorization: Bearer <key>` on all three.

**The download path is not the one in the docs.** ✅ `/datasets/v3/snapshot/{id}` returns
`404 Snapshot does not exist`; `/datasets/snapshot/{id}/download` returns the records. Both
`/datasets/snapshot/...` and `/datasets/snapshots/...` work for download.

Dataset ids, read live from `GET /datasets/list` ✅:

| id | dataset | size |
|---|---|---|
| `gd_l1viktl72bvl7bjuj0` | LinkedIn people profiles | 115,000,000 |
| `gd_l1vikfnt1wgvvqz95w` | LinkedIn company information | 55,000,000 |

`/datasets/list` is free and unauthenticated beyond the key. `/customer/balance` is refused by this key
("lacks the required permissions").

### The working filter

```json
{
  "dataset_id": "gd_l1viktl72bvl7bjuj0",
  "records_limit": 100,
  "filter": {"operator": "and", "filters": [
    {"name": "current_company_company_id", "operator": "in",
     "value": ["dp-world", "aramco", "emirates-nbd"]},
    {"name": "country_code", "operator": "in",
     "value": ["AE", "SA", "QA", "KW", "BH", "OM"]},
    {"operator": "or", "filters": [
      {"operator": "or", "filters": [
        {"name": "position", "operator": "includes", "value": "Chief"},
        {"name": "position", "operator": "includes", "value": "President"},
        {"name": "position", "operator": "includes", "value": "Managing Director"},
        {"name": "position", "operator": "includes", "value": "General Manager"}]},
      {"operator": "or", "filters": [
        {"name": "position", "operator": "includes", "value": "Vice President"},
        {"name": "position", "operator": "includes", "value": "Head of"},
        {"name": "position", "operator": "includes", "value": "Group Head"},
        {"name": "position", "operator": "includes", "value": "Country Head"}]}]}]}
}
```

Operators available: `=` `!=` `>` `<` `>=` `<=` `in` `not_in` `includes` `not_includes`
`array_includes` `not_array_includes` `is_null` `is_not_null`, plus `and`/`or` groups.

`includes` on `position` is a plain substring match, case-insensitive in practice ✅ — the filter above
returned `HEAD OF KEY ACCOUNTS MANAGEMENT` in caps.

### Constraints found by hitting them, not by reading

- **Max 4 rules per logical group** ✅ — an `or` of 18 title patterns is rejected outright with
  `"Filter logical groups can have a maximum of 4 rules."` The nested `or`-of-`or`s above is the
  workaround: 4 groups × 4 leaves = **16 title patterns maximum** under one branch, and the top-level
  `and` can hold only 4 rules including that branch. Any title vocabulary larger than 16 needs more
  than one job. This shapes the adapter's API and is the single most surprising limit here.
- Max 3 nesting levels.
- 120 filter requests/hour; 100 parallel jobs per dataset; snapshot expires after 14 days.
- **`array_includes` will not match inside an object array** ✅ — filtering `experience` by
  `{"company_id": "dp-world"}` is rejected as a bad value. Only `current_company_company_id` reaches
  the current employer. Past employers are not filterable, only readable once the row is bought.
- **Latency is minutes** ✅ — a single-company filter took 2–15 minutes to build; the 5-company `in`
  took ~20. This settles open decision #9 in the design doc: **queued job with progress states, not a
  synchronous call behind a spinner.**
- **There is no count endpoint.** A filter's yield cannot be known before paying for it. `records_limit`
  is therefore the *only* cost control and must be a required parameter on our adapter, never an
  optional one with a permissive default.

## Cost

| | Bright Data | Coresignal |
|---|---|---|
| company resolution | **$0** — slug join | search credits per company |
| breadth pull (S2) | $2.5 CPM → **$0.0025/row** | search credits |
| full profile (S7 body) | **included in the same row** | separate collect credit, $0.03–$0.196 |
| live currency re-check | $1.50/1k → $0.0015/person, 5k/month free | another collect credit |

Published figures; the account's own invoice was not readable (see [Unverified](#unverified)).

**The structural saving is bigger than the unit price.** ✅ A filtered row already carries the whole
profile: measured 22.7 KB average, 3.8 experience entries, plus education, languages, certifications,
followers, connections, `linkedin_num_id`. S2 and the S7 profile body are **one paid call**, not two.

Reference mandate, 15 companies, assuming ~2k LinkedIn employees each, ~60% GCC-resident, 7% passing
the seniority filter (the 7% is measured — see below):

- ~1,300 rows → **≈ $3.25, full profiles included**
- the same set through Coresignal → **$39–$255**, after separately paying to resolve and to search
- 25 shortlist live currency re-scrapes → **≈ $0.04**

## Measured recall and precision

Sampled 100 GCC profiles at one company with no title filter ✅:

- **7% have `position` = null.** A filter on `position` cannot see them at all. This is a hard recall
  floor, and it is invisible unless it is written down: those people are not "excluded", they are
  *unrepresented*. Either skip the title filter for companies small enough that the whole roster is
  cheap, or record `position_null` as a first-class exclusion reason in the filter ledger.
- **7% pass a 12-pattern seniority filter.** That is the S2 yield rate and the cost multiplier.

Quality of what comes back, DP World + GCC + seniority patterns ✅:

```
Chief Planning & Project Management Officer at DP World
Chief Executive Officer at P&O Maritime Logistics, a DP World company
Executive Vice President of Group Health, Safety & Environment
Group Vice President - Project Portfolio
Senior Vice President - Operations, Sub-Saharan Africa Region
Group Director - Financial Analysis & Mgmt Reporting
```

Noise in the same result: `Partnerships and Communications @ DP World` (caught by *Partner*),
`Robotics & Automation Engineer | Head of Technical Expertise` (*Head of*), `Deputy Fire Chief at
Saudi Aramco`, `Store General Manager`, `Experienced Hyper market store manager`.

That noise is the design working, not failing — §2.1 of the sourcing design says provider-side filters
are recall filters and cutting happens later on our own taxonomy. S4's `function` / `scope` /
`size_tier` predicates remove every line above, and "Deputy" is already a modifier in the S3 rule
engine.

## Batching, and the trap in it

`current_company_company_id` accepts `in` with several slugs, so a 15-company mandate can be one job
rather than fifteen ✅ — which matters against the 120/hour cap. Five companies, one job, 60 rows:

```
aramco 22 | emirates-nbd 15 | majid-al-futtaim 13 | dp-world 7 | almarai 3
SA 32 | AE 26 | KW 1 | OM 1
```

**But `records_limit` truncates globally, not per company.** Aramco took 37% of the budget and Almarai
got 3 rows. That is exactly the concentration failure the design doc rejected global top-N ranking at
S5 to avoid — reintroduced one stage earlier, where nobody is looking for it.

**Recommendation: one filter job per company.** Billing is per record, not per job, so per-company jobs
cost nothing extra, guarantee the coverage the client is paying for, and fit the 120/hour cap fifteen
times over. Batching is a false economy here.

## Gaps that matter

- **No per-record collected-at.** ✅ The record carries no `timestamp` field of any kind — the full key
  list is `about, activity, avatar, banner_image, bio_links, certifications, city, connections,
  country_code, courses, current_company, current_company_company_id, current_company_name,
  default_avatar, education, educations_details, experience, first_name, followers,
  honors_and_awards, id, influencer, input_url, languages, last_name, linkedin_id, linkedin_num_id,
  location, memorialized_account, name, organizations, patents, people_also_viewed, position, posts,
  projects, publications, recommendations, recommendations_count, similar_profiles, url,
  volunteer_experience`. The dataset is a snapshot of unknown age. This is the §S7 currency risk —
  "presenting a client with an executive who left eight months ago" — and the filter path cannot
  answer it at all.
- **The live scrape path can.** ✅ `POST /datasets/v3/scrape?dataset_id=gd_l1viktl72bvl7bjuj0` with
  `{"input":[{"url": "..."}]}` returned a record in 20.6s carrying `"timestamp": "2026-08-31T12:40:05Z"`.
  So the two-tier shape the design already wants is *forced by the data* rather than chosen: cheap
  dataset filter for breadth, live scrape for the ~25 shortlisted, and only the second one is
  auditable for freshness.
- **No job-change signals.** Coresignal's `experience_recently_started` / `_closed` /
  `experience_change_last_identified_at` have no equivalent on the Bright Data dataset path. §9.4's
  roster delta-refresh design depends on exactly those. Delta refresh does not survive the switch
  unchanged — a Bright Data roster is re-filtered, not delta'd.
- **Compliance exposure grows.** §11 already flags that building a persistent derived database from an
  Apollo export conflicts with Apollo's terms. This path *joins* an Apollo-derived slug to a purchased
  people dataset, making the Apollo derivation load-bearing rather than incidental. The GLEIF /
  regional-registry re-derivation becomes a blocker for this design, not a later cleanup.
- Discovery collectors are not available to this key ✅ — `type=discover_new` answers
  `"Incorrect discovery collector id Available types: "` with an empty list. Not needed by this
  design, but it rules out "discover people by keyword" without a key/plan change.

## What it does to the pipeline

| Stage | Design doc | With Bright Data |
|---|---|---|
| S1 company resolution | Coresignal search, cached permanently | **deleted** — Apollo slug is the key |
| S2 breadth pull | ~900 rows, search credits | one filter job per company, full profiles, ≈$3/mandate |
| S3 title normalisation | dictionary + rules + LLM cache-fill | unchanged — still the moat |
| S4 hard filters | SQL precision filter | unchanged, and now also cuts the ~7% title noise |
| S5 vector rank | top-K per company | unchanged |
| S6 LLM rerank | one batched call | unchanged |
| S7 profile body | separate paid collect | **already paid for at S2** |
| S7 currency check | Coresignal change signals | Bright Data live scrape, $0.0015/person, has a timestamp |
| S8 review | human | unchanged |

Coresignal keeps exactly one job it does better: change signals. Whether that is worth a second
contract is the same question §12 already asks about Crustdata.

## How it fits the vendor layer

`ea36ffc` built the shape and `CoresignalEmployeeClient` is the worked example to copy — `@Retryable`
naming `VendorRetryPredicate`, the body inside `VendorCallGuard`, an `Optional` return because "nobody
matched" is an answer, and our own record coming back out rather than a parsed vendor payload. Two
differences:

1. **It is asynchronous.** Filter returns a snapshot id; the answer arrives minutes later. That does
   not fit `VendorAttemptChain`, which models "try the precise lookup, then the loose one" within a
   single request. A sourcing run needs a row in Postgres holding the snapshot id and its state, and
   the poll is a scheduled read rather than part of the original call.
2. **Auth is `Authorization: Bearer <key>`**, so `VendorClientSpec.authValuePrefix` is `"Bearer "` —
   the field exists for exactly this, and the Coresignal adapter's `apikey`-header comment already
   explains why the header is named rather than assumed.

`captureErrorBody` must stay **false**: filter error bodies echo the filter, which names companies and
can name people.

## Unverified

- **Every snapshot in this investigation reported `cost: 0`** with `"billing_mode": "per_set"`. That is
  either free credits, a trial, or a per-set subscription on the account — it is not evidence that
  filtering is free. The per-record prices in this document are Bright Data's published figures, not
  this account's invoice, and `/customer/balance` is refused by this key. **Check the billing dashboard
  before quoting the cost model to anyone.**
- The ~2k-employees-per-company and 60%-GCC-resident assumptions in the cost estimate are guesses. The
  7% seniority yield and 7% null-position rate are measured, but at one company only.
- Whether GCC executives are reliably tagged with a GCC `country_code` was not tested. `country_code`
  appears to be residence, and an expatriate executive's profile may carry their home country — the
  DP World sample returned people in IN, GB and EC on a company-only filter. Filtering on country is a
  precision filter applied at the provider, which is the thing §2.1 warns against; consider pulling
  without it and cutting on geography in S4 instead.

## Reproducing this

The probe scripts were written to the session scratchpad, not the repo. To repeat, the whole
investigation is four calls:

```bash
# key is read from the gitignored local yml; never echo it
KEY=$(grep -A3 -i "BrightData:" apps/api/src/main/resources/application-local.yml \
      | grep -i api-key | head -1 | sed 's/.*api-key:[[:space:]]*//' | tr -d '"'"'"' \r')

# 1. what datasets this key can see
curl -s -H "Authorization: Bearer $KEY" https://api.brightdata.com/datasets/list

# 2. the field list for the people dataset (this is how the filterable names were found)
curl -s -H "Authorization: Bearer $KEY" \
  https://api.brightdata.com/datasets/gd_l1viktl72bvl7bjuj0/metadata

# 3. submit a filter (body as above) → {"snapshot_id": "snap_..."}
curl -s -X POST -H "Authorization: Bearer $KEY" -H 'Content-Type: application/json' \
  -d @filter.json https://api.brightdata.com/datasets/filter

# 4. poll, then download
curl -s -H "Authorization: Bearer $KEY" https://api.brightdata.com/datasets/snapshots/snap_xxx
curl -s -H "Authorization: Bearer $KEY" \
  "https://api.brightdata.com/datasets/snapshot/snap_xxx/download?format=json"
```

A snapshot that matches nothing ends `status: failed` with
`warning_code: "no_records_found"` — a normal answer, not an error, and the adapter must treat it as
"nobody here" the way `CoresignalEmployeeClient` treats an empty id array.

## Open questions this raises for the design doc

1. Does Coresignal stay for change signals alone, or does the currency check become a live re-scrape
   and the second contract go away entirely?
2. Country filter at the provider or geography at S4? (see [Unverified](#unverified) — this is §2.1
   applied to a filter the current draft puts on the wrong side of the line.)
3. What replaces §9.4's roster delta-refresh, given there are no change timestamps to delta on?
   A per-company re-filter on a schedule is the obvious answer, and it is not free.
4. Does the Apollo re-derivation (§11) become a launch blocker rather than a cleanup, now that the
   Apollo slug is the pipeline's primary join key rather than a seed?
