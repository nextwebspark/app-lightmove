# Find executives — design and build plan

Status: **built** — P0 this design and its mockup (#1195), P1 the search engine (#1201), P2 the panel end to end
(V209, `features/sourcing`). Where the build differs from the plan below, CLAUDE.md's Find executives paragraph is
current: a search's sources are not run side by side but in turn (the live index tiered, then the profile index's
untitled people checked free against it), and the copy guard's vendor names are a follow-up. The screens are
`uncava-design/FindExecutives.dc.html` (`?screen=canvas` shows every board).


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

It opens from the In-universe toolbar on the ticked companies, five at most, counted once each whatever rows name them
— with none ticked, or more, the button says so on press. While a search runs or its results wait, the button opens
that search, and its caret's **Open the last search** opens it whatever is ticked. A company's empty slot (the
add-executive icon) asks **Find with Uncava** — the panel on that company alone — or **Add manually**. It also
opens from a company drawer's **Find executives**, with that company alone. Either way it is the same panel and the
same search.
The toolbar press and Find with Uncava search straight away every company the panel has no results for, so one
press shows people; a company searched before shows what it found, and Search again is the consultant's. The
drawer's Find executives and Open the last search only open the panel.

**The search bar** is drafted by the AI once per position. Every change is saved for the position, and **Reset**
drafts it again. It opens folded to one line that says what it looks for, with the count line, **Edit search** and
**Search** at its end; Edit search opens six fields in a grid, each a dropdown of the same shape and size:

- **Level** — C-level · SVP/EVP · VP·Head·Director · Manager, any of them.
  - These are the levels the role check can tell apart (`TitleLevel` holds VP, Head and Director as one).
  - A level turns into title words in code, so nobody types "Chief OR Head".
  - Each level shows a free count.
- **Titles** — the field reads "Finance, Financial +2"; its dropdown holds the words as removable pills, a box where
  Enter adds any word, **Clear all**, and the suggested words: the AI's alternates from the same draft call, the
  function's abbreviation (IT and Technology), and words seen in found titles. Each suggestion carries a free count of
  the people it reaches at these companies.
- **Leave out** and **Where** (the brief's country and the Gulf) — the same pills and box. **Companies** (each counted,
  no LinkedIn page noted) and **Per company** (3, 5 or 10 profiles bought; three by default).
- **The count line** — "≈ 30 match · 12 to buy · 4 already on file". It is free and refreshes 500 ms after an edit.
  **Search** is the only thing that spends.

**Results** arrive company by company. While a search runs, a progress line names each company as done, searching
or waiting, with **Stop**. Closing the panel does not stop the search. A company the running search has not finished
— or has finished, before its results are read back — shows pulsing rows, never its older results.

- **Level tabs** filter what was found, on the client and for free.
- **Each company** lists the people who fit, best first:
  - each is marked Strong, Good or Possible, with up to three reason chips;
  - the top three are pre-ticked;
  - **Title not on record** and **Doesn't fit the role** fold under the company and can still be ticked. A person
    who doesn't fit shows why: below the role's level, no finance word in the title, or a leave-out word. Only someone
    the live index found under its own words is listed as not fitting; of what the profile index alone returned — the
    function's whole staff at the company — a title below the role, or a free check that found no such title, leaves
    the person off the list, and *Title not on record* is only someone no check reached;
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


## Proof of concept on live data (2026-11-02)

The test was 629 executives that researchers filed by hand, on four live positions of one firm's workspace. All of
them have a LinkedIn profile and a company. The positions were a CTO, a Merchandising Director, a GM Real Estate and
an HR Manager, KSA; CMO was left out, since its nine are mostly CEOs and partners.

The question asked was what share of those people a strategy finds. Cost of the test: about 440 Business Search
searches (~$0.90), around 5,000 ContactOut *count* calls (free) and no ContactOut credits. The golden set stayed
outside the repository.

**What the words reach**, checked against the titles researchers recorded:

| Words | Reach |
|---|---|
| Today's prompt: four seniority words and four function words | 36% |
| The new prompt: the function family, a level range, standalone titles, suggestions | **77%**, 79% with its suggestions |
| The new prompt told the companies' countries (it adds "Planlama", "Direktörü") | Merchandising Director 77% → 81% |

**What the sources hold:**

- **In either source, at the company.** 90% of the people.
- **ContactOut.** It holds 77% at the company. Its current titles match the family words for 56%, against 24% for
  today's words.
