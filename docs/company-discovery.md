# AI Research: searching the market we do not hold

## Context

`docs/assistant-tools.md` covers the tools a model may call inside a conversation. This one covers
the other half of the same problem: a consultant on the Strategy screen asking a question the
company universe cannot answer.

`app_lm_apollo_companies` is 100,631 rows with lopsided coverage — #443 records
`information technology & services` at 9,982 rows against `computer software` at **16**. A mandate
can legitimately find nothing. Until now Strategy searched that table and nothing else, so "who else
is out there" had no answer on the screen where it is asked; the work left the product for a browser
tab and came back as manual entry.

`Strategy.dc.html` has reserved the button all along, and says what it does:
*"AI research started — results stream into the table"*, with a Fit score per row and the grid sorted
by it. This is that.

## The rule

**The model may propose identifiers and a reason. Every figure comes from a record.**

A name, a LinkedIn page and a homepage are *addresses*: checkable, and only ever used as lookup keys.
A headcount, a revenue, a country, a city, an industry or a founding year is a *claim*, and a claim
the model made would reach a client-facing report indistinguishable from one somebody checked. That
is the failure this feature exists to prevent, and it is the same invariant `POST /triage` already
enforces from the other direction — the caller sends an id and the server resolves the snapshot, so
"a client cannot file a company under a name of its own choosing".

The rule is enforced in three places, and only the third would survive a careless edit:

1. **The prompts** say it three times, in the words a model reads.
2. **`ModelDiscoveryAnswer` has no field for a figure**, so a model that volunteers one has nowhere
   to put it and the value is dropped at the binding.
3. **`DiscoveredCompanyDto` has no public constructor.** Three named factories build one —
   `fromUniverse(row, …)`, `fromVendor(facts, …)`, `unresolved(candidate, …)` — and the third is
   handed the model's own answer and passes null for every figure. A line taking a headcount off a
   `DiscoveredCandidate` cannot be written. Discipline would have needed a reviewer to notice this;
   this needs a compiler.

The one number the model does supply is `fit`, and it is not an exception. It scores relevance to the
question that was asked, which is a judgement about the answer rather than a claim about the company.
It is drawn on the grid and never stored on a filed row.

## Where it lives, and why not in `strategy`

#464 proposed `strategy/discovery/`. It cannot live there. Discovery needs three things from three
places — the universe from `strategy`, the mandate's already-filed companies from `triagecompany`, a
vendor's record of a LinkedIn page from `enrichment` — and `strategy/package-info.java` documents its
dependency direction as one-way with exactly one bridged exception (`TriagedCompanyLookup`). Two more
bridges, plus a record duplicating `CapturedCompanyDetails` to keep the types clean, is more
machinery than the job needs.

`dataexport/package-info.java` already describes the shape: *"This package reads nothing of its own…
three seams, none depending back."* Five features are built that way, and `assistant` already imports
both `strategy` and `triagecompany`. So `companydiscovery` is a composer, and nothing depends back.

## The three steps

### Discovery — who is out there

`CompanyDiscovery` is the port, in the `LinkedInCompanyEnricher` shape: `discover`, `provider()`,
`isEnabled()`. The provider is a yml block and never a branch on a provider name.
`OffCompanyDiscovery` is the unconfigured deployment, and `isEnabled()` exists for the reason the
enrichment port documents — without it every question asked before a key is configured would be
charged against the workspace's day and answered with nothing.

`GeminiCompanyDiscovery` uses Google Search grounding, which is the Spring-native answer to "search
the web": no Custom Search key, no second HTTP client, no new dependency. Two provider facts shape
it, both checked against the 2.0.1 jars rather than the documentation.

**Vertex has historically refused grounding and a response schema on one call.** So there are two
paths: one grounded structured call, and — where that is refused — grounded prose followed by a
second, **ungrounded**, call that only transcribes it into the schema. Two billed calls instead of
one, which is why the first is tried at all. Dropping grounding to keep the schema is deliberately
not on the menu: a discovery answer with no web access is a different product wearing the same
button.

The refusal is detected by walking the exception's causal chain for a 400 / `INVALID_ARGUMENT` that
names the schema, and **that is string-sniffing, stated as such in its javadoc**. The provider wraps
every SDK failure in a bare `RuntimeException`, so there is no type to switch on. It is a cost
optimisation and never a correctness gate: guessing wrong in either direction lands on the same prose
path or the same empty answer, and all it decides is whether the next request pays for the probe
again. The latch is per process; a region whose answer is known sets
`lightmove.company.discovery.grounded-structured-output: false` and never probes.

