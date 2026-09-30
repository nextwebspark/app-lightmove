# Candidate CRM — one person per workspace, mapped to many mandates

The phased plan for the people half becoming a workspace-level CRM, in the shape of `workspace-modes.md`.
Decided and approved 2026-09-30; phases carry a `> **Built**` callout as they land.

## Where this stands — read this first

| Phase | State |
|---|---|
| 0 — Mockups | **Done**, merged (#600): `claude-design/Candidates.dc.html`, `Position.dc.html`, `Settings.dc.html` |
| 1 — Person/mapping split, auto-map, activity log (V91) | **Built**, merged (#602) |
| 2 — Stored profile slug (V92) + golden reads | **Built** in #604. Merges once #602 is deployed |
| 3 — Notes, timeline reads, `CANDIDATE_POOL_MANAGE` | **Next** |
| 4 — Screens, owner/tags/do-not-contact, merge, possible-duplicate | To do |
| Final — Cleanup migration | Last, tracked in #606. Drops V91's frozen copies and the mapping's `note` once 3–4 are deployed |

**Starting a new session on this plan:**
1. Confirm #602 is **deployed** and #604 (Phase 2) is merged (`git log origin/main --oneline | grep -i slug`).
   If Phase 2 is still open, finish it first: CI, review threads, and the rehearsal below.
2. **Rehearse V91 and V92 on a copy of the shared dev database** before either reaches anyone else
   (`dev:cloud` against a copy; needs gcloud). Both have only run on seeded data
   (`CandidatePersonBackfillMigrationTest`, `PersonProfileSlugMigrationTest`). Paste the counts into the PRs:
   V91's people founded, rows folded, contacts and photos copied and activity lines, and V92's `NOTICE`
   of people left without a slug because an older person holds their profile, with the `lc_ctype` it
   reports. Spot-check the slugs most likely to differ from Java:
   `SELECT linkedin_url, profile_slug FROM app_lm_person WHERE linkedin_url ~ '%' OR linkedin_url ~ '[^\x01-\x7f]'`.
3. **Deploy #602 at a quiet hour, or drain the previous revision first.** Between V91 running and the new
   revision taking traffic, the old revision still writes edits, contacts, lookups and enrichment to the old
   columns and ledger, and none of that reaches the person. V92 only adds, so it can ride any deploy
   after #602.
4. Load `java-spring-development` and `db-ops` for any migration; add `lightmove-domain` for Phase 3's
   action and `react` for any SPA work. Read the Phase 0 mockups before any screen.
5. Migration numbers below are the next free ones at the time of writing (V93 notes, V94 action, V95
   owner/tags; the final cleanup takes whatever is free when it comes). Check `apps/api/src/main/resources/db/migration/` for the next free number before writing
   one.

**Names as built.** This plan was written before Phase 1. Where it says one name, the code has another,
and the **code's name stands** for every later phase:

| The plan says | Built as |
|---|---|
| `app_lm_candidate`, entity `Candidate` (the person) | `app_lm_person`, `candidate/model/Person` |
| `MandateCandidate`, `MandateCandidateRepository` (the mapping) | `Candidate`, `CandidateRepository`, unchanged names on `app_lm_project_candidate` |
| `CandidateRepository` (person finders) | `PersonRepository`; every finder takes `workspaceId` |
| `CandidatePoolService.find`, `CandidateMatch` | `PersonMatcher.find` (package-private) called from `CandidateService.file`; no soft match yet, so no `CandidateMatch` |
| `app_lm_candidate_activity`, `CandidateActivityRecorder`, `CandidateActivityKind` | `app_lm_person_activity`, `PersonActivityRecorder` (`Propagation.MANDATORY`), `PersonActivityKind` |
| contacts/photo "re-pointed" | copied into `app_lm_person_contact` / `app_lm_person_photo`; the old tables are left for the final cleanup migration to drop |
| `CandidateResponse.candidateId` | `CandidateResponse.personId` |
| `app_lm_candidate_note`, `CandidateNoteService` (Phase 3) | use `app_lm_person_note`, `PersonNote`, `PersonNoteService` |
| `app_lm_candidate_tag` (Phase 4) | use `app_lm_person_tag` for the owned list; the catalog stays `app_lm_workspace_candidate_tag` |

The user-facing word stays **Candidates** and the pool's routes stay `/candidates…`; the ids those routes
take are **person** ids. A mandate's routes keep taking the mapping id, as they do today.

## Context

Before Phase 1, a candidate was `app_lm_project_candidate`: **one row per executive per mandate**. The mandate is the
mapping (`Candidate.java:45-47` says so; the table has **no `workspace_id`**, tenancy rides `project_id`).
The same person researched for two searches is two unrelated rows with two notes, two contact ledgers, two
compensation readings and two histories. Nothing in the workspace knows they are one person, and nothing
records a person's history: every audit event targets `("project", id)`, so there is no per-person trail.

The ask: make the people half a **workspace-level CRM**.
- Every executive a researcher adds — drawer, plugin, spreadsheet, Find executives — lands in the
  workspace's candidate pool automatically.
- If the person is already in the pool, the add only **maps** them to the new mandate.
- Notes and contacts are shared across mandates.
- **Every action is recorded with who did it and when**, readable per candidate and across the workspace.

### What standard CRM/ATS products do (research)

Surveyed Invenias, Clockwork, Bullhorn, Lever, Greenhouse, Ezekia, Loxo, Vincere, Recruit CRM, Manatal, and
the HubSpot/Salesforce timeline conventions. The pattern is uniform:

| | Shared record | Per-search link | Container |
|---|---|---|---|
| Invenias | Person | Candidate in Assignment (progress status) | Assignment |
| Clockwork | Person Panel | Candidacy (rank, status, client-visible) | Project |
| Bullhorn | Candidate | JobSubmission → Placement | JobOrder |
| Lever | Contact | Opportunity | Posting |
| Greenhouse | Candidate | Application | Job |
| Ezekia | Person | Assignment candidate | Assignment / List |

1. **Person vs link.** Identity, contacts, career, CV, tags, consent and general notes sit on the person.
   Status, rank, date added, mandate-specific notes and client visibility sit on the link. **Status is never
   on the person.**
2. **Duplicates.** Hard match on normalised LinkedIn URL, email, phone; soft "possible duplicate" on
   name + company. Auto-merge is off by default and never retroactive. Manual merge re-points children to a
   survivor, is its own permission, and is usually irreversible.
3. **Notes.** On the person, with the assignment as optional *context* (Ezekia, Bullhorn "About"). Typed
   (general, call, meeting, email, feedback). Client feedback = a note of type feedback scoped to the search.
4. **Timeline.** One reverse-chronological feed per record mixing user-logged entries and system events,
   each with **actor + time**, filterable by type and actor; one entry shows on every record it is linked to
   (Invenias Journal, HubSpot associations). Invenias also keeps a *Global Journal* of everyone's actions.
5. **Statuses** map to fixed categories (Clockwork: In / Out / Research / Other). **Off-limits** is a scoped
   record with who set it and from/to, not a boolean. One **owner** per person.
6. **GDPR**: lawful basis, consent with expiry, do-not-contact, erasure behind its own permission.
7. **Client portal**: one assignment, per-candidate visible toggle, whitelisted projection, never internal
   notes or other mandates.

### Decisions confirmed with you

- **D1 Note visibility.** Every note on a person is readable by all staff who can open the pool, whatever
  mandate it was written in; a chip names the position it was written for. Client seats never see notes.
- **D2 Who opens the pool.** A new workspace action `CANDIDATE_POOL_MANAGE`, seeded to ADMIN and MEMBER,
  never CLIENT. A researcher seated on no mandate still sees the firm's people.
- **D3 Compensation** moves to the shared person: recorded once, seen from every mandate, edits on the
  timeline; the Remuneration chapter reads the same figures per mapped person.
- **Who did what and when is first-class**: every row of the activity table and every note carries an
  actor and a timestamp; the person timeline, the mandate timeline and the workspace Activity tab all read
  the same rows.

### Guiding rule

**The person is the firm's; the mapping is the mandate's.** Anything true of the human — identity,
career, contacts, background, package, AI readings of the profile — lives once per workspace. Anything
that is a mandate's *decision or reading* — status, rank, the mandate note, the AI assessment against
*this* brief, the mandate's custom-column values, which triaged company the person hangs off — lives on
the mapping. Nothing a client seat reads ever carries another mandate, a note or the timeline.

---

## Codebase constraints that shape the design

(paths: `api/` = `apps/api/src/main/java/app/lightmove/api/`, `web/` = `apps/web/src/`)

- **Single reader seam.** talentmap, dataexport, report, dataimport, sourcing, enrichment, contact lookup,
  and two inverted adapters all go through `CandidateService` and consume `CandidateResponse`
  (`api/candidate/dto/CandidateResponse.java:16-49`). Keep that DTO's shape and every reader stays
  untouched. `listAllOfProject`, `addedByOf`, `findCandidateOfProject`, `mappedProfileSlugsOf`,
  `addSourced`, `applyResearch`, `dossierOf`, `applyFoundEmails/Phones`, `contactStateOf` keep their
  signatures.
- **Duplicate rules today** (`CandidateService.java:642-680`): name at company (`refuseDuplicate`,
  V36's two partial unique indexes), LinkedIn slug (`refuseHeldProfile`, `LinkedInUrls.profileSlugOrNull`),
  email only in import (`findCandidateOfProject`). All per project.
- **Contacts** are a `@ElementCollection` on `app_lm_candidate_contact` keyed `(candidate_id, channel,
  value_key)` (V54), rewritten wholesale by `Candidate.replaceContacts/remember/recordFound*`. Photo is
  `app_lm_candidate_photo` (V44), one per candidate.
- **Audit ledger** (`core/audit`) is a *security* record: `@Immutable`, append-only trigger, IP + UA,
  `@Async` writer that swallows errors, allowlisted into the project feed by `ProjectActivityService`
  (`SHOWN`, `DETAIL_KEYS` at l.35-59; `candidateId` is deliberately not exposed). It is the wrong store
  for a CRM timeline (editable notes, must not be lost on a write error, read per person and per actor).
  It stays as it is; the CRM gets its own domain tables.
- **RBAC**: `ProjectAction {PROJECT_EDIT, TEAM_MANAGE, CLIENT_ACCESS_MANAGE, WORK_VIEW, WORK_EXECUTE}`,
  `WorkspaceAction {WORKSPACE_MANAGE, MEMBER_MANAGE, MEMBER_INVITE, PROJECT_CREATE, PROJECT_BROWSE,
  CLIENT_RECORD_MANAGE, POSITION_TEMPLATE_MANAGE}`. A workspace-wide people read has no action today.
  Adding one = seed migration + enum constant, held in step by `RbacCatalogTest`. Controllers use
  `@RequireProjectPermission(ProjectAction.X)` (`core/security/rbac/RequireProjectPermission.java`).
- **Custom columns** are per project (V45); their values stay on the mapping row.
- **Mockups are the source of truth** and there is **no Candidates mockup**. `Position.dc.html:2887,2901`
  draws People → Candidates only as a placeholder; `web/app/routes.tsx:118` routes it to
  `ProjectPlaceholderPage`. `WorkspaceLayout.tsx:59-87` has the staff-only "Workspace" nav group where a
  Candidates item belongs. Phase 0 draws the screens first.
- **Docs to update with the code**: CLAUDE.md (the people-half paragraph, the Database section, layout
  table), `java-spring-development` (the "three tiers" paragraph, l.290-309, currently says the note,
  status and compensation are mandate-specific), `lightmove-domain` (the new action), `docs/spreadsheet-import.md`
  (matching now workspace-wide), `claude-design/README.md` (remove the stale `Project.dc.html` row, add
  `Candidates.dc.html`).

---

## Target model

> Written before the build. Read it through **Names as built** above: the shape stands, the names do not.

### Tables

**`app_lm_candidate`** (new, V91) — the person. `workspace_id uuid NOT NULL REFERENCES app_lm_workspace ON
DELETE CASCADE`; identity: `full_name NOT NULL`, `title`, `company_name` (current employer, snapshot),
`linkedin_url`, `profile_slug` (derived, lower-cased, indexed — the hard key), `location_city/_country`,
`nationality`, `gender`, `years_experience`, `seniority_level`, `summary`; `profile jsonb` (career,
languages, education, skills, enrichedAt); compensation columns + `compensation_breakdown` (D3);
`ai_inferred_fields`, `ai_nationality_reading`, `ai_enrich_failed_at`; `enriched_by`, `source` (the first
door), `source_url`; `owner_user_id` (nullable, Phase 4), `do_not_contact boolean NOT NULL DEFAULT false`
(Phase 4); `created_by`, timestamps, `version`.
Indexes: `(workspace_id, profile_slug) UNIQUE WHERE profile_slug IS NOT NULL`; `(workspace_id, lower(full_name))`
non-unique for the soft match; a GIN `tsvector` on name/title/company for the pool's search.

**`app_lm_candidate_contact`** re-pointed to the person: `candidate_id` now references `app_lm_candidate`.
Same PK `(candidate_id, channel, value_key)`, same CHECKs. Plus an index on `value_key` per workspace
(via join) for the email/phone hard match.

**`app_lm_candidate_photo`** re-pointed the same way.

**`app_lm_project_candidate`** (kept, becomes the mapping): `project_id`, **`candidate_id uuid NOT NULL
REFERENCES app_lm_candidate ON DELETE CASCADE`**, `triage_company_id` (ON DELETE SET NULL, as now),
`status`, `rank int` (Phase 3), `note` (the mandate note, kept), `ai_assessment` (brief-specific),
`custom_fields`, `source` (the door *this mandate* got them through), `added_by`, timestamps, `version`.
Unique `(project_id, candidate_id)`. The person columns are dropped in V92 after the code no longer reads
them (expand → migrate → contract).

**`app_lm_candidate_note`** (V93): `id uuid`, `workspace_id`, `candidate_id NOT NULL`, `project_id`
nullable (the *context*, ON DELETE SET NULL, name snapshotted as `project_title` so a deleted mandate's
notes still read), `kind` CHECK `('GENERAL','CALL','MEETING','EMAIL','FEEDBACK')`, `body text` (≤ 4000),
`pinned boolean`, `author_user_id NOT NULL`, `created_at`, `edited_at`, `edited_by`. Notes are user-authored
and editable by their author or an admin; deletion is real but leaves a `NOTE_REMOVED` activity row.

**`app_lm_candidate_activity`** (V91) — the immutable system timeline: `id bigserial`, `workspace_id`,
`candidate_id NOT NULL` (ON DELETE CASCADE), `project_id` nullable + `project_title` snapshot,
`actor_user_id` nullable (a worker acting on someone's request records that requester; a scheduled purge
records null), `kind varchar(32)` CHECK over `CandidateActivityKind`, `occurred_at`, `details jsonb`
(status from/to, channel, door, runId, noteId, mergedFromId…). Indexes `(candidate_id, id DESC)` and
`(workspace_id, id DESC)`, plus `(workspace_id, actor_user_id, id DESC)` for the per-researcher view.
Written **synchronously inside the writing transaction** by a `CandidateActivityRecorder` bean — unlike the
audit writer, a lost row here is lost product data. The audit ledger keeps being written exactly as today
(both are needed: one is the firm's CRM history, the other the security record with IP and outcome).

`CandidateActivityKind`: `ADDED_TO_POOL, MAPPED, UNMAPPED, STATUS_CHANGED, PROFILE_EDITED,
CONTACTS_EDITED, CONTACT_FOUND, RESEARCHED, AI_ASSESSED, BACKGROUND_CONFIRMED, IMPORTED, NOTE_ADDED,
NOTE_EDITED, NOTE_REMOVED, TAGGED, UNTAGGED, OWNER_CHANGED, DO_NOT_CONTACT_SET, DO_NOT_CONTACT_CLEARED,
MERGED`.

### Java types (`api/candidate/`)

- `model/Candidate` — the **person** entity (table `app_lm_candidate`). Keeps the aggregate methods that
  are about the human: `describe`, `remember`, `replaceContacts`, `enrich`, `proposeBackground`,
  `confirmBackground`, `recordNationalityReading`, `recordFoundEmails/Phones`, `compensation()`.
- `model/MandateCandidate` — the mapping entity (table `app_lm_project_candidate`): `moveTo(status)`,
  `remapTo(triageCompany)`, `annotate(note)`, `rank(int)`, `describeCustomFields`, `assess(aiAssessment)`.
- `model/CandidateMatch` — result of the pool lookup: `HARD(candidate, key)` / `POSSIBLE(candidates)` / `NONE`.
- `service/CandidatePoolService` — the workspace pool: `find(workspaceId, identity)` (slug → email → phone
  → name@company), `list/search`, `get`, `merge` (Phase 4), owner/tags/do-not-contact.
- `service/CandidateService` — unchanged public surface, now composing the two entities. `add`/`addSourced`/
  the import path call `CandidatePoolService.find` first and **map instead of insert** on a hard match.
- `service/CandidateActivityRecorder` + `CandidateActivityService` (reads, cursor paged).
- `service/CandidateNoteService`.
- `dto/CandidateResponse` — unchanged fields; assembled from `MandateCandidate` + `Candidate`. New optional
  `candidateId` (the person id) so the SPA can open the person from a mandate row.
- `dto/CandidatePoolRowResponse`, `CandidatePersonResponse` (person + its mandates with status), 
  `CandidateNoteResponse`, `CandidateActivityEntryResponse`, `CandidateTimelineResponse`.
- `constant/CandidateActivityKind`, `CandidateNoteKind`.
- New `WorkspaceAction.CANDIDATE_POOL_MANAGE` (D2) in `core/security/rbac`, seeded to ADMIN and MEMBER.

### Matching rule (all four doors, one method)

`CandidatePoolService.find(workspaceId, LinkedInUrl, emails, phones, fullName, companyName)`:
1. LinkedIn slug equal → **hard**.
2. Any email `value_key` equal on a person of this workspace → **hard**.
3. Any phone `value_key` equal → **hard**.
4. Case-insensitive name at the same employer (company name, or the triage company's slug) → **possible**.
5. Else none.

- Hard: map the existing person to this mandate (`MAPPED` activity, `CANDIDATE_ADDED` audit with
  `mappedExisting: true`). New facts arrive **additively**: empty person fields fill, contacts union by
  `value_key`, a vendor-researched profile refreshes `profile` when older than `people-cache-ttl`; a
  non-empty manual value is never overwritten. Already mapped to *this* mandate → `CANDIDATE_ALREADY_MAPPED`
  (409, as today).
- Possible, interactive door (drawer POST): 409 `CANDIDATE_POSSIBLE_DUPLICATE` carrying the candidates;
  the SPA offers "Map existing" (POST `/projects/{id}/candidates/map` with `candidateId`) or "Add as new"
  (`force: true`). Possible, non-interactive door (import, sourcing, plugin): import keeps its current
  behaviour and treats name@company as a match (it already does); sourcing and the plugin always carry a
  slug so they never reach step 4.
- No auto-merge, ever. Manual merge is Phase 4, `CANDIDATE_POOL_MANAGE`, records `MERGED` on the survivor
  and re-points mappings, notes, activity, contacts, photo; the loser row is deleted; a mapping that would
  collide on `(project_id, candidate_id)` keeps the survivor's and folds the loser's note into a note row.

### Existing data migrates automatically (V91 backfill)

Nothing is re-entered and nothing is deleted. V91 is a Flyway migration like every other: it runs once at
the next boot (local: `npm run dev`; shared dev: `dev:cloud`; production: the deploy), inside one
transaction, and every executive already mapped on any mandate comes out as a pool person plus a mapping:

1. Per workspace (join `app_lm_project`), group the existing rows by `profile_slug` (LinkedIn) first, then
   by any shared email `value_key`. The **oldest row founds the person**; every later row in the group
   becomes a mapping to it. Rows that match only by name are **not** folded (soft key) — they stay two
   people, and the Phase 4 merge tool is how a researcher joins them, with a `MERGED` line saying who did
   it and when.
2. Person fields take the oldest row's non-null values, filled from later rows; contacts union by
   `value_key`; the photo comes from the oldest row that has one.
3. Every mapping keeps its own status, mandate note, custom-column values, `added_by` and dates.
4. Every existing row gets one `ADDED_TO_POOL` (or `MAPPED`) activity stamped with the row's `created_at`
   and its `added_by`, so the timeline reads who filed whom and when for old data too.
5. The migration is tested on a Testcontainers database seeded at V90 with duplicates across mandates, and
   rehearsed on a copy of the shared dev database before it reaches anyone else.

### Tags (Phase 4, V95): "Open to work", "Open to relocate", …

How the products do it: LinkedIn Recruiter exposes *Open to work* and *Open to relocate* as structured
filters; the CRMs (Loxo, Greenhouse, Recruit CRM) carry such facts as **tags** on the person, filterable
any / all / not and applied in bulk, and Bullhorn's own guidance is a picklist over free text so a value is
spelled one way. So:

- **`app_lm_workspace_candidate_tag`** — the workspace's tag catalog: `workspace_id`, `label` (unique per
  workspace, case-insensitive), `colour`, `created_by`, timestamps. Seeded per workspace with a starter set
  (Open to work · Open to relocate · Passive · Referral · Prior placement · Interviewed before); an admin
  renames, merges or retires a tag from Settings, and any staff member adds one inline.
- **`app_lm_candidate_tag (candidate_id, tag_id)`** — the owned list on the person (V39 idiom), rewritten
  whole by `Candidate.retag`.
- Filters: the pool's filter panel offers **any of / all of / none of** over tags; the Companies grid's
  executive filter gains a tag facet; the spreadsheet import gains a `TAGS` target field (`;`-separated,
  matched to the catalog case-insensitively, unknown labels created) and the export a Tags column.
- Every tag change is a `TAGGED` / `UNTAGGED` activity row with actor and time, and rides the bulk bar.
- Tags are staff working labels: never on `CandidateResponse` for a client seat, never sent to a model.

---

## Phases

### Phase 0 — Mockups (`claude-design/`)

> **Built** (PR #600): `Candidates.dc.html` draws the pool, the Activity tab, the person drawer (Profile, Notes,
> Timeline) and the bulk, merge and tag dialogs. `Position.dc.html` draws the position's Candidates page, Add
> from your candidates, the possible-duplicate check, and the executive drawer's Positions, Notes and
> Timeline sections. `Settings.dc.html` draws Candidate tags, and every workspace shell lists Candidates.
> The notes below are the original plan.

- New **`Candidates.dc.html`**: (a) the workspace Candidates pool `/candidates` — table (name + avatar,
  current title & company, location, mandates as status chips, owner, last activity, tags), search, a
  filter panel, saved-view chips, tick boxes with a bulk bar (add to position, tag, set owner, export);
  (b) an **Activity** tab `/candidates/activity` — the workspace-wide feed with actor/kind/date/position
  filters (Invenias' Global Journal); (c) the **person drawer/page**: header, Mandates section (each
  mandate, its status, its added-by/added-at, open), the existing profile sections, **Notes** (stream with
  kind, author, time, mandate-context chip, pin, edit; a composer with kind picker and "for this position"
  toggle), **Timeline** (system events with actor and time, kind filter); (d) the project **People →
  Candidates** page `/projects/:id/candidates` — the mandate's pipeline: one row per mapping, status,
  rank, company, last activity, an "Add from pool" picker with possible-duplicate rows, and the
  possible-duplicate dialog for the drawer's add form.
- `Workspace.dc.html`: Candidates item in the Workspace nav group. `Position.dc.html`: replace the
  candidates placeholder. Vocabulary: "Position" in labels, `project` in code.
- `claude-design/README.md`: add the row, drop the stale `Project.dc.html` row.

### Phase 1 — The pool: person/mapping split + auto-map (backend, V91)

> **Built.** Where the build departs from the notes below, the build is what stands:
> - **Names.** The human is `Person` (`app_lm_person`); `Candidate` stays the mandate's row
>   (`app_lm_project_candidate`), so every route, id and `CandidateResponse` field the SPA, extension and
>   importer hold is unchanged. The response gains `personId`. The ledger, photo and timeline are
>   `app_lm_person_contact`, `app_lm_person_photo` and `app_lm_person_activity`.
> - **Expand only.** V91 copies contacts and photos into the person tables rather than rekeying V54/V44's,
>   and leaves the person's columns on the mandate row, because the previous revision is still serving
>   while Flyway runs. The final cleanup migration, after Phase 4, drops them.
> - **Matching** is profile slug, then email unless the two records name different profiles. **Phone is
>   not a key** — a switchboard is on every executive of a company. A name alone never matches; the
>   possible-duplicate 409 and its dialog ship with the SPA phase that draws the dialog, so until then a
>   name-only match simply founds a second person, which the merge tool will fold.
> - **Activity kinds** in V91: `ADDED_TO_POOL, MAPPED, UNMAPPED, STATUS_CHANGED, PROFILE_EDITED,
>   CONTACTS_EDITED, CONTACT_FOUND, RESEARCHED, AI_ASSESSED`. The door rides in `details` (so no
>   `IMPORTED`), and a confirmed background is a `PROFILE_EDITED` detail.
> - **Research once.** A plugin capture of someone already researched spends no vendor call; the new
>   mandate still has them scored against its own brief.
> - The within-mandate name rule (`CANDIDATE_ALREADY_MAPPED` for a second person of one name at one
>   company) is held by the service alone now that V36's name indexes are gone.
> - Scripts that write or read the tables directly moved with it: `ops/dev/seed-report.sql` files a person
>   and then its mapping, `ops/eval/export-golden.sh` reads the person, and the e2e checks in
>   `e2e/api/{17,18,19}` and `e2e/spa/{companies,import-export}.mjs` join `app_lm_person` /
>   `app_lm_person_contact`.
>
> **Not built in Phase 1, and where each now belongs:**
> - Possible-duplicate `409 CANDIDATE_POSSIBLE_DUPLICATE`, `force: true`, `POST /projects/{id}/candidates/map`,
>   and the extension's handling of the new code → **Phase 4**, with the dialog.
> - The golden before/after comparison of talent map, export and report → **Phase 2**, where it proves the
>   contract changed nothing.
> - An import-level test that a row whose email a person on *another* mandate holds maps that person. The
>   path works (the importer's add goes through `CandidateService.file`), but only the service test covers
>   it → **Phase 2**.
> - `linkedinUrlLocked` on `CandidateResponse`, so the SPA stops deriving the lock from the row's
>   `source === 'extension'` (`ContactPanel.tsx`, `triageRows.ts`) → **Phase 3**, with the drawer work.
> - The pool's full-text index → **Phase 4**, with the pool search.
> - A client seat reads `personId` on `CandidateResponse`. It is an opaque id and every pool route is gated,
>   but Phase 3 must test that no route resolves a person id for a client seat.

- **V91** creates `app_lm_candidate`, adds `candidate_id` to `app_lm_project_candidate`, backfills as above,
  re-points `app_lm_candidate_contact` and `app_lm_candidate_photo` (recreate with the new FK, copy rows,
  union on the PK), sets `candidate_id NOT NULL`, adds the unique `(project_id, candidate_id)` and drops
  V36's two name indexes (the name is a soft key now). Creates `app_lm_candidate_activity` (V93 folded here
  so the backfill can write it — one migration, one story).
- **Entities**: `Candidate` (person) and `MandateCandidate` (mapping), Lombok idiom, intention-named methods.
  `CandidateRepository` splits into `CandidateRepository` (person; every finder takes `workspaceId`) and
  `MandateCandidateRepository` (every finder takes `projectId`; the native grid-rank queries move here
  with a join). The class-doc rule "an unscoped lookup on people must not exist" holds on both.
- **`CandidatePoolService.find`** + the four doors: `CandidateService.add`, `addSourced`, the import's
  `findCandidateOfProject` (now: pool match → map/replace), the plugin's POST (same `add`). `refuseDuplicate`
  becomes the soft-match branch; `refuseHeldProfile` becomes step 1 of `find`.
- **`CandidateActivityRecorder`** and the first kinds written from every existing write path (`ADDED_TO_POOL,
  MAPPED, UNMAPPED, STATUS_CHANGED, PROFILE_EDITED, CONTACTS_EDITED, CONTACT_FOUND, RESEARCHED, AI_ASSESSED,
  BACKGROUND_CONFIRMED, IMPORTED`). Workers (`CandidateEnrichmentWorker`, `CandidateAiEnrichWorker`,
  `ExecutiveSourcingWorker`, `ContactLookupService`) pass the requesting user as actor.
- `CandidateResponse` unchanged + `candidateId`; `ProjectActivityService.DETAIL_KEYS` gains `candidateId` so
  the position drawer's feed can link a line to the person.
- New error codes: `CANDIDATE_POSSIBLE_DUPLICATE`; `CANDIDATE_ALREADY_MAPPED` kept. `POST
  /projects/{id}/candidates/map` (`WORK_EXECUTE`) maps a pool person by id.
- Sourcing: `heldSlugs` seeded from the mandate's mapped slugs as now; a hit whose slug is in the *pool* is
  mapped rather than researched again (the pool's profile is the research).
- **Tests**: integration — same person added to two mandates is one `app_lm_candidate` and two mappings;
  plugin capture of a pool person maps and keeps the person's manual fields; import matches across
  mandates by email; possible-duplicate 409 and the `force`/`map` follow-ups; talentmap/export/report
  answers identical before and after (fixture-based golden comparison); a client seat's `CandidateResponse`
  carries no other mandate; every write leaves exactly the expected activity rows with actor and time;
  backfill test on a seeded pre-V91 dataset (Testcontainers, run V90 → insert → V91 → assert).

### Phase 2 — Stored profile slug (V92), and the drop deferred

> **Built** (#604). Where the build departs from the notes below, the build is what stands:
> - **Nothing is dropped.** Dropping V91's copies was planned here, but nothing writes them any more, so
>   they are a frozen record of every executive as V91 found them — the one fallback if the split, or
>   Phase 3–4's notes and merge, turn out to have lost something. They go in the **final cleanup
>   migration**, after Phase 4, together with the mapping's `note`. V92 only adds.
> - **`app_lm_person.profile_slug`** is backfilled in plain SQL (deploy runs the Redgate Flyway CLI over the
>   SQL folder, so a Java migration is not possible). The trap is that **`LinkedInUrls` slugs
>   `URI.getPath()`, which is percent-decoded**, while V91's regex read the raw string: `/in/j%C3%A9r%C3%B4me`
>   is the slug `jérôme`, and a raw backfill would store a key no filing ever looks up. V92 decodes, and
>   refuses what `URI.create` refuses (a space, a stray `%`). `PersonProfileSlugMigrationTest` checks the
>   SQL against `LinkedInUrls` itself over sixteen spellings. A path whose escapes are not UTF-8 is where
>   the two can still differ: Java substitutes U+FFFD, V92 stores null.
> - **Duplicates are not folded.** Where two people of one workspace already share a profile, the older
>   keeps the slug and the younger keeps its URL with a null slug. This is the answer `PersonMatcher` has
>   always given (oldest first), the merge tool (Phase 4) is how they are joined, and the migration
>   `RAISE NOTICE`s the count and the database's `lc_ctype`. That younger person can still be edited:
>   `PersonMatcher.claimOf` answers `SHARED` when the URL names the profile they already carry, and
>   `CandidateService.replace` saves the edit and has the person `yieldProfileKey()`, so the older one
>   keeps it. A URL another person holds that this one does not already carry is `HELD` (409). The
>   mandate-scoped checks (`refuseHeldProfile`, `mappedProfileSlugsOf`) still read URLs, since the younger
>   person's key is null; only the workspace-wide lookups (`PersonMatcher.find`, `claimOf`) use the
>   stored slug.
> - **The key heals.** `Person` re-derives the slug from the URL on every save, so anything V92's SQL
>   reads differently from `LinkedInUrls` — a non-UTF-8 escape (Java substitutes U+FFFD, V92 stores
>   null), a `C`-ctype `lower()` — is corrected on the person's next edit. V92 also refuses a bracket in
>   the path and a host `URI` would not parse (an underscore), as Java does.
> - **Races**: `app_lm_person_profile_slug_uk` answers `PERSON_PROFILE_HELD` (409) through
>   `GlobalExceptionHandler`, and a sourcing run treats it as a raced pick (skipped), as it does V91's
>   `app_lm_project_candidate_person_uk`.
> - **Tests**: `PersonProfileSlugMigrationTest` checks the slug against `LinkedInUrls` and that V91's copies
>   are untouched. `CandidateReadsGoldenIntegrationTest` holds recordings of the talent map, both export
>   stages and the report on one seeded mandate, made on the V91 schema — the before/after proof the final
>   cleanup needs (`-Dgolden.record=true` rewrites them after a deliberate change). The import test carried
>   over from Phase 1 is `SpreadsheetImportIntegrationTest.mapsThePersonAnotherMandateHoldsByEmail`.
>
> The notes below are the original plan.

Precondition: #602 is **deployed** to production, so no serving revision reads the old columns. Keep the
expand and the contract in separate deploys.

- **V92** drops from `app_lm_project_candidate` every column the `Candidate` entity no longer maps:
  `full_name, title, seniority_level, linkedin_url, location_country, location_city, nationality, gender,
  years_experience, summary, compensation_currency, base_salary, bonus, allowances, long_term_incentive,
  notice_period, compensation_breakdown, profile, ai_inferred_fields, ai_nationality_reading, enriched_by,
  emails_looked_up_at, phones_looked_up_at, contacts_looked_up_via`. Diff this list against
  `\d app_lm_project_candidate` and the entity first. Drop them with their CHECKs, and drop any index
  that only served them. It also drops the tables `app_lm_candidate_contact` and `app_lm_candidate_photo`.
  The mapping keeps `id, project_id, person_id, triage_company_id, company_name, status, note,
  ai_assessment, ai_enrich_failed_at, custom_fields, source, source_url, added_by, created_at, updated_at,
  version`.
- `grep -rn` the repository for every dropped column and both tables (`apps/`, `ops/`, `e2e/`), migrations
  aside. Nothing should still name them.
- **`profile_slug` on `app_lm_person`** (review of #602): written by the entity from
  `LinkedInUrls.profileSlugOrNull` on every URL write, backfilled with V91's regex, with a partial unique
  index `(workspace_id, profile_slug) WHERE profile_slug IS NOT NULL`. `PersonMatcher` and
  `isHeldByAnother` become one equality lookup instead of today's `LIKE '%/in/<slug>%'` scan of the whole
  workspace on every filing, and two doors racing to found one new profile hit a 409 instead of leaving two
  people on one slug. Count the duplicate slugs on the dev copy first: the index cannot be built over them,
  so V92 folds or reports them before creating it.
- Tests: the golden before/after comparison (talent map, export CSV, report on a seeded mandate) and the
  import test carried over from Phase 1. `CandidatePersonBackfillMigrationTest` already stops at V91,
  and must stay pinned there: V92 drops the columns it seeds.
- Docs: CLAUDE.md's Database section drops the "expand-only" sentence, and this plan gets a Built callout.

### Phase 3 — Notes, timeline reads, and the new action (V93, V94)

- **V93** `app_lm_person_note` (the target model's `app_lm_candidate_note`); migrate each non-empty mapping `note` into a `GENERAL` note with that
  mandate as context, author `added_by`, `created_at = updated_at` of the row. The drawer's one autosaving
  "Note" box is **replaced** by the shared Notes, whose composer defaults to the position the drawer was
  opened from (settled in the Phase 0 mockups: two note surfaces read as two stores). The import's
  `CANDIDATE_NOTE` cell becomes a note row with the position as context, and the mapping's `note` column
  is dropped by the next contract migration once nothing reads it.
- **V94** seeds `CANDIDATE_POOL_MANAGE` to ADMIN and MEMBER (`RbacCatalogTest`).
- Endpoints (`api/candidate/controller/`):
  - `GET /candidates?query=&owner=&tag=&status=&position=&cursor=` — the pool, `CANDIDATE_POOL_MANAGE`.
  - `GET /candidates/{id}` — person + mandates (status, added by/at) + contacts; `CANDIDATE_POOL_MANAGE`.
  - `GET /candidates/{id}/timeline?kind=&before=` — notes and activity merged, newest first, cursor paged.
  - `POST/PUT/DELETE /candidates/{id}/notes[/{noteId}]` and `PATCH …/pin`.
  - `GET /candidates/activity?actor=&kind=&position=&from=&to=&before=` — the workspace feed (D1/D2 gate).
  - Mandate-scoped mirrors under `/projects/{id}/candidates/{mappingId}/{notes,timeline}` gated
    `WORK_EXECUTE` (a client seat sees none of it), answering the same person data — so a researcher seated
    on the mandate reaches the shared record from the Companies drawer without the workspace action.
- SPA: `features/candidates` gains `poolApi.ts` (keys `["candidate-pool", …]`, `["candidate", id,
  "timeline"]`), the drawer gains **Mandates**, **Notes** and **Timeline** sections (the mockup's), the
  position drawer's activity lines link to the person. `lib/candidateActivity.ts` phrases kinds ("mapped
  to *Group CFO*", "marked Interested on *…*", "found an email via ContactOut"), merging consecutive lines
  like `projects/lib/activity.ts` does. Actor and time on every line.
- Reads come from `app_lm_person_activity`. It already carries `workspace_id`, `person_id`, `project_id` +
  `project_title`, `actor_user_id`, `kind`, `occurred_at` and `details`, with indexes for the per-person,
  per-workspace and per-actor reads. New kinds (`NOTE_ADDED`, `NOTE_EDITED`, `NOTE_REMOVED`) widen its
  CHECK in the same migration as the notes.
- `CandidateResponse` gains `linkedinUrlLocked`, and the SPA reads it (carried over from Phase 1).

### Phase 4 — Screens and CRM fields (V95)

- Workspace **Candidates** page (`/candidates`, staff nav item, `RequireStaff`), **Activity** tab, **person
  page** (`/candidates/:id`), project **Candidates** pipeline page replacing the placeholder, "Add from pool"
  picker, possible-duplicate dialog, bulk bar (add to position, tag, owner, export via the existing CSV
  writer).
- **V95**: `owner_user_id`, `do_not_contact` (both on `app_lm_person`), the tag catalog and `app_lm_person_tag` (see Tags above),
  `rank` on the mapping. Owner/tag/do-not-contact writes record `OWNER_CHANGED`, `TAGGED`/`UNTAGGED`,
  `DO_NOT_CONTACT_*` and are audited. Do-not-contact **warns** in the drawer and blocks the ContactOut buttons; it does not block a
  mapping (Invenias' warn mode; block can follow).
- **Manual merge**: `POST /candidates/{id}/merge {loserId}` (`CANDIDATE_POOL_MANAGE`), preview response
  first, then commit; `MERGED` activity on the survivor with the loser's snapshot in `details`; audited.
- **Pool search**: Postgres FTS over name/title/company + the contact ledger's `value_key` (exact).
- **Possible duplicate** (carried over from Phase 1): a name + employer match on the drawer's add answers
  `409 CANDIDATE_POSSIBLE_DUPLICATE` with the people it found. "Map existing" posts
  `/projects/{id}/candidates/map {personId}`, "Add as new" resends with `force: true`, and the extension
  offers "map" on the same code.

### Final — Cleanup migration (after Phase 4, #606)

Precondition: Phases 3 and 4 are merged **and deployed**, `CandidateReadsGoldenIntegrationTest` is green,
and nobody has needed V91's copies for a repair. Take the next free migration number.

- Drop from `app_lm_project_candidate` every column the `Candidate` entity does not map: `full_name, title,
  seniority_level, linkedin_url, location_country, location_city, nationality, gender, years_experience,
  summary, compensation_currency, base_salary, bonus, allowances, long_term_incentive, notice_period,
  compensation_breakdown, profile, ai_inferred_fields, ai_nationality_reading, enriched_by,
  emails_looked_up_at, phones_looked_up_at, contacts_looked_up_via`, with their CHECKs; and `note`, once
  Phase 3 has moved it into `app_lm_person_note` and nothing reads it. Diff against
  `\d app_lm_project_candidate` and the entity first.
- Drop `app_lm_candidate_contact` and `app_lm_candidate_photo`.
- `grep -rn` the repository (`apps/`, `ops/`, `e2e/`, migrations aside) for every dropped name.
- `CandidatePersonBackfillMigrationTest` stays pinned at V91. `PersonProfileSlugMigrationTest`'s check that
  V91's copies are untouched stays true (it stops at V92). The golden reads must pass unchanged.
- Docs: CLAUDE.md's "expand-only" sentence goes.

### Phase 5 — Later (not in this plan's PRs, listed so the tables leave room)

Tasks and reminders (an "Upcoming" block above the timeline), documents/CV on the person, GDPR consent
(lawful basis, consent expiry, erasure behind its own action — erasure must also purge `app_lm_vendor_person`
rows the workspace bought, which is a cross-tenant cache and needs its own design), client-visible toggle on
the mapping with FEEDBACK notes from the client seat, saved views, configurable statuses with fixed
categories (today's seven `CandidateStatus` values stay).

### Suggested PR sequence

1. Phase 0 mockups (no code) — review the screens first.
2. Phase 1 backend split + auto-map + activity recorder (V91), docs updated in the same PR.
3. Phase 2 stored slug (V92) and golden reads — #604.
4. Phase 3 notes + timeline + action (V93, V94) with the drawer sections.
5. Phase 4 screens + owner/tags/do-not-contact + merge + possible duplicate (V95).
6. Final cleanup migration.

---

## Engineering standards (every phase)

Load `java-spring-development`, `react`, `lightmove-domain`, `db-ops` before touching their areas; copy the
neighbouring idiom. Backend by type (`constant/model/dto/repository/service/controller`); enums in
`constant`; one DTO record per file; `@RequiredArgsConstructor`; entities `@Getter` +
`@NoArgsConstructor(PROTECTED)` + intention-named methods, never setters/`@Data`/`@Builder`. Every tenant
query filters by `AuthPrincipal.requireWorkspaceId()`; person finders take `workspaceId`, mapping finders
take `projectId`. Gates are `@PreAuthorize`/`@RequireProjectPermission` over actions on controllers only;
nothing branches on a role or on `WorkspaceMode`. `@Transactional` in services, `readOnly` on reads, no
vendor/LLM call inside a write transaction (the activity recorder is a plain repository write). Errors are
`ApiException` + `ErrorCode`, fixed `userFacing` sentences; the SPA switches on `code`. Audit through
`AuditService` as today; the activity row is *additional*, never a replacement. New Flyway files only
(V91–V95), `app_lm_` prefix, V34's CHECK idiom, backfill before any constraint relies on it; `harden.sql`
grants for the new tables (`db-ops`). SPA: feature folder `features/candidates`, TanStack keys, Zod schemas,
labels through `useWorkspaceVocabulary` where the word is Position/Client. Comments only for a *why*. Update
CLAUDE.md and the skill paragraphs in the same PR as the behaviour.

## Critical files

**Backend** (`api/`), as built and to build:
`candidate/model/{Person,Candidate,PersonActivity,PersonPhoto,CandidateContact}.java` (+ `PersonNote`),
`candidate/repository/{PersonRepository,CandidateRepository,PersonActivityRepository,PersonPhotoRepository}.java`
(+ `PersonNoteRepository`),
`candidate/service/{CandidateService,PersonMatcher,PersonActivityRecorder,CandidateRequestReader,CandidateResponseMapper,ProjectCandidateCounterAdapter,MappedExecutiveLookupAdapter}.java`
(+ `PersonPoolService`, `PersonActivityService`, `PersonNoteService`),
`candidate/controller/{CandidateController}.java` (+ `CandidatePoolController`, `PersonNoteController`),
`candidate/constant/{PersonActivityKind}.java` (+ `PersonNoteKind`), `core/security/rbac/WorkspaceAction.java`,
`core/error/constant/ErrorCode.java`, `project/service/ProjectActivityService.java`,
`dataimport/service/{ProjectImportService,ImportRequestBuilder}.java`,
`enrichment/sourcing/service/ExecutiveSourcingWorker.java`, `enrichment/contact/service/ContactLookupService.java`,
`enrichment/candidate/service/{CandidateEnrichmentWorker,CandidateAiEnrichWorker}.java`,
migrations `V91__candidate_person_pool.sql` and `V92__person_profile_slug.sql` (built), then
`V93__person_notes.sql`, `V94__candidate_pool_action.sql`, `V95__person_owner_tags.sql`, `ops/cloudsql/harden.sql`.
Scripts that touch the tables directly: `ops/dev/seed-report.sql`, `ops/eval/export-golden.sh`, `e2e/api/{17,18,19}-*.sh`,
`e2e/spa/{companies,import-export}.mjs`.

**Web** (`web/`): `features/candidates/api/{candidatesApi,poolApi,types}.ts`,
`features/candidates/components/{CandidateDrawer,CandidateProfile,NoteSection}.tsx` (+ new
`MandatesSection`, `NotesSection`, `TimelineSection`, `PossibleDuplicateDialog`, `AddFromPoolPicker`),
`features/candidates/pages/{CandidatesPage,CandidateActivityPage,CandidatePage,ProjectCandidatesPage}.tsx`,
`features/candidates/lib/candidateActivity.ts`, `features/projects/lib/activity.ts`,
`components/layout/{WorkspaceLayout,ProjectLayout}.tsx`, `app/routes.tsx`, `lib/errorCodes.ts`,
`lib/projectRows.ts` (invalidate the pool keys too). Extension: `apps/extension/src/api/captureCandidateApi.ts`
(handle the new 409 code by offering "map").

**Mockups**: `claude-design/{Candidates,Workspace,Position}.dc.html`, `claude-design/README.md`.

## Verification

- **Backend**: `cd apps/api && ./mvnw test` (Testcontainers). Phase 1's are in
  `candidate/CandidatePoolIntegrationTest` and `candidate/CandidatePersonBackfillMigrationTest`, Phase 2's in
  `candidate/PersonProfileSlugMigrationTest` and `candidate/CandidateReadsGoldenIntegrationTest`; still to
  add: notes CRUD and who-may-edit; timeline merges notes and activity in order with actor and time;
  `GET /candidates` 404s for a pure client and for a stranger; `RbacCatalogTest` after V94.
- **Frontend**: `cd apps/web && npx vitest && npm run build`. Render tests: drawer Notes/Timeline sections
  show author and time; possible-duplicate dialog offers map/add; pool page filters; activity phrasing.
- **Extension**: `npm test` (its suite) after the 409 handling.
- **End to end** (`verify` skill, `npm run dev`, `npm run dev:db:seed-report` for people):
  1. Add "Fatima Al Mansoori" from the Companies grid on mandate A; add her by LinkedIn URL on mandate B →
     one pool row, two mandates in her drawer, a MAPPED line on the timeline naming who and when.
  2. Capture her with the plugin on mandate C → mapped, profile refreshed, contacts unioned, nothing typed
     lost.
  3. Import a sheet with her email under a different spelling of the name → matched, no duplicate.
  4. Add a namesake at the same company by hand → the possible-duplicate dialog; choose "Add as new".
  5. Write a call note in mandate A; open her from mandate B → the note shows with mandate A as context
     (per D1). Log in as the client representative on A → no notes, no timeline, no other mandate.
  6. Change status on B, find an email, run AI enrich → timeline lines with actor and time; the
     workspace Activity tab lists the same lines filtered by that researcher.
  7. Reports, Export and Map on A unchanged.
- **Regression**: `cd e2e && PROFILE=e2e ./run-all.sh`.
- **Migration**: `npm run dev:db:reset` then boot: V1→V95 clean; a second boot on a copy of the Cloud SQL dev
  database (`dev:cloud`) proves the V91 backfill on real duplicates before it reaches the shared database.

---

## Appendix — research sources

Vendor help centres were read through search summaries (their full pages are blocked from this
environment), so exact field names and column sets are likely rather than confirmed.

**Person vs assignment link**
- Invenias: [Candidates within an Assignment](https://kb.bullhorn.com/invenias/Content/Invenias/Topics/candidatesWithinAssignment.html),
  [Actions & Journal](https://kb.bullhorn.com/invenias/Content/Invenias/Topics/actionsAndJournal.html)
- Clockwork: [Person Panel](https://support.clockworkrecruiting.com/article/767-person-panel-overview),
  [Candidate Panel](https://support.clockworkrecruiting.com/article/1189-candidate-panel-overview),
  [Configure Statuses](https://support.clockworkrecruiting.com/article/144-configure-statuses)
- Bullhorn: [Entity reference](https://bullhorn.github.io/rest-api-docs/entityref.html),
  [Hiring workflow](https://kb.bullhorn.com/ats/Content/BHATS/Topics/understandingBHHiringProcess.htm)
- Lever data model: [Ashby's description](https://docs.ashbyhq.com/the-lever-data-model)
- Ezekia: [Note context](https://ezekia.freshdesk.com/support/solutions/articles/101000370052-what-does-a-context-do-on-a-note-),
  [Client portal](https://ezekia.freshdesk.com/support/solutions/articles/101000486642-client-portal)
- Loxo: [Intake notes](https://help.loxo.co/en/articles/5247372-use-intake-notes-and-note-templates),
  [Reporting](http://help.loxo.co/en/articles/3082958-reporting-101)

**Duplicates and merge**
- Greenhouse: [Auto-merge](https://support.greenhouse.io/hc/en-us/articles/208063316-Auto-merge),
  [Duplicate tag](https://support.greenhouse.io/hc/en-us/articles/49207850415003-Configure-the-Duplicate-tag),
  [Merge permission](https://support.greenhouse.io/hc/en-us/articles/360039867211-Permission-stripe-Can-merge-candidates-and-prospects)
- Loxo: [Managing duplicates](https://help.loxo.co/en/articles/446947-managing-duplicates-in-loxo)
- Bullhorn: [Duplicate checking](https://kb.bullhorn.com/ats/Content/BHATS/Topics/understandingBHRecordDuplicateChecking.htm),
  [Merge FAQ](https://kb.bullhorn.com/bhone/Content/BH1/Topics/mergingRecordsBH1FAQ.htm)
- Manatal: [Duplicate management](https://support.manatal.com/docs/duplicate-management-system)
- Recruiterflow: [Duplicate detection](https://help.recruiterflow.com/en/articles/4366865-how-recruiterflow-detects-and-manages-duplicate-candidates)

**Notes, timeline, tasks**
- Bullhorn: [Managing notes](https://kb.bullhorn.com/ats/Content/BHATS/Topics/NoteManagement.htm)
- Greenhouse: [Private note](https://support.greenhouse.io/hc/en-us/articles/360031123492-Private-candidate-note),
  [Activity feed](https://support.greenhouse.io/hc/en-us/articles/360037386272-Candidate-activity-feed)
- HubSpot: [Record layout](https://knowledge.hubspot.com/records/work-with-records),
  [Associate activities](https://knowledge.hubspot.com/records/associate-activities-with-records)
- Salesforce: [Activity timeline filters](https://help.salesforce.com/s/articleView?language=en_US&id=activity_timeline_filters.htm&type=0)

**Tags, off-limits, GDPR, client portal**
- LinkedIn Recruiter: [Open to relocate filter](https://www.linkedin.com/help/recruiter/answer/a412436)
- Bullhorn: [Choosing a field type](https://www.bullhorn.com/uk/customer-blog/choosing-the-best-field-type-for-the-job/)
- Recruit CRM: [Hotlists](https://help.recruitcrm.io/en/articles/2907826-how-can-i-create-and-access-my-hotlists),
  [Bulk updating](https://help.recruitcrm.io/en/articles/1794019-bulk-updating-records)
- Invenias: [Off limits](https://kb.bullhorn.com/invenias/Content/Invenias/Topics/offLimits.html),
  [Data privacy module](https://kb.bullhorn.com/invenias/Content/Invenias/Topics/dataPrivacyModuleUserGuide.html),
  [Assignment sharing](https://kb.bullhorn.com/invenias/Content/Invenias/Topics/assignmentSharing.htm)
- Thrive TRM: [Off-limits capabilities](https://thrivetrm.com/thrive-launches-new-off-limits-capabilities-for-exec-recruiters/)
- Vincere: [GDPR and consent](https://help.vincere.io/en/articles/5355447-gdpr-compliance-and-candidate-consent)
- Clockwork: [Candidate visibility for clients](https://support.clockworkrecruiting.com/article/592-change-candidate-visibility-for-clients)
