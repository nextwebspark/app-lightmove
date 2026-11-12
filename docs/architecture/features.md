# Features — detail moved out of CLAUDE.md

Moved verbatim from CLAUDE.md (2026-11-11) so it loads only when read. Grep this file for a feature, class or V-number before touching it.

## Product and features

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
mandate triages what it took from it — **Shortlisted is switched off on screen for now** (`SHORTLIST_OFFERED` in
`triageStages.ts`: the web app redirects it; the API, the stored rows and the extension keep all three), and on screen
**Declined is a company's alone** (`DECLINE_OFFERED`): the company drawer declines a company with everyone mapped at it
(asking first, counted, when anyone is), no grid line and no People-mode filing declines a person, and the Declined page
offers nothing but Back to universe, which brings the company and everyone at it back. A company reaches those pages four ways — out of the market
(Strategy's per-row add, or the Companies screen's own picker over the same universe, both `POST
/triage` with an id the server resolves), typed in by hand, captured by the browser plugin
(`POST /triage/capture`), or **imported from a spreadsheet**. Strategy's rows also carry a tick box:
a selection raises a floating bar over the grid whose three buttons file every ticked company at one
stage in one request (`POST /triage/bulk`), so a mandate can decline forty companies without first
taking them into the universe.
**A company's notes are a feed (V143)**, the person's notes' shape for a company of one mandate: authored, timed,
typed, pinned, formatted and soft-deleted with Undo, and **staff-only** (`WORK_EXECUTE`, `CompanyNoteService`) —
the company drawer gains an Overview · Notes tab bar for staff, and the grid's Notes column is a "3 notes" chip
that opens it, its `noteCount` on the staff grid's read alone. A door's note (the add form, the plugin, a sheet's
Company note) is filed once as a plain note, an already-held company's included; V32's single `note` column is
frozen, and with it the leak that let a client seat read it through the grid, the talent map and the export.
Who filed a company (`added_by`) is the staff grid's **Added by** column, the drawer's header and the export's — never
a client seat's (`addedBy*`, null outside a staff read, as `noteCount` is); the cell adds the executive's adder's face
where someone else filed them, read from the candidates list's staff-only `addedBy` map beside the rows, never on
`CandidateResponse`.
Deleting one drops the project↔company row, its notes and everyone mapped at it on any stage (the persons stay in the
pool) — after a warning that counts them, which the caller sends back as `expectedExecutives`; the server locks the
company row (`FOR UPDATE`) and refuses when more are mapped than that (`TRIAGE_COMPANY_HAS_EXECUTIVES`), so nobody
goes unannounced; removing a company's last executive offers to take the company too (`?withCompany=true`), and
nobody is left behind with a name and no company. The Apollo universe is read-only to the app. On top of
that sits the **people half**: an executive mapped for a mandate, optionally against one of its triaged
companies, added by hand from the Companies grid — where a row is a *person at a company*, so a company
with three of them is three lines and one with none keeps its "Add executive" slot. The grid reads those lines whole —
`GET …/triage/lines` (`pairing`'s `StageLinesService`) pairs the stage and orders it as one list by the sorted column,
so Executive or Title sorts the people whatever company they sit at, text naturally (`NaturalTextOrder`: case aside,
numbers by value) — and its header opens the column's menu (sort, move, freeze, hide, and the Company, Executive and Status
filters; a mandate's own column edits there and deletes with an Undo). **Each executive holds a stage
of their own** (V150, #903): their company's when filed or remapped, then wherever their line's own Shortlist /
Decline / Back to universe sent them (`PUT …/candidates/{id}/triage-status`) — a line's buttons and trash act on its
person alone. A stage lists its own companies plus any company with someone listed there; the company's own move
(its drawer, or the line of a company with nobody on that stage) carries everyone at it (`TriageCompanyMoved`). An executive is also
captured by the plugin, through the same endpoint the drawer posts to (`source: "extension"`). The plugin's side panel files into
the position chosen at its top, asks first whether that position already holds the page (`GET …/candidates/held` by
profile slug, `GET …/triage/held` by company page then name; staff-only, 204 when not) and then opens that record —
status, tags and the position's notes, ready for the next — rather than a form; it writes its notes through the notes
routes in the app's own editor (never the door's plain `note`), and files a company into the universe only. Its Undo
says why when it leaves something: a person's takes the company filed for them (`?withFiledCompany=true`, V167) only
while it is still In universe with nobody else at it and no note on it, and a company's is refused once anyone is mapped there.
**Every executive is a workspace person first (V91, `docs/candidate-crm.md`)**: `app_lm_person` is the
human — profile, background, package, contact ledger, research — owned by the workspace, and a mandate's
`Candidate` row maps that person and keeps only the mandate's own status, custom-column values and
brief-specific AI assessment. Whichever door files someone, `PersonMatcher` first asks whether the
workspace already knows them — by LinkedIn profile slug (stored, V95), or by an email the ledger holds unless the two
name different profiles; never by phone (a switchboard is on everyone) and never by name alone — and a
match is **mapped, not duplicated**: what the new mandate brings only fills what nobody recorded, an
edit through one mandate is what every other reads, and removing someone from a mandate keeps the
person. A profile is one person's: an edit that would give a second person of the workspace the same
LinkedIn profile is refused (`PERSON_PROFILE_HELD`). The people controls below (the suppression list, erasure,
restriction and the candidate retention review) are offered only where `lightmove.data-protection.enabled`
(`DATA_PROTECTION_ENABLED`, off by default, read by the SPA from `GET /data-protection/config`; the four controllers are `@ConditionalOnProperty` on it)
is on: off, their routes answer 404 and no screen offers them, while the doors' checks stay. **A workspace's suppression list (V195, #920)** names the people no door may file again — by an HMAC of
their profile slug or email under `lightmove.crypto.suppression-keyset` (`SuppressionKey`, `KeyedHasher`), never the key
itself: the add form, the plugin and a spreadsheet are refused `PERSON_SUPPRESSED` (an edit only for a key it adds; a
known person by their own keys), while Find executives, People Search and Add to position leave them out, counted as
`suppressed` (`SuppressionGate`; an admin's `/workspace/suppressions`, `SuppressionListService`, `WORKSPACE_MANAGE`). A
workspace holding entries the keyset cannot read is refused `SUPPRESSION_UNAVAILABLE` at every door, never let through. **An admin erases a person
for good (#918, `PERSON_ERASE`)**: `POST /candidates/{personId}/erase` with the name typed back, after
`…/erase-preview` counts what goes; one transaction deletes the person (every cascade from `app_lm_person`) and, through
each `PersonErasureContributor` — declared in `candidate`, implemented where the rows live — refuses while an email is
mid-send, queues their Recall bots in `app_lm_recall_bot_erasure` (global, bot ids only) for `RecallBotErasureSweeper` to
call back and strip of media until Recall answers, forgets the vendor cache's copy of the profile and scrubs the name from the bell
and Find executives' runs; their files are queued in `app_lm_storage_deletion` (global, keys only) for
`StorageDeletionSweeper`, and their profile and addresses suppressed unless the admin unticks it. Audited by id and
counts, never the name. **An admin exports everything held on a person (#919, `PERSON_SUBJECT_ACCESS`, V209, GDPR
Art. 15 and 20)** from the same ⋯ (`GET /candidates/{personId}/subject-access-export`, `PersonSubjectAccessService`): one
zip of `person.json`, `person.html` (escaped, no script) and the latest file of each document, every row read whole
(`core/export`'s `RowSnapshotReader`, only machinery withheld) through each `SubjectAccessContributor` — declared in
`candidate`, implemented where the rows live — on the request thread, since the zip streams where row-level security
reads nothing; deleted notes until purged, V91's frozen copies, the ids named (`references`), and the fixed Art. 15(1)
answers with each AI use's logic text (`AiUse`). `SubjectAccessCoverageTest` holds every person table to
`docs/gdpr/subject-access-export.md` (exported or excluded with a reason) and `AiLogicCatalogTest` every prompt to an
`AiUse`; audited `PERSON_SUBJECT_ACCESS_EXPORTED` by id and counts. **Anyone on the team restricts processing of a person (#920, V198, GDPR Art. 18)** from the
Candidates drawer's ⋯, with a reason and optionally the suppression list as an objection (`PUT
/candidates/{personId}/processing-restriction`, `CANDIDATE_POOL_MANAGE`, `ProcessingRestrictionService`), lifted only
by whoever restricted them or a `WORKSPACE_MANAGE` holder (`PERSON_PROCESSING_RESTRICTION_NOT_YOURS`; the read's
`mayLift`); an objection added to someone already restricted is audited on its own: the record stays readable and
editable, and nothing uses it until it is lifted — `CandidateService.dossierOf`, every model run's one read, refuses
`PERSON_PROCESSING_RESTRICTED` (Suggest with AI, Assess with AI, the openers, the interview plan and copilot; the
automatic background run and the assistant's profile tool skip them); a contact lookup, an own email, Book a call and
a Notetaker are refused; Add to sequence skips them and a run or a waiting email stops at its next send
(`PROCESSING_RESTRICTED`); no door maps them onto a further position (Find executives and People Search count a skip,
Add to position counts them `restricted`); their waiting or recording bots are called off in the restriction's own
transaction and queued for `RecallBotErasureSweeper` (V197's table) to call back and strip until Recall answers, and a
transcript arriving later is never summarised. Client-facing reads (the grid, talent map, report, export) still show
them — that exclusion waits on counsel. **What is kept, and for how long, is `docs/gdpr/retention-schedule.md`** (#921): one row per
table holding a person, an account or an address, which `RetentionScheduleCoverageTest` holds the schema to.
`RetentionSweeper` (hourly, `lightmove.retention.*`, defaults awaiting counsel; off unless `RETENTION_ENABLED=true`) deletes expired refresh-token families,
verification links and two-factor challenges, the vendor caches past their TTL, closed invitations, read
notifications, sent digests, idle assistant chats and aged metered uses — the tenant tables only in the workspaces
V199's `app_lm_resolve_workspaces_with_retention_due` names, each in its own `WorkspaceScope`. **Candidate retention (#921, V200) never deletes on its own**: a
workspace's period (`app_lm_workspace_retention`, 12/24/36/60 months or never; no row is
`lightmove.retention.candidate-months`) makes due whoever is untouched that long — no timeline line, admin's keep
(`app_lm_person.retention_kept_at`) or founding since — on no live position and not restricted, and Settings → Data &
retention (`CandidateRetentionService`, `WORKSPACE_MANAGE`; deleting `PERSON_ERASE`) lists them for an admin to keep for
another period or delete through `PersonErasureService.eraseUnderRetention` — each in its own transaction, asked again
once their row is held, never suppressed, audited `reason: retention`; the same page carries the suppression list. A plugin capture of someone already researched spends no second vendor call, and is filed under the employer their latest position recorded (`PersonEmployerResolver.knownEmployerOf`, that position's company row's LinkedIn page and all). Every change to a
person — added, mapped, unmapped, status, profile, contacts, research, AI — is a line of
`app_lm_person_activity` naming who did it, on which mandate, and when, written in the same transaction
as the change (`PersonActivityRecorder`); the security audit trail is written as before, beside it.
**Notes are the person's (V96)**, never a field of the row: typed (note, call, meeting, email, LinkedIn, WhatsApp — V164 and V201, each network drawn with its own mark by `NoteKindMark`) — the Notes tabs write and list plain notes alone, a call, a LinkedIn or WhatsApp message or a meeting written up without the Notetaker is logged from Outreach (`NoteComposerModal`, of the kind its button named, with no switch to another) with when it happened — now unless changed, never in the
future — and an optional title (V185 `occurred_at`, `title`), a meeting's tied to the past Uncava meeting it was about
— its calendar meeting (`meeting_id`, bare; `PastMeetingLookup`, answered by `outreach`) or, once the calendar has let
the meeting go, its Notetaker recording (V188 `recording_id`; `PastRecordingLookup`, answered by `notetaker`, from that
recording's position alone) — or held outside Uncava, while a call is a phone call and names none; every past call's ⋯
offers Log meeting notes, a recorded one's included, so a researcher's own notes sit beside the Notetaker's; the
conversation and the timeline order them by when they happened. An edit keeps what it leaves
out and lets go of what the note's kind cannot carry (`LoggedNoteRules`), and the Candidates page's Notes tab lists
the logs in a group of their own, so one about a position the person has left is never lost. A recorded call's is the Notetaker's, and an email is never logged by hand: a new `email` note is refused (`VALIDATION_FAILED`), an old one keeps its kind through an edit, and a note sent with no kind is plain —, authored,
timed and **staff-only** — `CandidateResponse`, the talent map and the report carry none, because a client
seat reads all three (decision D1). A note is about one position or about the person: read from a position,
only that position's and the person's ("All positions") show, and another position's is not found
(`NoteViewpoint`) — not listed, not quoted on the timeline, not changed, pinned or linked to a task
there, never sent to that position's AI — while the Candidates page reads them all (D1, revised 2026-10-15).
The position drawer's composer offers This position or All positions, never another position. A note is
formatted (bold, italic, underline, strike, two heading sizes, bulleted, numbered and checklist lists, a quote, a
link, @mentions) in one shared editor — the composers, an edit in place, a business unit's or client's notes —
which opens in a larger view on Expand without losing a word — the whole composer moves, its kind, who reads it
and its save included, and Collapse sits where Expand was. A note a door
sends (`SaveCandidateRequest.note`: the add form, the plugin, a sheet's
Note column) is filed once as a note about that mandate; only its author or a `WORKSPACE_MANAGE` holder
may change, remove or restore one (`PERSON_NOTE_NOT_YOURS`), and a note line on the timeline names the note,
never quotes it. A removed note is out of every read at once (V141) and offered back by the toast's Undo
(`…/notes/{noteId}/restore`); `PersonNotePurge` deletes it for good after `lightmove.notes.purge-after` (30 days),
so from then it leaves no copy. Both person drawers — a position's executive drawer and the
Candidates page's — share one header (`PersonHeaderLayout`: the photo beside one column of the name with its LinkedIn
mark — the drawer's only view of the link, edited with the details' pencil — then on a position the
status picker and that pencil, the primary email and phone with "+N" and the contact details window, the tags held to
one line with a +N chip, and do not contact) and one tab order, `Position.dc.html`'s, grouped by what the consultant
is doing: **Profile · Notes · Assessment · Outreach · Activity** — Profile's folds Summary, Experience, Education,
Background, Compensation, Documents and Your columns, Summary alone open on arrival (`useProfileSections`); Outreach
the conversation — the action row first (Write email, Log call, Log meeting, Log LinkedIn, Log WhatsApp, Book a meeting, Add to sequence, held
while a run is live), then the run where there is one, the calls to come, then one timeline (its latest five, See more for
the rest) (`DrawerOutreachTimeline`, over the conversation read and
`MeetingsSection`'s past calls with their ⋯: emails, replies, logged calls and LinkedIn messages, each past
call with the Meeting note its recording filed, every one a card led by its kind's tile — LinkedIn's own mark, a camera
for a video or recorded call, a calendar for one met in person, its platform named as text ("Google Meet"; never Meet's
or Teams' logo, which their owners license), a recorded one badged Recorded — and the face of who sent, wrote or held
it beside the time, never their name on show (a call known only from its recording, the sender's) (`OutreachEntryCard`, which the Outreach page's
history shares); status and run changes are the Activity tab's; All · Email · Calls · LinkedIn · Meetings) and the contacts last, their
pencil the header's contact details window; a link to a call, LinkedIn or meeting note lands there — and **Positions** on
the Candidates page's drawer alone (`lib/drawerTabs.ts`; retired `?tab=background|compensation|documents|contact|…`
links still land), a client seat reading Profile alone with no tab bar — Compensation and Documents are staff's on
screen, though `CandidateResponse` still carries the package; the workspace drawer leaves out only what a position
holds (Assessment and Outreach). **Assessment** (V135) is the
consultant's own scorecard against the brief's competencies: 0–10 or unscored (an open question, never a zero) per
competency, out of 100 by each panel's `technical_share` (`ScorecardScoring`, mirrored live by
`lib/scorecardScoring.ts`), plus evidence and a five-section report, saved from the footer as a draft or **Mark
reviewed & submit**. A score snapshots its criterion and is matched on panel + name, so one the brief drops is kept,
uncounted. A stale save is `CANDIDATE_ASSESSMENT_STALE`; it is staff-only (`WORK_EXECUTE`, never on `CandidateResponse`).
**Assess with AI** (V136, `POST …/candidates/{id}/assessment/ai`, answered while the consultant waits) drafts it:
`CandidateScorecardDrafter` sends the `CandidateDossier`, the person's notes and documents (below) and the brief's
`AssessmentBrief` seam (role, criteria, competencies with ids — never compensation or the internal context) and gets
back, per competency, evidence naming its source then a 0–10 score against an anchored rubric — a note, a reference
or a CV can state a behaviour; read from the career alone a score is held to 3–7 and marked "Inferred from …", and
null only with nothing on file — and the five sections; the weights and the total stay `ScorecardScoring`'s. It writes only what nobody holds — an empty value, or one still in
`ai_drafted` (an untouched earlier draft, sparkled in the tab) — never a consultant's; an edit takes a value out of
`ai_drafted`, and **Mark reviewed & submit** adopts every AI value. It runs only when pressed — never on a filing. The executive drawer draws **Notes** and **Activity** for staff, through the
position's own `WORK_EXECUTE` routes (`…/candidates/{id}/positions|notes|timeline`, `PersonCrmController`).
**A position's timeline is that position's alone** (`PersonTimelineService.positionTimelineOf`): its own lines and the
person-level kinds (`PERSON_LEVEL_KINDS`: profile, contacts, tags, owner, do not contact, documents) wherever they were
made, the latter named to no position; another position's notes, emails, meetings, status and tasks are filtered in
the query and never leave the server. A profile save or a call's changes keeps each field old → new (`ProfileChanges`,
the response's `changes`; never a contact, never the employer). The Activity tab (`components/activity`,
`Position.dc.html`) reads it whole (`getPositionHistory`), filters by kind and by person in two menus, folds quick
changes, and opens each line read-only (`ActivityDetailDialog`) through the reads its tab already uses.
The Candidates page's drawer draws the same timeline (`ActivityTimeline`'s `person` scope) over the person-wide read,
which carries only `PERSON_WIDE_KINDS` — the person-level kinds and each position's milestones (added, removed, stage,
status) — each card naming its position and a Position menu beside the other two; a position's notes, emails, calls
and tasks stay in that position's drawer, and the Notes tab.
The workspace's routes (`/api/v1/candidates/{personId}…` and `/candidates/activity`, the feed) take
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
`components/documents`: both drawers' Documents fold (on Profile) and a preview sheet (a PDF or image fetched as a blob, never a link); the
upload tray says what each file will become before sending, and a duplicate is the server's 409.
`CandidateResponse.linkedinUrlLocked` says the plugin read the held URL off its page; it fences nothing —
a person's URL is always editable, and an edit that changes it clears the flag.
**The workspace's Candidates page** (`/candidates`, `RequireStaff`, `Candidates.dc.html`, Phase 4) reads
those routes: a People list the server searches (name, title, employer, an email; a plain scan per
workspace, V33's reasoning), pages, sorts and narrows — quick views (owned by me, in an active position,
in none) in the toolbar, then tags any/all/none, position, status, owner and country in a Strategy-style filter rail hidden until asked for — with a selection bar that adds people to
a position as Identified (that position's `WORK_EXECUTE` too, since filing someone is work on it) under the employer their
latest position recorded, filed into its universe as a plugin capture of someone known is, tags
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
**One email and one phone are primary** (V183 `is_primary`, at most one per channel by partial unique index): starred
in the Contact section (Make primary, read or edited), drawn in both drawers' header beside the employer's logo, and
listed first everywhere — the grid's Email and Phone columns, the export, every send's default (Write email, Add to
sequence, Book a call), each still changeable at the send. None chosen, the first of the ledger's order stands in:
personal before work before untagged (an approach to someone at their current employer goes to a personal inbox),
verified first, what a person supplied before what a lookup found, oldest first (`Person.listed`). A person the plugin
captured keeps their LinkedIn URL editable, since people change their address (`PERSON_PROFILE_HELD` still refuses
a second person's profile). `lightmove.enrichment.contactout` sits **beside**
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
A stage also leaves as a file: **Export** (under the toolbar's Advanced ▾) downloads the whole stage —
every row, not the page on screen, narrowed by the toolbar's search and whichever of the grid's three header filters
are in force, rows as well as companies, so the file is what the screen was showing — carrying every
column the grid draws and every custom column the mandate added, with the two Links icons spelled
out as Website and Company LinkedIn. It is `WORK_VIEW`, the gate that reads the grid, so a client
representative may take the mandate they can already read; unlike every other read it records an
audit event, because a mandate leaving as a file is not the same act as reading a page of it. Past
`lightmove.export.*` it refuses rather than truncating.
**The Companies toolbar** (`Position.dc.html`) is the view switch, one search box in every view, then Add to sequence and Send email (the drawer's composer, for anyone on the position) where
outreach is offered — greyed (`aria-disabled`, saying where it works) on the map and until something is ticked — and,
on Table and Map only, Find executives filled, **Advanced ▾** (`AdvancedMenu`) and **Create ▾** (Add executive, Add
company). The Pipeline's row is the switch, the search and the outreach pair alone.
Advanced ▾ holds the rest, by view: show/hide and manage columns on the table, the map's layers, Add to sequence again
on the table, Import and Export. Strategy's toolbar and Outreach's header are the same row (`components/ui/Toolbar`:
the page's views, then the search, the page's actions pushed right), Outreach's search in it. The search is matched by the server, the same
rule in every read (`search` on the triage list, its
people reads, the export and the talent map, the pipeline's `q`): a company by name, place or industry, an executive
by name, title, place or employer, and — only for a `WORK_EXECUTE` holder, decided server-side — the company's and
the person's live notes readable from this position and the person's tags; a matching company keeps all its people.
In universe reads as **Table | Pipeline** (`?view=`; **Map is switched off on screen for now**, `MAP_OFFERED` in
`triageStages.ts`: the globe, its reads and a stored choice stay, and a link or remembered choice of it lands on the table),
Strategy as Companies | People and Outreach as
Conversations | Sequences — each the page's own `ToolbarViewSwitch`, first in its toolbar at every width and in full
screen, one width whether it holds two views or three so the search starts at the same place on all three. **The row stays
on one line** from a phone up (`TOOLBAR_ROW`, `components/ui/Toolbar`): the search and the switch shrink first, then the
buttons give up their words for their icons by the row's own width (`ToolbarLabel`, a container query), and only below
`sm` does it wrap. The rail is one row per page and nests nothing. `useCompaniesViews` decides what In universe offers. A view someone
*chose* in the switch is remembered per mandate; a link that only names one, a
notification's `?view=pipeline`, is a visit. **Pipeline** is the position's
board by pipeline stage — the retired CRM Pipeline page, board only, every executive on the position whatever their
Companies stage, staff only (`WORK_EXECUTE`); `/projects/{id}/pipeline` and its `?candidate=` links land there
(`positionPipelinePath`). Its columns are the stages holding a status in process, the five seeded ones each with its
mark (`stageMarkOf`); under them the **Out of process** band, resized from its grip, holds a cell per stage of whoever
left at one of its ways out — a drop in a cell offers that stage's ways out alone — and a stage of ways out alone gets
a cell of its own. A card's foot is its client-visibility eye and four actions: Log call (straight into the call note composer),
Email, Add note and Schedule (Book a call, said why not where do not contact or no email stops it).
The In-universe page also reads as a **map**: the Map view (offered only
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
and matches a word inside longer ones, so an exclusion hiding in a senior title word is dropped, and a seat
abbreviation hiding in one — "CTO" in Director and Doctor — is never sent to it, `forSubstringMatching`),
one **synchronous** Bright Data people search per company keys on the company's LinkedIn slug
(`current_company_company_id`; the numeric id matches nothing) with the brief's country and the Gulf
neighbours only — nobody living elsewhere is searched for, and a brief with no country searches everywhere.
**No model judges the people**: the hits not already mapped are ranked in code (`SourcedHitRanking` —
the vendor matched the headline and sends no relevance order, so it reads the *current role's* title:
a function word first, then its level against the brief's seat — every word matched whole, `TitleWords`), filed only
when the current role holds the seat (`SeatFit`: the brief's function, Head or above for a C-suite or N-1 seat; a round
finding only others is reworded), and the first `picks-per-company` are each filed through `CandidateService.addSourced` — the search hit *is* the research, so no
second vendor call — as `AI_SOURCED` (no badge on the grid; the drawer's source reads "Sourced"), and
the same automatic background run a capture gets proposes the missing background, fired with the `SOURCING`
trigger that skips the per-user LLM meter. A company whose search finds **nobody** is searched again with other title
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
(`enrichment/peoplesearch`, V93) is the sixth door: Companies | People first in Strategy's toolbar
(`?mode=people`, staff only) swaps the company filter for ContactOut's People Search filter —
only parameters its API reference lists, the values its accepted-values sheet lists
(`data/contactout-people-vocabulary.json`; the dashboard's Revenue, Gender and exclude lists have no
API) — all but the name, which is the toolbar's search box, as Companies' is. The filter autosaves as
`app_lm_strategy.people_filter` and is counted free and live
(`GET …/strategy/people/count`, its own 10/s pacer beside search's 60/min); **Search** is a press — the rail's
button, or Enter in the name box — (`POST …/strategy/people/search?page=`), the top 25 in ContactOut's order — it offers no other — and
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
tab — **Dashboard** in the rail and the phone's bar, while the route and code keep `reports` — is the mandate's talent
mapping report (`GET /projects/{id}/report`): four chapters — mapping
progress, shape of the market, remuneration, diversity — aggregated live by `report` from the same
rows, so nothing is stored and nothing goes stale. It reads one chapter at a time behind a numbered
step rail, the chapter kept in the URL (`?chapter=`), and its mockup is the `reports` page of
`uncava-design/Position.dc.html` — drawn in the UNCAVA palette (`--color-u-*`) — the only palette
the code has: the old app names (`panel`, `amber`, `sky`, …) are gone from both apps, and an unset
theme opens dark. It states only what the rows carry: a candidate's
status but no pipeline outcome, and a package in another currency is counted rather than converted.
**Gender (V56) is recorded on a candidate, or proposed and flagged — never silently inferred** — the
chapter divides by the executives who have one on file, not by the headcount, so a mandate nobody has
recorded reads as unmeasured rather than as a pool of one gender. **The AI on an executive** is ungrounded
Gemini calls at temperature 0 (no web search — grounding was too slow): the background (`CandidateAiEnricher` and
the nationality classifier, asked side by side by `BackgroundProposer`) and the assessment draft (above).
`CandidateAiEnrichWorker` runs the background alone, unprompted and audited like a press, once a capture's research
lands or Find executives files someone — asking nothing when nothing is missing — and the drawer runs each on its
own, synchronously:
**Suggest with AI** in the Background section's header (`POST …/candidates/{id}/background/ai`, offered while
gender, years or seniority is empty, or nationality is with no reading yet) and **Assess with AI**. Each press is
its own `LlmBudget` meter and one `AI_ENRICH` fair-use unit, recorded only once its answer is on file; a model that
answers nothing usable — an empty draft included — is `AI_UNAVAILABLE`, charging nothing, Assess with AI with every
score and section already a consultant's is `CANDIDATE_ASSESSMENT_NOTHING_OPEN` before anything is spent, and a
prompt the injection guard stops is `VALIDATION_FAILED` naming the words on file (`AiRefusals`), never "try again".
The old panel-level assessment and its header button are gone (V79's `ai_assessment` and V80's
`ai_enrich_failed_at` frozen, unmapped). The background call (`CandidateAiEnricher`) always proposes its
most probable value — never "unknown" — for whichever of gender, years of experience and seniority
level are still empty — a value already on the row always stands, and each filled one is flagged in
`ai_inferred_fields` (V78, an "AI" badge) until a researcher changes it. **Nationality is the deliberate exception: it may be "Unknown"**,
because a wrong one costs more than a blank. It is `CandidateNationalityClassifier`'s, asked only while
the field is empty, against an evidence rubric (`candidate-nationality-system.st`: residence, a GCC
employer and a regional business school are not evidence; a GCC nationality needs positive evidence;
name alone is low confidence; conflicting signals are Unknown), with the career sent oldest first. Its
reading — category, confidence, evidence for and against, rule — is kept whole (V83
`ai_nationality_reading`); only a `high` one fills the field and flags it, a `medium` or `low` one is
a suggestion the drawer's Background section offers to accept in one click, and Unknown leaves the
field null and the drawer says the AI could not tell. `NationalityEval` (`@Tag("eval")`, excluded from
`./mvnw test`) scores both prompts against a golden set that never enters the repository — real
profiles, exported by `ops/eval/export-golden.sh` — and appends counts to `docs/eval/nationality-eval.md`. **Every
AI run on an executive reads the person's notes and documents too** (`CandidateMaterialService` →
`CandidateMaterialReader`): every note that position reads, newest first, and the text of the latest file of each
document (a PDF, DOCX, DOC or TXT; an image or a scan without a text layer is left out), each held to a length,
and the audit line counts how many of each went. Assess with AI alone also reads the **transcripts** of the calls that
position recorded (`MeetingTranscriptLookup`, answered by `notetaker`), newest first, only while each call's Meeting
note is live, redacted and held to 150k characters a call (start and end kept) and 250k in all, a staff member's voice
marked "(your team)" in code; a claim the executive makes about themselves with no example behind it scores as
indirect evidence, and a transcript the prompt guard stops is dropped and the assessment asked again without it. **The model never sees a candidate's contacts, compensation
fields or custom fields**: the profile is the `CandidateDossier` allowlist, and `core/text`'s `ContactRedaction` —
the one copy of the email and phone patterns, which `PositionDocumentRedactor` uses too — takes every address
and number out of the About, the notes, the transcripts and
the documents first, sparing dates, ranges and money; a figure a note or a CV states is sent as written. The assessment ranks a person and the nationality reading reasons about their origin, so both are
staff-only — `GET …/candidates/{id}/assessment` and `…/nationality-reading`, `WORK_EXECUTE` — and never on `CandidateResponse`, which a
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
and quality is what is on file (a contact, a verified one, a base salary). A fifth chapter, **Ranking**
(the `reports` page's `chRank`), is staff-only too — `GET /projects/{id}/report/ranking`, `WORK_EXECUTE`, read
through `CandidateScorecardService.rankingInputsOf`: every executive with a score against a current competency, draft
or reviewed, at the brief's own weighting (the drawer's totals, an unscored competency earning nothing), the rest counted
as not assessed, capped by `lightmove.report.max-ranked`. Beside it the position keeps **one shared scenario** (V204,
`RankingService`, `PUT …/report/ranking/scenario`): a technical share and competency weights the team tries out, with
a rationale, version-locked (`RANKING_SCENARIO_STALE`), refused once the brief's competencies move under it
(`RANKING_BRIEF_CHANGED`), and cleared by saving the brief's own weighting. It never writes the brief, and v1 draws no
per-competency minimum, since the brief records none. The order, the shifts and every sentence about them are the
screen's, computed from those numbers — no model is asked (`features/reports/lib/ranking.ts`, `rankingWording.ts`; the
leader's flip points are found exactly, a total being a line in the technical share), and the chapter is offered to
staff alone, by link as by menu (`STAFF_ONLY_CHAPTERS`). A projects-list row opens
the **position side panel** (`Workspace.dc.html`'s Position drawer): mapping progress as universe
companies with an executive mapped (`mappedCompanies` of `companies` on `GET /projects`), key
metrics, stage gates, the team and hiring managers, and **recent activity** — `GET
/projects/{id}/activity`, a cursor-paged, allowlisted read of the audit trail, `WORK_EXECUTE` so a
client seat never sees it, phrased and merged into lines by `lib/activity.ts`. The **Position**
screen is the mandate's brief, drawn in `uncava-design/Position.dc.html` (issue #442) in the UNCAVA
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
| `apps/api` | Spring Boot 4.1 (Java 21, Maven). Features: `core`, `common`, `workspace`, `project`, `position`, `positiontemplate`, `strategy`, `triagecompany`, `candidate`, `enrichment` (with `sourcing`, the Find executives run, and `peoplesearch`, Strategy's People mode), `customcolumn`, `dataimport`, `dataexport`, `geocoding`, `talentmap`, `report`, `assistant`, `outreach`, `pairing`, `publicapi`, `mcp`, `billing` (epic #734: `plan`, the contact-credit ledger `credit`, fair use, `usage`, the Settings → Billing reads, `overview`, Stripe, `payment`, staff seats, `seat`, and the billing emails, `notice`), `task` and `notification` (epic #862, `docs/tasks-notifications.md`), `notetaker` (Recall.ai bots, `docs/notetaker.md`), `account` (a person's own account: its export and deletion, #926) |
| `apps/web` | React 19 SPA (Vite 8, TypeScript, Tailwind v4) |
| `apps/extension` | LightMove Capture — the Chrome extension (Manifest V3, React 19, Vite 8). Its own workspace; shares no code with `apps/web`. |
| `../uncava-design/` (separate repo) | HTML mockups — **the source of truth for all UI**. Read the relevant `*.dc.html` before building a screen. |
| `ops/cloudsql/` | Database bootstrap and hardening scripts |

`position` is the mandate's **brief** — what the role is, why it exists, what it pays and what a
candidate is scored against. It owns `app_lm_position` and its owned lists, and it depends on `project`
because the mandate keeps two of the fields the screen shows: the role title, which step one edits, and
the one target date (V8), which the screen only displays — it is set on the project and nowhere else.
Two features read it: `enrichment` takes the whole brief (`PositionService.briefOf`) for sourcing and the AI
assessment, and `candidate`'s scorecard only `competenciesOf` — `position`'s own `BriefCompetencies` record, never
the brief's DTOs. `position` depends on neither.

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
`triagecompany` through four public methods — resolving the company an executive is mapped to,
filing a researched employer into the universe, reading company rows' logos for the Candidates
page (`logoUrlsOf`), and reading the company row a known person's other position recorded (`get`) —
and `triagecompany` never depends back.
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
**write-only** — stored only as `core/crypto` ciphertext, never returned by any read but for its last four characters
(V169 `secret_hint`, the identifier Google's console shows; none under 16 characters), and discarded when the
workspace returns to the shared app. Its keys are held to the shape each provider issues (a Google client ID's
suffix, Entra's GUIDs and directory, no whitespace in a secret — refused, never trimmed — an expiry for Microsoft
alone, within 24 months) and **saved only once the provider accepts them**: Save and verify asks its token endpoint
first, outside any transaction (`ProviderTokenClient.checkKeys` — Entra's app-only grant, or a code nobody issued at
Google and Zoom), a refusal `INTEGRATION_KEYS_REFUSED` with the provider's code — at Entra only a refused client or an
unknown directory; any other refusal there is no answer — and no answer `INTEGRATION_PROVIDER_UNREACHABLE`, both
writing nothing; **Verify again** (`POST …/integrations/{provider}/verify`, audited) keeps the answer either way, ten
checks a minute per workspace on each instance (`OwnAppKeyCheck`); and a refresh refusing the app records the same
(`OwnAppKeyHealth`), so the card reads Keys refused unasked, cleared by the next refresh answered while one is on
record. The screen is `Settings.dc.html`'s
(`docs/integrations/settings-page-ux.md`): a closed card per provider with a status pill, the console's own order and
field names for a first set-up, and a saved app as read-only rows — the secret masked to its hint, Replace, never
revealed. A change of app is a change of who holds the tokens: a refresh token belongs to the
client it was issued to, so every mailbox or Zoom account connected through the app being left (V168
`app_client_id`, null read as the app until now) is withdrawn in the change's own transaction
(`ReplacedAppConnections`, one bulk update so no versioned row fails the save, its Recall calendar let go,
`connectionsWithdrawn` on the audit line), the screen naming how many first (`connectionsThroughApp`) — and, whatever
the switch missed (a connect committing during it, the shared app's id rotated), `MailboxTokens` and `ZoomTokens`
refuse at refresh a token issued to another app than the one resolved; new keys for the same app hand every existing
Recall calendar the new ones once they commit (`ProviderAppKeysReplaced`). This is the epic's deliberate reversal of "never the provider's tokens":
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
a consent it showed — for `gmail.send`, `gmail.readonly` (a reply's words, phase 2; `gmail.metadata` before it),
`calendar.events` and `calendar.freebusy`. A send is one raw
RFC 2822 message (`RawEmail`: UTF-8 HTML, an RFC 2047 subject where it is not ASCII, a line break in any header
refused); a follow-up names the thread and carries the last message's own `Message-ID` in `In-Reply-To` and
`References`, read with `format=metadata`, so it threads in the executive's client whatever it is. The poll reads
the thread by its `From` headers and drops `SENT` and `DRAFT` by label, then, where the consent granted the read,
fetches each message it kept whole; a consent that unticks the read is refused (`MAILBOX_READ_NOT_GRANTED`). Google revokes by the refresh token, so
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
half-hours inside their working hours (`FreeSlots`: a week a page, an hour's notice, 60 days out, as Nylas's page keeps), and `POST …/{slug}` (an email address alone, nobody signed in — nothing a caller types reaches the invite but where it goes) asks the calendar
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
**Add to sequence** — from In universe / Shortlisted (the ticked companies' executives), In universe's Pipeline (the
ticked executives) or the executive drawer — chooses, reviews and starts. Choose shows who is skipped and why (no email, do not contact, out
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
the sequence's own sending days and hours (V113, `send_days`/`window_start`/`window_end`, Mon–Fri 08:00–18:00
by default) in the mailbox's `time_zone` — the consultant's own time zone (Settings → Profile, else the workspace's, V210) copied to
each of their mailboxes (V148, `TimezoneChanged`) — and under its daily cap. A follow-up waits its delay in those days and
goes at its step's `send_time`, or the time of day the step before went. Start chooses when the first emails
go (`startMode`): `NOW` or `AT` a chosen instant (≤ 60 days) pins it (`first_send_pinned`), so the window
never moves it though the cap still does, and `NEXT_WINDOW` waits for the window; each person after the
first is due 1–3 minutes after the one before (`FirstSendSpacing`), and keeps that offset past any opening the
window or the cap holds it for (`send_offset_seconds`), so a deferred batch never goes at once. A time-zone change
is audited with `PROFILE_UPDATED` and takes a region's zone or UTC only. Step 1 is the
frozen email; a follow-up is rendered from the sequence as it stands and replies to the last message, which
threads it. The first send moves Identified to Contacted, forward only. A reply or bounce arrives on the
public `/api/v1/outreach/webhooks/mailbox`, whose HMAC signature is its credential
(`lightmove.outreach.nylas.webhook-secret`; blank refuses every delivery), with a 15-minute poll of each
listening thread as the fallback. Only a thread Uncava started is read, and a reply's words only where the mailbox
grants them (phase 2, below). A mail daemon's message is a bounce. Every email is an `app_lm_outreach_message` row (staff-only); every end is an
`EMAIL_REPLIED` / `OUTREACH_STOPPED` line and an audit event (`OutreachOutcomes`). The Outreach page reads
`…/outreach/people` (counts and runs, filtered in the SPA), the drawer's Outreach fold
`…/outreach/candidates/{id}`, and Stop is `…/enrollments/{id}/stop`, all `WORK_EXECUTE`.
**Conversations (`docs/outreach-conversations.md`, six phases, all built)**: the message table is one
executive's conversation on one position (V144) — every line carries person, position, `direction` and `channel`
(email only so far; WhatsApp, SMS and calls reserved) — and `ConversationService` answers the three-pane
Outreach page: `…/outreach/conversations` (the action queue — Needs reply, Follow-ups, Preparation, Attention, read
off each executive's open tasks through `NextActionLookup.openTasksOf` and their latest run — by tab, Mine or Team,
and a search over names and what was sent) and `…/outreach/conversations/{candidateId}` (emails, run events, this
position's notes and meetings merged newest first). Both `WORK_EXECUTE`; they own nothing.
**Phase 2 (V145)**: every reply and bounce is a line of that ledger (`INBOUND`, `REPLY | BOUNCE`), found by the
webhook or by `OutreachInboxService`'s poll of **every thread an email went into** within `reply-listen-window` (no
longer only live runs' threads) through one `MailboxGateway.messagesInThread(grantId, threadId, since, withText)`,
deduplicated on the provider's id (a partial unique index, inbound only). Its words are kept — staff-only, never
logged, the quoted history cut by `ReplyText` (jsoup for an HTML body) — only where the mailbox grants them
(`ReplyTextAccess`): a direct mailbox's consent (`reply_text_granted`, from the token's `scope`: Google's
`gmail.readonly`, Microsoft's `Mail.Read*`), a Nylas one by `lightmove.outreach.nylas.reads-replies` (off by default,
since no grant says what the Nylas app's connectors reach); otherwise the line has no text and still stops the run.
A Google mailbox consented before the read is asked to reconnect (`reconnectToReadReplies`). Every reply writes its
`EMAIL_REPLIED` line, audit event and `OutreachReplied` (now with `messageId`); only the first, while its run still
listens, ends the run — and a reply received more than a day before it is found is a line alone, announced to
nobody, unless its run still waits. V145 gave each earlier reply and bounce a **placeholder** line (no provider id),
which the first matching message a read finds — received before it was noticed — completes instead of joining.
**Phase 3 (V146)**: a consultant's **own email** — a first email, or a reply in the thread their mailbox last wrote to
the person in — through `POST …/outreach/conversations/{candidateId}/messages` (Markdown, tokens, now or `sendAt` ≤ 60
days), checked as Start checks (mailbox, live position, do not contact, the address on the record, Send now under the
cap) and kept as a `SCHEDULED` line that `OutreachMessageSendService`, the dispatcher's second loop, claims, re-checks
— the sender's own `WORK_EXECUTE` and a reply's thread still their mailbox's gateway and address included — and sends
once — never retried, held to the cap but never to a window, cancelled by its sender until taken
(`DELETE …/outreach/messages/{id}`) or by archiving. `OutreachMarkdownEmail` renders it: raw HTML as text, links
http/https/mailto only, an image as its alt text, a mention as a name. A send writes `EMAIL_SENT` (`manual`) with its
status automation, stops a live run in the same thread as `MANUAL`, and publishes `OutreachEmailSentByHand`, on which
`task` closes the person's open reply-rule tasks. Only a *sent* line says whose a thread is: one still waiting never
takes a thread from the run in it.
**Phase 4** is the screen: `/projects/:id/outreach` (`RequireStaff`, viewport-filling by an exact path match) draws
`Outreach.dc.html`'s three panes — `ConversationListPane`, `ConversationPane` (history and `ReplyComposer`) and
`CandidateDetailsPane` — with `?view=&who=&q=&candidate=` in the URL; it lands on everyone (All, Team) and opens
the top conversation where the list leaves room (the list alone on a phone), the tabs being chips in the list (All · To do · Scheduled · Sent). `OutreachHeader` is
the same frame for Conversations and Sequences (`…/outreach/sequences` and the sequence editor under it, its switch first),
the mailbox chip (`MailboxChip`;
`lib/mailboxState.ts` is the one reading the banner, chip, setup card and composer share) and the view's action —
New email (anyone on the position) or New sequence. The executive drawer offers Write email (the same composer in
`EmailComposerDialog`) beside Add to sequence. The conversation read gained `contact` and `composer`
(`replySubject` and `replyStopsRun` for the caller's mailbox, `emailSentMovesTo`), the mailbox read `sentToday` and
`scheduledEmails`.
**Phase 5 (V147)**: an own email carries **files** — one of the person's documents, copied under the email's own storage
key when it is written (so a version removed later neither changes nor breaks it), or one uploaded in the composer
(`multipart/form-data`: the request a `message` part, each file a `files` part), its bytes matched to its name
(`DocumentFormat`) — held to `lightmove.outreach.attachments.*` (5 files, 3,000,000 bytes each, Graph's single-post
limit; 10 MB together) before anything is stored (`OutreachAttachments`). Each gateway sends them its own largest way:
Gmail's upload endpoint (`RawEmail` as `multipart/mixed`, RFC 2231 names folded into continuations), a `fileAttachment`
post per file on the Graph draft (a refused one deletes the draft and fails the email, `AttachmentNotAccepted`), Nylas's
multipart send (its JSON send caps the whole request at 3 MB). Bytes are read at send and never logged
(`EmailAttachment.toString`); a document that went is audited `PERSON_DOCUMENT_SENT` by ids alone (a CV's name is its
person's), a download from the history `OUTREACH_ATTACHMENT_DOWNLOADED`, and a cancel or a definite failure deletes the
files once it commits (`OutreachMessage.keepsAttachmentFiles`).
**Phase 6** is live: every JPA write of an outreach message, an enrollment or a task announces `outreach` / `task` on
the position's project stream from an entity listener inside the writing transaction (`OutreachStreamAnnouncer`,
`TaskStreamAnnouncer`; once per mandate and kind per transaction), and those two kinds are **staff-only** — `ProjectStreamRegistry` sends them only to a stream
whose holder held `WORK_EXECUTE` when it subscribed, since the stream itself is `WORK_VIEW`. The page refreshes on them,
reading every minute while the stream is down and every five while it is live (a note, a meeting or a status is not
announced), never a call's free slots, which ask the mail provider. The drawer's Outreach fold links to the
conversation and quotes the latest reply (`CandidateOutreachResponse.latestReply`), a run or none.
**Meetings (V102, #628)** read the same grant's calendar: an event is kept (`app_lm_person_meeting`, one row
per matching person) only when an attendee's address is on a person's ledger — nothing else of anyone's
calendar is stored — through the `event.*` webhook (`MeetingSync`) and a 90-day read either side of today
when a mailbox is connected (`MeetingBackfill`, retried by the reply poll while `calendar_synced_at` is
null — V103 backs each failed read off from 15 minutes to a day, ten calendars a poll, and gives up after
five until a reconnect). A recurring series is never kept: Nylas keys it by its master in a webhook and by
each occurrence in a read. A free/busy answer with an error entry is a failure, never free time. The drawer's Meetings section reads `…/candidates/{id}/meetings`; **Book a call** is the
consultant's own week (`…/meetings/calendar`, `WeekTimeGrid`): their calendar as free/busy alone and their working
hours, a call dragged from any time to any time on any day (`ConsultantBooking`: a five-minute mark, 5–480 minutes, five
minutes ahead at the soonest, six months out at most) — working hours only label it — and `POST …/meetings`
re-checks do not contact, the ledger and that the calendar is still free, creates the event (never retried), then writes
`MEETING_BOOKED`, moves Identified or Contacted to Engaged (forward only) and ends a live run as `BOOKED`.
A call booked in Uncava (Book a call or the link), still to come, is **moved or cancelled** from its row by whoever
holds its calendar (`editable`; `PUT|DELETE …/meetings/{id}`, `MEETING_NOT_EDITABLE`, `MEETING_NOT_YOURS`): a move
reads the call's own time as free (`…/calendar?excluding=`), re-checks do not contact and the slot, moves its Zoom meeting
first (V149 keeps its id; put back if the calendar refuses) and sends the update; a cancel tells the executive. Neither
is retried, moves a status or restarts a run (`MEETING_RESCHEDULED` / `MEETING_CANCELLED`, audited).
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

**Email signatures (V193, epic #1157, `docs/email-signatures.md`)** are a consultant's own, per workspace, in
Settings → Email signature (`/me/signatures`, staff only, every row keyed on the caller — another's reads as missing):
at most ten, exactly one the default while any exist (unticking it does nothing; another is made the default), each a
layout (Classic, With photo, Photo + logo, Logo banner, Minimal, Custom in the notes' editor) whose empty fields follow
the profile (name, title, phone, LinkedIn, photo), the workspace (name, website, logo) and the mailbox's address; a
phone override is held to the profile's rule (`PhoneNumbers`). `EmailSignatureRenderer` draws them email-safe — tables
and inline styles, every value escaped, links http/https/mailto/tel only, no picture but Uncava's own — and the
editor's preview is its drawing (`…/preview`), shown in a sandboxed frame. A preview or the list writes nothing: a
booking link not yet claimed is drawn as a stand-in, and only a signature about to be sent claims it and makes its page
(`SignatureSenders.forSending` → `BookingPages.prepare`). No mail provider adds a signature to email sent through its
API, so Uncava adds its own. Pictures are `core/storage`'s `EmailImageService`: the person's stored avatar (V170,
uploaded or a provider's copy) or the workspace's logo — fetched once through `PictureFetcher` on `core/http`'s
`GuardedHttpClient` — each copied once (keyed on the avatar's or the logo URL's SHA-256), refused past 40 megapixels
from the header and decoded subsampled, re-encoded, kept in the bucket and served publicly at
`/api/v1/email-images/{token}`, a 256-bit token per picture (never per message or recipient) its only credential,
cached for good and never logged (`RequestLogFilter` skips it; `ops/gcp/bootstrap.sh` excludes it from Cloud Run's
request log), counted or rate-limited per address (mail clients' image proxies fetch for everyone from a few). An image
row outlives the avatar it was copied from, since a sent email names it. A Custom signature also takes the person's own pictures (V210, a
group's logo, a membership badge): Insert picture, paste or drop uploads one (`POST /me/signatures/pictures`, JPEG or
PNG ≤ 5 MB, kept inside 1200 × 400), the editor writes `![alt](/api/v1/email-images/{token} "size")` into the
Markdown, a save refuses more than six or one that is not the signer's own, and the renderer draws only the signer's
own at the size the title names (small, medium, large), anything else as its alt text. **Send test to me**
(`POST /me/signatures/test`) sends the signature being edited, saved or not, from the person's mailbox to itself. A new signature starts from With logo, With photo or Custom: `POST /me/signatures/starter` turns the
first two (or an older fixed layout being edited) into that Markdown — the profile, workspace and mailbox details, the
photo and logo as pictures — and every signature is then saved as Custom; a Custom signature may show the signer's own
photo and the workspace logo as pictures as well as their uploads. The composer's **Include signature** ticks the
sender's default (a picker where they keep more than one; replies alike) and `SendMessageRequest.signatureId` draws it
when the email is written, into the stored `body` only — `body_text` stays the consultant's words. **A signature is
never part of an email's words** (revised 2026-11-11, after the market: Outreach, Gong, HubSpot, Apollo and Gem all add
it at send): Add to sequence's review carries the same Include signature, `StartSequenceRequest.signatureId` resolved by
the one `EmailSignatureService.htmlForSending` an own email uses, drawn once at Start and frozen on each enrollment (V194
`signature_html`), closing every email of the run after its words; unticked, or none kept, the run goes unsigned. A
template and a sequence step both refuse `{{signature}}` on its field (`SignatureTokenRefusal`), and V207 and V208 took
#1277's step token out of every stored step and frozen first email, subjects included. Either way a signature showing Book a call
has its page made before anything is written. The mailbox's test email carries the default, leaving Book a call out
where its page cannot be made, since the test proves the mailbox and nothing else.

**Email templates (V205, epic #1247, `docs/email-templates.md`; Phases 1–3 built)** are a consultant's saved emails and
replies, per workspace, in Settings → Email templates (`/me/email-templates`, staff only, `EmailTemplateService`):
personal, or shared with the workspace, where every staff member applies or duplicates one and only its owner or a
`WORKSPACE_MANAGE` holder edits, unshares or deletes it (`EMAIL_TEMPLATE_NOT_YOURS`); another's personal one reads as
missing, an admin's included (`EMAIL_TEMPLATE_NOT_FOUND`), and every write carries the `version` it read
(`EMAIL_TEMPLATE_STALE`, the row locked). A name, an optional one-line subject and a Markdown body; at most 100 per
person, counted under `UserRepository.lockForOwnContentChange`, the lock signatures share. Never a signature: Include
signature adds the sender's own, so the editor draws the author's default greyed beneath. **Variables are a
sequence's** (decision 16): the editor carries the sequence editor's own Insert bar (`TokenInsertBar`, the same chips
in the same order, less the opener) and a variable is written and shown as typed, `{{firstName}}`, with
`{{firstName|there}}` the fallback `SequenceTokens` (and its web twin) now read everywhere a token renders — an escaped
pipe included, since the notes editor writes one. A template holds only the six and `bookingLink` (where booking pages
are offered): `{{opener}}`, `{{signature}}`, an unknown name or a fallback on the link is `VALIDATION_FAILED` on its
field. Sharing, unsharing and any edit or delete of a shared one are Outreach audit events named only while the
template is still shared (`SharedEmailTemplateNames`); a personal one is unaudited. **The composer** (`ReplyComposer`,
so Write email, the Outreach reply and New email alike) carries the same Insert bar, a **Templates** picker beside
Attach (`TemplatePicker`: search, Recently used — the caller's five, from `app_lm_outreach_message.template_id` — Mine,
Shared with the owner's face, and a preview with this executive's values) whose foot saves the words as a template
(`SaveAsTemplateDialog`, never the signature). A template goes into an empty editor as the body and into words at the
cursor (`NoteEditorHandle.insertDoc`); a first email takes its subject only while its own is empty, a reply never;
Include signature is left alone. The conversation read's `composer.tokens` is the executive's values: a variable with
none and no fallback, or one no email fills, is named under the editor and holds Send, and the server refuses the same
with `OUTREACH_TOKENS_UNFILLED` (409, `tokens`), a first email's subject filled on one line (`renderLine`). The email
records the template it came from (`templateId`), only one the sender may see in this workspace, else null.
**A sequence step** (`SequenceEditorPage`) takes one by **From template** (`StepTemplatePicker`): a copy, never a link,
of its `plainBody` — the words as plain text, paragraphs apart (`NoteText.plainEmailTextOf`), since a step has no
formatting — its subject into the first email alone, asked first where the step holds words, refused past a step's
5,000; the run's signature closes it, never its words. **Save as
template** on a step saves its words with their variables as Markdown (`plainEmailDoc`), the first email's subject too;
a step using `{{opener}}` is refused, saying why. The editor's preview and Add to
sequence's review mark, per person, a variable with nothing on file red and a fallback standing in underlined
(`FilledVariable`); the review names both under the email and flags the person "N blank", and Start is not held.

**The Notetaker (V151, `docs/notetaker.md`, `notetaker`)** is a Recall.ai bot a consultant sends into a kept call from
the executive drawer's Meetings section (`WORK_EXECUTE`, only on a call on the caller's own calendar or booked by them,
never for do not contact; a recording is read only from the position that sent it). It is kept out of the way: a call's
row is the call, its Join link and its ⋯ (`CallMenu`), whose **Notetaker & questions…** opens the call's panel
(`CallAssistantPanel`, one section per job). The ⋯ opens on where the call has got to, carries what came of it once the
notes are filed — the changes, the transcript, Try again — and wears a dot while the call records or waits on the
consultant; nothing about the Notetaker is drawn under the call. Nothing is recorded until asked (V157): the panel's **Join at start** switch schedules it a minute early
and, switched off, calls it back (Live copilot beside it, usable once a bot is set), and a small **Send Notetaker
now** ("Call started early?") sends it in at once — on a day the call is not booked for, after asking — never onto a
link an earlier call still holds, or brings a scheduled one forward; **Remove Notetaker** takes it out at any point until End & file notes, and a bot whose call moves or goes is
called back by the reconcile (the drawer pairs bot and call by start); sending never opens the meeting, a separate **Open meeting** link does (a new bot first, the scheduled one called back after); Book a call
carries no Notetaker. It is claimed first (one live
bot per call by join URL and start, whoever presses), then sent once and never retried — an empty pool of at-once bots
is `NOTETAKER_BUSY` — as "UNCAVA Notetaker" with Uncava's card as its camera, saying in the chat whose notes it records
and that anyone may ask it out. **End & file notes** takes a recording bot out of a call that finished early, and the
notes follow as after any call; Remove discards. A call the bot has left is listed under Past, and a recording whose call left the calendar keeps a row of its own. Recall's Svix-signed webhook (`/api/v1/notetaker/webhooks/recall`,
`lightmove.recall.bot-webhook-secret`, blank offers nothing) walks its status; on `transcript.done`, `MeetingNotesWorker`
keeps the transcript (staff-only), summarises it against the brief's `AssessmentBrief` with contacts redacted, files one
Markdown `MEETING` note in the scheduler's name (one worker per transcript, by claim; an `AI_ENRICH` use) and leaves
Recall's copy of the call to its 7-day retention (V158), during which the bot's sender alone may download the video from the
call's ⋯ — streamed by the API and audited, never a link — so Try again can read it again; the transcript is kept unless
`lightmove.recall.transcript-retention` is set, which clears one whose note is purged or never filed once that long untouched.
`NotetakerReconcile` reads back a recording still live past its call. **Suggest changes** (V152, `CallSuggestionService`)
reads the transcript against the record once the note is filed, with no press — claimed in the filing's own transaction,
in the sender's name and on their meters, a refusal leaving it to a press — and lists every change — notice period,
package, location, Open to relocate, motivation, expected package, the consultant's next steps as tasks — on one review,
each ticked to keep and editable in place; one **Apply N changes** (`…/suggestions/review`) writes what is ticked and
skips the rest, all or nothing, each through its field's owner (`CandidateCallUpdates`, `TaskService`) with an
`UPDATED_FROM_CALL` line naming who and which call. There is no Undo: once reviewed, a change is corrected on the record,
and the call's ⋯ reads "N changes applied", its **Changes from the call** reopening each one with what it replaced, who
applied it and the words it rests on. **The interview
copilot** (V154) is two more: **Prepare questions** on an upcoming call (in its panel) drafts an interview plan (`InterviewPlanService`
— fit questions per competency, a polite one per fact the record is missing, told which are missing and never a value
or a figure), edited and saved per call — ready, and followed by the copilot, from its first draft; and **Live copilot**, chosen before the bot joins by whoever sent it, streams
the call (`NotetakerLiveFeed`, each bot's own URL token the credential) to a coach (`InterviewCoachWorker`) that folds competency coverage, the gaps heard, the questions
asked forward only — it suggests no next question, and keeps a reading only where an executive's own line backs it; the Live view ends in the last speaker's one line — read by a floating window (Document Picture-in-Picture, a popup, or the
drawer's Copilot tab, first in the bar only while the copilot runs) whose ⋯ stops it for everyone; the streamed lines go when the call ends, and the note is filed from an accurate
transcript asked of Recall after the call (`FinalTranscriptRequester`), never the English-only stream. A key without the calendar webhook secret
leaves calendars read directly (`RecallSettings.isCalendarOffered`). It depends on `outreach` (`KeptMeetings`),
`candidate` and `position`; nothing depends back.

**Tasks and the bell (epic #862, V133, `docs/tasks-notifications.md`).** A **next action is a task**
(`task`): one `app_lm_task` row per job on one executive of one position, and the Pipeline card's Next action is
that executive's earliest open one — `task` answers `candidate`'s `NextActionLookup`, so the board, the drawer, My
tasks and the bell read the same rows. In the executive drawer it is a fixed-height bar docked in the footer, on every tab
(`NextActionBar`: the earliest open task, ticked done in place, the rest in a popover grouped overdue/today/upcoming,
folded to one square beside the Assessment tab's save buttons), the drawer's other actions — Add note, Set next action,
Book a call, Remove from mandate — behind the footer's ⋯;
off a position, the Candidates drawer shows it on each Positions card (V132's free-text `next_action` columns became open tasks and are no longer
written). A task goes, and a note's `@[Name](user:<uuid>)` / `@[team](team)` mention reaches, only someone who may
work the position — `WORK_EXECUTE`, read again on every save (`ProjectAccess.allows`) — never a client seat; a note
about no position mentions nobody. Anyone on the team completes a task; changing, reassigning or cancelling it is
its creator's, its holder's, `TEAM_MANAGE`'s or `WORKSPACE_MANAGE`'s (`TASK_NOT_YOURS`). A seat that goes
(`ProjectSeatVacated`, from the team screen or a member's removal) hands its open tasks to the position's first
lead in the same transaction, and so does a role change or removal (`MemberAccessNarrowed`) for a task held on a
position its holder worked only as an admin. The due scan claims inside a `TransactionTemplate`: a scheduled call
is a self-invocation, and without a transaction the after-commit listeners never hear it. Presets are the workspace's catalog (`app_lm_workspace_task_preset`, V98's
seeded-on-first-read, retired-not-deleted idiom); free text is a title only. Rules file tasks from outreach — a
reply, a bounce, a booked call — once per event (`rule_key`, unique), each switchable per workspace.
`notification` is the bell: `NotificationRules` turns those events (and a mention, a seat taken, a task falling
due) into `app_lm_notification` rows after commit, each recipient re-checked first; rows of one `group_key` fold
while unread; a row names a note and never quotes it; `core/stream`'s per-user channel (`lm_user_stream`,
`/me/notifications/stream`) nudges the open tabs. Email goes only for an assignment or a mention still unread
after `lightmove.notifications.email-delay`, and a weekday digest at 08:00 in the person's profile time zone, each
claimed once. All of it is staff-only and rides no `CandidateResponse`, report, public API or MCP read.

**A position is archived, never deleted** (V137): its lead alone — `PROJECT_ARCHIVE`, the one project action a
workspace admin does not hold without a seat (they seat themselves as lead first) — types its title back in a warning
that counts what it stops (`GET …/archive-preview`), and `POST /projects/{id}/archive` stamps `archived_at`. It then
leaves every list and count (`GET /projects` answers live positions unless `?includeArchived=true`, which the SPA sends
and filters, so a link still opens one; a pure client never gets archived ones), the pickers, the public API and MCP
lists, My tasks, the due scan and the bell (rows kept, back on restore; their emails dropped), and its live runs stop
as `POSITION_ARCHIVED` in the same transaction (`ProjectArchived` → `PositionArchiveOutreach`) — a Start or an
opener draft there is `PROJECT_ARCHIVED`. Nothing is frozen and nothing deleted; the screens stay editable under an
Archived banner, and Restore (one click, the same gate) brings it back without restarting a run. It lives in the side
panel's foot (Archive at the start, Open position at the end; no menu of one) and the ⋯ of the position's topbar; the
list draws an Archived chip after the stages.
**A business unit or client is archived the same way** (V138), but by a workspace admin (`WORKSPACE_MANAGE`), from
Archive at the start of its drawer's foot, and only once none of its positions is live — `CLIENT_HAS_LIVE_POSITIONS` names them,
since archiving those is their leads' call. It leaves the registry's list (`GET /clients?includeArchived=true` as above)
and the New position picker; nothing is opened or restored under it (`CLIENT_ARCHIVED`, the record row locked
against a racing archive), and its name stays held (`CLIENT_NAME_ARCHIVED`, kept apart from
`CLIENT_ALREADY_EXISTS`, whose caller reuses the record it names).

**A workspace is deleted with a grace period, then purged (#926, V202).** `DELETE /workspace` (the name typed back)
ends access at once — every membership ended (`ended_with_workspace`), invitations revoked, API keys and AI apps
revoked, live runs stopped `WORKSPACE_DELETED` and waiting emails cancelled in the same transaction
(`WorkspaceDeleted` → `WorkspaceDeletionOutreach`), every Notetaker bot called off and queued for
`RecallBotErasureSweeper` there too, and once it commits every mailbox and Zoom account let go at its provider — its row kept, `ERROR`, until the provider
answers the revoke, since it is the only copy of the grant — and a Stripe subscription set to cancel at the period's end
(`WorkspaceBillingClosure`; no month's credits are granted to a deleted workspace). It stamps `purge_after`
(`lightmove.retention.deleted-workspaces-after`, 30 days ⚖️) and the deleting admin's address, captured because nobody
can sign in by the purge. `WorkspacePurge` claims a due workspace on `app_lm_workspace` (no policy) with a committed
lease renewed every batch, and inside its `WorkspaceScope` empties every table carrying `workspace_id` but `WorkspacePurgeTables.RETAINED`
(the audit trail, the credit ledger and billing records, kept by id) — read off the schema, children first by the
foreign keys, so a new tenant table is purged without anyone listing it — after each `WorkspacePurgeContributor`
(Recall's media, a grant the deletion could not revoke) — one still holding something fails the attempt, retried
after the lease, until `WorkspacePurge.RELEASE_PATIENCE` (7 days) from the purge's start; deletes every file under `ws/{id}/`
(`DocumentStore.deletePrefix`); refuses to finish while any row is left; scrubs the row into a tombstone (ids kept,
every word naming the firm gone); and completes `app_lm_workspace_purge_certificate` — counts per table taken at its
first attempt, files, times, no name, and the SHA-256 of its body — audits `WORKSPACE_PURGED` and emails it — owed,
the address and the firm's name kept on the tombstone (`purge_notice_name`), until the mail service accepts it or a week
has passed. An export finishing after its row went is queued for deletion. A platform
admin (`WORKSPACE_LIFECYCLE_MANAGE`) restores a workspace and the memberships its deletion ended until the purge
starts (`POST /platform/workspaces/{id}/restore`; mailboxes, keys and invitations stay gone) and reads the certificate
(`GET …/purge-certificate`), resolve-then-scope. **Export workspace data** (Settings → Data & retention, so only where
`DATA_PROTECTION_ENABLED` offers that page; `WORKSPACE_MANAGE`, audited start and download) builds one zip at a time off the request
(`WorkspaceExportWorker` → `WorkspaceArchiveWriter`, streamed into the store through `core/export`'s
`ZipArchiveWriter`, which #919 reuses): every position with its brief and its three Companies stages as the staff grid
exports them, the Candidates CSV, each person's notes and the latest file of each document, and the whole audit log;
the admin is emailed, and `WorkspaceExportExpiry` (V202's resolver) deletes it `workspace-exports-after` (7 days ⚖️)
later, or fails one whose worker died.

**A person deletes their own account (#926, V203)** from Settings → Profile (`account`): `GET
/auth/me/deletion-preview` names the workspaces they leave and refuses — `ACCOUNT_HANDOVER_REQUIRED`, naming them —
while they are the only admin of one or the only lead of a live position (read in that workspace's scope); `DELETE
/auth/me` asks the password, and a code where two-factor is on, or for an account with none a sign-in within
`lightmove.auth.mfa.recent-sign-in` (`ACCOUNT_SIGN_IN_AGAIN`; the dialog offers the provider's popup). In one
transaction — each workspace's roster lock taken first, in id order, so a workspace's last two admins (or a
position's last two leads) deleting at once cannot both pass the handover — every membership ends through
`MemberService`'s removal (audited `MEMBER_REMOVED`, reason `MemberRemovalReason.ACCOUNT_DELETED`; keys, AI apps and
seats with it), each workspace in `WorkspaceScope.runInJoinedTransaction` and
flushed there; each `AccountDeletionContributor` — declared in `account`, implemented where the rows live — deletes the
person's own rows in every workspace they ever joined (the bell, its settings and digests, working hours, a mailbox
connect in flight, assistant chats); `AccountCredentialErasure` deletes sessions, sign-ins, links, the second factor, the stored photo and its signature
copies (`app_lm_email_image`, their files queued for `StorageDeletionSweeper`);
and the `app_lm_user` row is anonymised in place ("Former member", `deleted-<id>@deleted.invalid`, `deleted_at`), so
what they did stays the workspace's and still resolves. Once it commits, the confirmation goes to the former address and
mailboxes and Zoom accounts are let go at the provider in transactions of their own; the access token that outlives it
reads 404 on the deletion preview, the export, a second deletion and the profile write. **Download my data** (`GET /auth/me/export`, audited `ACCOUNT_DATA_EXPORTED`) is
the account as JSON — profile, consents, sign-in methods, two-factor, every membership with its personal keys and AI
apps, sessions and a year of sign-ins, each workspace read in its own scope; what they added inside a workspace is its
admin's to export.

**The customer audit log (#1083)** is Settings → Security's Audit log tab, an admin's alone: `GET
/workspace/audit-events` (cursor-paged; actor, Category, outcome and a date range), `…/people` (everyone the workspace
ever held, for the Person filter) and `…/export` (the same view as a CSV, itself audited `AUDIT_LOG_EXPORTED` and refused
whole past `lightmove.export.max-audit-events`), gated `staffOnly and can(WORKSPACE_MANAGE)` so a member is refused and a
pure client is not told it exists. It is an **allowlist**: `AuditLogGroup` names the types each Category shows,
`AuditLogPhrasing` phrases each from `AuditLine.text`'s allowlisted keys (an email or a name in an event's metadata is
unreadable, #927), a type nobody phrased reads as its own name and one the code does not know as "Other activity" — the
row's metadata never leaves the server. Names come from live rows (the roster, invitations, and positions and clients
through `project`'s `AuditTargetLabelsAdapter`). Sign-ins are the account's: a successful one is stamped with the
workspace it opened into, and an account's security events (a failed sign-in, a lockout, two-factor and password
changes) show in every workspace the person is a member of, from their latest joining (a rejoin resets it, so the
time away never shows) until the `MEMBER_REMOVED` line that ended it; a failed sign-in for an unknown address shows
nowhere. Uncava staff's rows read as "Uncava", never a name
or an address. Its index `(workspace_id, id DESC)` is `harden.sql`'s in production (V165 skips it there).

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

The **MCP server** (epic #699) starts with its **OAuth 2.1 authorization server** (#701, `core/security/oauth`):
Spring Security's own, its endpoints moved under `/api/v1/oauth/**` (`/oauth2/**` is the sign-in providers') with
RFC 8414 metadata at `/.well-known/oauth-authorization-server`, behind `MCP_ENABLED` (off: all of it a 404). Clients
are public only — PKCE S256, the code and refresh grants — and every request must name the MCP endpoint as its
`resource` (RFC 8707), which becomes the token's `aud`. An unsigned-in authorize request lands on the SPA's
`/oauth/consent`, which signs in, offers the caller's staff workspaces (`API_KEY_MANAGE`, so never a pure client)
and posts the request and then its consent with the session's bearer, answered as JSON (`redirectUri`, carrying
RFC 9207 `iss`). A **grant** (V115 `app_lm_oauth_authorization`) is one user × one client × one workspace, every
token in it stored as SHA-256 only (`HashingAuthorizationService`), consent asked every time and never remembered
(`PerGrantConsentService`), the access token an RS256 JWT on its **own key** (`McpTokenKeys`, `kid` `mcp-…`) with
`sub`, `wsId`, `scope`, `client_id` and `grant_id` and no roles, and the refresh token rotated on every use — a
rotated one replayed deletes the grant. Settings → Connected AI apps (`/settings/ai-apps`, staff, #707) reads
`/api/v1/workspace/oauth-grants` (`API_KEY_MANAGE`, `?all=true` under `WORKSPACE_MANAGE`, as API keys): the MCP server
URL to copy, one row per grant drawn with the consent screen's `ClientMark`, its last use stamped by every MCP call at
most once a minute, and Disconnect. API keys offer the `mcp:use` tick, off by default and never alone. A grant ends with
its membership or workspace, as a personal key does, and every grant of an account ends with a password change or reset.
A client is never signed up by hand (#702): it registers itself through Spring's RFC 7591 endpoint
(`POST /api/v1/oauth/register`, open, 10 an hour per address, public clients only, pruned after 30 days unconnected), or
its `client_id` is the https URL of its own **metadata document** (CIMD), fetched by `HttpClientMetadataFetcher` — a
public address only, checked on the addresses the connection dials, no redirect, 5 KB, 5 s — and kept as a row for its
`Cache-Control` lifetime (`ClientMetadataDocumentClients`: one fetch at a time per document, a failed one not retried
for 5 minutes, a stale copy serving meanwhile). A redirect URI matches exactly, except a listener on this machine
(`127.0.0.1`, `[::1]`, `localhost`) whose port may change; a private-use scheme is refused. The consent screen calls only
a document under a listed URL prefix (`lightmove.mcp.verified-client-id-prefixes`) verified — a registration never is.
A client revokes its own grant at `/api/v1/oauth/revoke` (RFC 7009) with its id alone, budgeted per address, and that
path never fetches a document.
The **MCP server** itself (#703, `mcp`) is Spring AI's stateless Streamable HTTP transport at `/api/v1/mcp` behind its
own resource-server chain (`McpServerConfig`), which tells two credentials apart by shape: an access token from our
authorization server (the MCP key, our issuer, the MCP endpoint as `aud`, its grant still live and its user still staff,
re-read every call) or an API key holding the opt-in `mcp:use` (V117), which reads nothing by itself. A session token is
neither and opens nothing; tools call services in-process, never passing a caller's token on. Either becomes one
`McpCaller` in the MCP transport context, which every tool reads and runs through `McpToolCalls` — one `MCP_TOOL_CALL`
audit line per call, never its arguments. RFC 9728 metadata is Spring Security's own at
`/.well-known/oauth-protected-resource[/api/v1/mcp]`, and a 401 points at it. A browser `Origin` not on
`lightmove.mcp.allowed-origins` (the deployment's own is always on it) is refused, by the endpoint's own CORS policy and
by the transport; bodies are capped (read and replayed, so a body with no declared length is held too), calls are
budgeted per grant or key, and an address only on its refused credentials — hosted clients call from shared egress, so
an address budget on calls would let one tenant refuse the rest. Only `@McpTool` beans are served: Spring AI's
`StatelessToolCallbackConverterAutoConfiguration`, which would publish every `ToolCallback` bean, is excluded on
`LightMoveApplication`, and so is Spring AI's own tool list: `McpServerConfig` builds it from the same `@McpTool` beans
with every tool behind `McpToolGuard`. The parity tools (#704) are `uncava_whoami`, `uncava_search_positions`,
`uncava_get_position`, `uncava_list_companies`, `uncava_list_candidates` and `uncava_get_universe`; the task-shaped
ones (#705) — `uncava_get_position_summary`, `uncava_get_candidate`, `uncava_get_company` and
`uncava_search_candidates` (a name, title or employer, held to a stage's companies) — each have a public REST twin
(`…/summary`, `…/candidates/{id}`, `…/companies/{id}`, `…/candidates?q=&stage=`) over the same read. Every one reads through
`PublicReadService` and the public DTOs — so REST's field rules hold, contacts and compensation with their own scopes —
as a `PublicReader` (`core/security/apikey`, a key or a connection alike; a personal one reads only positions its user
holds `WORK_VIEW` on, through `PublicApiAuthorizer.requireProjectRead`). A tool's scopes are its `@McpToolScopes`,
required on every tool (`{}` for none) and read at boot under the name the SDK gives it, so a tool without one, or two
under one name, fails the start rather than being served unchecked (`McpToolRegistry`): an OAuth token lacking one is answered 403 `insufficient_scope` naming them (`McpScopeStepUpFilter`, the spec's step-up)
and a key, which cannot step up, with an `isError` result naming them. Every failure leaves as a `McpToolRefusal` — an
`McpError`, the one exception Spring AI's callback does not turn into a result quoting its message, which for an
`ApiException` is the internal detail — carrying a fixed sentence; a foreign position and one without a seat read alike.
Each tool declares an output schema and answers `structuredContent`, which the SDK validates and copies as text (a
nullable enum is given null by `McpToolRegistry`, since the generator leaves it out). Rows are concise, or carry the
whole public record as `detail` under `response_format=detailed`; lists page by an opaque offset `cursor`, and a page
past `lightmove.mcp.max-result-chars` (about 25k tokens) is cut with a `notice` and reads on from its first left-out
row. What clients are told is `docs/mcp/tools.json`, which `McpToolContractTest` regenerates and diffs.
**Shipping it (#708)**: `docs/mcp.md` is the user's guide — connecting Claude, ChatGPT, Cursor and the APIs, the tools,
scopes, limits and errors — and the SPA draws it, public, at `/docs/mcp` (`features/docs`, its maintainers' section cut
and its URLs put on the page's own origin; the Dockerfile copies that one file in), linked from Settings → Connected AI
apps. Cursor connects by key: its OAuth return is a `cursor://` link, which is refused. `deploy.yml`'s `MCP_ENABLED`
mounts the MCP keypair (`lightmove-mcp-jwt-private-key` / `-public-key`, made by hand as `ops/gcp/bootstrap.sh` says)
and smoke-tests the 401 and both metadata documents. `ops/eval/mcp` asks Claude Code the questions in its
`cases.json` against a local stack and reports the tools it chose (`docs/eval/mcp-eval.md`); it never gates a build.

