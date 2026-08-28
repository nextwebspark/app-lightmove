# AI in LightMove — Position Brief Extraction & Candidate Ranking

> **Purpose of this document.** Two AI features are wanted: (1) uploading a position description
> auto-fills the Position screen *and* drafts the mandate's Strategy filter, and (2) once a mandate's
> candidates are mapped, a second pass ranks them against that brief. The question on the table is
> **Spring AI or LangChain4j**. This document answers that, then specifies both features against the
> schema, the packages and the invariants that already exist in this repository — so the build starts
> from ground truth rather than from a blog post.
>
> Nothing here is built yet. `app_lm_position_document` (V16) is the one piece of schema that was
> written in anticipation of it and is currently unused by any Java code.

---

## 1. The short answer

**Use Spring AI 2.0.** Wrap it behind our own `service` interface in `core/ai` so the choice is
reversible in one class.

| | **Spring AI 2.0** | **LangChain4j (+ Spring starters)** |
|---|---|---|
| Boot 4.1 support | Designed for Boot 4.0/4.1 + Framework 7 | Boot 4 supported via separate `-spring-boot4-starter` artifacts |
| Release maturity | **2.0.0 GA** (12 Jun 2026) | Every current release is `-betaNN` (e.g. `1.19.0-beta29`) |
| Jackson | **Jackson 3** — matches this repo | Jackson 2 in places; a second mapper on the classpath is exactly the trap the skill file warns about |
| Version management | `spring-ai-bom`, moves with Boot | Pin each starter yourself |
| Provider config | Auto-config from `application.yml` properties | Auto-config from `application.yml` properties |
| Declarative API | `ChatClient` fluent API + `.entity(Class)` | `@AiService` interfaces — genuinely nicer ergonomics |
| Provider breadth | Consolidated on the majors (Anthropic, OpenAI, Bedrock, Google, Mistral, DeepSeek, Ollama) | Wider, including long-tail providers |
| Structured output | `BeanOutputConverter` / `.entity()`, plus a validation-retry advisor | JSON-Schema-backed structured outputs |
| Document parsing | Tika reader module | Tika parser module |

### Why Spring AI wins *here* specifically

1. **The stack is already the deciding factor.** This repository runs Boot 4.1, Framework 7, Jackson 3
   — the exact triple Spring AI 2.0 was cut for. LangChain4j supports Boot 4, but through a parallel
   artifact family that is still beta, and its Jackson lineage is the kind of "compiles, then fails at
   runtime with no ObjectMapper bean" problem the `java-spring-development` skill already lists as a
   known trap.
2. **GA beats beta for a tenant-facing write path.** Extraction writes a client's position brief and
   ranking writes an opinion about named executives. Neither is a place to carry a `-beta29` dependency
   whose API can move between patch releases.
3. **It matches how this codebase already treats providers.** The invariant is *"an identity provider is
   a yml block — never branch on a provider name anywhere."* Spring AI's model auto-configuration is
   the same shape: the model is a yml block, the code holds a `ChatClient`. That is a one-line
   restatement of a rule the codebase already enforces, not a new pattern to learn.
4. **One dependency-management story.** `spring-ai-bom` sits beside `spring-boot-starter-parent` and is
   versioned in step with it. We already carry four hand-pinned versions in `pom.xml` (`bucket4j`,
   `cloud-sql`, `logstash-encoder`); a fifth family of hand-pinned betas is avoidable.

### Where LangChain4j is genuinely better, and why it doesn't change the answer

`@AiService` is the nicest declarative LLM API in Java — you declare an interface, annotate the system
and user messages, and get a typed result. Spring AI's `ChatClient` + `.entity(...)` is two more lines
per call. That is a real ergonomic loss and it is worth naming honestly. It is not worth a beta
dependency and a second Jackson on the classpath in a multi-tenant application. If LangChain4j's Boot 4
starters reach a stable release and the ergonomics still matter, the port below makes switching a
single-class change.

### The escape hatch (do this on day one, not later)

The codebase already has the pattern: `EmailSender` and `RateLimiter` are plain `service` interfaces
with a swappable implementation, so a provider change never reaches a caller. Do the same:

