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
workspace, `wsId`, and `POST /auth/switch-workspace` is the only thing that moves it), the projects/clients
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
same map as points alone for the poll that waits on places rather than on people. The **Reports**
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
recorded reads as unmeasured rather than as a pool of one gender. **AI enrichment** is one
Google-grounded Gemini call (`CandidateAiEnricher`, Spring AI `googleSearchRetrieval`) run by
`CandidateAiEnrichWorker` after a capture's vendor research lands, and again from the drawer's
**AI deep enrich** button (`POST …/candidates/{id}/ai-enrich`, 202, `WORK_EXECUTE`). It reads the
profile as given and searches the web only for what LinkedIn does not say. It proposes whichever of gender,
nationality (one of the nine groups) and years of experience are still empty — a value already on the
row always stands, and each filled one is flagged in `ai_inferred_fields` (V78, an "AI" badge) until a
researcher changes it — and scores the executive 1–10 on the brief's technical and behavioural
competencies with at most five positives and five negatives each, a summary, and the pages it relied
on (V79 `ai_assessment`, replaced whole per run, LinkedIn links dropped). **The model never sees a
candidate's contacts, compensation, note or custom fields**: its input is the `CandidateDossier`
allowlist. The assessment ranks a person, so it is staff-only — its own read
(`GET …/ai-assessment`, `WORK_EXECUTE`) and never on `CandidateResponse`, which a client seat reads. **Nationality is counted in nine groups** — the Gulf six by name,
and everyone else as Western expat, South Asian or Arab expat, non-GCC: the drawer offers exactly those
nine and stores the label, while a spreadsheet's "Egyptian" is folded into its group by `report` at read
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
and quality is what is on file (a contact, a verified one, a base salary). The standalone
Candidates screen, and the pipeline and outreach tables, don't exist yet. A projects-list row opens
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
`project` and `client`; a screen whose mockup still says project or client keeps saying it. It is served at `https://beta.uncava.com` (Cloud Run domain mapping,
Cloudflare DNS with the proxy off; README, "Custom domain"), and a link to it pasted into a chat app
draws a card from the Open Graph tags in `apps/web/index.html` over `public/og-image-v2.png` — static,
because no crawler runs the bundle (README, "Link previews"). Publishing stamps who
called the brief ready and **freezes nothing** (V38 retired the lock deliberately). Don't build ahead of
the mockups: if a screen isn't being built this session, its tables and entities don't exist yet.

## Layout

