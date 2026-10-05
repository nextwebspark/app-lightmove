# LightMove

Multi-tenant SaaS for executive search and talent mapping.

A **Workspace** is the tenant. It holds **Members** whose membership carries a *set* of workspace roles
(`ADMIN` / `MEMBER` / `CLIENT`) who run **Projects** — search mandates for client companies — where each
seat holds **one** staff project role (`LEAD` / `RESEARCHER`) — the project tier has no admin; `LEAD`
owns the mandate. `CLIENT` is a hiring-company representative: read-only, scoped to the mandates
they're attached to. It is not a fence — a member may hold `CLIENT` **alongside** a staff role and is
then treated as staff; a *pure* client (only `CLIENT`) is the one kept out of staff surfaces. The
workspace `CLIENT` role grants nothing; access is the project `CLIENT` seat, which grants `WORK_VIEW`
(read a mandate's content, never edit).

**Built so far: auth, workspace management, projects, the RBAC layer, and the search layer.** Signup
(4 steps), login, OAuth sign-in (Google and LinkedIn, run in a popup), invitations, the roster, **several
workspaces per user** (V81: a staff member founds a further one from Settings → Workspaces through the
wizard's organisation and invite steps in a modal, an invitation to a second workspace is accepted from
the topbar menu or the emailed link, and the topbar menu switches — a session is in exactly one
workspace, `wsId`, moved by `POST /auth/switch-workspace` or, audited, by a web refresh once its membership
has ended), the projects/clients
screens, a project's Team & access tab, the client registry with representative invites and their
scoped read-only project access, and **Strategy → Companies**: a filter over the company universe, the
searches saved against it, and the three Companies pages (In universe / Shortlisted / Declined) where a
mandate triages what it took from it. A company reaches those pages four ways — out of the market
(Strategy's per-row add, or the Companies screen's own picker over the same universe, both `POST
/triage` with an id the server resolves), typed in by hand, captured by the browser plugin
(`POST /triage/capture`), or **imported from a spreadsheet**. Strategy's rows also carry a tick box:
a selection raises a floating bar over the grid whose three buttons file every ticked company at one
stage in one request (`POST /triage/bulk`), so a mandate can decline forty companies without first
taking them into the universe.
Deleting one drops the project↔company row only: the Apollo universe is read-only to the app. On top of
that sits the **people half**: an executive mapped for a mandate, optionally against one of its triaged
companies, added by hand from the Companies grid — where a row is a *person at a company*, so a company
with three of them is three lines and one with none keeps its "Add executive" slot. An executive is also
captured by the plugin, through the same endpoint the drawer posts to (`source: "extension"`).
**Every executive is a workspace person first (V91, `docs/candidate-crm.md`)**: `app_lm_person` is the
human — profile, background, package, contact ledger, research — owned by the workspace, and a mandate's
`Candidate` row maps that person and keeps only the mandate's own status, custom-column values and
brief-specific AI assessment. Whichever door files someone, `PersonMatcher` first asks whether the
workspace already knows them — by LinkedIn profile slug (stored, V95), or by an email the ledger holds unless the two
name different profiles; never by phone (a switchboard is on everyone) and never by name alone — and a
match is **mapped, not duplicated**: what the new mandate brings only fills what nobody recorded, an
edit through one mandate is what every other reads, and removing someone from a mandate keeps the
person. A profile is one person's: an edit that would give a second person of the workspace the same
LinkedIn profile is refused (`PERSON_PROFILE_HELD`). A plugin capture of someone already researched spends no second vendor call. Every change to a
person — added, mapped, unmapped, status, profile, contacts, research, AI — is a line of
`app_lm_person_activity` naming who did it, on which mandate, and when, written in the same transaction
as the change (`PersonActivityRecorder`); the security audit trail is written as before, beside it.
**Notes are the person's (V96)**, never a field of the row: typed (note, call, meeting, email), authored,
timed, about a position or about the person, shared by every mandate that maps them and **staff-only** —
`CandidateResponse`, the talent map and the report carry none, because a client seat reads all three
(decision D1). A note a door sends (`SaveCandidateRequest.note`: the add form, the plugin, a sheet's
Note column) is filed once as a note about that mandate; only its author or a `WORKSPACE_MANAGE` holder
may change or remove one (`PERSON_NOTE_NOT_YOURS`), and a note line on the timeline names the note,
never quotes it, so a removed note leaves no copy. The executive drawer draws **Positions** (every
mandate mapping the person, this one first), **Notes** and **Timeline** for staff, through the
position's own `WORK_EXECUTE` routes (`…/candidates/{id}/positions|notes|timeline`, `PersonCrmController`);
the workspace's routes (`/api/v1/candidates/{personId}…` and `/candidates/activity`, the feed) take
person ids and V97's `CANDIDATE_POOL_MANAGE`, ADMIN and MEMBER and never CLIENT.
**Documents are the person's too (V105, `docs/candidate-documents.md`)**: a CV, cover letter or
reference is a card whose files are versions — a file sent under a name already on the person is its
next version unless the upload says `asNewDocument`, the same bytes are refused
(`PERSON_DOCUMENT_DUPLICATE`), a file is accepted only where its name and its bytes agree
(`DocumentFormat`), and one CV per person carries the primary mark. Staff-only like notes, through the
same two doors (`…/candidates/{id}/documents`, `/candidates/{personId}/documents`); removing is the
uploader's or a `WORKSPACE_MANAGE` holder's (`PERSON_DOCUMENT_NOT_YOURS`), every download is audited,
and each change is a timeline line that names the document only while it exists. The bytes live in a
private GCS bucket behind `core/storage`'s `DocumentStore` (`lightmove.storage.*`; the filesystem store
for `npm run dev` and tests), streamed by the API, never by a signed URL. The SPA draws them in
`components/documents`: the Candidates drawer's Documents tab, the executive drawer's Documents section, a
header chip for the primary CV and a preview sheet (a PDF or image fetched as a blob, never a link); the
upload tray says what each file will become before sending, and a duplicate is the server's 409.
`CandidateResponse.linkedinUrlLocked` is the server's own lock, which the Contact section reads rather
than guessing from this mandate's door.
**The workspace's Candidates page** (`/candidates`, `RequireStaff`, `Candidates.dc.html`, Phase 4) reads
those routes: a People list the server searches (name, title, employer, an email; a plain scan per
workspace, V33's reasoning), pages, sorts and narrows — quick views (owned by me, in an active position,
in none) in the toolbar, then tags any/all/none, position, status, owner and country in a Strategy-style filter rail hidden until asked for — with a selection bar that adds people to
a position as Identified (that position's `WORK_EXECUTE` too, since filing someone is work on it), tags
them, sets an owner or exports them (audited, `dataexport`); an Activity feed; and a drawer keyed by
person id. V98 gives the person the team's own facts — an **owner** (a colleague; it changes nobody's
access), **do not contact** (warns on every position and refuses a contact lookup,
`PERSON_DO_NOT_CONTACT`, before anything is spent; it never blocks a mapping) and **tags** from the
workspace's catalog (any staff member adds one, an admin renames, recolours or retires it in Settings →
Candidate tags under `WORKSPACE_MANAGE`; a person holds the tag's id, so a rename reaches everyone, and a
retired one stays where it is but is never put on anyone again). Each is a timeline line and an audit
event, and none rides `CandidateResponse`. **Phase 4b-1**: a hand-typed add of someone the workspace holds by name alone at that employer is
asked first (`409 CANDIDATE_POSSIBLE_DUPLICATE` with `personIds`; the drawer resends with
`existingPersonId` or `addAsNewPerson`), never on the plugin, import or run doors. A position has **no
Candidates page of its own**: In universe lists its executives, Outreach works them, and the workspace
Candidates page's Add to position files people onto it (`/projects/:id/candidates` redirects to In
universe). Merge is Phase 4b-2.
An executive's drawer also **finds their contacts**: two buttons in the Contact section ask ContactOut
for an email or a phone, one channel per press because the two bill from separate pools. Every email
and phone the mandate knows is a row of `app_lm_candidate_contact` (V54, the only store since V55
dropped the row's `email`/`phone` columns) with the door it came through — typed, imported, captured,
or found — and a lookup stamps a timestamp per channel on the candidate, which is the whole point: a
value already held, or a miss already recorded, is answered off the row and never bought twice. The
Contact section draws one row per channel, whatever door a value came through, and its pencil turns
the same rows editable in place — add, remove, retag work/personal, mark verified (recorded as
"Verified by researcher", beside ContactOut's own "Verified"; neither is printed on the row, and no
"via ContactOut" caption is — who did what is the audit trail's) — saved as one list through
`PUT …/candidates/{id}/contacts`; a profile PUT adds what it supplies, never removes a contact, and is
held to the same ten per channel.
No primary flag anywhere: the grid's Email and Phone columns list them all. A person the plugin
captured keeps their LinkedIn URL locked (`CANDIDATE_PROFILE_URL_LOCKED`): it is the page they were
read off and everything keys on it. `lightmove.enrichment.contactout` sits **beside**
`…enrichment.provider` and is never selected by it: contact lookup is its own account with its own bill,
so `provider: off` leaves the buttons working, and an unconfigured deployment simply does not offer them.
The **spreadsheet import** is that fourth door and carries both halves at once: a CSV or Excel file
whose rows are people at companies, mapped column-by-column onto our fields, then confirmed by a
person before anything is written. **The model is asked only where a header is in doubt** — a sheet
whose every header is a known spelling (anything built from the downloadable template, and most second
imports) maps with no LLM call at all; when it is asked it gets headers and a locally computed value
shape only, **never cell values**, and a synonym matcher answers alone when Vertex cannot be reached.
A header no field covers becomes a **per-project custom column**: the definitions
are rows in `app_lm_project_custom_column` and the values a jsonb bag on the row, so the grid renders
it like any built-in while a new mandate still starts from the built-ins alone. The importer writes
nothing itself — it builds the same requests the Companies drawer posts and hands them to
`triagecompany` and `candidate`, so every scope check, duplicate rule and audit event stays where it
already lives. **An imported row is never resolved against the market and never researched** — a file
states its own figures and arrives a thousand rows at once, so the two things a one-at-a-time capture
affords are exactly the two it cannot.
A stage also leaves as a file: **Export** on the Companies toolbar downloads the whole stage —
every row, not the page on screen, narrowed by whichever of the grid's three header filters are in
force, rows as well as companies, so the file is what the screen was showing — carrying every
column the grid draws and every custom column the mandate added, with the two Links icons spelled
out as Website and Company LinkedIn. It is `WORK_VIEW`, the gate that reads the grid, so a client
representative may take the mandate they can already read; unlike every other read it records an
audit event, because a mandate leaving as a file is not the same act as reading a page of it. Past
`lightmove.export.*` it refuses rather than truncating.
The In-universe page also reads as a **map**: a Table | Map toggle on its toolbar (offered only
where a Mapbox public token is configured) swaps the grid for a mapping panel (country → company →
executives) beside a Mapbox globe, with a pin per company and
per executive and the same two drawers opened from a pin's popup or a panel row. Nothing carries a
coordinate, so `geocoding` resolves each distinct city + country once through Mapbox and keeps it in
`app_lm_geocoded_place`; `talentmap` composes the stage's companies, people and points into one
unpaged, capped read (`GET /projects/{id}/talent-map`), with `…/talent-map/locations` answering the
same map as points alone for the poll that waits on places rather than on people. **Find executives** (`enrichment/sourcing`, V86) is the fifth door and the one that fills a
universe by itself: a button on the In-universe toolbar takes the ticked companies — or, with none
ticked, the first `max-companies-per-run` with nobody mapped — and answers 202 with a run row that
the strip under the toolbar polls (and the project stream announces). Off the request thread, one
Gemini call turns the brief into single-word title tokens (`SourcingSpec`: a seniority group, a
function group — never empty, since "Chief" alone bought every Chief Accountant — and an excluded group
sent as `not_includes`, four words each — the vendor refuses a fifth rule, runs a phrase at ten seconds
and matches a word inside longer ones, so an exclusion hiding in a senior title word is dropped),
one **synchronous** Bright Data people search per company keys on the company's LinkedIn slug
(`current_company_company_id`; the numeric id matches nothing) with the brief's country and the Gulf
neighbours only — nobody living elsewhere is searched for, and a brief with no country searches everywhere.
**No model judges the people**: the hits not already mapped are ranked in code (`SourcedHitRanking` —
the vendor matched the headline and sends no relevance order, so it reads the *current role's* title:
a function word first, then its level against the brief's seat) and the first `picks-per-company` are each filed through `CandidateService.addSourced` — the search hit *is* the research, so no
second vendor call — as `AI_SOURCED` (no badge on the grid; the drawer's source reads "Sourced"), and
the same deep enrichment a capture gets writes the assessment, fired with the `SOURCING` trigger that
skips the per-user LLM meter. A company whose search finds **nobody** is searched again with other title
words (`SourcingSpecRefiner`), up to `max-search-rounds`: the first search that finds anybody is the
last. The refiner reads the brief, the company's headcount and every word each earlier round tried — its own earlier answers
included, replayed as one conversation because the model keeps nothing between calls; words a round
already searched are re-asked once, then given up. Every returned hit is billed and a search finding
nobody costs nothing, so `max-companies-per-run × hits-per-company` is still one press's ceiling
(5 × 10 for the trial).
`sourcing.people-source: contactout` swaps the index for **ContactOut's People Search** on the
contact-lookup key (`ContactOutPeopleSearch`): Bright Data's titles are a cache frozen when LinkedIn locked
Position/Experience on 13 Nov 2025, ContactOut's are the current experience's. The same words go as one
Boolean title (`(seniority OR …) AND (function OR …)`, whole words, not substrings), the company by its
website domain or name (a company LinkedIn URL matches nothing there), one search credit per profile
returned; each hit is read into Bright Data's record shape (`ContactOutPeopleRecords`) so the cache,
the ranking and the filing are unchanged, and is filed `enriched_by = CONTACTOUT` (V90). The people
cache answers a search only from the provider it asks. Where Bright Data is also configured it stands
behind ContactOut (`PeopleSearchChain`, walked by `ChainedPeopleSearch`, the cache wrapping each provider): a company ContactOut finds nobody at, cannot key, or fails on —
out of credits included — is searched on the dataset, and filed `enriched_by = BRIGHTDATA`; the run's
outcome records the `source` that answered. Offered where the chosen index is configured
(`provider: brightdata`, or a ContactOut key); a client seat sees none of it. **Strategy → People**
(`enrichment/peoplesearch`, V93) is the sixth door: a Companies | People toggle on the Strategy
toolbar (`?mode=people`, staff only) swaps the company filter for ContactOut's People Search filter —
only parameters its API reference lists, the values its accepted-values sheet lists
(`data/contactout-people-vocabulary.json`; the dashboard's Revenue, Gender and exclude lists have no
API). The filter autosaves as `app_lm_strategy.people_filter` and is counted free and live
(`GET …/strategy/people/count`, its own 10/s pacer beside search's 60/min); **Search** is a press
(`POST …/strategy/people/search?page=`), the top 25 in ContactOut's order — it offers no other — and
Load more the next 25. Every page goes through the V87 cache keyed on the body and the page, so the
same question and page is never bought twice, by anyone, and the screen reopens on the pages already
bought (`GET …/strategy/people/results`, which never buys). The mandate's declined companies narrow only
the free count: the search asks exactly the question it is cached under, since that page answers every
workspace, and people at declined companies — the whole stage, by name or LinkedIn slug — are left off
the page as it is read, so declining someone never turns a paid page into a new question. A person already mapped
comes back, and is billed, because ContactOut cannot exclude one — the grid marks them "In mandate".
The selection bar and the person panel file the ticked people at **In universe, Shortlisted or
Declined** — the stage lands on their employer, filed by `captureFromResearch` as `PEOPLE_SEARCH` and
matched to the market where it can be, while an employer already held keeps its stage — from the
cache, never the vendor (`addResearched`, `PEOPLE_SEARCH`, `enriched_by = CONTACTOUT`, no AI
enrichment). V94 keeps each ContactOut profile whole (`source_record`) beside the Bright Data-shaped
`raw`, so the panel shows everything it sent — headline, company facts, certifications, projects, the
free contact-availability flags — a fold per part, drawn only when it has something; once the person is
in the mandate the panel's Contact fold is the candidate drawer's, Find email and phone included. The
results read as a Table or as Cards (a per-viewer localStorage choice). The Location box suggests from
two letters: countries, then LinkedIn's own spellings of where people on file live (`raw ->> 'city'`),
then Mapbox, sent as `City, Country`. Saved searches carry a `kind`.
The **Reports**
tab is the mandate's talent mapping report (`GET /projects/{id}/report`): four chapters — mapping
progress, shape of the market, remuneration, diversity — aggregated live by `report` from the same
rows, so nothing is stored and nothing goes stale. It reads one chapter at a time behind a numbered
step rail, the chapter kept in the URL (`?chapter=`), and its mockup is the `reports` page of
`claude-design/Position.dc.html` — drawn in the UNCAVA palette (`--color-u-*`) — the only palette
the code has: the old app names (`panel`, `amber`, `sky`, …) are gone from both apps, and an unset
theme opens dark. It states only what the rows carry: a candidate's
status but no pipeline outcome, and a package in another currency is counted rather than converted.
**Gender (V56) is recorded on a candidate, or proposed and flagged — never silently inferred** — the
chapter divides by the executives who have one on file, not by the headcount, so a mandate nobody has
recorded reads as unmeasured rather than as a pool of one gender. **AI enrichment** is two
ungrounded Gemini calls at temperature 0 (no web search — grounding was too slow) run by
`CandidateAiEnrichWorker` after a capture's vendor research lands, and again from the drawer's
**AI deep enrich** button (`POST …/candidates/{id}/ai-enrich`, 202, `WORK_EXECUTE`), one budget unit
for both. The assessment call (`CandidateAiEnricher`) reads the profile alone and always proposes its
most probable value — never "unknown" — for whichever of gender, years of experience and seniority
level are still empty — a value already on the row always stands, and each filled one is flagged in
`ai_inferred_fields` (V78, an "AI" badge) until a researcher changes it — and scores the executive 1–10
on the brief's technical and behavioural competencies with at most five positives and five negatives
each, and a summary (V79 `ai_assessment`, replaced whole per run; a `sources` key left by earlier
grounded runs is ignored on read). **Nationality is the deliberate exception: it may be "Unknown"**,
because a wrong one costs more than a blank. It is `CandidateNationalityClassifier`'s, asked only while
the field is empty, against an evidence rubric (`candidate-nationality-system.st`: residence, a GCC
employer and a regional business school are not evidence; a GCC nationality needs positive evidence;
name alone is low confidence; conflicting signals are Unknown), with the career sent oldest first. Its
reading — category, confidence, evidence for and against, rule — is kept whole (V83
`ai_nationality_reading`); only a `high` one fills the field and flags it, a `medium` or `low` one is
a suggestion the drawer's Background section offers to accept in one click, and Unknown leaves the
field null and the drawer says the AI could not tell. `NationalityEval` (`@Tag("eval")`, excluded from
`./mvnw test`) scores both prompts against a golden set that never enters the repository — real
profiles, exported by `ops/eval/export-golden.sh` — and appends counts to `docs/eval/nationality-eval.md`. **The model never sees a
candidate's contacts, compensation, note or custom fields**: its input is the `CandidateDossier`
allowlist. The assessment ranks a person and the nationality reading reasons about their origin, so both are
staff-only — one read (`GET …/ai-assessment`, `WORK_EXECUTE`) and never on `CandidateResponse`, which a
client seat reads; a filled seniority, like gender, is a mapping fact and is. **Nationality is counted
in eleven groups** — the Gulf six by name, and everyone else as Western expat (all of Europe, the east
included), South Asian, Asian (East and South-East), Arab expat, non-GCC, or Other expat (any country
no other group claims): the drawer offers exactly those eleven and stores the label, while a spreadsheet's "Egyptian" is folded into its group by `report` at read
time and never rewritten. **A notice period is one of five** — None, 1, 2, 3 or 6 months — on both halves
of a mandate: the brief keeps the months as its own `noticeValue`/`noticeUnit` pair and an executive keeps
the option's label, neither column narrowed to them, so a brief already stating ninety days and a row
imported as "negotiable" stay offered as recorded rather than being cleared. An import that cannot fold a
cell onto one of the five (`RowValues.noticePeriod`) writes nothing rather than rounding it, and the new
executive's currency arrives from the brief (`GET …/position/compensation`, which unlike the brief's own
read drafts nothing). The two **cross-mandate benchmarks** the chapters
name but cannot yet derive say so on the page rather than leaving a hole: marked not built, with no
fabricated progress count. The mockup's relevance mix is not drawn at all — nothing records how a
company was reached (V30 dropped `app_lm_strategy_sector.kind`), and an illustrative bar on a
client report was judged worse than none. The
market chapter's hubs carry a point from `geocoding` — asked only for the handful of cities it names
— so it draws a small map beside the bars where a Mapbox token is configured, and the bars alone
where none is. Under Recent momentum the progress chapter carries **Researcher performance** (the
`reports` page's handoff mock), the one staff-only part of the report: its own read,
`GET /projects/{id}/report/team?from=&to=`, gated `WORK_EXECUTE`, so a client seat never sees the firm's
people ranked. Every executive counts for whoever filed it (`added_by`, read through
`CandidateService.addedByOf`, never put on `CandidateResponse`, which a client seat also reads) and a
company for whoever filed its first executive. The mock's confidence score, conversion funnel and
per-company target have no row behind them, so they are not drawn: the drawers show a status *mix*,
and quality is what is on file (a contact, a verified one, a base salary). A projects-list row opens
the **position side panel** (`Workspace.dc.html`'s Position drawer): mapping progress as universe
companies with an executive mapped (`mappedCompanies` of `companies` on `GET /projects`), key
metrics, stage gates, the team and hiring managers, and **recent activity** — `GET
/projects/{id}/activity`, a cursor-paged, allowlisted read of the audit trail, `WORK_EXECUTE` so a
client seat never sees it, phrased and merged into lines by `lib/activity.ts`. The **Position**
screen is the mandate's brief, drawn in `claude-design/Position.dc.html` (issue #442) in the UNCAVA
palette like every screen — as five steps behind a rail (Role Brief,
Reporting, Compensation, Assessment Criteria, Review & Publish), the step kept in the URL (`?step=`)
and each section autosaving through its own write. It opens drafted rather than
blank: a **role-template library** of seventeen briefs (twelve C-suite, four functional heads, one
generic fallback) lives in the database, matched against the mandate's role title at creation. The
Role Brief's **Role title is a combobox**: free text — a mandate is titled "Group CFO – Energy Division" as
often as it is titled "Chief Financial Officer" — that type-aheads the seventeen titles, and picking
one takes that title and redrafts the brief from its template (`GET /position-templates` +
`POST .../position/template`). Templates are edited in Settings (V57/V58; **Settings → Templates** for a
firm's admin, **Settings → Template library** for a super admin): a LightMove **super admin** — a *platform* role granted only by
`ops/cloudsql/grant-platform-role.sh`, reading no tenant's data — edits the shared library, and a
workspace admin customises, hides, adds, exports and imports the firm's own. A firm's copy **shadows**
the library template of the same `code`, so a library edit reaches every firm that never customised
it; neither ever touches a brief already drafted. The file format is JSON with a published schema, so
a template can be written outside the app, AI included, and previewed before anything is written.
The Role Brief's location is two halves (V66: `location_city` and `location_country`, the country
settled by the same catalog every other country box reads), its target start is the project's own
date written through `PATCH /projects/{id}`, and its notice period is the reporting section's —
one screen over four writes. Reporting is an editable React Flow org chart — add, rename, re-parent
and drag any seat; only the role's own seat is fixed — and its team size is the seat's children,
counted rather than typed. Compensation states a bonus as a share of base or a **fixed amount**
(`BonusBasis.FIXED_AMOUNT`, V66 widened `bonus_value` to hold money), and the assessment carries a
**technical share** (V66 `technical_share`; the behavioural panel takes the rest) beside its two
weighted panels. The Role Brief attaches the position description and keeps it with the mandate.
Attaching it **reads it silently** (epic #393): the four `…/position/document/extract/*` routes —
compensation is never read, most descriptions state no figure — fan out into `lib/documentFill.ts`'s
`fillBrief`, which folds every scalar and repeatable list into the brief field-by-field, source-aware
(`TEMPLATE | DOCUMENT | MANUAL`, V67): a `DOCUMENT` value is replaced by a fresh reading, a `MANUAL`
one never is. There is no review-then-accept panel — the old wizard's went with it — a filled field
wears a small sparkle (`ProvenanceMarker`) instead, whose popover carries the snippet and an Undo.
A reading that worked says nothing more than a toast; only one that went wrong leaves a line
(`DocumentReadNotice`) — an unreadable file, a section that failed, a reader that could not be
reached. **Extract with AI** on the file card reads again; the Reporting and Assessment
steps carry **Read from document** in their own header, Compensation none. The reporting reading
also offers the matched template's usual direct reports as **Suggested seats** under the chart.
When the document reads as a template's role, that template is **applied automatically — but only
to a brief nobody has typed into** (`isUntouched`), because applying one replaces responsibilities,
the org chart, competencies and benefits wholesale, typed rows included; on an edited brief the
suggestion is dropped. The same reading is then folded over the redrafted brief (no second read),
and the title is the document's when it states one, never the template's. **A document's role title
always replaces the brief's** — typed or not, it renames the mandate; it carries no provenance (no
`fieldSources` key), so it takes no sparkle and no Undo.
Everything a reading leaves behind — confidence, snippet, Undo — is the tab's, never
the database's: `lib/receiptStore.ts` keeps it in `sessionStorage` stamped with the attached
document's name, so a reload reads back the same popover instead of a sparkle with nothing behind it,
and a closed tab takes the quoted lines of a client's description with it. Only `source` outlives the
tab. The sparkle's popover is **portalled** — it opens inside a table that scrolls sideways and inside
the React Flow canvas, both of which clip an `absolute` panel, and the canvas's `transform` defeats
`position: fixed` too.
Publishing stays ungated: the review's
checklist reports, it does not gate. A published brief then **reads back** rather than locking —
the rail offers **Edit position** in place of Publish and no draft to save, and the review's sections
drop their "Edit section" link. **Publishing the changes is the way back out**, closing the review
up again. Opening any step but the review is the same statement as pressing Edit position, because
those screens are live fields; nothing is frozen server-side (V38). Every step's foot walks the
brief — the step before on one side, the step after on the other — and the review, having no step
after it, offers **Move to Strategy** there once published, the mandate's market being what is left
to do. Nothing in that row is filled: the brief's two acts are the rail's, on every step, and the
mockup's third copy of the pair at the review's top right is deliberately not drawn. **The product is Uncava**: the mark is the rhombus over an isometric cube
in `apps/web/public/brand` (`favicon.svg`, the SPA's `AppIcon`, the extension's `BrandMark` and icons,
and the email's `uncava-mark-email-v1.png` are drawn from that one geometry) and every user-facing
string says Uncava — the mockups included — while the code, packages, persisted keys and JWT issuer
keep the `lightmove` name — a deliberate split, not drift. The same split holds for the domain
vocabulary: where a mockup says **Position** and **Business unit** (and **Hiring manager** for a
client representative), the screen says so, while the code, routes, API and tables keep
`project` and `client`; a screen whose mockup still says project or client keeps saying it. Business
unit and Hiring manager are an in-house workspace's words: an agency (V84 `mode`) says **Client** and
**Client contact**, and every such label comes from `useWorkspaceVocabulary`
(`features/workspace/lib/vocabulary.ts`), never a literal. It is served at `https://beta.uncava.com` (Cloud Run domain mapping,
Cloudflare DNS with the proxy off; README, "Custom domain"), and a link to it pasted into a chat app
draws a card from the Open Graph tags in `apps/web/index.html` over `public/og-image-v2.png` — static,
because no crawler runs the bundle (README, "Link previews"). Publishing stamps who
called the brief ready and **freezes nothing** (V38 retired the lock deliberately). Don't build ahead of
the mockups: if a screen isn't being built this session, its tables and entities don't exist yet.

## Layout

| Path | What |
|---|---|
| `apps/api` | Spring Boot 4.1 (Java 21, Maven). Features: `core`, `common`, `workspace`, `project`, `position`, `positiontemplate`, `strategy`, `triagecompany`, `candidate`, `enrichment` (with `sourcing`, the Find executives run, and `peoplesearch`, Strategy's People mode), `customcolumn`, `dataimport`, `dataexport`, `geocoding`, `talentmap`, `report`, `assistant`, `outreach`, `pairing`, `publicapi` |
| `apps/web` | React 19 SPA (Vite 8, TypeScript, Tailwind v4) |
| `apps/extension` | LightMove Capture — the Chrome extension (Manifest V3, React 19, Vite 8). Its own workspace; shares no code with `apps/web`. |
| `claude-design/` | HTML mockups — **the source of truth for all UI**. Read the relevant `*.dc.html` before building a screen. |
| `ops/cloudsql/` | Database bootstrap and hardening scripts |

`position` is the mandate's **brief** — what the role is, why it exists, what it pays and what a
candidate is scored against. It owns `app_lm_position` and its owned lists, and it depends on `project`
because the mandate keeps two of the fields the screen shows: the role title, which step one edits, and
the one target date (V8), which the screen only displays — it is set on the project and nowhere else.
Nothing else depends on it: the one reverse edge that existed — `project`'s `ReportService` reading the
position repository for the report's salary band — went with the report.

`positiontemplate` is the **role-template library** a brief is drafted from — the shared library, each
firm's copies and own templates, the picker, and the JSON export/import. It is admin-curated reference
content, so it is its own feature rather than part of the brief: `position` reads it through
`PositionTemplateService` (`matching` when a mandate is created, `require` when a consultant picks one,
`suggestFor`/`matchingByTitle` when a read document proposes one) and `positiontemplate` never depends back. The vocabulary both speak (employment type, benefit
frequency, competency panel, …) lives in `common/constant` for exactly that reason.

`strategy` and `triagecompany` split one story in two, in the order a consultant works: **`strategy`
is the market side** — the saved filter, the saved searches, the reads over the Apollo universe, and
every band/facet/taxonomy the search is expressed in. A *strategy company* is a row of the market that
belongs to nobody. **`triagecompany` is the mapping side** — one project↔company row per decision,
carrying a triage stage (in universe / shortlisted / declined) and a write-time snapshot. A *triage
company* is a decision. Searching goes in `strategy` however company-shaped its name; `triagecompany`
holds only what a mandate *did* about a company. **`candidate` is the people side** — a workspace
`Person` per human, and one `Candidate` row per mandate that maps them, belonging to the *project* and
only optionally to one of its triaged companies, because a researcher meets people at companies the
universe does not carry. It depends on
`triagecompany` through three public methods — resolving the company an executive is mapped to,
filing a researched employer into the universe, and reading company rows' logos for the Candidates
page (`logoUrlsOf`) — and `triagecompany` never depends back.
**`customcolumn` is the columns a mandate added to its own grid** — definitions only, plus the one
method (`applyTo`) that decides what a row may store in them, since the bag is open and nothing else
stands between it and arbitrary caller-chosen keys. `triagecompany` and `candidate` depend on it; it
depends on neither and knows nothing about companies or people. **`dataimport` is the spreadsheet** —
read the file, work out what its columns mean, and write what it carries through the doors that
already exist. It depends on those three and none of them depends back. **`dataexport` is the same
three doors outward** — one stage of the Companies grid, composed and written as a CSV, reading
through the seams `talentmap` already uses; it depends on the same three and on nothing else. Details
in `java-spring-development`.

`outreach` is **email from a consultant's own mailbox** (epic #620). So far it connects one: Nylas's
hosted sign-in behind `MailboxGateway` (`lightmove.outreach.nylas.*`; blank leaves it unoffered), a
grant id per person per workspace (V99). The callback is the one public
`/api/v1` GET a navigation reaches: its single-use state counts only beside the `lm_mailbox_connect`
cookie the starting browser holds, so a consent link handed to someone else connects nothing. The
connect popup lands in the SPA and, like the sign-in popup, must never restore the session there
(`isReturningMailboxPopup`). A send is never retried — a second approach to an executive is worse than
a failure.
**In-house gateways (epic #642, V106)** replace Nylas behind the same seam, one PR at a time on
`feature/inhouse-mail-gateways`; Nylas stays the active gateway until rollout (#650). Each provider (Google,
Microsoft, Zoom) is connected through an OAuth app chosen per workspace in **Settings → Integrations**
(`WORKSPACE_MANAGE`, audited as an `integrations` section): Uncava's **shared** app
(`lightmove.outreach.providers.*`; blank leaves it unoffered) or the workspace's **own**, its keys pasted by
its admin. `ProviderCredentialsResolver` is the one read: no row is shared. An own app's client secret is
**write-only** — stored only as `core/crypto` ciphertext, never returned by any read, and discarded when the
workspace returns to the shared app. This is the epic's deliberate reversal of "never the provider's tokens":
what the app now holds is held **encrypted** — Tink envelope encryption (a data key per value, wrapped by a
key-encryption keyset from Secret Manager, `lightmove.crypto.keyset`, not Cloud KMS: a KMS round trip would sit
in front of every send), bound to its workspace and purpose as associated data so a value copied elsewhere does
not decrypt. A deployment without a keyset boots and refuses own keys (`INTEGRATION_ENCRYPTION_UNAVAILABLE`);
`npm run dev` and the tests share one dev-only keyset. The workspace also chooses how calendar events are read,
`calendar_sync` (`RECALL | DIRECT`, `PUT /workspace/calendar-sync`, audited like `mode`): on Recall the app's
keys and each consultant's calendar refresh token are handed to Recall.ai (one platform account,
`lightmove.recall.*`), and the page says so before an admin enters their own app's keys.
**Routing (V107, #644)**: everything injects `RoutingMailboxGateway`, which answers each connection through
the gateway that made it — read off the grant id, since ours are minted `direct:<provider>:<uuid>`
(`MailboxGrants`), so a revoke after the row is gone still lands — and connects a new one through
`lightmove.outreach.gateway` (`nylas`, the default, or `direct`, our own `DirectMailboxGateway` wherever one
covers the provider, Nylas the rest); a direct grant whose gateway the deployment lacks is refused, never
handed to Nylas. A direct connection keeps the provider's **refresh token, encrypted** (bound to workspace
and consultant), never logged or returned; `MailboxTokens` turns it into an access token through the
workspace's resolved app, held in memory until two minutes before it expires, keeps a rotated one, and
treats a refusal (`invalid_grant`, `interaction_required`) as Nylas's `grant.expired` — `ERROR`, and the
`CREDENTIALS` failure every sender already reads as "reconnect". A refused **app** (`invalid_client`,
`unauthorized_client`, any 401 — an own app's secret expired) is `ProviderAppUnavailable` instead: the
mailbox stays `ACTIVE` and a send waits an hour rather than stopping the run, because one broken workspace
app must not take every consultant's mailbox down. On `RECALL` sync each direct mailbox has
one Recall calendar (`RecallCalendars`): made after the connect commits, handed the new token on a
reconnect to the same mailbox — a reconnect at another host or address gets a new one, since a calendar
keeps the platform it was made for — deleted on a disconnect, a refused refresh or a switch to `DIRECT`
(made again on a switch back); a failed create never fails the connect — the reply poll makes it, on
V103's backoff, giving up after five until the mailbox reconnects. Recall's webhook
(`/api/v1/outreach/webhooks/recall`, public, its Svix signature under `lightmove.recall.webhook-secret` the
credential; blank refuses every delivery) reporting a calendar `disconnected` marks the mailbox `ERROR`, and its
`calendar.sync_events` is a read of the events changed since (`GET /api/v2/calendar-events/`, only the `cursor` of
its `next` link taken) fed to `MeetingSync` as a direct read would be — a deleted, cancelled or recurring one removed.
**Microsoft (#645)** is `MicrosoftMailboxGateway`, over Graph: it is offered to a workspace with a Microsoft app,
shared (`/organizations`) or its own (its tenant), and Nylas answers for one without. It asks
`offline_access User.Read Mail.ReadWrite Mail.Send Calendars.ReadWrite` — `Mail.ReadWrite` because every email is a
**draft then a send** (a follow-up a `createReply` on the last message, addressed to the executive), the only way
Graph answers with the message and conversation ids threading and the reply poll key on, requested immutable so they
survive the move to Sent Items; `sendMail` answers nothing. A reply drafted on our own message would go back to the
consultant, so its recipients are set to the executive alone before the send, and a send Graph definitely refuses
deletes its draft — a finished approach left in Drafts is one click from the second send "never retried" forbids
(a timeout leaves it: it may have gone). The poll reads `from`, `receivedDateTime`, `isDraft` and the folder, and
drops drafts and Sent Items, so the consultant's own mail never reads as a reply whatever address it went from.
Offered to a workspace only with an app (`isOfferedTo`, which the mailbox screen's providers read), and to the
deployment only where some app exists. No webhook yet, and no per-app revoke (the stored token goes with the row).
Microsoft's admin-consent return lands on Settings →
Integrations, which records it (`POST /workspace/integrations/MICROSOFT/admin-consent`, `WORKSPACE_MANAGE`,
audited) only with the `state` our link carried — an HMAC of the workspace under the shared app's secret, so a
crafted return link records nothing — and the card says "Approved for your organisation"; it gates nothing.
**Google (#646)** is `GoogleMailboxGateway`, over the Gmail API, offered the same way (Uncava's app, or a firm's
Internal one). Its consent asks `access_type=offline` with `prompt=consent` — Google sends a refresh token only on
a consent it showed — for `gmail.send`, `gmail.metadata`, `calendar.events` and `calendar.freebusy`. A send is one raw
RFC 2822 message (`RawEmail`: UTF-8 HTML, an RFC 2047 subject where it is not ASCII, a line break in any header
refused); a follow-up names the thread and carries the last message's own `Message-ID` in `In-Reply-To` and
`References`, read with `format=metadata`, so it threads in the executive's client whatever it is. The poll reads
`From` headers alone and drops `SENT` and `DRAFT` by label. Google revokes by the refresh token, so
`MailboxService` decrypts it before a disconnect or a reconnect lets the row go (`ReleasedGrant`, only where the
gateway `revokesByRefreshToken`) and hands it to `revoke`. A reconnect of the **same mailbox** drops the old token
without revoking it: Google's revoke withdraws the account's whole grant to the app, the token just issued included. Bounces: a mail daemon's `From` (`mailer-daemon`, `postmaster`, and Exchange
Online's fixed `MicrosoftExchange329e71ec88ae4615bbc36ab6ce41109e` system mailbox) is a bounce, never a reply.
**Calendar on both (#647)** is read and written directly, whatever `calendar_sync` says: `events.list` on `primary`
(`singleEvents=true`) and Graph's `calendarView`, each asked only for the fields a meeting row keeps — never a
description or a body — with Graph's times asked in UTC and only the paging of its `@odata.nextLink` taken, never the
link itself, and a read stopped by the page cap is logged; free/busy is `freeBusy.query` and `getSchedule`, where an
error entry is a failure as on Nylas, and Graph offers only `free` and `workingElsewhere` — `unknown` is taken. A
booked call is `events.insert` with `sendUpdates=all` and a Meet `createRequest`, or `POST /me/events` with
`teamsForBusiness` — asked only where the calendar's `allowedOnlineMeetingProviders` lists it, so in an organisation
without Teams the invite goes without a link rather than being refused, and Book a call says so. Book a call offers
only the link the connected calendar can make (Meet on Google, Teams on Microsoft). A meeting is keyed on Google's
event id and on Outlook's `iCalUId` — Recall's copy of an Outlook event carries the ordinary Graph id, never the
immutable one our reads ask for — so a meeting read, booked or pushed by Recall is one row (`RecallEventReading`).
The connect's 90-day read stays direct on both syncs, since Recall's first sync lags the connect. A direct calendar
nothing pushes from (`DIRECT`, or no Recall calendar) is read again when the drawer opens: off the request thread,
at most every five minutes, a week back and 90 days on, and a meeting that read no longer finds in its window goes
(`MeetingBackfill.refreshUnpushed`), so a move or delete shows on the next opening.
**Zoom (#648, V109)** is a consultant's own account, apart from the mailbox: `app_lm_zoom_connection` per person
per workspace, Zoom's refresh token sealed as a direct mailbox's is (`ZoomConnection.refreshTokenContext`), Zoom's
user id beside it so a reconnect elsewhere revokes the account it replaced. Connected through the workspace's Zoom
app (`ProviderCredentialsResolver`, shared or its own; the app's scopes, `user:read:token` included for #651's
on-behalf-of token, are set on the app, never asked on the consent screen) by the mailbox's popup flow — an
`app_lm_mailbox_authorization` row with provider `zoom`, the `lm_zoom_connect` cookie, the public
`/api/v1/outreach/zoom/callback` landing on the SPA's one popup page; neither flow redeems the other's state.
`ZoomTokens` holds access tokens in memory and keeps Zoom's rotated refresh token; a refusal marks the connection
`ERROR` (`ZOOM_RECONNECT_NEEDED`, "Reconnect Zoom" in the drawer's Meetings section and on the Outreach page).
`MeetingVideo.ZOOM` is offered in Book a call only while the consultant's Zoom is usable (`zoomOffered` on the
slots): the meeting is made first (`POST /v2/users/me/meetings`, never retried), its `join_url` goes into the
invite's location and description through whichever calendar sends it, and an invite that fails deletes the meeting
so no orphan link is left. A read-back event's Zoom link is found in its location (`ZoomLinks`), on every gateway.
**Moving off Nylas (#650)**: a Nylas mailbox keeps working until its consultant reconnects once; where a reconnect
would now go through our own gateway (`movesOffNylas` on the mailbox read) the Outreach page offers "Reconnect to move
off Nylas", counting the runs it stops. A run keeps the gateway that made its thread (V110 `thread_gateway`, set by its
first send): whichever way the sender came to another gateway — a reconnect, or a disconnect and a fresh connect — the
run stops at its next send as `MAILBOX_MOVED` and the reply poll leaves its thread alone, since the new gateway may not
read it. **A direct mailbox's booking link opens Uncava's own page** (`DirectBookingPage`, the same slug kept across
the move): `GET /api/v1/outreach/booking/{slug}` answers `kind: DIRECT`, `…/{slug}/slots` the consultant's free
half-hours read as Book a call reads them, and `POST …/{slug}` (an email address alone, nobody signed in — nothing a caller types reaches the invite but where it goes) asks the calendar
once more, invites whoever picked the time with the calendar's own video link, and hands the booking to
`LinkBookings` — so it counts only for an address that consultant emailed. All three are public and rate-limited per
IP and link (`booking-page-bookings-per-hour`, 5, for the write). A Nylas mailbox's link still opens Nylas's
scheduler; `BOOKING_LINK_UNAVAILABLE` is no longer written.
**An own app's secret expiry (#650, V111)**: `IntegrationSecretExpiryWarnings` runs daily
(`lightmove.outreach.secret-expiry-check`, a UTC cron) and emails whoever holds `WORKSPACE_MANAGE` 30 and 7 days
before an own app's `secret_expires_on` and on the day it lapses — each threshold once per expiry date
(`secret_expiry_warned_days`, claimed by a conditional update committed before any email goes, so of two instances
only one sends, and cleared when the date changes or the workspace returns to the shared app) — and the
provider's card in Settings → Integrations says the same from 30 days out.
**Registering the shared apps (#649)** is `docs/integrations/registration.md` — every console value, the Secret
Manager names and the `deploy.yml` switches (`GOOGLE_MAIL_ENABLED`, `MICROSOFT_MAIL_ENABLED`, `ZOOM_ENABLED`,
`RECALL_ENABLED`, each off until its secrets exist) — and the five admin guides beside it are what the
`*_GUIDE_URL` settings link Settings → Integrations to once published.
Sequences (V100, #623) are a position's, `WORK_EXECUTE`: up to three emails (V39's owned list), and
**Add to sequence** — from In universe / Shortlisted (the ticked companies' executives) or the executive
drawer — chooses, reviews and starts. Choose shows who is skipped and why (no email, do not contact, out
of the running, already in a live sequence); the openers and Start decide the same rules again
(`RecipientEligibility`), so nobody skipped is sent to the model, charged for or enrolled. Each person's first email is rendered and **frozen on their enrollment** with their own opener,
so an edit reaches nobody else; Start creates `SCHEDULED` rows and sends nothing. The
`{{opener}}` is one Gemini call per person (`OutreachOpenerDrafter`) over the `CandidateDossier` and an
`OpenerBrief` — role title, level, the client's industry and the brief's location, **never the hiring
company's name** — one `LlmBudget.OUTREACH_DRAFT` unit per press. `outreach` reads people and writes
their `OUTREACH_ENROLLED` line through `candidate`'s `CandidateOutreachService`, and nothing depends back.
**Sending (V101, #624)** is `OutreachDispatcher`, the codebase's first `@Scheduled` job (`SchedulingConfig`,
off in tests, which call `dispatchAt(instant)`): every minute it claims due rows in one
`UPDATE … FOR UPDATE SKIP LOCKED` that commits `sending_since` before the mail service is called, so
instances never share a row, and a claim nobody released is stopped `SEND_UNCERTAIN`, never resent. Each
send re-checks what Start checked (do not contact, still mapped, still in the running, address still on the
ledger, mailbox active) and stops the run with that reason rather than hooking `candidate`; it waits for
the sender's weekday 08:00–18:00 in their mailbox's `time_zone` and under its daily cap. Step 1 is the
frozen email; a follow-up is rendered from the sequence as it stands and replies to the last message, which
threads it. The first send moves Identified to Contacted, forward only. A reply or bounce arrives on the
public `/api/v1/outreach/webhooks/mailbox`, whose HMAC signature is its credential
(`lightmove.outreach.nylas.webhook-secret`; blank refuses every delivery), with a 15-minute poll of each
listening thread as the fallback; only who wrote into a thread is read, never what. A mail daemon's message
is a bounce. Every email is an `app_lm_outreach_message` row (staff-only); every end is an
`EMAIL_REPLIED` / `OUTREACH_STOPPED` line and an audit event (`OutreachOutcomes`). The Outreach page reads
`…/outreach/people` (counts and runs, filtered in the SPA), the drawer's Outreach fold
`…/outreach/candidates/{id}`, and Stop is `…/enrollments/{id}/stop`, all `WORK_EXECUTE`.
**Meetings (V102, #628)** read the same grant's calendar: an event is kept (`app_lm_person_meeting`, one row
per matching person) only when an attendee's address is on a person's ledger — nothing else of anyone's
calendar is stored — through the `event.*` webhook (`MeetingSync`) and a 90-day read either side of today
when a mailbox is connected (`MeetingBackfill`, retried by the reply poll while `calendar_synced_at` is
null — V103 backs each failed read off from 15 minutes to a day, ten calendars a poll, and gives up after
five until a reconnect). A recurring series is never kept: Nylas keys it by its master in a webhook and by
each occurrence in a read. A free/busy answer with an error entry is a failure, never free time. The drawer's Meetings section reads `…/candidates/{id}/meetings`; **Book a call** offers the
consultant's free half-hours in their own window (`FreeSlots`, `…/meetings/slots`) and `POST …/meetings`
re-checks do not contact, the ledger and the slot, creates the event (never retried), then writes
`MEETING_BOOKED`, moves Identified or Contacted to Engaged (forward only) and ends a live run as `BOOKED`.
All `WORK_EXECUTE`. **The booking link (V104)** is `{{bookingLink}}`, offered only where the Nylas plan
carries Scheduler (`lightmove.outreach.nylas.scheduler-enabled`; a sequence using it is refused otherwise,
`OUTREACH_BOOKING_LINK_UNAVAILABLE`): `<web.base-url>/book/<slug>`, the slug the consultant's name **plus
eight random characters** — the page books without a session, so the link itself is the secret — unique,
kept across a reconnect, and the one anchor `OutreachEmailBody` writes. A send never calls the mail service
for it — the slug is all an email needs — so `BookingPages.prepare` makes the Scheduler page at Start, and
again once a reconnect commits (`MailboxConnected`). The public `GET /api/v1/outreach/booking/{slug}` only
reads, rate-limited per IP and link, and answers 404 alike for anything that leads nowhere. The SPA's
`/book/:slug` is public and loads `@nylas/react`'s scheduler lazily (its own chunk). A `booking.created`
webhook names the page; `LinkBookings` counts it **only for an address that consultant emailed**
(`toAddress`, never merely the ledger — the booking form's address is typed by whoever holds the link),
keeps the meeting `booked_via_link`, ends their listening runs as `BOOKED` before the next step, and moves
the person forward to Engaged once per position — a booking after a reply included.

`publicapi` is **the public API** (epic #690, `docs/public-api.md`): read-only JSON for an ATS, a BI tool or a
script, under `/api/v1/public/**` and nowhere else, documented as OpenAPI 3.1 at `/api/v1/public/openapi.json`
with Swagger UI at `/api/v1/public/docs`. Its credential is an **API key** (V114 `app_lm_api_key`), made in
**Settings → API keys**: personal (`uncava_pat_`, any staff member's own) or workspace (`uncava_svc_`,
`WORKSPACE_MANAGE` only), with scopes and an expiry, stored as a SHA-256 hash and shown once. Keys ride their own
security chain (an opaque-token resource server, `ApiKeyIntrospector`) and open no other route, as a session's
token opens none of these. Every call re-reads the key and its owner: a personal key reads only positions its
owner holds `WORK_VIEW` on (`PublicApiAuthorizer`, `@RequirePublicScope`, `@RequirePublicProjectRead`) and dies
with their staff access; a workspace key reads its whole workspace. Each route reads through the service the
screens use and narrows to public DTOs: `contacts` and `compensation` only with their own scopes, a model's
unconfirmed guess sent as null, and no note, AI assessment, `added_by` or custom field ever. Every read is
audited (`PUBLIC_API_READ`), budgets are per key and per IP, spent before the database
(`lightmove.public-api.*`, per instance), and `PUBLIC_API_ENABLED=false` answers 404 for all of it. The
contract is held by `docs/public-api/openapi.json`, which `PublicApiContractTest` regenerates and diffs, so a
change to what integrations see is reviewed in its PR. `pairing` is the one place a stage's companies are
paired with their executives — the talent map, the export and the public universe read all go through
`StagePairingService`.

## Commands

```bash
npm run dev                  # docker postgres (:55433) + api (:8080) + web (:5173)
npm run dev:db:reset         # drop the local database; next boot re-runs every migration from V1
npm run dev:db:psql          # psql shell in the local container
npm run dev:db:apollo        # copy the Apollo company universe down from Cloud SQL into it
npm run dev:db:seed-report   # demo companies + executives for the Reports tab, local database only
npm run dev:cloud            # api + web against the SHARED Cloud SQL dev database
npm test                     # all three suites: api, web, extension
cd apps/api && ./mvnw test   # backend — needs Docker (Testcontainers)
cd apps/web && npx vitest    # frontend
cd apps/web && npm run build # the real frontend typecheck
cd e2e && PROFILE=e2e ./run-all.sh   # the end-to-end matrix — never without PROFILE=e2e
```

**The e2e matrix always runs `PROFILE=e2e`.** `stack/up.sh` still defaults to `local`, which is the
gitignored personal profile and does not raise `password-reset-requests-per-hour` — so a plain
`./run-all.sh` burns the production budget of 3/hour and fails six cases (N20.2-3, N30.1-4) that are
green on the profile CI uses. Those failures are the profile, never the code.

`npm run dev` needs Docker and nothing else — no gcloud, no `application-local.yml`. Its database is
yours alone, so a migration in your tree applies only to you. The one thing it cannot conjure is the
Apollo universe: `npm run dev:db:apollo` pulls the 100,631 rows down once (that step needs gcloud), and
from there `dev:db:reset` snapshots them out and back in rather than wiping them with everything else.

`npm run dev:cloud` hits the shared dev database and applies your migrations to everyone at boot. It
needs `cp apps/api/src/main/resources/application-local.yml{.example,}` with the DB password filled in,
and the Cloud SQL connector authenticates as you — `gcloud auth application-default login`. That file
is also where the OAuth client credentials live, so OAuth sign-in needs it on either path.

## Load the right skill before you start

Task-specific detail lives in `.claude/skills/`, not here. Load the matching skill **before** touching
its area — the invariants below are the summary; the skills hold the rationale and the traps.

| Skill | Load before |
|---|---|
| `lightmove-domain` | **any** auth / signup / OAuth / workspace / membership / invitation / RBAC / client-representative / verification work |
| `java-spring-development` | any backend code — architecture, conventions, Boot 4 notes, and the backend traps live there |
| `react` | any frontend code — real stack, conventions, and the frontend traps live there |
| `chrome-extension` | any work in `apps/extension`, on `/api/v1/auth/extension`, or on the SPA's `/extension/connect` |
| `db-ops` | migrations, grants, `harden.sql`, `ops/cloudsql` scripts, the Apollo company universe |
| `pr-cleanup` | addressing PR review feedback |
| `verify` | running the app end-to-end |

## Security invariants (one line each — full rationale in `lightmove-domain`)

- Identity is a **work email**; the domain signals the firm but is **not** unique — a domain does not own a workspace.
- **Membership is invitation-only**; the one second door is a staff member naming a client representative.
- A user may hold **several** active memberships (V81 dropped V1's partial unique index), but a session is in exactly **one** workspace — the `wsId` claim, chosen at sign-in from `last_workspace_id`, remembered per refresh-token family, and changed by `POST /auth/switch-workspace` (a 404 unless the caller is an active member of the target) or by a web refresh whose membership has ended (audited `MEMBERSHIP_ENDED`; `/me` and the extension never fall through). The signup wizard's `POST /onboarding/workspace` founds a *first* workspace only (`ALREADY_IN_WORKSPACE`); a further one is `POST /workspaces`, staff-gated.
- Verification gates the *proof of mailbox*, not the channel — an invite token or a password reset proves it too.
- **Tenant isolation:** every workspace-scoped query filters by `AuthPrincipal.requireWorkspaceId()`, never a request parameter.
- **Authorise by action, never by role** (`@PreAuthorize` + `@workspaceAuthorizer`/`@projectAuthorizer`); guard beans re-read the DB every check; the JWT `roles` claim is never trusted for a decision.
- Client access is **two tiers, two decisions**: registry (`CLIENT_RECORD_MANAGE`, ADMIN+MEMBER) vs mandate (`CLIENT_ACCESS_MANAGE`, LEAD only). Project content is seat-gated `WORK_VIEW`/`WORK_EXECUTE`, not `PROJECT_BROWSE`.
- **A platform role sits above every tenant and inside none**: `SUPER_ADMIN` gates `/api/v1/platform/**` (`@platformAuthorizer`) and nothing else, is granted by ops script only, and never rides in the JWT.
- **An identity provider is a yml block** — never branch on a provider name anywhere.
- **Tokens are never stored raw** (SHA-256); the refresh cookie rotates on every use; the access token lives in JS memory only.
- **The SPA and API are one origin**; every endpoint lives under `/api/v1`. Don't split hosts.
- **An API key reaches only `/api/v1/public/**`**, read-only, and never more than its scopes ∩ its owner's live permissions — re-read every call; a session token opens no public route.
- **Auth errors are deliberately vague** — one sentence, one timing, for every password-login failure.

## Database

Cloud SQL Postgres 16, instance `bright-gcc`, database `lightmove`. All tables prefixed **`app_lm_`**.
**Hibernate never touches the schema** — `ddl-auto: none`; hand-written Flyway SQL in
`apps/api/src/main/resources/db/migration/`. **Never edit an applied migration; add a new one.**
`app_lm_apollo_companies` is the **company universe** — 100,631 companies, ETL-owned and read-only
to the application, keyed on `apollo_account_id`. Anything that stores a company stores that id plus a
**write-time snapshot**, and never a foreign key: the pipeline reloads the table wholesale. A company
the market does not carry has no id to store, so `app_lm_project_triage_company.apollo_account_id` is
nullable and `source` records which door the row came through (V34).
`app_lm_companies` is the retired brightdata copy — nothing reads it, nothing refills it, and it is
left in place rather than dropped. A mandate's whole filter is one `jsonb` column on `app_lm_strategy`
(V30 explains why). `app_lm_project_candidate` (V36) is the people half: `project_id` is the mapping and
`triage_company_id` is nullable with **ON DELETE SET NULL** beside a snapshotted `company_name`, so
removing a company from a mandate unmaps its executives rather than deleting them. Since V91 it maps a
`person_id` — `app_lm_person`, tenant data scoped by `workspace_id`, holding the profile (career history
and languages one `profile` jsonb column for V30's reasons), background, package and research — once
per mandate (`app_lm_project_candidate_person_uk`). V91's backfill folded existing rows into one person
on a shared profile slug or email within a workspace, never two rows of one mandate, and wrote each row
an `ADDED_TO_POOL` or `MAPPED` line under its `added_by`. V91 is **expand-only**: the person's columns
are still on the mandate row, and `app_lm_candidate_contact` / `app_lm_candidate_photo` still exist —
nothing reads or writes them, so they are a frozen copy of every executive as V91 found them, kept as a
fallback until the final cleanup migration drops them once the CRM phases are built and deployed
(`docs/candidate-crm.md`, issue #606). V95 stores `app_lm_person.profile_slug` — written by `Person`
with every URL change, `LinkedInUrls.profileSlugOrNull`'s reading, percent-decoded as `URI.getPath()`
decodes, and re-derived on every save so a backfilled key that read differently heals — unique per
workspace, so `PersonMatcher` finds a profile by equality and two doors racing to found one answer
`PERSON_PROFILE_HELD`. A person V91 left sharing a profile with an older one keeps the URL and a null
slug until the merge tool folds them, and can still be edited (`ProfileClaim.SHARED`: the save goes
through and the person yields the key). V96 adds `app_lm_person_note` — the person's notes, staff-only,
`project_id` the optional context with its title snapshotted — copies every mandate's `note` into a
general note about that mandate with a `NOTE_ADDED` line, and widens the activity kinds with
`NOTE_ADDED`/`NOTE_EDITED`/`NOTE_REMOVED`; the row's `note` column joins V91's frozen copy for #606.
V97 seeds the workspace action `CANDIDATE_POOL_MANAGE` to ADMIN and MEMBER. V98 adds to `app_lm_person`
`owner_user_id` and `do_not_contact` with its reason, setter and time (a CHECK clears all three with it),
the workspace tag catalog `app_lm_workspace_candidate_tag` (unique per workspace on `lower(label)`,
`retired_at` rather than deletion, seeded per workspace — `CandidateTagService` seeds one created later on
first read) and `app_lm_person_tag (person_id, tag_id)`, and widens the activity kinds with `TAGGED`,
`UNTAGGED`, `OWNER_CHANGED`, `DO_NOT_CONTACT_SET` and `DO_NOT_CONTACT_CLEARED`. `app_lm_position` and its six owned-list
tables are the brief (V7, grown by V39): every list a step edits is a child table replaced wholesale by
its step's write, so the aggregate keeps one idiom rather than mixing rows and jsonb. V66 splits its
`location` into `location_city` + `location_country` (backfilled from the one line; a comma-less value
counts as a country only where a dedicated country column already holds that spelling), widens
`bonus_value` to `numeric(14, 2)` for a fixed-amount bonus, and adds `technical_share` — seeded at 50
and written explicitly from there (V40's idiom). V67 records where every field came from: a
`source` column (`TEMPLATE | DOCUMENT | MANUAL`, V34's CHECK idiom) on each of the six owned-list tables,
and one `field_sources` jsonb map on the brief for the ten scalars a reading can claim — the
compensation figures and the role title are deliberately absent, having nothing to claim. The
criterion's `from_brief` boolean folds into that same column. Backfill keys on `version`: a brief
nobody has saved is the template's, a saved one is somebody's, so nothing anybody typed is ever
read back as a template default.
`app_lm_position_org_node` is the org chart — a tree of seats with exactly one flagged
`mandate_seat`, so "reports to" is that seat's parent and "direct reports" are its children, both
derived rather than stored twice.
`app_lm_project_custom_column` (V45) is the columns a mandate added to its own grid, and V46 puts their
values in a `custom_fields` jsonb bag on both row tables: a column per tenant in real DDL would be
unmigratable and would need the runtime role to hold the `CREATE` privilege `harden.sql` revokes, so
the definitions are rows and the values are a document. `field_key` is slugged once and never
rewritten — every stored value points at it — while `label` is the header a user renames.
V47 adds `'CSV'` to the triage company's `source` CHECK, the spelling V36 had already reserved on the
candidate side.
V48 gives `app_lm_client` the two snapshot columns V15 left out — `hq_city` and `logo_url` — and
backfills them for existing Apollo-backed rows by their stored provenance id, so a client renders with
its own mark rather than an initials tile.
`app_lm_geocoded_place` (V49) is the geocoding cache: one row per distinct normalised city + country
pair ever asked for, with the point Mapbox gave it or null for a stored miss. **Deliberately not
tenant-scoped** — a centroid is not client data and carries no PII — and never written by Hibernate:
`GeocodedPlaceStore` upserts on `place_key` so two reads racing on one city land on one row. A row is
re-asked after `lightmove.mapbox.cache-ttl` unless the account holds Mapbox's permanent-geocoding
entitlement (`permanent-geocoding: true`), because their terms forbid storing a temporary result
indefinitely.
`app_lm_person_contact` (V91, V54's `app_lm_candidate_contact` keyed on the person) is every email and
phone known for an executive, one row each, with
`source` naming the door (`MANUAL` / `CSV` / `EXTENSION` / `CONTACTOUT`) and `kind` / `verified` only
ever what the provider said. V39's owned-list idiom: `Person` rewrites it from its own methods, and
its identity is `value_key` (lower-cased address, digits of a number) because providers spell one
number three ways. A miss is not a row — it is `emails_looked_up_at` / `phones_looked_up_at` on the
person with nothing from the provider beside it, so a lookup bought through one mandate answers every other. V54 moved the old `profile.contacts` jsonb into
the table and V55 dropped the row's `email` and `phone` columns: the ledger is the only store, the
importer matches a person on any address they hold, and the grid lists them all.
V61 is the industry catch-up, V50's shape for a second column: the universe publishes LinkedIn's
**legacy V1** vocabulary and Bright Data answers in **V2**, so `industry` held both until `Industries`
(`common/industry`, static for `Countries`' reason) began canonicalising in `CapturedCompanyDetails`'
compact constructor. `data/industry-map.json` is keyed on LinkedIn's industry id — V2 renamed V1's
industries in place at the same ids, which is what makes the map derivable rather than guessed — and
`ops/industry-map/build.py` rebuilds it (`--check` in CI would catch a hand edit). Its one trap is id
25: V2 gave it to the `Manufacturing` root where V1 had it as `Consumer Goods`.
`app_lm_industry` and `app_lm_industry_v2` (V62) are that file as data — 148 labels with their V2 name
and sector group, and 434 V2 industries each resolved to the universe label covering it, which is what
a V2 selection expands from. Emitted by the same script (`--sql`), **not** tenant-scoped for V49's
reason, and never read at runtime: the JSON stays the authority because `Industries` is static, so
`IndustryVocabularyIntegrationTest` asserts the table answers as the resolver does for all 434.
V63 puts `industry_v2_code`, `industry_v2_label` and `sector_group` beside `industry` on the triage and
off-limits rows, so the report can group without a query per company. All four come from one
`Industries.resolve` call through one method per table (`TriageCompany.fileUnder`,
`StrategyCompanyRef.of`, `TriageCompanyWriter.rowPlaceholders`) — **that single writer is the whole
guarantee they agree**, and a label nobody can resolve keeps itself and leaves the other three null.
`app_lm_vendor_company` (V64) is the vendor company cache — named well clear of `app_lm_companies`
above, because two live company tables one letter apart is a typo nobody catches. One row per
LinkedIn slug ever researched, holding what a provider said about that page — its own V2 industry leaf (the one place in the schema where
`industry_v2_*` is finer data rather than V1 renamed), the V1 label and sector group `Industries`
resolves it to, its specialties as lower-cased `keywords`, and the raw payload, which is what makes a
row re-mappable when the industry map improves instead of re-billed. `found = false` is a stored miss,
for V49's reason. **Vendor-sourced only, and that is a tenant boundary** — a hand-typed or spreadsheet
row is one firm's own research and stays in `app_lm_project_triage_company`; this table carries no
`workspace_id`, `project_id` or `added_by`, and who captured a company is in the audit trail.
`CompanyResearch` reads it before calling the vendor and writes it after, so
`TriageCompanyService.applyEnrichment` is unchanged: a cache hit fills the same `CapturedCompanyDetails`
a fresh call would. A row is re-asked after `lightmove.enrichment.company-cache-ttl`, and a LinkedIn
*search* URL never reaches the table — `LinkedInUrls.companySlugOrNull` answers null and there is
nothing to key it on.
V56 adds `app_lm_project_candidate.gender` (`FEMALE | MALE | OTHER`), nullable with no default:
NULL is "nobody recorded it" and is deliberately not a fourth value, because "not recorded" and
"recorded as other" are different facts and the report counts them apart.
V78 adds `app_lm_project_candidate.ai_inferred_fields` jsonb — the keys (`nationality`, `gender`,
`yearsExperience`, and since #563 `seniority`) holding a model's proposal that no researcher has
changed since.
V79 adds `app_lm_project_candidate.ai_assessment` jsonb — the AI enrichment's summary, per-panel
score with positives and negatives, and source links; the model's own reading, replaced whole per run.
V80 adds `ai_enrich_failed_at` — the last AI enrichment run that produced nothing, so the drawer says
so at once; a later success clears it. Saving the drawer's Background section (`confirmBackground`)
confirms its AI values and clears `ai_inferred_fields`.
V83 adds `app_lm_project_candidate.ai_nationality_reading` jsonb — the nationality classifier's last
reading (category or Unknown, confidence, evidence, rule), replaced whole per run and staff-only.
`app_lm_vendor_person` and `app_lm_vendor_people_search` (V87) are the people cache, V64's shape and
tenant rule for people: every record a billed search or lookup returned, keyed on the profile slug,
and which slugs each normalised search returned. `CachedPeopleSearch` answers a repeated search from
them with no vendor call, and otherwise reads back the fitting people on file at that employer and
asks the vendor only for the rest with every person on file excluded (`linkedin_id not_in`, nested in
an `and` to stay under four rules a group) — a returned record is billed whether or not it was new.
`BrightDataProfileEnricher` reads the same table first, so a capture of someone a run bought is free.
Both age out after `lightmove.enrichment.people-cache-ttl` (30d), and every billed call purges what
has aged past it (V89 indexes `fetched_at` for that), so a third party's record is never held longer
than it may be read. The exclusion is the employer's
people on file, never a mandate's own roster: the answer is shared by every workspace.
`app_lm_executive_sourcing_run` (V86) is one Find executives run — the companies frozen at request
time, the spec the model proposed, and an outcome per company as each finishes — kept as a row so the
screen can poll it, read it back after a reload and be refused a second one while it runs. V86 also
adds `AI_SOURCED` to both source CHECKs (the contact ledger's only because `ContactSource.ofDoor` is
exhaustive; nothing writes it).
V88 holds a mandate to one run in progress (a partial unique index behind the service's own check), and
the next request fails a run still marked in progress past `run-deadline`, since its worker died with
the instance.
V93 is Strategy's People mode: `app_lm_strategy.people_filter` and `app_lm_strategy_search.kind` +
`people_filter` (a people search leaves the company `filter` empty rather than null), a nullable
`app_lm_vendor_people_search.company_slug` (a people-first page is asked of no company), an index over
the cached people's LinkedIn place line for the Location box, and `PEOPLE_SEARCH` on the candidate,
contact-ledger, person-pool and triage-company source CHECKs.
V94 adds `app_lm_vendor_person.source_record` — a provider's own record, untouched, where `raw` holds it
read into Bright Data's shape (ContactOut's); null for a Bright Data row. It ages out with the row.
V81 lets a person belong to several workspaces: it drops V1's `app_lm_workspace_member_single_org_per_user_uk`
(the `(workspace_id, user_id)` unique stays — one row per person per workspace whatever its status, so a
removed member who is re-invited **rejoins** that row rather than inserting) and records which workspace
a session is in on `app_lm_refresh_token.workspace_id` (a web refresh re-reads the membership there and,
once it has ended, falls through to another the user is still in, audited; V82 indexes both pointers), and where the next sign-in opens on `app_lm_user.last_workspace_id`
(written on every explicit choice — sign-in, switch, create, accept — never by a background refresh).
Both are backfilled before the index is dropped, while it still guarantees one row to copy from.
`WorkspaceSelection` is the one place that rule lives; `WorkspaceMemberRepository` deliberately has no
singular by-user lookup any more, because an `Optional` over two rows throws.
V100 adds `app_lm_outreach_sequence` + `…_step` and `app_lm_outreach_enrollment` (a partial unique
index holds one `SCHEDULED`/`ACTIVE` enrollment per person per position; `candidate_id` is SET NULL so
unmapping keeps the record) and widens the activity kinds with `OUTREACH_ENROLLED`.
V101 gives `app_lm_mailbox_connection` a `time_zone` (default `Asia/Dubai`), the enrollment its thread and
last message ids, `last_sent_at`, `replied_at`, `stopped_at`, a `stop_reason` CHECK and the dispatcher's
`sending_since` claim (with a partial index on due rows), adds `app_lm_outreach_message` (unique per
enrollment and step), and widens the activity kinds with `EMAIL_SENT`, `EMAIL_REPLIED` and `OUTREACH_STOPPED`.
V105 adds `app_lm_person_document` (category, title, `name_key` — the latest file's lower-cased name an
upload is matched on — and a `primary_cv` mark held to one per person by a partial unique index) and
`app_lm_person_document_version` (one row per file: sanitised name, the type its bytes were read as, size,
`sha256`, the bucket's `storage_key`; `person_id` beside `document_id` so a duplicate is one lookup), and
widens the activity kinds with `DOCUMENT_ADDED`, `DOCUMENT_VERSION_ADDED`, `DOCUMENT_REMOVED` and
`DOCUMENT_VERSION_REMOVED`. No bytes are in the database.
V106 adds `app_lm_workspace_mail_integration` — one row per workspace per provider (`GOOGLE | MICROSOFT |
ZOOM`), `mode` `SHARED | OWN`, and on `OWN` the client id, `client_secret_encrypted`, the Entra `tenant_id`
(Microsoft only, by CHECK) and the secret's own expiry; a `SHARED` row holds no key (CHECK), and no row means
shared — and `app_lm_workspace.calendar_sync` (`RECALL | DIRECT`, default `RECALL`).
V107 gives `app_lm_mailbox_connection` its `gateway` (`NYLAS | DIRECT`, existing rows `NYLAS`),
`refresh_token_encrypted` (set exactly on a `DIRECT` row, by CHECK), `recall_calendar_id` (indexed, for
Recall's webhook, which names nothing else) and V103's backoff pair for it, `recall_calendar_attempts` and
`recall_calendar_retry_at`.
V108 gives `app_lm_workspace_mail_integration` Microsoft's admin consent — `admin_consented_at`, the
`admin_consent_tenant_id` it was given for and who reported it — by CHECK on the Microsoft row alone.
V110 gives `app_lm_outreach_enrollment` its `thread_gateway` (`NYLAS | DIRECT`, backfilled from the sender's
mailbox, Nylas where it is gone) and adds `MAILBOX_MOVED` and `BOOKING_LINK_UNAVAILABLE` to its `stop_reason` CHECK.
V111 gives `app_lm_workspace_mail_integration` `secret_expiry_warned_days` — the fewest days left an expiry warning
was already sent for.
V114 adds `app_lm_api_key` — the public API's keys: kind (`PERSONAL` with an owner, `SERVICE` without, by CHECK),
`token_hash` (SHA-256, unique) and a `token_hint` a list can show, scopes as a jsonb array held to the five tokens
by CHECK, expiry, last use and revocation (`REVOKED | MEMBER_REMOVED | WORKSPACE_DELETED`).
V84 adds `app_lm_workspace.mode` (`AGENCY | COMPANY`, V34's CHECK idiom; every existing row `COMPANY`):
who a workspace hires for — client companies, or its own business units. Chosen at creation with **no
default** (`CreateWorkspaceRequest.mode` is required, the organisation step preselects nothing) and
switched by an admin through `PUT /workspace/mode` (`WORKSPACE_MANAGE`, audited as a `mode` section) —
the signup wizard's Back (`PATCH /onboarding/workspace`) goes through the same audited switch, so no
path changes the mode unrecorded.
It changes labels, what a client record shows and whose persona the assistant reads — **never what is
stored or who may do what**, which is why a switch migrates no row. It rides `WorkspaceSummary`, so a
pure client reads the same labels as staff. The phased plan is `docs/workspace-modes.md`.
V76 adds `app_lm_project_candidate.compensation_breakdown` jsonb — the drawer's allowance lines and
LTIP instruments. `allowances` stays the total every reader sums; `CandidateCompensation` keeps the
two agreeing (lines supply a missing total, a contradicting total drops them). The editor's
Annual/Monthly and %-of-base toggles are how a figure was typed, never stored.
V77 makes AED the default currency (`DefaultCurrency` / the SPA's `DEFAULT_CURRENCY`): new workspaces,
briefs and templates start in it, and it moved the shared library, never-saved briefs (`version = 0`)
and workspace defaults off USD — a saved brief and a firm's own template keep what somebody chose.
Revenue in the universe and the Strategy filter stays USD: that is what the data is in.
`app_lm_position_template` (V42) is the role-template library — the identity a picker lists as columns,
the drafted brief as one `jsonb` body (V30's idiom, not V39's child tables: a template is a
heterogeneous document read and written whole), and the match keywords as a child table because they
are the catalog's lookup key. `workspace_id` is nullable: NULL is the shared library, non-null is one
firm's own. V58 makes both editable: a firm's row sharing a library row's `code` is its copy and
shadows the library row in every read (`PositionTemplateRepository.findAllVisibleTo`), `customised_from`
against the library's `revised_at` says the library moved on since, and `app_lm_position_template_hidden`
takes a library template out of one firm's picker and title matching.
V68 gives `app_lm_workspace` the same write-time company snapshot: signup's organisation step picks
the firm from the universe (`GET /onboarding/companies`, since `/companies/search` needs a workspace)
and the server files it under the resolved row's name, id, industry, city, country, website, LinkedIn
and logo; a firm typed in by hand leaves them all null.
V73 gives `app_lm_project` the New position modal's decisions: `project_type` (`MAPPING | SEARCH`, V34's
CHECK idiom, existing rows `SEARCH`) and a timeline — `start_date`, `delivery_date` (when the business
unit expects the map or the shortlist) and, on a search only, `mapping_target_date`, which
`ProjectTimeline` defaults to 60% of the window when the modal sends none. `target_date` is untouched
and stays the brief's hire date; the list's Target and the derived health read `delivery_date` and
fall back to it (`Project.deadline()`, the SPA's `deadlineOf`).
V69 adds the workspace's `persona` jsonb — main business, sectors, competitors, geographies, notes —
for the assistant to tailor research to: seeded at signup with the picked company's industry, its
sector group and its country — and re-filed when Settings re-picks the firm, the old company's chips
giving way to the new one's (`HiringPersona.refiledFrom`, `common/persona`) — written by an admin through
`PUT /workspace/persona` (Settings → General), read by staff on `GET /workspace` and never carried
on `/me`. The assistant's empty chat offers a sector starter for each of its first two sectors.
V85 gives `app_lm_client` the same `persona` jsonb, for an **agency**: seeded from the picked company's
industry and country, edited in the agency client drawer through `PUT /clients/{id}/persona`
(`CLIENT_RECORD_MANAGE`, audited as a `persona` section) and never on `ProjectResponse`, which a client
seat reads. At an agency the assistant's prompt and starters read the **mandate's client** as the hiring
company (`HiringSideResolver` → `HiringContext`, the agency named in one line); in-house they read the
firm, as before. That drawer is the company panel Strategy opens — `CompanyDrawerHeader` and
`CompanyFactsSections` from the universe (`GET /companies/{apolloAccountId}`, the id on the client
detail), or editable basics for a client typed in by hand — never the business-unit record.
V57 adds the `PLATFORM` role scope and `app_lm_user_platform_role` — written by
`grant-platform-role.sh`, never by the application.
`app_lm_position_document` holds the attached position description inline (`bytea`) — one small file per
mandate, read back only by its own download endpoint. Everything else (roles, hardening, grants) →
`db-ops` skill.

## Conventions (the short form)

- Names carry intent; every type name must read standalone.
- **Comments are the exception, not the habit.** Default to none: a well-named function needs no
  preamble, and a paragraph justifying an ordinary decision is noise a reader has to wade through.
  Write one only where the logic is genuinely hard to follow, or where the *why* is invisible from
  the code — a trap, a security boundary, a non-obvious ordering. Never restate what the line does,
  never narrate alternatives that were not taken, and keep a class doc to a line or two.
  The exception that stays: **inline comments documenting shipped bugs are load-bearing, never strip
  them** — they are why the bug has not come back.
- Errors: RFC 9457 via `GlobalExceptionHandler`; the frontend switches on `code`, never `detail`.
- Java/Lombok/architecture detail → `java-spring-development` skill. React detail → `react` skill.

Review will be done by fable or codex