```
core/ai/
  config/   AiSettings                     (a *Settings record per §4 — never a nested record)
  model/    BriefExtraction, CriterionDraft, CompetencyDraft,
            MarketDescriptors, CandidateAssessment, CriterionVerdict
  service/  StructuredCompletion           (the port — the ONLY type that knows Spring AI exists)
            SpringAiStructuredCompletion   (the adapter)
            PromptBudget                   (token ceilings, truncation policy)
```

```java
/** Sends one prompt and binds the answer to a typed record. The only seam onto an LLM provider. */
public interface StructuredCompletion {
    <T> T complete(CompletionRequest request, Class<T> shape);
}
```

Everything above this line — `PositionBriefExtractionService`, `CandidateRankingService` — imports
`StructuredCompletion` and nothing from `org.springframework.ai`. Swapping to LangChain4j means writing
one new adapter class.

### Model

Default to **`claude-opus-5`** for both features (Anthropic; `spring-ai-starter-model-anthropic`). Both
tasks are judgement, not transcription: extraction has to infer a seniority tier and split criteria into
required vs preferred from prose, and ranking is the whole point. The tuning lever is
`output_config.effort`, not a smaller model — measure at `high` before reaching for `max`, and only
drop to a cheaper model with a measured quality comparison in hand. That is a cost decision for the
owner, not a default to take quietly.

---

## 2. What these features land on (existing anchors)

Neither feature is greenfield. The schema anticipated both, in places:

| Anchor | Where | What it means for us |
|---|---|---|
| `app_lm_position_document` | V16 | The upload table already exists — `content bytea`, `extraction_status`, `extraction_error`, 1:1 with `app_lm_position`. **No Java code reads or writes it today.** Its own comment names the LLM auto-fill as its reason to exist. |
| `PositionCriterion.fromBrief` | V7 + `PositionCriterion.java` | Already documented as *"True when seeded from the brief (today: the template library; **later: the AI drafter**)"*. The mockup renders a "From brief" badge off it. Extraction sets this flag; a criterion the consultant types does not carry it. |
| The Position mockup's dropzone | `claude-design/Position.dc.html:135-165` | `<input type="file" accept=".pdf,.doc,.docx">`, a "Drop a position description to auto-fill fields" panel, and a "Parsing document and extracting details…" state. The UI is designed; `PositionHero.tsx` does not implement it yet. |
| `StrategyFilter` + saved searches | V30, V32, V35 | The filter is one jsonb value of **wire tokens**, and saved searches hold the identical shape. That is the natural landing place for an AI-proposed filter — see §6. |
| `Candidate` + `CandidateProfile` | V36, V37 | Career history, languages, seniority tier, compensation, summary — the material a ranking pass reads. |
| `RateLimiter` / `RateLimitGuard` | `core/ratelimit` | Reusable for per-workspace AI budgets. Note it is in-memory Bucket4j — a *speed bump per instance*, not a quota (see §9). |
| `AuditService` | `core/audit` | Every AI run must land here. |

**Two fields the Position entity does not map.** V20 added `seniority` and `reports_to_name` columns to
`app_lm_position`, but `Position.java` maps neither and there is no `Seniority` enum in
`project/constant`. Extraction wants both (a position description states the reporting line, and
seniority is the axis `CandidateSeniority` already uses). Mapping them is a prerequisite of Phase 1,
and it is a Java-only change — the columns are already there.

---

## 3. Feature 1 — Position document → brief + market filter

### 3.1 Flow

```
  SPA: drop CFO-Position-Description.pdf on the Position hero
   │
   ▼  POST /api/v1/projects/{projectId}/position/document   (multipart)
  PositionDocumentService
   ├─ 1. validate      size / content-type / page count          → 400 before any spend
   ├─ 2. persist       app_lm_position_document (bytea)          → the file is ours now
   ├─ 3. extract text  Tika (or native PDF passthrough, §3.2)
   ├─ 4. LLM call #1   text ──► BriefExtraction                  (typed, schema-constrained)
   ├─ 5. LLM call #2   text ──► MarketDescriptors                (free-text market language)
   ├─ 6. resolve       MarketDescriptors ──► StrategyFilter      (deterministic, §6)
   └─ 7. return        a PROPOSAL — nothing is written to app_lm_position yet
   │
   ▼  SPA renders the proposal as a review sheet (field-by-field accept/discard)
   ▼  PUT /position, PUT /position/criteria, PUT /position/competencies  ← the existing endpoints
   ▼  POST /strategy/searches                                            ← the existing endpoint
```

