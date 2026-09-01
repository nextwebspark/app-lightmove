# Coresignal Employee API — investigation notes

Everything here was **run against the live API on 2026-08-31**, not read in documentation. Where the
documentation disagrees with what the API did, the API wins and the disagreement is recorded, because
Coresignal's docs contradict themselves on several of the fields this integration depends on.

Tier investigated: **Clean Employee API** (10 credits/record against multi-source's 20).
Test set: six Gulf food companies drawn from `app_lm_apollo_companies`, joined by LinkedIn URL.
Spend: 660 credits.

**Verdict: Coresignal is a usable source for executive mapping.** Coverage is real, the seniority
ladder has the right rungs, and search is free. Two things must be built around: the provider's
seniority label cannot be trusted alone, and a search result describes a person's *primary* job rather
than their job at the company you asked about.

---

## 1. Connection facts

| | |
|---|---|
| Base URL | `https://api.coresignal.com/cdapi/v2` — **the `/cdapi` segment is required**; `/v2/...` returns 404 |
| Auth | header `apikey: <key>`, no bearer prefix |
| Key location (this repo) | `lightmove.vendor.coresignal.api-key` in the gitignored `application-local.yml` |

Response headers on every call:

```
x-credits-remaining     spend is observable per call
x-total-results         the true count — the body is capped, this is not
x-total-pages
x-items-per-page        1000 on search
x-next-page-after       a search_after cursor
x-ratelimit-limit-second
x-ratelimit-remaining-second
x-correlation-id
```

**Rate limit is per key and per plan**: free/mini/starter 5 req/s, Pro 10, Growth 20, Premium 50,
Scale 100. The key tested reported `x-ratelimit-limit-second: 18`, which happens to match the
`requests-per-second: 18` already configured in `application.yml`. Read it from the header rather than
assuming it.

## 2. Endpoints and what they really cost

| Endpoint | Method | Cost | Returns |
|---|---|---|---|
| `/employee_clean/search/es_dsl` | POST | **free** | array of employee ids, capped at 1000; true count in `x-total-results` |
| `/employee_clean/semantic_search/es_dsl` | POST | **free** | same |
| `/employee_clean/search/es_dsl/preview?page=N` | POST | **10 per page of 20** | 18 shallow fields per person |
| `/employee_clean/collect/{id}` | GET | **10 per person** | full profile |
| `/company_multi_source/search/es_dsl` | POST | **free** | company ids |
| `/company_multi_source/collect/{id}` | GET | **20 per company** | 175 fields incl. `key_executives`, arrivals/departures, seniority census |

**Search is free.** That single fact should shape the whole design: filter exhaustively, read sparingly.

**Preview page size is fixed at 20.** `items_per_page`, `limit`, `size` and `per_page` were each
tested at 100 and 1000 — every one returned 20 rows and charged 10 credits. **0.5 credits per person
is the floor and cannot be lowered.** The documentation's "10 credits per record" is wrong; it is 10
per page.

**Preview accepts an explicit id list** — `{"query":{"terms":{"id":[…20 ids…]}}}` returns exactly those
20. This is what makes a cross-mandate id cache possible: free search for the id set, subtract ids
already bought, preview the remainder in exact batches of twenty.

## 3. Schema — verified, and where the docs are wrong

| Question | Docs said | **Reality** |
|---|---|---|
| Nested experience array | `member_experience` | **`experience`** (`member_experience` → HTTP 400) |
| Company link inside it | `company_professional_network_url` *or* `company_url` | **`company_professional_network_url`** works; `company_url` is mapped but populated on zero documents |
| Current-role marker | `active_experience` / `is_current` | **Neither exists.** An open role is one with **no `experience.date_to`** |
| Preview cost | 10 per record | 10 per page of 20 |

**Top level** (all searchable): `full_name`, `job_title`, `headline`, `management_level`,
`department`, `is_working`, `is_deleted`, `is_hidden`, `is_decision_maker`, `location_country`,
`location_regions`, `skills`, **`company_id`**.

`is_parent` exists in the mapping but is populated on **zero** documents — the clean tier has no
duplicate-fork flag, and duplicates are real (see §7).

**Inside `experience`** (nested mapping — a `nested` query is mandatory): `company_id`,
`company_name`, `company_professional_network_url`, `company_shorthand_name`, `company_website`,
`title`, `department`, `management_level`, `date_from`, `date_to`, `duration`, `location`,
`order_in_profile`.

**Only these top-level company fields are indexed: `company_id`.** `company_name`,
`company_website`, `company_linkedin_url` and `company_industry` appear in *preview output* but
`exists` returns 0 on all of them — they cannot be queried.

### Value formats — normalisation is mandatory

The index stores these **without scheme and without `www`**:

```
experience.company_professional_network_url   linkedin.com/company/<slug>
experience.company_website                    <bare domain>          e.g. almarai.com
```

Apollo stores `http://www.linkedin.com/company/<slug>`. **Joining without normalising returns zero,
silently.** Strip scheme, strip `www.`, strip the trailing slash, lowercase.

### Three traps that cost real time

1. **`match` on a slug is catastrophic.** `match` analyses `al-kabeer-group-of-companies` into tokens
   and matched **35 million** profiles containing "group" or "companies". Use `term`.
2. **`experience.management_level` is analysed text; the top-level `management_level` is a keyword.**
   A `terms` query on the nested one returns 0 for every company — which looks exactly like "this
   company has no executives" and is the easiest way to ship something that silently finds nothing.
3. **Apollo's stored slug goes stale.** `company_shorthand_name` returned 0 for three of seven
   companies whose full normalised URL resolved fine. **Join on the URL, not the slug.**

## 4. The two populations, and how to tell "do they actually work there"

There are two different questions, and both are worth asking.

**Primary current employer** — `{"term":{"company_id": <id>}}` plus `{"term":{"is_working":1}}`.
This is *their job*. When it holds, the top-level `job_title`, `management_level` and `department`
describe the role **at that company**.

Resolve the id free:

```json
POST /cdapi/v2/company_multi_source/search/es_dsl
{"query":{"term":{"professional_network_url":"linkedin.com/company/iffco"}}}   ->  [3616746]
```

**Any open role there** — the nested join below. 5–10% wider than the primary set, and that
difference is **board members, non-executive directors and advisors**: high-value people for a search
mandate, but not the company's operating executives.

| Company | company_id | open role there | primary employer | kept |
|---|---|---|---|---|
| Almarai | 8946549 | 24,312 | 22,973 | 94% |
| Americana Restaurants | 6518206 | 7,655 | 6,917 | 90% |
| Agthia | 33289867 | 1,682 | 1,590 | 95% |
| IFFCO | 3616746 | 6,253 | 5,722 | 92% |
| Tanmiah | 30761765 | 703 | 665 | 95% |
| Sunbulah | 4457718 | 649 | 602 | 93% |
| Al Kabeer | 8851998 | 586 | 542 | 92% |

**Without the primary filter the shallow row shows the wrong title.** Verified by enriching one
profile: a person returned under Al Kabeer showed "Group Chief Financial Officer / Savola Group". Both
are true — Savola is his job, and he holds an open Al Kabeer audit-committee seat since 2021. Reported
naively, he reads as Al Kabeer's CFO.

Absolute certainty on a shortlist still needs one `collect` (10 credits), which returns every
experience entry with `date_from` / `date_to`.

## 5. Seniority

Live enum, counted across the whole index:

| value | profiles | | value | profiles |
|---|---|---|---|---|
| Specialist | 292.6M | | Head | 7.4M |
| Manager | 75.0M | | C-Level | 6.9M |
| Senior | 20.7M | | President/Vice President | 5.9M |
| Director | 18.7M | | Founder | 4.6M |
| Owner | 16.8M | | Partner | 2.7M |
| Intern | 7.7M | | | |

### Precision, measured on 433 real rows

| level | rows | junk | junk rate |
|---|---|---|---|
| **Owner** | 21 | 18 | **86%** — "Self Employed", "Dairy Farm Owner" |
| Partner | 11 | 3 | 27% — catches "HR Business Partner" |
| C-Level | 67 | 10 | 15% — "Executive", CEO of a personal side venture |
| Head | 101 | 4 | 4% |
| Director | 104 | 2 | 2% |
| President/Vice President | 33 | 0 | **0%** |

### The misses matter more than the noise

| Actual title | Level Coresignal assigned |
|---|---|
| Managing Director (×7 at one company) | `Director` — an n-level role filed two rungs down |
| Regional CFO — Africa | `Manager` |
| Group Director and Group Chief Financial Officer | **none** |
| Board Member & Chairman of the Executive Committee | **none** |

**Filtering on the provider's level alone loses group CFOs and board chairs.** Union it with a
title-pattern clause. Measured contribution of the title clause: one company went from 287 by level to
553 with it; another from 284 to 309. Worth between 10% and 90%.

`is_decision_maker` is an independent boolean and roughly halves any set — a precision filter, not a
recall one.

## 6. Our layer taxonomy

Coresignal's `management_level` cannot express these: it has no board tier, files Managing Directors
as `Director`, and leaves group CFOs unlabelled. So layers are assigned **from the title, after
retrieval**, with the provider level as a tie-breaker only.

| Layer | Title patterns |
|---|---|
| **Board** | Chairman, Vice Chairman, Board Member/Director, Non-Executive Director, Executive Board Member, Audit/Risk/Nomination/Executive Committee |
| **C-Suite** | CEO CFO COO CHRO CTO CIO CCO CDO CMO, any "Chief … Officer", Managing Director, Managing Partner, Group President, Deputy CEO |
| **n-1** | EVP, SVP, VP, Group Head, Group Director, Country Head, Country Manager, General Manager*, Division Head, Regional Director, Group General Counsel, Chief of Staff, BU Head |
| **n-2** | Director, Senior/Associate Director, Head of …, Department Head, Deputy/Assistant General Manager, HOD |
| **n-3** | Senior Manager, Group Manager, Section Head, Area/Regional/National Manager, Team Lead |

**Order matters. Test Board before C-Suite**, or "Non-Executive Director" is read as an executive on
the words "Executive Director". Exclude "Assistant to", "Executive Assistant", "Secretary to"
outright. Drop `Owner` and `Partner` as levels and recover the real ones through titles.

### *The General Manager rule

`General Manager` means the head of an operating entity in one company and a branch manager in
another. Do not include or exclude it globally — **measure its share of indexed staff first, free:**

| Company | staff | GMs | share | reading |
|---|---|---|---|---|
| Americana Restaurants | 6,917 | 247 | **3.57%** | branch managers |
| Tanmiah | 665 | 10 | 1.50% | branch managers |
| IFFCO | 5,722 | 47 | 0.82% | real n-1 |
| Sunbulah | 602 | 4 | 0.66% | real n-1 |
| Almarai | 22,973 | 22 | 0.10% | real n-1 |

**Above roughly 1%, GM is a branch title** — bucket it as "needs review" rather than paying to preview
it. Excluding it globally would have dropped one company 257 → 6 but also cut another's real entity
GMs 61 → 15.

## 7. Data quality

- **Duplicates are real: ~6%** (25 of 433 rows). Same person, same title, different ids. `is_parent`
  is unpopulated, so deduplicate on id and probably on name+company too.
- **Freshness looks good.** The profile enriched carried `last_updated` four weeks old.
- **A stable person key exists**: `shorthand_names` (an array, so renames are tracked) plus
  `public_profile_id`. Never key on the profile URL.
- **A wrong seed fails quietly.** A company whose Apollo slug was wrong returned 13 people and one
  "executive" — no error, just a plausible small answer. The correct slug returned 254 and 15. The
  company API is the loud check: a wrong slug resolves to **no company id at all**.
- **Coverage gaps are real and worth surfacing.** One 1,500-person group has 542 staff indexed and
  **zero** C-level whose primary employer is that company; its entire C-suite result was people whose
  main job is elsewhere.

## 8. The working query

```json
{"query":{"bool":{
  "must":[
    {"term":{"company_id": 3616746}},
    {"term":{"is_working":1}},
    {"bool":{"minimum_should_match":1,"should":[
      {"terms":{"management_level":["C-Level","Founder","President/Vice President","Head","Director"]}},
      {"query_string":{"default_field":"job_title","query":
        "CEO OR \"Chief Executive\" OR CFO OR \"Chief Financial\" OR COO OR CHRO OR CTO OR \"Managing Director\" OR \"General Manager\" OR \"Country Head\" OR \"Group Head\" OR \"Vice President\" OR \"Executive Director\" OR Chairman"}}]}}],
  "must_not":[
    {"query_string":{"default_field":"job_title","query":
      "\"Assistant To\" OR \"Executive Assistant\" OR \"Self Employed\" OR \"Business Partner\" OR Intern OR Trainee OR Freelance"}},
    {"terms":{"management_level":["Intern","Specialist","Senior","Owner"]}}]}},
 "sort":["_score"]}
```

For the board and dual-role population, swap the `company_id` term for the nested join and exclude it:

```json
"must":[{"nested":{"path":"experience","query":{"bool":{
          "must":[{"term":{"experience.company_professional_network_url":"linkedin.com/company/iffco"}}],
          "must_not":[{"exists":{"field":"experience.date_to"}}]}}}},
        {"term":{"is_working":1}}],
"must_not":[{"term":{"company_id":3616746}}]
```

## 9. Cost model

Measured, for Board + C-Suite + n-1 + n-2 across seven companies:

| Bands bought | Credits |
|---|---|
| Board + C-Suite + n-1 + n-2 | 490 |
| Board + C-Suite + n-1 | 290 |
| …with the GM rule applied | **120** |
| …further narrowed by `is_decision_maker` | 100 |
| Board + C-Suite only | 90 |

n-2 is more than half the bill — buy it per company, only where the count is small.

**Recommended sequence per company**

| Step | Cost |
|---|---|
| 1. Resolve `company_id` from the LinkedIn URL | free — also catches a bad seed |
| 2. Count staff, each layer, and the GM share | free |
| 3. Preview Board + C-Suite | 10–30 |
| 4. Preview n-1 with the GM rule applied | 10–30 |
| 5. Preview n-2 only where the count is small | 0–50 |
| 6. `company_multi_source/collect` for the census and change signals | 20, optional |
| 7. `employee_clean/collect` on the shortlist only | 10 each |

Fifteen-company mandate, Board + C-Suite + n-1, with a cross-mandate id cache: roughly **250 credits
first run**, far less afterwards.

## 10. `company_multi_source/collect` — worth knowing about

20 credits, one call, 175 fields. The useful ones:

- **`key_executives`** — 38 for a 750-person company, name + title, including the Chairman, board
  members and CEO. **No employee id**, so these people cannot be enriched, deduplicated or
  currency-checked. Use it as a cross-check, not as the source.
- **`key_executive_arrivals` / `key_executive_departures`** — name, title and month. Job-change
  signals, which removes the reason to buy a second data contract for them.
- **`employees_count_breakdown_by_seniority`** — an exact census. For one company: C-level 1, VP 2,
  Head 15, Director 18, Manager 141, Senior 22, Specialist 175, Intern 4. **This tells you how many
  executives exist before you spend anything on people**, which is the cheapest possible way to size a
  company.
- 36 months of headcount history, attrition rate, employee review scores.

## 11. Open questions

1. **Freshness at scale.** One profile four weeks old is encouraging, not evidence. Sample twenty
   known-current executives before putting any of this in front of a client.
2. **Which plan are we on?** The rate limit and the credit balance both depend on it, and
   `requests-per-second: 18` in `application.yml` is currently a guess that happened to be right.
3. **Where does company size tier come from?** Peer comparability needs it, and Coresignal's employee
   count under-reports in low-adoption markets (one 750-person company indexed 602; a 47,000-person
   one indexed 22,973).
4. **Company resolution needs a human step.** Three of seven Apollo seeds had a stale or wrong slug,
   and one company (BRF) is absent from the Apollo universe entirely.

---

## Notes for whoever implements this

- **Nothing in this repo currently speaks to Coresignal correctly.** `CoresignalEmployeeClient` posts
  a flat map to `/v2/employee_multi_source/search/filter` — an endpoint that does not exist, on a base
  path that 404s. The shared vendor layer under `core/vendor/` (timeouts, key header, retry policy,
  failure classification, rate pacing, cascade semantics) is sound and should be kept; the Coresignal
  adapter on top of it should be rewritten against §8.
- The probe scripts that produced all of this are session scratch, not in the repo. Every finding here
  is reproducible from §1–§3 with an API key.
- Raw payloads name real individuals. They stayed out of the repo deliberately, and anything derived
  from them is professional contact data subject to the retention and erasure rules in the sourcing
  design.