**Spring AI does not surface grounding metadata.** Nothing in the provider copies `groundingMetadata`
onto a `Generation`, so there is no citation list on the response. Source URLs come back inside the
model's own answer or not at all, which is why `sourceUrl` is a schema field and why a missing one
stays null rather than being synthesised.

`DiscoveryMode` — `GROUNDED_STRUCTURED`, `GROUNDED_PROSE_EXTRACTED`, `UNAVAILABLE` — is on the wire,
in the audit row and on the panel. The fallback is never silent, and an empty market and an
unreachable provider are different answers.

**There is no local fallback**, unlike `ColumnMappingProposer`, which degrades to a heuristic matcher.
A keyword scan of our own universe returned under an "AI Research" heading would be indistinguishable
to the user from a web search while being its exact opposite.

### Resolution — do we already hold this

Free, internal, and the whole cost gate. `CandidateResolver`, four keys, first hit wins:

1. **The LinkedIn slug** the model cited — `matchEmployer(slug, null)`. The strongest key: a page it
   actually read, matched exactly.
2. **The homepage** — `matchByDomain`, new beside `matchEmployer`. Weaker, because a plausible domain
   is easy to produce, but exact all the same.
3. **An exact unique name** — `matchEmployer(null, name)`, the existing unique-or-nothing tier.
4. **The vendor**, and only for a real slug: `LinkedInUrls.companySlugOrNull` is the billing gate here
   exactly as it is for contact lookup, and `CompanyResearch` means the same page is not bought twice
   across the whole platform. A vendor answer is then tried against the universe once more **on its
   own website**, because a slug the universe misses may be a company it carries under a domain.

Otherwise the row is unresolved: the name, the page, the reason, and every figure null.

`matchByDomain` deserves its own note. The `LIKE` is a prefilter and `WebsiteDomain.of` is what
decides, applied to both sides in Java — a regex in SQL would be a second definition of "domain"
drifting from the one every other caller uses, and `%acwapower.com%` alone matches
`notacwapower.com.tr`. Two rows on one domain answer nothing rather than the first, because a
subsidiary and its parent share a homepage. It is a **sequential scan of the whole universe per
call, and no index can be added**: V23 records that the deployed table is owned by the pipeline's
account rather than `lm_app`, so `CREATE INDEX` there fails with "must be owner of table".
Affordable at the couple of dozen calls a capped answer makes, behind a model call that already costs
seconds; batching every domain into one scan is the fix for when it is not.

**`source` and `alreadyInMandate` are two fields, not one enum.** They are independent facts, and
conflating them is how a universe row a mandate already holds ends up badged as a web find.
`MandateCompanyReader` reads the four identifying columns in **one** query per request, keying names,
domains and slugs through the same two functions the resolver uses on the candidate, so both sides are
keyed identically rather than nearly identically.

### Filing — through the doors that already exist

No new write path, no new scope check, no new audit site. The frontend splits the ticked rows:
those with an `apolloAccountId` go through `POST /triage/bulk` and land as `STRATEGY` with the server
resolving the snapshot; those without go through `POST /triage/capture` with `source: "web"` and only
what a record supplied. `TRIAGE_COMPANY_ALREADY_HELD` counts as a skip, which is the same arithmetic
`AssistantProposalService` performs server-side.

V68 adds `WEB` to the triage source CHECK. **The apollo-source CHECK (V34) is untouched and is what
the split leans on** rather than works around: it says a `STRATEGY` row must carry a universe id, and
that is exactly true of the half that files by id. Widening it would break the guarantee, not extend
it. `app_lm_project_candidate_source_chk` is deliberately left alone — discovery proposes companies,
never executives, and a spelling the schema permits with no code behind it is a claim nothing tests.

`WEB` is capturable but deliberately **not** in `SUPPLIED_ONE_AT_A_TIME`. That set drives a second
market resolution discovery has already performed, and `announceForResearch`, which fires a billed
vendor lookup per filed row against no cap at all — a consultant filing twenty discovered companies
is what turns that from a rounding error into a bill. `ASSISTANT` is excluded for the same reason.