**The proposal is not the write.** This is the single most important design decision in the feature.
Extraction returns a document the consultant reviews; applying it goes through the endpoints that
already exist and already validate. Three reasons:

- A position description is **untrusted input** (see §9). Routing its influence through a review step
  and then through the same Bean Validation every manual edit passes means a hostile document can at
  worst produce a bad draft, never a silent write.
- The Position screen is an **autosave** screen. A background write racing the user's typing is a lost
  edit; a proposal the user accepts is not.
- It costs nothing extra. `PUT /position` already exists, is already authorised, already audited.

### 3.2 Getting text out of the file

Two routes, and the fallback matters:

1. **Native document passthrough.** The Anthropic API accepts a PDF as a `document` content block and
   reads its layout directly — better on tables, headed sections and the two-column briefs search firms
   actually receive. Verify Spring AI 2.0's `Media` support for `application/pdf` on the Anthropic model
   at implementation time; if it round-trips, prefer it for PDFs.
2. **Tika text extraction** (`spring-ai-tika-document-reader`, wrapping Apache Tika). The universal
   path, and the *only* path for `.doc`/`.docx`, which is half of what the mockup's `accept` attribute
   allows.

Build (2) first because it covers every accepted type, and add (1) as a PDF-only optimisation if it
measurably improves extraction quality. Do not ship an OCR path in v1: a scanned image of a position
description should be rejected with a clear message ("we could not read any text from this file"),
not silently half-extracted.

**Hard limits before any model call** — the cheapest rejection is the one that costs nothing:

| Limit | Value | Why |
|---|---|---|
| File size | 10 MB | The column is `bytea` in a shared `db-f1-micro` instance; a document library this is not. |
| Content types | `application/pdf`, `.docx`, `.doc`, `text/plain` | Matches the mockup's `accept`. |
| Extracted characters | ~120k, then truncate at a section boundary and record it | A 300-page appendix is not a brief. |
| One document per position | Already enforced — `position_id` is `UNIQUE` in V16 | Re-upload replaces. |

### 3.3 The extraction shape

The model fills a record, not a blob. Every field maps to something the Position screen already edits;
every closed set is an existing enum so an invented value fails binding rather than reaching the DB.

```java
/** What one position description yielded. Every field is nullable: the brief said it, or it did not. */
public record BriefExtraction(
        String positionTitle,
        MandateReason mandateReason,          // enum — NEW_ROLE | BACKFILL | SUCCESSION | RESTRUCTURING
        Seniority seniority,                  // enum — to be added beside V20's unmapped column
        String narrative,
        String reportsToTitle,
        String reportsToName,
        Integer directReports,
        Integer teamSize,
        String location,
        EmploymentType employmentType,        // enum
        Long salaryMin, Long salaryMax, String currency,
        Integer noticeValue, NoticeUnit noticeUnit,
        Integer bonusTargetPct,
        String ltip,
        List<String> benefits,
        List<CriterionDraft> criteria,        // text + REQUIRED/PREFERRED
        List<CompetencyDraft> technical,      // name + weight
        List<CompetencyDraft> behavioural,
        List<String> unreadable) {}           // fields the brief did not state — shown, not guessed
```

Rules the prompt must carry, and the service must enforce regardless of what the model returns:

- **Absent is absent.** A missing salary band is `null`, never a market estimate. `unreadable` is how
  the model says "the brief does not state this", and the review sheet shows that list explicitly —
  a consultant needs to know what the document *didn't* say as much as what it did.
- **`confidential` and `internalContext` are never extracted.** They are the firm's own annotations
  about the mandate. A client's PDF has no business setting them.
