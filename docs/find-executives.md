# Find executives — design and build plan

Status: **designed** (approved 2026-11-02). Three PRs: P0 is this design and its mockup, P1 the search engine, P2
the panel end to end. Until a phase ships, the tables, routes and entities it names do not exist. The screens are
`claude-design/FindExecutives.dc.html` (`?screen=canvas` shows every board).


## Context

Find executives today is one press that runs out of sight:

- The AI turns the brief into title words that nobody sees.
- Each company is searched once (ContactOut, with Bright Data's frozen dataset behind it).
- The top three are **filed on their own**.

The consultant cannot see the words, the people turned away or why anyone was chosen, and cannot steer. It also
spends blind: every ContactOut profile is a credit from one monthly pool that Strategy → People shares. Matching
inside longer words once filed pharmacists into a Head of IT search; #1181 and #1183 closed that hole.

The redesign is **one panel the consultant can see and steer**:

- **The search.** It is drafted from the brief, shown in full and changed in place, with help on levels and titles,
  and free counts before anything is spent.
- **Searching.** Two sources are searched side by side, merged and de-duplicated, then ranked against the role.
  Every person carries the reasons for their place.
- **Adding.** Nothing is filed until the consultant ticks people and presses **Add**.


## The panel

It opens from the In-universe toolbar, with the ticked companies or else the first ten with nobody mapped. It also
opens from a company drawer's **Find executives**, with that company alone. Either way it is the same panel and the
same search.

**The search bar** is drafted by the AI once per position. Every change is saved for the position, and **Reset**
drafts it again.

- **Level** — C-level · SVP/EVP · VP·Head·Director · Manager, any of them.
  - These are the levels the role check can tell apart (`TitleLevel` holds VP, Head and Director as one).
  - A level turns into title words in code, so nobody types "Chief OR Head".
  - Each level shows a free count.
- **Titles** — chips. Focusing the box suggests words: the AI's alternates from the same draft call, the function's
  abbreviation (IT and Technology), and words seen in found titles. Each suggestion carries a free count of the
  people it reaches at these companies. Enter adds any word.
- **Leave out**, **Where** (the brief's country and the Gulf), **Companies** (each counted, no LinkedIn page noted),
  and **Per company** (5, 10 or 25).
- **The count line** — "≈ 30 match · 26 new · 4 already on file". It is free and refreshes 500 ms after an edit.
  **Search** is the only thing that spends.

**Results** arrive company by company. While a search runs, a progress line names each company as done, searching
or waiting, with **Stop**. Closing the panel does not stop the search.

- **Level tabs** filter what was found, on the client and for free.
- **Each company** lists the people who fit, best first:
  - each is marked Strong, Good or Possible, with up to three reason chips;
  - the top three are pre-ticked;
  - **Title not on record** and **Doesn't fit the role** fold under the company and can still be ticked. A person
    who doesn't fit shows why: below the role's level, no finance word in the title, or a leave-out word;
  - people already on the position read **On this position**.
- **How we searched** gives a company's words, levels and each source's counts.
- **A row opens the profile** in a pane inside the panel.
- **Nobody fits** offers suggested words and levels with their counts, and **Search this company again**. Above the
  list, **Retry N with these words** covers every company that found nobody. There is no automatic AI retry.
- **Add** files the ticked people at In universe from the people already read, with no second vendor call.

**The toolbar button carries the run**, and the strip under the toolbar is gone. With nothing filed on its own, a
run has two things to say — it is busy, then it found people — and the button and one toast say both:

| State | Button | Pressing it |
|---|---|---|
| Idle | **Find executives** | opens the panel on the saved search |
| Searching | **Finding… 3/6**, still a button | opens the panel on the live progress, with Stop |
| Results waiting | **Find executives** with a count | opens the results; the count clears |
| Stopped or failed | **Find executives** with a warning dot | finished companies keep their results |

One toast on finish ("7 executives found at 6 companies · Review"), one on a stop or failure.


## Searching one company (P1)

```
saved search ─► ContactOut People Search ─┐  side by side; each through the 30-day people cache
             ─► Business Search ──────────┘  (Business Search paced 1 call / 1.5 s)
                     ▼
   merge by LinkedIn profile: ContactOut's record wins (current title, career);
   Business Search adds tenure, years and past titles; a record at another employer is dropped
                     ▼
   people already on the position marked
                     ▼
   the CURRENT title judged (SeatFit): fits / doesn't fit (+ reason) / title not on record
                     ▼
   scored 0–100 → Strong / Good / Possible, with reason codes → ranked
                     ▼
   stored as suggestions: profile slug, verdict, score, reasons — never a name or a title
```

- **Business Search makes two searches a company, about $0.004.**
  - The *titled* one: `current_company_id` is the slug, `country_code` is in the region, the title has a level word
    and a title word, and has no leave-out word.
  - The *role group* one: `role` has a title word, kept only where `current_title` is null. It is the only way a
    person whose title the index no longer carries can surface, as **Title not on record**.
- **Scoring is fixed, in code; no model judges the people.** Every point is a reason chip:

  | Factor | Points |
  |---|---|
  | Title word in the current role | brief's word or its abbreviation 40 · a searched-only word 30 |
  | Level against the role's | same 30 · one apart 18 · two apart 6 · below the lowest chosen: doesn't fit |
  | Lives in | the role's country 10 · the Gulf 6 · unknown 3 |
  | Current role read from | a dated role 8 · the index alone 3 · a headline 2 |
  | Found by both sources | 5 |
  | Time in role | 2 years or more 3 · under 1 year reads "New in role" |

  Strong is 75 or more, Good 55–74, Possible under 55. The eval below tunes all of it.


## What costs what

| Action | Cost |
|---|---|
| Draft or Reset | one Gemini call |
| Every count (levels, titles, companies, on file) | free: ContactOut's count, the cache |
| Search | ContactOut: a credit per profile, at most "per company"; Business Search: about $0.004 a company |
| Filter, open a profile, Add | free |

- **Fair use counts profiles read**, which is the real spend, not people filed. It is checked at Search against the
  counts and recorded when the run ends.
- **The run calls no model** once the search is drafted, so no rewording rounds are spent.


## Data and routes (P2)

**Tables** (one migration, numbered above main's head at merge):

- `app_lm_executive_sourcing_criteria` — one per position: `criteria` jsonb, `source` (DEFAULT, AI or EDITED),
  `brief_fingerprint`, who and when, version.
- `app_lm_executive_sourcing_suggestion`:
  - Its columns are workspace, project, run, company, `linkedin_slug`, verdict (FITS, NO_TITLE or NOT_FIT), score,
    rank, `reasons text[]` (codes and our own words only), `found_by` and created.
  - It is unique on run, company and slug.
  - "Added" is read from the position's mapped profiles, so there is no status column.
  - Rows go after the people cache's 30 days.
- `app_lm_executive_sourcing_run` — gains a `criteria` snapshot and a `suggested` count. Old rows still read.

**Routes**, all `WORK_EXECUTE`, under `/projects/{id}/executive-sourcing`:

| Route | Does |
|---|---|
| `GET /criteria`, `PUT /criteria` | the saved search |
| `POST /criteria/suggest` | the AI draft (LLM budget) |
| `POST /counts` | free counts per company, level and suggested word |
| `POST /` | a search over these companies (202) |
| `GET /latest`, `GET /{runId}` | the run |
| `POST /{runId}/stop` | stop the run |
| `GET /suggestions?companyIds=` | each company's latest results, joined with the cache, reusing Strategy People's `PersonResultDto` |
| `POST /suggestions/add` | at most 50; idempotent; reports added, already on the position, expired and refused |


## Fixes folded into P1

- **The people cache** keeps one row per profile, the last write winning. A fresh ContactOut record must never be
  replaced by a thinner one, and a null `source_record` must never wipe a stored one.
- **Pacing:**
  - The vendor limiter learns a period (one call every 1.5 s).
  - A background call gets its own permit wait: today four companies at once trip ContactOut's two-second wait and
    fall back without a word.
- **ContactOut:**
  - keep its own record on this path;
  - cache and count the profiles it bills outside the region;
  - ask the wider title tier until min(5, per company) are found.
- **The activity feed** never received the counts it phrases (`ProjectActivityService.DETAIL_KEYS`).
- **The copy guard** gains ContactOut, Bright Data and Apollo, and the five screens that show "ContactOut" change.


## Copy

- No vendor names on screen. In *How we searched* the two sources read **Live profiles** (ContactOut) and
  **Profile index** (Business Search).
- Never "seat", "triage" or "enrichment". The screen says **fits the role**, **doesn't fit the role** and **title
  not on record**.


## Build

| PR | Content | Users see |
|---|---|---|
| P0 | this design and `claude-design/FindExecutives.dc.html` | nothing |
| P1 | the search engine: Business Search, the two sources merged (the dataset leaves Find executives), the fixes above, `SourcingEval` | better people under today's flow |
| P2 | the panel end to end: the migration, the criteria, counts, suggestions and add, scoring, fair use, audit and stream; `features/sourcing` (button, panel, drawer button); the strip, the old flow and their dead code removed; help article, glossary, CLAUDE.md | the new panel |

**`SourcingEval`** (`@Tag("eval")`, as `NationalityEval`) replays executives researchers filed by hand:

- The golden set is exported into the gitignored `eval-data/`, never the repository.
- It reports recall at 5 and at 10, precision at 5, credits per company, companies with nobody found, and the share
  of people without a title.
- Its first run spends once (at most 200 ContactOut credits), only with an explicit yes, and saves the answers, so
  every later run re-scores them for free.
- It tunes the scoring, the per-company default, and whether the *Title not on record* search earns its place.