## What it is allowed to cost

Two ceilings, and they are not substitutes.

`LlmBudget.COMPANY_DISCOVERY` is the familiar one: per user, per minute, in one JVM's heap. It makes a
double-click cheap. It is coarser than every other meter by a factor of two or three, because one
request here can be a grounded call, an extraction and a repair where the others count one call per
request.

`WorkspaceDailySpend` is the new one, and the first ceiling in this application that survives a second
Cloud Run instance. #430 already reached the verdict — *"in-memory is fine on one instance; it is not
fine as the thing standing between you and a bill"* — and this is that counter, built here because AI
Research is the first button that needs it. One row per workspace, meter and UTC day; claim and count
in a single upsert whose `WHERE` guards the `DO UPDATE` branch, so **no row back is the refusal** and
two requests racing on the last slot cannot both take it. `REQUIRES_NEW`, so a caller that later fails
cannot un-spend what it was billed for.

Three details worth keeping:

- **The date is pinned to UTC in the statement**, never `current_date`. Two instances in two session
  timezones would disagree about when the day turned, and the disagreement would read as a firm
  getting two days' budget.
- **A ceiling of zero is refused in Java.** The `WHERE` guards only the update branch, so zero would
  still admit the day's first search. Turning the feature off is `enabled: false`.
- **The day is claimed before the provider is asked**, so an outage burns a search. The obvious fix —
  a compensating decrement — is worse: a refund is a second write that can itself fail or race, and
  double-spend or a negative counter is a worse failure than the one it removes. The default is sized
  for it.

`meter` names the workload rather than the feature, so #430 inherits this table rather than building
a second. What is left to that issue: the assistant's own meter, `DefaultToolCallingManager`'s native
`maxTotalToolCalls` / `maxCallsPerTool`, per-turn **token** recording into V65's existing columns,
`UsageAccumulator`, and the Micrometer chat observations. This one counts *searches*, not tokens — a
coarse brake, not a meter.

## The endpoint

`POST /api/v1/companies/discover`, on `CompanySearchController`'s path prefix and deliberately in a
class of its own: that one's javadoc states nothing there is writable or scoped to a mandate, and
this spends money, writes an audit row, increments a counter and takes an optional mandate. A `POST`
because it spends — this is not a read a back button may repeat. `GET .../discover/config` lets the
toolbar draw a disabled CTA rather than one that answers 503.

Both are gated `@PreAuthorize("@workspaceAuthorizer.can(principal, 'PROJECT_BROWSE')")`, the same
expression the four search routes use. `PROJECT_BROWSE` is ADMIN and MEMBER only (V6:109-112), which
is what keeps a pure client representative off the market side. Unlike the assistant's tools this is
an ordinary request-thread read, so `@PreAuthorize` works here where it cannot on a worker thread —
which is why #464 was never blocked on #425.

**`projectId` is an optional body field, not a path variable.** The mandate is not what is being
searched; it only decorates the answer with "you already hold this". It is authorised on its own
against `WORK_VIEW` before it is used for anything, and `ProjectAccess.requireAction` 404s a project
outside the caller's workspace before the admin bypass, so another firm's mandate is not visible
without a second check. A path variable would have claimed the mandate scoped the read, which becomes
a lie the first time `PROJECT_BROWSE` and `WORK_VIEW` disagree.

The limit is **refused, not clamped**, the rule `CompanySearchController` already held and now shares
through `CompanySearchLimits` — a silently narrowed limit is a wrong answer the caller cannot tell it
got. (Two comments claiming the opposite were corrected in the same change.)

Audited as `WorkspaceEventType.COMPANY_DISCOVERY_RAN`, in `ProjectExportService`'s idiom rather than
as the read it resembles, for both of that one's reasons at once: it spends the firm's money on a
named person's behalf, and it brings names from outside the product into it. Hitting the cap is the
same event, `.failed().reason("daily_cap")`. **The question text is not a detail** — it is the
consultant's own research thinking, and the ledger is not where that belongs.

## The screen

The CTA stops opening the assistant — #432 wired it there when discovery was still a toast stub — and
opens a question panel over the grid. The assistant is unchanged and still reachable from its own
launcher.