| Path | What |
|---|---|
| `apps/api` | Spring Boot 4.1 (Java 21, Maven). Features: `core`, `common`, `workspace`, `project`, `position`, `positiontemplate`, `strategy`, `triagecompany`, `candidate`, `enrichment`, `customcolumn`, `dataimport`, `dataexport`, `geocoding`, `talentmap`, `report`, `assistant` |
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
holds only what a mandate *did* about a company. **`candidate` is the people side** — one row per
executive a mandate has mapped, belonging to the *project* and only optionally to one of its triaged
companies, because a researcher meets people at companies the universe does not carry. It depends on
`triagecompany` through two public methods — resolving the company an executive is mapped to, and
filing a researched employer into the universe — and `triagecompany` never depends back.
**`customcolumn` is the columns a mandate added to its own grid** — definitions only, plus the one
method (`applyTo`) that decides what a row may store in them, since the bag is open and nothing else
stands between it and arbitrary caller-chosen keys. `triagecompany` and `candidate` depend on it; it
depends on neither and knows nothing about companies or people. **`dataimport` is the spreadsheet** —
read the file, work out what its columns mean, and write what it carries through the doors that
already exist. It depends on those three and none of them depends back. **`dataexport` is the same
three doors outward** — one stage of the Companies grid, composed and written as a CSV, reading
through the seams `talentmap` already uses; it depends on the same three and on nothing else. Details
in `java-spring-development`.

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
- A user may hold **several** active memberships (V81 dropped V1's partial unique index), but a session is in exactly **one** workspace — the `wsId` claim, chosen at sign-in from `last_workspace_id`, remembered per refresh-token family, and changed only by `POST /auth/switch-workspace`, which is a 404 unless the caller is an active member of the target.
- Verification gates the *proof of mailbox*, not the channel — an invite token or a password reset proves it too.
- **Tenant isolation:** every workspace-scoped query filters by `AuthPrincipal.requireWorkspaceId()`, never a request parameter.
- **Authorise by action, never by role** (`@PreAuthorize` + `@workspaceAuthorizer`/`@projectAuthorizer`); guard beans re-read the DB every check; the JWT `roles` claim is never trusted for a decision.
- Client access is **two tiers, two decisions**: registry (`CLIENT_RECORD_MANAGE`, ADMIN+MEMBER) vs mandate (`CLIENT_ACCESS_MANAGE`, LEAD only). Project content is seat-gated `WORK_VIEW`/`WORK_EXECUTE`, not `PROJECT_BROWSE`.
- **A platform role sits above every tenant and inside none**: `SUPER_ADMIN` gates `/api/v1/platform/**` (`@platformAuthorizer`) and nothing else, is granted by ops script only, and never rides in the JWT.
- **An identity provider is a yml block** — never branch on a provider name anywhere.
- **Tokens are never stored raw** (SHA-256); the refresh cookie rotates on every use; the access token lives in JS memory only.
- **The SPA and API are one origin**; every endpoint lives under `/api/v1`. Don't split hosts.
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
removing a company from a mandate unmaps its executives rather than deleting them; career history and
languages are one `profile` jsonb column for V30's reasons. `app_lm_position` and its six owned-list
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
`app_lm_candidate_contact` (V54) is every email and phone known for an executive, one row each, with
`source` naming the door (`MANUAL` / `CSV` / `EXTENSION` / `CONTACTOUT`) and `kind` / `verified` only
ever what the provider said. V39's owned-list idiom: `Candidate` rewrites it from its own methods, and
its identity is `value_key` (lower-cased address, digits of a number) because providers spell one
number three ways. A miss is not a row — it is `emails_looked_up_at` / `phones_looked_up_at` on the
candidate with nothing from the provider beside it. V54 moved the old `profile.contacts` jsonb into
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
`yearsExperience`) holding a model's proposal that no researcher has changed since.
V79 adds `app_lm_project_candidate.ai_assessment` jsonb — the AI enrichment's summary, per-panel
score with positives and negatives, and source links; the model's own reading, replaced whole per run.
V80 adds `ai_enrich_failed_at` — the last AI enrichment run that produced nothing, so the drawer says
so at once; a later success clears it. Saving the drawer's Background section (`confirmBackground`)
confirms its AI values and clears `ai_inferred_fields`.
V81 lets a person belong to several workspaces: it drops V1's `app_lm_workspace_member_single_org_per_user_uk`
(the `(workspace_id, user_id)` unique stays — one row per person per workspace whatever its status, so a
removed member who is re-invited **rejoins** that row rather than inserting) and records which workspace
a session is in on `app_lm_refresh_token.workspace_id` (a refresh re-reads the membership there and falls
through to another the user is still in), and where the next sign-in opens on `app_lm_user.last_workspace_id`
(written on every explicit choice — sign-in, switch, create, accept — never by a background refresh).
Both are backfilled before the index is dropped, while it still guarantees one row to copy from.
`WorkspaceSelection` is the one place that rule lives; `WorkspaceMemberRepository` deliberately has no
singular by-user lookup any more, because an `Optional` over two rows throws.
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
giving way to the new one's (`WorkspacePersona.refiledFrom`) — written by an admin through
`PUT /workspace/persona` (Settings → General), read by staff on `GET /workspace` and never carried
on `/me`. The assistant's empty chat offers a sector starter for each of its first two sectors.
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
