# Database — migration notes moved out of CLAUDE.md

Moved verbatim from CLAUDE.md (2026-11-11). One paragraph per migration or table; grep for a V-number or table name.

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
`triage_company_id` is nullable with **ON DELETE SET NULL** beside a snapshotted `company_name`; removing a
company now takes its executives with it (`CompanyRemovalFollower`), and V166 put back under their company, at
their own stage, the people an earlier removal, or a name-only Add to position, had stranded. Since V91 it maps a
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
V80 adds `ai_enrich_failed_at` — the last AI enrichment run that produced nothing. Both are frozen since V136,
unmapped and unwritten, left for a later cleanup to drop. Saving the drawer's Background section (`confirmBackground`)
confirms its AI values and clears `ai_inferred_fields`.
V83 adds `app_lm_project_candidate.ai_nationality_reading` jsonb — the nationality classifier's last
reading (category or Unknown, confidence, evidence, rule), replaced whole per run and staff-only.
`app_lm_vendor_person` and `app_lm_vendor_people_search` (V87) are the people cache, V64's shape and
tenant rule for people: every record a billed search or lookup returned, keyed on the profile slug,
and which slugs each normalised search returned. `PeopleCache` answers a repeated search from them with
no vendor call, and otherwise reads back the fitting people on file at that employer and asks the vendor
only for how many more the free count says there are — ContactOut cannot leave anyone out, so a returned
record is billed whether or not it was new. One row a person, whichever provider returned them: a fresh
record is never replaced by a thinner provider's (`CachedPeopleStore`'s precedence, ContactOut over
Business Search over the dataset), and a provider's own `source_record` survives its later answer
without one. `BrightDataProfileEnricher` reads the same table first, so a capture of someone a run bought
is free — never from a Business Search row, which says who works where and often holds no title.
Both age out after `lightmove.enrichment.people-cache-ttl` (30d), and every billed call purges what
has aged past it (V89 indexes `fetched_at` for that), so a third party's record is never held longer
than it may be read. What is read back is the employer's people on file, never a mandate's own roster:
the answer is shared by every workspace.
`app_lm_executive_sourcing_run` (V86) is one Find executives run — the companies frozen at request
time, the words the model drafted (`spec`, read back from an older run's shape too), and an outcome per company as each finishes — kept as a row so the
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
V115 adds the MCP authorization server's tables: `app_lm_oauth_client` (public clients; redirect URIs and the most
it may ask for, held to the five scopes by CHECK; `source` `SEEDED | DCR | CIMD`), `app_lm_oauth_authorization` (one
grant: client, user, workspace — null only while waiting for consent — and the state, code, access and refresh
token as SHA-256 hashes, unique each) and `app_lm_oauth_retired_refresh_token` (rotated-away refresh hashes, the
replay check). Deleting a grant row is revoking it.
V116 gives `app_lm_oauth_client` `last_authorized_at` (stamped at consent; the purge's clock for an unused registration)
and, on a metadata document's client alone (CHECK), `metadata_fetched_at` and `metadata_expires_at`.
V117 adds `mcp:use` to `app_lm_api_key`'s scopes CHECK: the opt-in that lets a key reach the MCP server.
V120 adds `app_lm_assistant_turn.questions` jsonb — the clarifying questions an assistant answer asked through
`spring-ai-agent-utils`' `AskUserQuestionTool` in place of answering. The questions are recorded and the model told
to stop (`AskUserQuestionCallback`), never waited on, since an ask is one request with a deadline (`lightmove.assistant.answer-deadline`); the consultant's choices
are the chat's next question (`docs/assistant-tools.md`).
V121 is billing's first (epic #734): the plan catalogue (`app_lm_billing_plan`, Core / Pro / Enterprise priced per
staff seat), one `app_lm_workspace_subscription` per workspace (every existing one backfilled Core, `INVOICED`, a seat per
active staff member), and the contact-credit ledger — `app_lm_credit_grant` buckets a spend drains (plan, then given,
then bought; soonest expiry first), `app_lm_credit_hold` (one per paid action, unique on its idempotency key per
workspace), `app_lm_credit_entry` (append-only by trigger, and by `harden.sql` like the audit trail) and
`app_lm_credit_balance`, the per-workspace sum of the entries whose row every ledger write locks first.
`CreditLedger` is the only door; `lightmove.billing.enforce` (on since #741; the test profile turns it off) refuses a
spend the credits cannot cover with 402 `INSUFFICIENT_CREDITS`, and off records it as an overdraft. Off is billing
switched off for every workspace (`BILLING_ENFORCE`, prod's demo setting): no credit, trial, seat or fair-use refusal,
no billing email but a failed payment, and `GET /billing`'s `enforced` hides the chip, prices, seat cost and
Settings → Billing; finds still drain the month's credits and claim its low-credit warnings. Every ledger write expires the workspace's lapsed grants first (an `EXPIRE` line,
never a delete), and `CreditLedgerSweeper` (`lightmove.billing.sweep-interval`) does the same for idle workspaces and
releases holds nobody settled within `hold-ttl`. Billing's foreign keys to the workspace do not cascade: a financial record outlives it.
A Find email or Find phone press (#737) holds its price before ContactOut is asked — keyed on the person, the channel
and how many of their holds were released before (V122 indexes that count), so presses in flight together share one
hold — captures it only on a find and releases it on a miss or a failure; the answer carries `creditsSpent` and
`creditsLeft`.
Search and AI carry no price (#738): V123's `app_lm_usage_event` records each use with its estimated vendor cost once
the work succeeds (`UsageRecorder`) — a People Search page the first time *this workspace* reads it, bought or cached;
a Find executives run's filed executives; a Suggest with AI or Assess with AI press; the people an opener was written for; an assistant
ask, whose turn also keeps its model and tokens (V123 re-adds the columns V70 dropped). `FairUseGuard` refuses a use that would pass the
month's ceiling — staff seats × `lightmove.billing.fair-use.<kind>-per-seat`, `0` for none — with 429
`FAIR_USE_REACHED` (`kind`, `resetsAt`), after `LlmBudgetGuard` and before any vendor or model is asked.
**The billing month** (`BillingMonth`) is what both run over: monthly steps from the subscription's period start, so a
yearly plan still resets monthly, or the UTC calendar month without one. `MonthlyCreditReset` (#740,
`lightmove.billing.jobs.monthly-reset`, hourly) grants each live plan its month's credits expiring with the month —
no rollover, the ledger expiring the old grant first — keyed `plan:<workspace>:<month start>`, the key Stripe's
`invoice.paid` will use, so neither grants a month twice. Every spend claims the 80%, 90% and used-up thresholds of
the month's plan credits it reached (V124 `app_lm_credit_threshold_crossing`, under the balance lock) and publishes
`ContactCreditThresholdCrossed` for the highest newly claimed. `CreditBalanceReconcile` checks nightly that each
balance is its entries' and its grants' sum, audits a drift (`CREDIT_BALANCE_DRIFT`) and corrects nothing; a job
that must run once across instances claims an `app_lm_billing_job_run` row first.
`GET /api/v1/billing` and `…/billing/usage` (#741) are Settings → Billing's reads, any staff member's and a 404 to a
pure client: the plan, seats and seat price, the credits (`monthly`, `left`, `bought`, `given`, `usedPercent`,
`level`, `resetsAt`), the prices, and this billing month's finds and credits per member. `GrandfatherCredits` gives
every workspace that exists the first time a release boots with enforcement on `lightmove.billing.grandfather.credits`
promotional credits, keyed `grandfather:<workspace>`, under a job claim nothing prunes, so none founded later gets them.
The SPA's `features/billing` (#742) draws them as Settings → Billing (`Billing.dc.html`, every staff member's; seats and
more credits an admin's) and as the topbar chip, absent below 80%. Every refused request passes `apiClient`'s
`onRequestRefused`, so a 402 `INSUFFICIENT_CREDITS` or a 429 `FAIR_USE_REACHED` from any screen opens one sheet
(`BillingRefusalSheets`, mounted once in `main.tsx`) and its caller stays quiet (`isBillingRefusal`). Prices on the
Find buttons are the read's `prices`, never literals; search carries none.
**Stripe (#743, V125)** is `billing/payment`'s `PaymentGateway` (`StripePaymentGateway` over `stripe-java`, whose
release pins the API version the webhook endpoint must be made on; a blank `lightmove.billing.stripe.secret-key` leaves
`UnconfiguredPaymentGateway`, every workspace invoiced and `stripeOffered` false). An admin (`BILLING_MANAGE`, audited)
is sent to Checkout for a per-seat plan — quantity the workspace's staff, the price id a deployment's
(`…stripe.prices.*`, test and live differ) — or a pack (`…billing.packs`), or to the Customer Portal, which is where a
Stripe subscriber changes plan, refused a second subscription Checkout even before its webhook lands (Stripe is asked,
and any Checkout still open is expired first); Checkout collects the address and TRN for Stripe Tax's 5% VAT. One customer per
workspace (`app_lm_billing_customer`), made on first need, is how a webhook finds its workspace. The webhook
(`/api/v1/billing/webhooks/stripe`, public, the signature its credential, a 400 with nothing written otherwise) claims
each event in `app_lm_billing_webhook_event` in the transaction that handles it (`StripeEventHandler`): a subscription
event or paid invoice sets plan, seats, period and status unless an event created later already did
(`stripe_synced_at`); a paid invoice opening a period grants its month under the monthly reset's own key; an upgrade
tops the month up to the new plan at once (`upgrade:<workspace>:<month>:<plan>`); a paid pack is a `PURCHASED` grant
keyed on its payment intent, sized by the pack's configuration (never the session's metadata), valued at what was
paid before VAT and kept a year; a price or pack this deployment does not sell is claimed and ignored, never retried. A failed payment is `PAST_DUE`
(`past_due_since`); past `past-due-grace` (7d) the reset grants no further month until Stripe is paid.
**Seats (#744, V126)** are staff — an active member holding ADMIN or MEMBER; CLIENT alone never takes one — and
`billing/seat`'s `SeatAllowance` is the one door `workspace` calls (accepting a staff invitation, a rejoin, a role change
across the staff line, a removal). An invoiced workspace is held to its agreed `seats` while enforcement is on — a staff
invitation counting the ones still pending, an accept and a promotion each refused with 409 `SEAT_LIMIT_REACHED` until a
platform admin raises it. A Stripe-billed one is never refused: the change marks `seat_sync_due_at` in its own
transaction and, once that commits, `StripeSeatSync` sets the subscription's quantity (an added seat invoiced at once, a
removed one billing no more from the next invoice), grants each added seat its share of the month's credits for the time
left (`seat:<workspace>:<month>:<seat number>`, so a seat removed and refilled in one month is granted once), audits
`SEAT_ADDED`/`SEAT_REMOVED` and clears the mark — unless the staff moved again meanwhile. A sync Stripe refuses leaves
the membership as it is and the mark for `lightmove.billing.jobs.seat-sync`.
**Billing emails (#745, V127)** go to a workspace's `BILLING_MANAGE` holders alone — members see the chip and the
banner — from `billing/notice`'s `BillingNotices`, after the commit that caused them: a threshold crossing (80%, 90%,
used up; V124's row is the claim, so concurrent spends send one email per admin) offers Buy more credits to a
Stripe-billed workspace and Contact Uncava (`lightmove.billing.contact-email`, which `GET /billing` also carries as `contactEmail` for every Contact Uncava link in the SPA) to an invoiced one; a failed invoice (`SubscriptionPaymentFailed`) and bought
credits lapsing within a week (`lightmove.billing.jobs.purchased-credit-expiry`, daily) are each claimed in
`app_lm_billing_notice` (per invoice, per grant) in a transaction of their own before anything is sent, so a replayed
or retried event, or a second instance, sends nothing more.
**Buying (#747)**: `GET /billing` also carries the catalogue an admin may buy — `plans` (per-seat prices, credits per
seat, and the `checkoutIntervals` Stripe sells each in) and `packs` — both empty where Stripe is not offered, and to
anyone without `BILLING_MANAGE`. Settings → Billing's **Change plan** / **See plans** opens the plans dialog: a
workspace with no card goes to Checkout, one Stripe already bills to the Customer Portal (the API refuses it a second
Checkout), Enterprise is "Talk to us". **Buy more credits** (banner, meter, out-of-credits sheet) is the packs dialog
with VAT shown; **Invoices & card** is the portal. The card it names ("Visa •••• 4242") is `GET /billing/card`, a
billing manager's like the portal, asked of Stripe and never stored — an answer held a minute per instance, the page's
query a minute more — and the chip's polled read never asks Stripe. Stripe sends the admin back to
`?checkout=subscribed|credits|cancelled`, and the page polls the read until the webhook's change shows
(`lib/checkoutReturn.ts`; bought credits before Checkout are kept in `sessionStorage` to tell). An invoiced workspace,
or a deployment without Stripe, gets Contact Uncava in place of every buying control, and the invite dialog says what a
staff seat adds to a Stripe bill (`SeatCostNotice`) and, on a card, asks before sending: the seat's price, about what
Stripe charges at once for the rest of the period, and the bill after (`SeatChargeConfirm`, `seatChargeOf`).
**Trial (#771, V128)**: founding a workspace (`OnboardingService` → `billing/trial`'s `WorkspaceTrials`, in the same
transaction) puts it on Pro, `TRIALING`, until `trial_ends_at` (`lightmove.billing.trial.length`, 14d) with a `PLAN` grant
of `trial.credits` (50) keyed `trial:<workspace>` and expiring with it — no card, no Stripe, no seats of its own (fair use
counts the staff) and no monthly reset. One trial per founder (`trial_started_by`): their next workspace starts ended.
Once an app trial ends unpaid, `TrialGate` — asked first by `CreditLedger.hold`/`charge` and `FairUseGuard.check`, so
every contact find, search and AI use — refuses with 402 `TRIAL_ENDED` while enforcement is on; nothing else locks and
nothing is deleted. Checkout from the plans dialog converts it (Stripe's webhook overwrites the row), as does a platform
admin's invoicing; either ends the trial's grant there and then (`AppTrialConverted` → `CreditLedger.endEarly`, an
`EXPIRE` line), so the month's credits are the plan's alone. Billing managers are emailed 3 days before the end and
once it has (`TRIAL_ENDING`/`TRIAL_ENDED` claims). The SPA counts it down in the banner and the topbar chip, and the
ended sheet offers Choose a plan.
`trial.enabled: false` (the test profile's) founds workspaces with no subscription, as before.
V133 adds tasks and the bell: `app_lm_task` (person beside the nullable mapping, `rule_key` unique where set,
due-soon/overdue notice stamps; V132's next actions backfilled as open tasks), `app_lm_workspace_task_preset`
(seeded eight per workspace), `app_lm_workspace_task_rule` (no row is on), `app_lm_note_mention`,
`app_lm_notification` (one live row per `group_key` while unread, by partial unique index; `email_due_at`/`emailed_at`
the email claim), `app_lm_notification_preference` (no row is on) and `app_lm_notification_digest` (one per person
per local day), and widens the activity kinds with `TASK_CREATED`, `TASK_COMPLETED`, `TASK_REOPENED`,
`TASK_REASSIGNED` and `TASK_CANCELLED`.
V135 adds `app_lm_candidate_scorecard` — one per mapping (`candidate_id` unique, cascading), `status` `DRAFT |
REVIEWED` with `reviewed_at` set exactly when reviewed (CHECK), the scores a jsonb array of `{panel, name, weight,
score, evidence}` snapshots and the report a jsonb object — and widens the activity kinds with `ASSESSMENT_REVIEWED`.
V136 gives it `ai_drafted` (jsonb array: the criterion match keys and `report:<section>` still holding the AI's
untouched draft) and `ai_drafted_at`, and freezes V79's `ai_assessment` and V80's `ai_enrich_failed_at`.
V137 adds `app_lm_project.archived_at` / `archived_by` (both or neither, by CHECK), seeds `PROJECT_ARCHIVE` to LEAD
alone, and adds `POSITION_ARCHIVED` to the enrollment's `stop_reason` CHECK. V138 gives `app_lm_client` the same pair.
V139 makes the org chart's `(position_id, node_id)` uniqueness a constraint checked at commit (`DEFERRABLE INITIALLY
DEFERRED`): Hibernate rewrites the chart in place slot by slot, so removing or reordering a seat held one node id on
two rows mid-write, and V39's index refused every later save of that chart.
V140 makes every pipeline status the workspace's: it drops V131's CHECK that kept a seeded status live (its
`system_key` stays, as a wire key only), so an admin retires any status, or a stage with its statuses
(`retireStatuses`, after a confirmation read off `…/retire-preview`: who holds each, and the automations it stops).
It adds `app_lm_workspace_status_automation` — per workspace, the status each outreach event (`EMAIL_SENT`,
`REPLIED`, `CALL_BOOKED`) moves a candidate forward to, set on Settings → Pipeline stages' Move candidates
automatically (`StatusAutomationService`). No row is the seeded behaviour (an email to Contacted, a booking to
Engaged, a reply nowhere); a rule whose status was retired is reported `broken`, flagged on the page, and moves
nobody — it never fails the send or booking behind it.
V141 soft-deletes a person's note: `deleted_at` / `deleted_by` on `app_lm_person_note`, which `PersonNote`'s
`@SQLRestriction` hides from every JPA read (two native queries in `PersonNoteRepository` find and restore one), and
`NOTE_RESTORED` among the activity kinds. A deleted note keeps its task, notification and mention rows, so a restore
re-announces nobody; `NotificationEmails` asks whether a mention's note is still live rather than reading `note_id`.
V142 gives a note its format: `app_lm_person_note.body_format` and `app_lm_client.notes_format` (`PLAIN | MARKDOWN`,
every existing row and every door's note `PLAIN`), and raises a person's note to 10,000 characters. The editor
(`features/notes`, Tiptap behind a lazy chunk) writes CommonMark + GFM with `++underline++`, a mention kept as the
server's own token; the card draws it through the same schema as React, never as HTML, and links only http, https
and mailto. `common/note`'s `NoteText` (commonmark-java) reads it back as plain words for the timeline excerpt, and
as Markdown with `@Name` for mentions for the AI.
V143 adds `app_lm_company_note` (workspace, project and `triage_company_id`, ON DELETE CASCADE; kind, body ≤ 10,000,
`body_format`, pinned, author, edited and soft-delete columns) and backfills each company's V32 `note` as its first
note, by its `added_by`, leaving the column frozen; `CompanyNotePurge` deletes a deleted one for good as
`PersonNotePurge` does.
V144 widens `app_lm_outreach_message` into the conversation ledger: `direction`, `channel`, `kind`, `status`,
`project_id`/`person_id`/`candidate_id` (SET NULL), the two addresses, `thread_gateway`, `occurred_at`, and
`body_text` (≤ 20,000) with its `body_format`, each defaulting to what an old row is so the previous revision's
insert lands mid-deploy; backfilled from each row's enrollment, a first email's text from its run's `first_body`
and a follow-up's decoded from its HTML through Spring's whole entity table. `sent_at` is set only on an outbound email
that went (a CHECK), since the daily cap counts it; the `(enrollment_id, step)` uniqueness holds for sequence steps
alone. V145 fills any deploy-window row's `project_id`/`person_id` and sets both `NOT NULL`, inserts a reply placeholder
per answered run and a bounce placeholder per bounced one, adds `app_lm_mailbox_connection.reply_text_granted`
(direct Microsoft rows backfilled true), and indexes the thread, the inbound dedupe and the listening scan. V146 gives
it a consultant's own email's `send_at`, `sending_since` claim, `in_reply_to` and `fail_reason`, with the due index.
V147 adds `app_lm_outreach_message_attachment` — an own email's files in order (name, read type, size, `sha256`, the
bucket's `storage_key`, `source` `UPLOAD | PERSON_DOCUMENT` and the copied version's id, SET NULL); no bytes in the database.
V150 gives `app_lm_project_candidate` `triage_status` (`IN_UNIVERSE | SHORTLISTED | DECLINED`, backfilled from each
row's company): the Companies stage the executive is listed at, their own.
V151 adds `app_lm_meeting_recording` — one Notetaker bot per row: the call snapshotted (meeting id kept bare, never a
foreign key, since a calendar re-read replaces its rows), Recall's bot id (unique), status, Recall's last code and
sub-code, the transcript as jsonb and the note it became; one live row per call by partial unique index.
V152 gives it `suggestions` jsonb (Suggest changes' answer, null until pressed, cleared with the transcript where a retention is set),
`suggested_at` and the `suggesting_since` claim, and widens the activity kinds with `UPDATED_FROM_CALL`.
V154 adds `app_lm_interview_plan` (one per call per person on a position, matched on start and title), gives the recording
its live copilot (`live_copilot`, `live_status`, the `live` reading, the coach's cursor, runs, failures and claim, the
live URL's `live_token_hash` and the after-call `final_transcript_id` the note is filed from), and adds
`app_lm_meeting_live_line` (the streamed transcript, deleted when the call ends) and `app_lm_meeting_copilot_viewer`.
V157 gives the recording `join_at` (null for a bot sent at once, and while Join now replaces a scheduled one),
`recording_started_at` (where the call clock counts from) and End & file notes' `end_requested_at`/`end_requested_by`
(both or neither, by CHECK).
V155 adds working hours and the daily email cap (**Meetings & email** in Settings, an admin's; **Availability**, each
consultant's; `AvailabilityService`): `app_lm_workspace_availability` (one per workspace; a null `weekly_hours` is the week
the firm's country keeps — Saudi Arabia, Qatar, Kuwait, Bahrain, Oman and most of the Arab world Sunday to Thursday,
`WorkingWeeks` — a null `daily_email_cap` the deployment's `daily-cap`, at most `max-daily-cap`) and
`app_lm_member_availability` (per consultant, a null column the workspace's). A consultant's working hours are their own
first — a choice made in Uncava outranks Outlook's, which every mailbox states, chosen or not (Exchange's default is
Monday to Friday) — then Outlook's where a direct Microsoft mailbox states them (`getSchedule`'s `workingHours`, read
beside its free/busy in one call, `MailboxGateway.schedule`; a Windows zone name through `WindowsZones`), then the
workspace's; Google and Nylas state none. They shape the booking link alone — Uncava's own page and, re-sent on every change
(`updateBookingPage`), Nylas's — and a new sequence's default days and hours. The cap is the workspace's, or a
consultant's lower one, for every send — lowering the workspace's lowers any consultant's above it — and
`app_lm_mailbox_connection.daily_cap` is frozen. Both rows are written insert-if-missing first, so a double-clicked
first save writes one row.
V149 gives `app_lm_person_meeting` `zoom_meeting_id` and widens the activity kinds with `MEETING_RESCHEDULED` and
`MEETING_CANCELLED`.
V148 makes a person's time zone one clock: each user takes their latest-connected mailbox's zone (the one that sent),
the profile's old `Etc/GMT` becomes `UTC`, and every mailbox's `time_zone` is set to its user's — kept so from then by
the profile write, and taken by a new connection. Since V211/V212 that clock is settled, not stored: the zone the person
pinned, else the one their device last reported (Automatic), else their workspace's, else its country's
(`DateTimePreferenceService`, the one place the order lives).
V211 is **Date & time** — the workspace's time zone, date format and time format on Settings → General
(`PUT /workspace/date-time`, `WORKSPACE_MANAGE`, audited as a `dateTime` section), each a nullable column on
`app_lm_workspace` (null: the country's zone through `CountryTimeZones`, the standard `DD_MMM_YYYY` / `H24`), and a
person's own choice of any of them on `app_lm_user` (`timezone` now nullable, `date_format`, `time_format`; null keeps
the workspace's, chosen on Settings → Profile as "Workspace default" and applying in every workspace they are in). Every
value is a fixed set: 42 zones (`data/time-zones.json`, `StandardTimeZones`; the SPA's `lib/datetime/timeZones.ts` is
held equal to it), 5 date shapes, 2 clocks — a zone saved before the list keeps working until changed. The backfill
moves nobody's clock: a workspace took its active members' most common zone, and a person kept theirs only where it
differs from a workspace they are in. `/me` carries `dateTime` (`effective`, `own`, `workspace`); a mailbox's
`time_zone` copy is read again on `TimezoneChanged` (after commit, per workspace in its own scope) and
`WorkspaceDateTimeChanged`; server text (the digest, task emails, billing and secret-expiry emails — billing on UTC's
calendar — the lock email and the lines a call files) goes through `DateTimeFormats`, while exports and model input
stay ISO. In the SPA every date is `lib/datetime`'s, read in the effective preferences that `AuthProvider` sets and
`DateTimeBoundary` redraws on; `lib/dateRatchet.test.ts` refuses a browser date formatter anywhere else, and time
pickers are `TimeInput` (labelled in the person's clock), never a native time input.
V212 makes a person's time zone **Automatic** by default — it follows their device, as Slack's, HubSpot's and Teams'
do — and the workspace's zone a fallback, for a member whose browser has not yet reported and for workspace emails.
`app_lm_user.device_time_zone` (and `_at`) is the zone the person's browser last sent (`PUT /auth/me/device-time-zone`,
any region zone the JDK knows, the fixed list's or not; rate-limited, not audited, nothing written when unchanged); a
null `timezone` is Automatic and a set one is pinned in Settings → Profile. A change publishes `TimezoneChanged` only
while Automatic, so the mailbox's send window and the digest follow the person. The backfill moves nobody's clock: each
person's device zone is seeded with the zone they read in, and a zone stays pinned only where the audit trail shows
they chose it (the latest `PROFILE_UPDATED` with a `timezoneTo` Postgres knows as a zone, read in RLS maintenance). In the SPA an Automatic person reads in
this browser's zone at once (`automaticAware`), `AuthProvider` reports it once per zone per tab
(`features/auth/deviceTimeZone.ts`), and `TimeZoneMoveNotice` says a move aloud until answered (held in the browser,
`features/auth/timeZoneMove.ts`) — *Keep London* or *Stay on Dubai time*, which pins the zone they came from and is
offered only where that zone is on the fixed list.
V162 adds two-factor authentication (#1081): `app_lm_user_mfa_factor` (one per user, the authenticator secret sealed by
`core/crypto` under `EncryptionContext.ofUser`, `enabled_at` null while setup waits for a first code, `last_used_step` the
replay guard), `app_lm_user_mfa_recovery_code` (SHA-256 per code, spent by a conditional update) and
`app_lm_mfa_challenge` (a right password's single-use challenge, hashed), and adds `MFA_CHANGED` to the refresh token's
revoke reasons. Account data, keyed on the user, never a workspace. Offered only where `lightmove.auth.mfa.enabled` and a
keyset are both set; off in deployments until its rollout.
An account with no password (a Google or LinkedIn sign-in) turns two-factor on with the code alone, only within
`lightmove.auth.mfa.recent-sign-in` of signing in (`MFA_SIGN_IN_AGAIN`), and turns it off or takes new recovery codes
with the code alone.
V165 indexes `app_lm_audit_event (workspace_id, id DESC)` for the customer audit log, guarded on ownership as V74 is:
once `harden.sql` has run the index is its to build (`CONCURRENTLY`). An index only; no row is written.
V184 forces row-level security on the people and position core (#1084; the rest is #1085): `app_lm_tenant_visible`
over `app.workspace_id`, set on every connection checkout by `TenantAwareDataSource`; a person's children are visible
through their person, and `app_lm_project_candidate` / `app_lm_project_triage_company` gain their project's
`workspace_id` (filled by trigger, held by a composite key). Flyway's own session sets `app.rls_maintenance` until
#256; the runtime role must be no superuser and no `BYPASSRLS`, or `RowLevelSecurityPosture` refuses the boot.
V185 gives `app_lm_person_note` `occurred_at` (when a logged call, LinkedIn message or meeting happened; null on a
plain note, by CHECK, and on every older note, where `created_at` stands in), `title` and `meeting_id` (a meeting's
notes only, by CHECK; bare, as V151's recording keeps its meeting's id).
V188 adds `app_lm_person_note.recording_id` (the Notetaker recording a meeting's notes are about, where the calendar no
longer holds its meeting; bare, a meeting's notes only and never beside a `meeting_id`, by CHECK).
V189 adds the `app_lm_resolve_*` functions — `SECURITY DEFINER`, one key each, ids and workspace out — that a door with
no principal asks before opening its scope (`TenantResolvers`), and `app_lm_trial_started_by`. V190 forces row-level
security on every other tenant table (#1085): a direct policy where the table has a `workspace_id`, one through the
parent where it has none; a library template (null workspace) is everyone's to read, and the audit
trail takes every insert and is read only in its own workspace or the account's. V192 lets only a `SharedRows` scope
(`app.shared_rows`) write a library template or reach a grant awaiting consent at all. `app_lm_audit_event` and
`app_lm_credit_entry` are harden.sql's once it has run (step 2c), which must be re-run after V190 deploys.
V191 adds the job resolvers (`app_lm_workspaces_with_*`, `app_lm_workspaces_owing_seat_sync`) — the same idiom,
workspace ids out — so the dispatcher, the copilot coach, the bell's emails, the due scan, the seat sync and the ledger
sweep walk only the workspaces with their work; each condition is wider than its job's claim, and changes with it.
V193 adds `app_lm_user.phone` / `linkedin_url`, `app_lm_email_image` (a photo's owner or a logo's workspace, and the
source's SHA-256, by CHECK; one copy per avatar per person and per logo per workspace) and `app_lm_email_signature` (`fields` jsonb holds only the
overrides; one default per person per workspace by partial unique index), both under forced row-level security —
a photo row, account data with no workspace, read wherever its person signs in — and `app_lm_resolve_email_image`, the
public picture route's resolver.
V194 adds `app_lm_outreach_enrollment.signature_html` — the signature Include signature chose at Start, closing every
email of the run; null sends none.
V205 adds `app_lm_email_template` (owner and workspace, both cascading; `shared_at` null is personal; `updated_by` the
last editor; one-line subject and non-blank body by CHECK) under forced row-level security, its owner and shared
indexes, and `app_lm_outreach_message.template_id` (SET NULL, partial index) — the template an own email came from;
account deletion deletes a person's templates explicitly (`AccountDeletionOutreach`), since V203 never deletes the user
row. V206 indexes a sender's emails that came from a template, for Recently used.
V207 takes the `{{signature}}` token (#1277) out of every `app_lm_outreach_sequence_step.body` and
`app_lm_outreach_enrollment.first_body`, its gap closed to one blank line; no schema change, `signature_html` kept.
V208 does the same to the step's `subject` and the frozen `first_subject` (a step subject left empty is null).
V209 seeds `PERSON_SUBJECT_ACCESS` to ADMIN alone: the subject access export (#919).
V210 adds the `SIGNATURE` kind to `app_lm_email_image`: a Custom signature's own uploaded picture, carrying both its
person and its workspace (V193's policy already fences a row with a workspace), one per file per person per workspace
by partial unique index on the content's SHA-256.
V216 adds #922's four AI switches to `app_lm_workspace` (`ai_infer_gender`, `ai_infer_nationality`,
`ai_draft_assessments`, `ai_enrich_automatically`): every workspace that existed is backfilled `true`, then the default
becomes `false`, so a workspace founded since starts with all four off (`docs/gdpr/ai-controls-922.md`).
V217 adds `candidates.demographics:read` to the scopes CHECKs of `app_lm_api_key` and `app_lm_oauth_client`: gender and
nationality leave over the public API and MCP only under it.
V195 adds `app_lm_workspace_suppression` (`kind` `PROFILE_SLUG | EMAIL`, a keyed `value_hash` unique per workspace and
kind, the `key_id` it was taken under, `reason` `ERASED | OBJECTED | MANUAL`, an admin's `note`), forced row-level security
in the same migration.
V196 seeds `PERSON_ERASE` to ADMIN alone and adds `app_lm_storage_deletion` (a bucket key per row, its attempts and
next attempt; global by design, so outside row-level security), drained by `StorageDeletionSweeper`
(`lightmove.storage.deletion-sweep-interval`) with a back-off to a day, never giving up.
V204 adds `app_lm_ranking_scenario` — a position's one ranking scenario (unique per project, composite key to the
project's): `technical_share`, `weights` (competency match key → weight), `saved_against` (the brief's weighting at the
save) and an optional `rationale`; no row, or one cleared back to the brief's weighting (kept so its version keeps
climbing), is the brief's own weighting, and what a scenario left at the brief's weight or share follows later brief edits. Forced row-level security in the same migration.
V197 adds `app_lm_recall_bot_erasure` — an erased or restricted person's Recall bots (bot id, whether to call it back, whether its
media is still held), global for V196's reason, drained by `RecallBotErasureSweeper`
(`lightmove.recall.bot-erasure-sweep-interval`) with a back-off to an hour, given up only past Recall's 7-day retention.
V202 gives `app_lm_workspace` its deletion and purge columns (`deleted_at`/`deleted_by`, `purge_after`,
`purge_notice_email` with the `purge_notice_name` its certificate email gives, `purge_started_at`, the claim's `purge_claimed_until`, `purged_at`; a workspace deleted before it
was given 30 days from the deploy), `app_lm_workspace_member.ended_with_workspace`, `WORKSPACE_DELETED` among the
enrollment's stop reasons, `app_lm_workspace_export` and `app_lm_workspace_purge_certificate` (both under forced
row-level security; the certificate is never purged), the resolver `app_lm_resolve_workspace_exports_to_close`, and the
platform action `WORKSPACE_LIFECYCLE_MANAGE`.
V203 gives `app_lm_user` `deleted_at` (set only on a `DELETED` account, by CHECK): account deletion anonymises the row
rather than deleting it.
V168 adds `app_client_id` to `app_lm_mailbox_connection` and `app_lm_zoom_connection` — the OAuth client a direct
mailbox or a Zoom account was connected through; null for a Nylas mailbox and for every row before it (no backfill
could tell which app it was), recorded by the first refresh an app answers — and clears a secret expiry left on a
Google or Zoom row, since only Entra's expire.
V169 gives `app_lm_workspace_mail_integration` the saved app's read-only facts: `secret_hint` (the last four),
`secret_saved_at`/`secret_saved_by`, and `keys_checked_at`/`keys_refused_with` (the provider's last answer, its code
when it refused) — all null on a shared row (CHECK). No backfill: no SQL can read the ciphertext, so an older
secret's hint is read from it at runtime and never written back.
V170 adds `app_lm_user_avatar` — a person's profile photo held by us (account data, one per user): their own upload
from Settings → Profile (cropped in the browser, re-encoded by `core/security/avatar`'s `AvatarImages` to a ≤ 512px JPEG,
JPEG or PNG only) or a copy of the picture their sign-in provider named, downloaded once a sign-in has completed through
`HttpPictureFetcher` (on `core/http`'s `GuardedHttpClient`, the client id metadata fetch's guard: every resolved address
public, no redirect, a size cap, one deadline) — and `app_lm_user.avatar_url` names it as the public
`/api/v1/avatars/{token}`, a new token per picture, cached privately for a year. V25's `USER` source is written: an upload
or a removal no provider replaces (`User.mayTakeAvatarFrom`), and once a workspace can enforce its identity provider's
sign-in, `isAvatarEditable` is where its directory photo becomes final. It clears every provider link stored before it,
which expired and, once dead, kept every other provider out: the next sign-in copies the picture.
V213 is the Find executives panel: `app_lm_executive_sourcing_criteria` (one per position — the search as `criteria`
jsonb, `source` `DEFAULT | AI | EDITED`, the `brief_fingerprint` it was drafted from, unique on the project) and
`app_lm_executive_sourcing_suggestion` (one row per person a run found at a company: the profile slug, `verdict`
`FITS | NO_TITLE | NOT_FIT`, `score`, `rank`, `reasons text[]` — our codes and our search's words, never a name or a
title, which are read from the people cache — and `found_by` `LIVE | INDEX | BOTH`; unique on run, company and slug;
no status, since "added" is read from the position's mapped profiles; deleted by the next run once older than the
people cache's life). The run gains its `criteria` snapshot, a `suggested` count, `stop_requested_at` and a `STOPPED`
status; its `spec`, `executives_filed` and `model_calls` columns are frozen, read by nothing since, and the names of
whom older runs filed are taken off their `outcomes` (an erasure forgets a person's suggestions by slug). Both new
tables are under forced row-level security.
V214 adds `photo_shape` (`CIRCLE | ROUNDED | SQUARE`, default `CIRCLE`) to `app_lm_email_signature` and drops V193's
`app_lm_email_signature_custom_chk`: `custom_body` is now any layout's extra words, not only Custom's. A picture's own
shape is a word in its Markdown title; its cut copy is another `SIGNATURE` row of `app_lm_email_image`.
V215 adds `app_lm_executive_sourcing_run.progress` (jsonb, `{}` by default): each started company's step
(`LOOKING | CHECKING | CONFIRMING | RANKING`), the people considered, a counted step's done/total, and its start and
finish in epoch millis, keyed by triage company id — counts and times only, no person. Written by one merging `UPDATE`
that leaves the row's `version` alone, so companies searched side by side never fail the outcome writes; the entity
maps it read-only. Under the run's existing row-level security.
V167 adds `app_lm_project_candidate.company_filed_for_them`: their company was filed for them (research, or the
employer the workspace knew them at) rather than already held, cleared by any move; only the plugin's Undo reads it.
V163 puts **out of process** on the status (`app_lm_workspace_candidate_status.out_of_process`) and makes every stage
alike: whoever holds a flagged status sits in the board's Out of process band, skipped and stopped by outreach (`LEFT_THE_RUNNING`),
moved by no automation and left out of the projects list's Candidates and Engaged — whatever stage it sits under, so
`hasLeftTheRunning()` and every query read the column alone. The stage's `kind` is frozen (unmapped, its old values
kept so a revision still reading it during the deploy treats an old Declined stage as closed; a cleanup drops it): the
old closed stage's statuses were backfilled flagged and lost their default mark, and it is now a stage like any other,
reordered and retired freely. A stage's default is always a live status in process (`PIPELINE_DEFAULT_IN_PROCESS`),
since a card dropped on the stage lands there, so a stage of ways out alone has none and the board draws no column
for it (`PipelineCatalog.holdsAStatusInProcess`; Settings says "Not on the board"). The pipeline's edits are three
services by subject — `PipelineCatalogService` (reads and the confirmations' previews), `PipelineStageService`,
`PipelineStatusService` — over `PipelineRules`, `PipelineDefaultMarks` and `PipelineEditAudit`. The seed
(`app_lm_seed_candidate_pipeline`) is now ALAC Partners' own five stages — Research, Approach, Assessment, Shortlist,
Offer, each with its ways out flagged — under the legacy stage and system-status keys, and a workspace still holding
V131's seed to the letter, that nobody's candidates hold and no automation names, was reseeded onto it; every other
catalog was left as it stood. The public API and MCP no longer carry a stage `kind`; an executive's `outOfProcess`
says it.
V134 indexes the references V133 left bare (a task's candidate and note, a notification's candidate, task and
note), partial where set, so unmapping someone or deleting a note never scans the notification table.
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
**The workspace's currency is the one every new figure starts in** (#1291): a workspace founded from a picked
firm starts in its country's (`Currencies.startingCurrencyFor`, the JDK's ISO 3166 → 4217 table; AED with no
country), an admin changes it in Settings → General (`@CurrencyCode`, audited), and a brief drafts in it
(`PositionBriefLoader`, through `WorkspaceSettingsService.defaultCurrencyOf`) — the shared library's currency
never replaces it, a firm's own template's does — so a package follows the brief. A change relabels and
converts nothing already recorded. `Currencies.isKnownCode` is the one accepted set (historic codes included);
every picker offers `GET /currencies` (every currency a country uses today) through `CurrencyOptions`.
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
unit expects the map or the shortlist) and, on a search only, `mapping_target_date`, entered
by hand beside the shortlist date and never defaulted (`ProjectTimeline`). `target_date` is untouched
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
company (`HiringSideResolver` → `HiringContext`, the agency named in one line). In-house the same column is
a **business unit's persona** (#1048), edited in the unit's drawer through the same PUT: once the mandate's unit
has recorded anything, the prompt and starters read the unit as the hiring company, each list it left empty
(sectors, competitors, geographies), its place, and its industry while it names no sectors taken from the firm's
(`HiringPersona.fallingBackTo`, `HiringCompanyProfile.within`), with the firm after it as the parent group —
name, place, size, summary and notes, never its sectors or competitors; a unit with nothing recorded reads as
the firm alone, as before. No migration: V85's column was already on every client row. That drawer is the company panel Strategy opens — `CompanyDrawerHeader` and
`CompanyFactsSections` from the universe (`GET /companies/{apolloAccountId}`, the id on the client
detail), or editable basics for a client typed in by hand — never the business-unit record.
V57 adds the `PLATFORM` role scope and `app_lm_user_platform_role` — written by
`grant-platform-role.sh`, never by the application.
`app_lm_position_document` holds the attached position description inline (`bytea`) — one small file per
mandate, read back only by its own download endpoint. Everything else (roles, hardening, grants) →
`db-ops` skill.