**One grid frame, two row sets.** Widening `CompanyResult` with optional `ref`/`fit`/`reason` was the
alternative and is worse: the market's rows key on an Apollo id and are paged and sorted by the
server, an answer is one capped list in which half the rows have no id at all, and carrying both
would make every market cell conditional to serve two columns that exist on only one of them. So the
frame, the tick boxes and the floating bar are shared, and the contents swap.

**The web query is kept out of the saved Apollo filter.** That is the trap the screen is built
around: bands, sector groups and market segments are the universe's own vocabulary, chosen so a SQL
predicate can be built from them, and a question put to a web search is a sentence. The panel holds
its own state, never calls `applyFilter`, and never invalidates `STRATEGY_KEY` — and a test asserts
`putFilter` was not called after a search.

Discovery is a **`useMutation`, not a `useQuery`**. It spends the workspace's day, so a key-driven
refetch on focus or remount would bill a firm for a window resize. Only the config read is cached.

The market grid's Fit column stays an em-dash, and its comment now says why rather than promising a
fill: a fit score is relevance to a question, and a filter is not a question. The populated column is
on the discovery grid. A row the mandate already holds cannot be ticked — `SelectionCheckbox` gained
a disabled state so it is drawn and explained rather than missing, since a gap in a column of tick
boxes reads as a rendering fault.

## Deliberately out of scope

**The Bright Data MCP server.** We already hold their dataset Search API behind `VendorClientFactory`,
`VendorRateLimiter`, `VendorCallGuard`, `@Retryable` and the `app_lm_vendor_company` cache. Extending
that to a real multi-condition filter is a *second adapter behind this same port* and is cheaper than
any new infrastructure. An MCP server is a tool surface for a model to choose from; its home is the
assistant's tool loop through `spring-ai-starter-mcp-client`, which is not currently a dependency.
The port is what would make either a small change.

## Verification

- `CandidateResolverTest` — an unresolved row keeps every figure null and drops the homepage that
  failed to find anything; each of the four keys resolves; a name the universe holds twice resolves to
  nothing rather than a guess; a vendor answer whose website re-resolves lands as `universe`; nothing
  is bought without a slug; one company named twice is one row; `alreadyInMandate` is proven
  independent of `source`.
- `GeminiCompanyDiscoveryTest` — the grounded structured path is one call and reports its mode; a
  refusal naming the schema falls through to a grounded prose call and an **ungrounded** extraction,
  asserted per call; the latch means the probe is not paid for twice; any other failure is
  `UNAVAILABLE` and empty; a blocked question asks nothing and is not a company called by the marker.
- `CompanyDiscoveryIntegrationTest` — the three badges end to end, an unresolved row's empty figures,
  `alreadyInMandate` against a real triage row, the two filing doors landing `strategy` and `web`, an
  over-limit refused before anything is billed, and an unconfigured deployment refusing rather than
  answering an empty market.
- `CompanyDiscoverySpendIntegrationTest` — the day runs out, the refusal costs the provider nothing,
  the cap is audited as a failure naming `daily_cap`, and one firm's day is not another's.
- `WorkspaceDailySpendIntegrationTest` — the ceiling holds, a concurrent pair does not both pass, a
  new UTC day resets, and a ceiling of zero is refused rather than admitting the first call.
- `ApolloDomainMatchIntegrationTest` — a homepage finds its company however either side spells the
  URL; a substring the `LIKE` catches and the parse does not is no match; two rows on one domain
  match neither.
- `WebCompanySourceIntegrationTest` — a web row files with no universe id and is not researched; it
  cannot become a `STRATEGY` row; a caller still cannot supply `strategy` through capture.
- `CompanySearchAuthorizationIntegrationTest` — a pure client representative is refused both new
  routes, and another workspace's mandate is a 404 rather than a 403.
- `StrategyPage.test.tsx` — the typed question reaches the API and the saved filter is not written;
  the badges and the empty cells render; a held row cannot be ticked; a mixed selection files through
  both doors with no model figure on the captured row; the cap renders its own wording; the CTA is
  disabled where no provider is configured.

Run: `cd apps/api && ./mvnw -B test-compile && ./mvnw test` (Docker, for Testcontainers), and
`cd apps/web && npx vitest && npm run build`. By hand: `npm run dev` plus `npm run dev:db:apollo`
once, then the `verify` skill — discovery needs Vertex, so that path wants `npm run dev:cloud` or
Application Default Credentials.