- **Currency is a three-letter code** — `UpdatePositionRequest` enforces `[A-Z]{3}`.
- **Criteria carry `fromBrief = true`**, and only these. Length ceiling 300 characters (V7's column),
  at most 20; `REQUIRED` for what the brief states as a must, `PREFERRED` for the rest.
- **Competency weights are normalised in Java, not by the model.** The model proposes relative
  importance; `PositionBriefExtractionService` scales each panel to sum to 100 and clamps to V7's
  `BETWEEN 0 AND 100`. The frontend already has the mirror of this in `lib/rebalance.ts`.
- **Salary sanity:** if `salaryMin > salaryMax`, drop both and add the field to `unreadable`.

### 3.4 Endpoints

| Method | Path | Action gate | Notes |
|---|---|---|---|
| `POST` | `/api/v1/projects/{projectId}/position/document` | `PROJECT_EDIT` | multipart; persists the file, returns `PositionExtractionResponse` (the proposal) |
| `GET` | `/api/v1/projects/{projectId}/position/document` | `WORK_VIEW` | metadata only — name, size, uploaded-by, status |
| `GET` | `/api/v1/projects/{projectId}/position/document/content` | `WORK_VIEW` | the original file back, `Content-Disposition: attachment` |
| `DELETE` | `/api/v1/projects/{projectId}/position/document` | `PROJECT_EDIT` | drops the row; the brief keeps whatever was applied |

Authorise by action, never by role — `@PreAuthorize("@projectAuthorizer.can(principal, #projectId, 'PROJECT_EDIT')")`,
matching `PositionController`'s existing gates. **No new RBAC action is needed**: uploading a brief and
editing a brief are the same permission, and inventing `AI_ASSIST` would be authorising by feature
rather than by effect.

### 3.5 Errors

New `ErrorCode` entries, in the house style (stable code, frontend switches on `code` never `detail`):

| Code | Status | When |
|---|---|---|
| `DOCUMENT_TOO_LARGE` | 400 | Above the size ceiling |
| `DOCUMENT_TYPE_UNSUPPORTED` | 400 | Not one of the four accepted types |
| `DOCUMENT_UNREADABLE` | 400 | Tika returned no usable text (scan, empty, encrypted) |
| `AI_UNAVAILABLE` | 503 | Provider timeout, 5xx, or rate limit — retryable, and the SPA says so |
| `AI_QUOTA_EXCEEDED` | 429 | Our own per-workspace budget, not the provider's |

Note V16's own comment: `extraction_status` exists so that a *later* relaxation of the all-or-nothing
rule needs no migration. Keep the rule for v1 — a failed extraction rolls the upload back and the user
retries — and let the column stay the option it was written to be.

---

## 4. Configuration

Provider settings are yml, and the code never names a provider:

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:}
      chat:
        options:
          model: claude-opus-5
          max-tokens: 8000

lightmove:
  ai:
    # Off by default so a fresh clone boots and every existing test passes without a provider account —
    # the same reason LogEmailSender is the default EmailSender. Disabled means the upload endpoint
    # returns AI_UNAVAILABLE and nothing else in the application changes.
    enabled: ${AI_ENABLED:false}
    extraction:
      max-file-size: 10MB
      max-characters: 120000
      timeout: 90s
    ranking:
      max-candidates-per-run: 250
      concurrency: 8
      timeout-per-candidate: 45s
    budget:
      runs-per-workspace-per-day: ${AI_RUNS_PER_WORKSPACE_PER_DAY:50}