- **Business Search.**
  - It holds 66% at the company.
  - Only 62 of those 424 still carry a current title: October's refresh dropped them, and each refresh drops more.
    A title search there reaches 2–6%, so it is not used.
  - The **role** group (the function words in title, past titles or About) reaches **31%**, for one search a company
    (~$0.002, median 23 people back).
- **A Gulf-only residence filter loses 24%**: Turkish retailers' teams, people in London.

**Mixing them** (the share found, out of 629):

| Strategy | Found | ContactOut credits a company |
|---|---|---|
| ContactOut, five profiles bought at random | 35% | 2.6 |
| ContactOut, five bought tiered (the role's level and core words first) | 39% | 2.7 |
| Business Search role group + ContactOut tiered, three bought | 51% | 1.9 |
| …plus the company's people in Business Search, each checked against the words by a **free name-narrowed ContactOut count** | **62%** | 1.9 |
| …the same with ten bought | 65% | 4.1 |
| Ceiling: every person whose current title fits the words, in either source | 66% | — |

The last third are mainly people a title search cannot find. Researchers also file CEOs, advisors and consultants
around a role, and some people have moved and are not yet in either source.

**The strategy that follows:**

1. **The AI drafts the whole function family.** That means up to 12 terms, phrases allowed: the core word in every
   spelling, the sub-functions that report into the role, and the words of the target companies' languages. It also
   gives:
   - a level range: the role's level and down — C-suite to Director; N-1 adds Manager; N-2 runs Director to Specialist;
   - the standalone titles (CIO, CTO);
   - up to 4 support words to leave out;
   - 8 suggestions for the search bar.

   It is told the countries of the companies being searched.
2. **Business Search finds who is there, and ContactOut confirms the title for free.** The role-group search, plus the
   company's people in the region where that list is small, gives the candidates. A free ContactOut count narrowed
   to each candidate's name says whether their current title holds the words, so they can show as *title confirmed*
   with nothing bought.
3. **ContactOut is bought sparingly.** Three profiles a company by default (3, 5 or 10), tiered: the role's own level
   and core words first, then the rest of the family. Each tier is counted free before any profile is bought.
4. **Location.** The brief's country, the Gulf and each searched company's own country. Elsewhere ranks lower; it
   is never dropped.


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

- **Business Search makes one role-group search a company, about $0.002.** It asks for people whose
  `current_company_id` is the slug and whose `role` (title, past titles, About) holds a function term.
  - Its candidates are checked by a free ContactOut count narrowed to the person's name. Where the company is small,
    everyone the index holds there in the region is checked the same way.
  - A match shows as **title confirmed**; the rest are left off the list. Every untitled person on the page is
    checked (`title-checks-per-company`, 100); one past it shows as **Title not on record**.
  - Someone the index holds under a title below the role is left off too: that search finds the function, not the
    seat, and returned HVAC engineers for a CTO.
  - A title search in the index is not used: it has dropped most current titles.
- **ContactOut is bought in tiers.** The role's own level and core words come first, then the rest of the family,
  three profiles a company by default. Each tier is counted free first.
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
| Search | ContactOut: a credit per profile, three a company by default (1.9 on average in the test); Business Search: about $0.002 a company; the title checks: free counts |
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
| P0 | this design and `uncava-design/FindExecutives.dc.html` | nothing |
| P1 | the search engine: the new word prompt; Business Search's role-group search with free name-narrowed title checks; ContactOut bought in tiers (the dataset leaves Find executives); the location rule; the fixes above; `SourcingEval` replaying the proof of concept | better people under today's flow |
| P2 | the panel end to end: the migration, the criteria, counts, suggestions and add, scoring, fair use, audit and stream; `features/sourcing` (button, panel, drawer button); the strip, the old flow and their dead code removed; help article, glossary, CLAUDE.md | the new panel |

**`SourcingEval`** (`@Tag("eval")`, as `NationalityEval`) replays executives researchers filed by hand:

- The golden set is exported into the gitignored `eval-data/`, never the repository.
- It reports recall at 5 and at 10, precision at 5, credits per company, companies with nobody found, and the share
  of people without a title.
- Its first run spends once (at most 200 ContactOut credits), only with an explicit yes, and saves the answers, so
  every later run re-scores them for free.
- It tunes the scoring, the per-company default, and whether the *Title not on record* search earns its place.