```

Per §"Config records specifically": `AiSettings`, `AiExtractionSettings`, `AiRankingSettings`,
`AiBudgetSettings` are **one record per file in `core/config`**, named to read standalone. Never a
nested record inside `LightMoveProperties` — that is issue #53's exact shape.

`spring.ai.anthropic.api-key` empty must not break startup. Guard the adapter bean on
`lightmove.ai.enabled` so a clone with no key boots clean, and make the *absence* of the key at runtime
an `AI_UNAVAILABLE`, not a `NoSuchBeanDefinitionException`.

---

## 5. Feature 2 — Ranking candidates against the brief

### 5.1 The governing principle

**The model judges; Java computes the score.**

Do not ask for a number. Ask, per candidate and per criterion, for a verdict and the evidence behind
it; then do the arithmetic in Java against the weights the consultant already set on the Position
screen. This is not a stylistic preference — it buys four things a "give me a 0-100" prompt cannot:

- **Explainability.** Every point in the total traces to a named criterion and a quoted piece of
  evidence. An executive search firm has to defend a shortlist to a client.
- **Reproducibility.** Two runs over the same data differ only where a verdict differs, not because
  the model felt differently about arithmetic.
- **Consistency across candidates.** A per-candidate scalar is not comparable between calls; a verdict
  against a fixed rubric is.
- **The weights are already the consultant's.** V7's competency weights (0-100 per panel) and the
  `REQUIRED`/`PREFERRED` split are the firm's own model of the mandate. Letting the LLM re-weight them
  discards the one piece of expert input the system already holds.

```java
/** One model's reading of one executive against one mandate. Scores are computed from this, not by it. */
public record CandidateAssessment(
        List<CriterionVerdict> criteria,        // per position criterion: MET | PARTIAL | NOT_MET | UNKNOWN + evidence
        List<CompetencyVerdict> competencies,   // per competency: 0-4 band + evidence
        SeniorityFit seniorityFit,              // ABOVE | MATCH | BELOW | UNKNOWN
        LocationFit locationFit,
        String rationale,                       // ≤ 3 sentences, for the drawer
        List<String> risks,                     // "no GCC experience", "3-month notice"
        List<String> missingEvidence) {}        // what the profile does not say — never inferred
```

`CandidateRankingScorer` (pure, no LLM, unit-tested with no Testcontainers) turns that into:

- **required coverage** — a `NOT_MET` on a `REQUIRED` criterion is disqualifying, not a deduction, and
  the row surfaces as "does not meet the brief" rather than as a low number;
- **preferred coverage** — a straight percentage of `PREFERRED` criteria met;
- **competency score** — Σ(band ÷ 4 × weight) over both panels, weights already summing to 100;
- **confidence** — the share of verdicts that are not `UNKNOWN`. A sparse profile scores *unconfidently*,
  not *badly*, and the UI must show that difference. Ranking a half-filled profile last is how a
  researcher's data-entry backlog silently becomes a hiring decision.

### 5.2 Storage — V39

Two tables. A run is a first-class object because a re-run after the brief changes must not destroy the
previous reading, and because "why did this candidate move" is a question the consultant will ask.

```sql
-- The run: one per "rank this mandate's candidates" click.
CREATE TABLE app_lm_candidate_ranking_run (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id        uuid        NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    status            varchar(16) NOT NULL,      -- RUNNING | COMPLETED | FAILED | PARTIAL
    -- The brief as it stood when the run was made. Without it a score is unreadable six weeks later,
    -- because the criteria it scored against have since been edited. Same argument V36 makes for
    -- company_name: the snapshot is what lets the row outlive what it points at.
    brief_snapshot    jsonb       NOT NULL,
    model             varchar(64) NOT NULL,      -- provenance: which model produced this reading
    candidate_count   integer     NOT NULL,
    failed_count      integer     NOT NULL DEFAULT 0,
    error             text,
    requested_by      uuid        NOT NULL REFERENCES app_lm_user (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    completed_at      timestamptz,
    ...
);

-- One score per candidate per run.
CREATE TABLE app_lm_candidate_ranking_score (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id               uuid    NOT NULL REFERENCES app_lm_candidate_ranking_run (id) ON DELETE CASCADE,
    candidate_id         uuid    NOT NULL REFERENCES app_lm_project_candidate (id) ON DELETE CASCADE,
    overall_score        integer,                -- null when required coverage disqualifies
    required_coverage    integer NOT NULL,
    preferred_coverage   integer NOT NULL,
    competency_score     integer NOT NULL,
    confidence           integer NOT NULL,
    rank                 integer,
    -- The verdicts and their evidence: read whole, written whole, never queried by axis.
    -- V30's argument, and the reason this is not six child tables.
    assessment           jsonb   NOT NULL,
    ...
    UNIQUE (run_id, candidate_id)
);
```

Both need the FK indexes V26/V28 had to add after the fact for the earlier tables — write them in the
same migration, not in a follow-up.

### 5.3 Running it

`CandidateRankingService.rank(workspaceId, projectId, userId)`:

1. Load the brief and the mandate's candidates through the existing workspace-scoped reads. Refuse with
   a clear message if the brief has no criteria and no competencies — there is nothing to rank against,
   and a run that scores everyone 50 is worse than no run.
2. Create the run row `RUNNING` and **return immediately**. A 200-candidate run is minutes, not
   milliseconds; a synchronous HTTP call would time out at the proxy.
3. Fan out over candidates on virtual threads (already enabled in `application.yml`), bounded by
   `lightmove.ai.ranking.concurrency`. **One candidate per call**, with the brief as a cached prefix —
   this is what makes the whole thing affordable (§8).
4. Each candidate result is written as it lands. A failed candidate marks that row and continues; the
   run ends `PARTIAL` and names how many failed. A single provider hiccup must not throw away 199
   completed assessments.
5. Compute ranks in one pass at the end, write `COMPLETED`, audit.

**The `@Async` trap applies.** `AuditService` already delegates to a separate `AuditEventWriter` bean
because a self-call bypasses the proxy and makes the annotation inert. The ranking runner is a separate
bean called from the service, for exactly the same reason.

### 5.4 Endpoints

| Method | Path | Action gate |
|---|---|---|
| `POST` | `/api/v1/projects/{projectId}/candidates/ranking` | `WORK_EXECUTE` |
| `GET` | `/api/v1/projects/{projectId}/candidates/ranking` | `WORK_VIEW` — latest run + status, for polling |
| `GET` | `/api/v1/projects/{projectId}/candidates/{candidateId}/ranking` | `WORK_VIEW` — the assessment for the drawer |

`WORK_EXECUTE` to run, `WORK_VIEW` to read, matching `CandidateController` exactly. That also means a
**pure client representative can read a ranking and cannot trigger one** — which is the right answer,
and it falls out of the existing seat model rather than needing a rule.

### 5.5 What the SPA shows

- A **Rank** action on the Candidates grid, disabled with a reason when the brief is empty.
- A score column: the number, a confidence indicator, and a distinct treatment for "does not meet a
  required criterion" — that is a different statement from a low score and must not render as one.
- In `CandidateDrawer`, the per-criterion verdicts with their evidence, the risks, and the
  `missingEvidence` list, under the date and model of the run that produced them.
- **The score never reorders the grid by default.** It is a column the consultant can sort by. A
  researcher's grid silently reordering itself around a model's opinion is how the tool stops being a
  tool. `useGridSort` already handles this.

---

## 6. The Strategy filter half — and the trap in it

The ask is "create filter query and save in strategy". The trap is that `StrategyFilter` holds
**wire tokens, not display labels**: Apollo industry values, Apollo country names, market-segment names,
and `EmployeeBand`/`RevenueBand` slugs. A model asked to emit those directly will produce
plausible-looking strings — `"fintech services"`, `"UAE"`, `"1000-5000"` — that resolve against nothing
and silently scope a live mandate to zero companies. `StrategyFilter`'s own class doc says why the
tokens exist: *"a stored filter that stopped resolving because a row was renamed would be a silent
scope change on a live mandate."* An AI writing unresolvable tokens is the same failure, self-inflicted.

**So the model never emits wire tokens.** Two steps:

```
LLM  ──►  MarketDescriptors               (free text, the language of the brief)
            industries:   ["payments", "digital banking"]
            geographies:  ["United Arab Emirates", "Saudi Arabia"]
            sizeHint:     "large enterprise, 5,000+ staff"
            keywords:     ["Islamic finance", "core banking migration"]

Java ──►  StrategyFilter                  (deterministic resolution, existing services)
            SectorTaxonomy / IndustryAdjacency  →  Apollo industry values
            the Apollo country vocabulary       →  Apollo country names
            EmployeeBand.fromValue / bounds     →  band slugs
            MarketSegments                      →  segment names
            anything unresolved                 →  DROPPED, and reported back by name
```

`strategy` already owns every one of those resolvers (`SectorTaxonomy`, `IndustryAdjacency`,
`MarketSegments`, `ClasspathVocabulary`, `EmployeeBand`, `RevenueBand`). The AI supplies market
*language*; the existing vocabulary layer supplies the tokens. A descriptor that resolves to nothing is
dropped and shown to the user as "we could not map 'Islamic finance' to an industry" — never
approximated.

### Where the proposal lands

**As a saved search, never as an overwrite of `app_lm_strategy.filter`.** V32 gives us saved searches
holding the identical `StrategyFilter` shape, and V35 gives them visibility. So the extraction creates a
search named for the document ("From CFO Position Description.pdf"), and applying it is the click the
consultant already knows. The mandate's live filter is a thing a human tuned; a document upload must not
silently replace it.

Return the **match count** alongside the proposal — `ApolloCompanyQueryService` already answers this, so
the review sheet can say "this filter matches 412 companies" before anyone commits to it. A filter that
matches 3 companies or 40,000 is visibly wrong, and the count is the cheapest way to see that.

### The seam

`project` → `strategy` through a public service method, which is the direction already sanctioned
(`ReportService` and `ClientService` both call `StrategyService`). Add one method — something like
`StrategyService.proposeSearch(workspaceId, projectId, MarketDescriptors)` returning the resolved filter,
the dropped descriptors and the match count. `strategy` never learns that a position document exists.

---

## 7. Prompt design notes

- **The system prompt is stable; the document is volatile.** Order every request `system` → brief →
  per-candidate content. Prompt caching is a *prefix* match, so the ranking pass caches the entire
  position brief once and pays ~10% for it on every subsequent candidate. This is the difference
  between a $40 run and an $8 one — see §8.
- **Give the model no tools.** Neither feature needs one. A model with no tools cannot be talked into
  an action by a hostile document (§9), and every output path is a typed record.
- **Never interpolate untrusted text into the system prompt.** The document body and the candidate
  profile go in a user turn, clearly fenced, with the standing instruction that content inside the
  fence is data to be read, never instructions to be followed.
- **Ask for the absence.** `unreadable` and `missingEvidence` exist so the model has a legitimate place
  to put "the source does not say", which is the single most effective structural defence against
  invention. Without such a field, a schema requiring a value teaches the model to produce one.
- **Version the prompts** as classpath resources with a version string recorded on the run
  (`app_lm_candidate_ranking_run.model` sits beside it), so "the scores changed" has an answer.

---

## 8. Cost

Order-of-magnitude, at Claude Opus 5 list rates ($5/MTok in, $25/MTok out). Measure before quoting these
to anyone — they are a sanity check, not a budget.

| Operation | Input | Output | ≈ Cost |
|---|---|---|---|
| One brief extraction (15k-token document, two calls) | ~20k | ~4k | **~$0.20** |
| One candidate assessment, brief cached | ~1k fresh + ~3k cached | ~800 | **~$0.03** |
| A 200-candidate ranking run | | | **~$6-8** |
| The same run without prompt caching | | | ~$20+ |

Two levers before anything else: **prompt caching** on the brief prefix (already the difference above),
and the **Batch API at 50%** for ranking runs, which is a natural fit — a run is already asynchronous
and nobody is watching a spinner for 200 executives. Effort tuning is the third lever; a smaller model
is the fourth and needs a measured comparison, not an assumption.

---

## 9. Security, privacy and tenancy

Each of these is an existing invariant, restated for the AI surface — none of them is new policy.

- **Tenant isolation.** Every read is scoped by `AuthPrincipal.requireWorkspaceId()`, never a request
  parameter. `PositionService.loadBrief` already shows the pattern: a foreign project 404s before any
  row is touched. Ranking loads candidates the same way.
- **Uploaded documents are untrusted input.** A position description is a file a client emailed to a
  consultant. It can contain instructions aimed at the model. The mitigations are structural rather
  than lexical: the model has no tools, its output binds to typed records with closed enums, every
  vocabulary term is resolved server-side against a fixed list, and nothing reaches the database
  without passing the same Bean Validation a manual edit passes. The worst outcome of a hostile
  document is a bad draft on a review sheet.
- **Candidate profiles are personal data about identifiable people who did not sign up for this.**
  This is the sharpest issue in the whole design and it is a decision for the owner, not a default for
  the implementer: sending a named executive's employer, compensation and career history to a
  third-party model needs a lawful basis, a processor agreement, and a line in the DPA. Practical
  positions worth considering — a zero-retention provider configuration; a per-workspace opt-in with
  the setting stored on the workspace; pseudonymising the name and contact details before the call,
  since the assessment does not need them (the rubric scores experience, not identity). **Pseudonymise
  by default**: strip `full_name`, `email`, `phone` and `linkedin_url` from the payload and rejoin on
  the row id. It costs nothing and removes most of the exposure.
- **Never log a prompt or a completion at INFO.** They carry both the client's brief and named
  executives. The audit trail records *that* a run happened, by whom, over how many candidates, with
  which model and prompt version — never the content.
- **Audit both features.** New `ProjectEventType` entries: `POSITION_DOCUMENT_UPLOADED`,
  `POSITION_EXTRACTION_APPLIED`, `CANDIDATE_RANKING_REQUESTED`, `CANDIDATE_RANKING_COMPLETED`.
- **The rate limiter is a speed bump, not a quota.** `Bucket4jRateLimiter` is in-memory per instance,
  so the real ceiling is limit × Cloud Run max-instances, and buckets are wiped by every cold start.
  `application.yml` already says this about the auth limits. For a spend cap that actually holds, count
  runs in the database per workspace per day — the AI budget is money, not just abuse pressure.
- **`ANTHROPIC_API_KEY` is a deployment secret** — the `${...:}` placeholder pattern in
  `application.yml` and a value in the deploy workflow, never a checked-in default.

---

## 10. Testing

The suite runs on Testcontainers and must keep running with **no provider account and no network**.

- `StructuredCompletion` is an interface; the tests bind a deterministic stub returning canned records.
  Same shape as `LogEmailSender` — the default that makes a fresh clone fully testable.
- `CandidateRankingScorer` is pure arithmetic over records: plain unit tests, no Spring, no Docker.
  This is where the disqualification rule, the weight normalisation and the confidence calculation
  earn their coverage.
- The descriptor→`StrategyFilter` resolver is likewise pure and deserves the most tests in the feature:
  every unresolvable descriptor is a silent scope change if it slips through, so assert on the
  *dropped* list as hard as on the resolved one.
- Controller slice tests for the RBAC gates: a pure client representative may `GET` a ranking and may
  not `POST` one; a foreign workspace 404s.
- Keep a handful of **recorded real completions** as fixtures so a prompt change can be diffed against
  known documents. Do not call the live provider in CI.

---

## 11. Build order

| Phase | Scope | Ships |
|---|---|---|
| **0** | `pom.xml` (`spring-ai-bom` + Anthropic starter + Tika reader), `core/ai` port and adapter, `AiSettings`, the disabled-by-default flag | Nothing user-visible; the seam exists |
| **1** | Map V20's `seniority` / `reports_to_name` on `Position`, add the `Seniority` enum | The brief can hold what extraction will fill |
| **2** | Upload + persist + Tika + `BriefExtraction` + the review sheet in `PositionHero` | **"Drop a position description to auto-fill fields" works** |
| **3** | `MarketDescriptors` → resolver → saved search, with the match count | Strategy is drafted from the brief |
| **4** | V39, `CandidateRankingScorer`, the run/score model, the async runner | Ranking, no UI |
| **5** | The score column, the drawer panel, the run status | **Ranking is usable** |
| **6** | Prompt caching measurement, Batch API for large runs, per-workspace budget | It is affordable |

Phases 2 and 4 are independent of each other and can run in parallel; both need Phase 0.

---

## 12. Open questions for the owner

1. **Candidate data and the LLM.** Pseudonymised by default (recommended), or does this need an explicit
   per-workspace opt-in and a DPA line before Phase 4 starts at all? This is the one item that can
   block a phase.
2. **When does ranking run?** The ask says "when all the candidates are filled" — but a mandate is never
   finished. Explicit button (recommended: the consultant knows when the map is ready), automatic on a
   candidate-count threshold, or scheduled?
3. **Does an edited brief invalidate existing scores?** Recommended: keep them, badge them stale against
   the run's `brief_snapshot`, and offer a re-run. Silently deleting a shortlist because someone fixed
   a typo in a criterion is the wrong failure.
4. **Does the client representative see scores?** `WORK_VIEW` currently says yes. A model's ranking of
   executives may be internal working material a firm does not want a hiring company reading over its
   shoulder — if so, that is a deliberate carve-out to specify, not an accident to leave in.
5. **Provider region.** The tenant base is GCC; the database is `us-central1`. If inference geography
   matters contractually, it is a configuration decision to take before Phase 0, not after.
