# AI production readiness — Position extraction, candidate AI enrichment, the assistant

An evaluation matrix for the three places Uncava calls a model:

- **Position extraction:** reading a position description into the brief.
- **Candidate AI enrichment:** `CandidateAiEnricher`, run on capture and from the drawer's button.
- **The assistant:** its chat.

The status of each row comes from reading the code in `apps/api` and `apps/web` as of this branch; nothing was run.

All three share one stack:
- a Spring AI `ChatClient` over Gemini 2.5 Flash on Vertex (`core/llm/config`);
- a 20s timeout and 2 retries per call;
- `LlmBudgetGuard`: 10 requests per minute per user, held in memory on each instance;
- `ChatCallLog`: token counts in log lines only;
- `StubChatModel` in every test.

---

## 1. Must-have before production

Without these four, the first bad day is an incident rather than a bug report. Everything else in the matrix can follow launch.

### 1. A hard cost ceiling and a kill switch

**What goes wrong without it.** A retry loop, a bulk capture, or a user hammering the assistant can run up an unbounded Vertex and search-grounding bill. The limiter is per instance and counts requests rather than billed calls: retries, the repair re-ask and grounded searches are all free to it. Nothing stops the spend, and the assistant and the enrich button cannot be switched off without a deploy.

**Done when:**
- a shared budget (Postgres, not instance memory) caps each workspace per day, counting every billed call, retries, repair asks and grounding included;
- each AI surface has a runtime on/off flag. Today only `position.extraction.enabled` and `ai-enrich-on-capture` exist; the assistant and the enrich button have none;
- a GCP billing budget alert sits on the Vertex `app` label.

### 2. Legal sign-off on candidate AI enrichment — protected attributes and data residency

**What goes wrong without it.** The enrichment prompt infers **gender from a name and pronouns** and **nationality from a profile**. It writes both into real candidate columns, flagged only by `ai_inferred_fields`, and it scores the person 1–10. Hiring is a regulated use of AI: the EU AI Act lists it as high-risk, and GDPR and UAE PDPL both treat these attributes as special. Everything is processed in `us-central1`, whatever the client's contract says.

**Done when:**
- gender and nationality inference is **off by default**, a per-workspace opt-in;
- the report's diversity chapter never counts a value that is still in `ai_inferred_fields`;
- the Vertex region and its data-use terms are confirmed against GCC client contracts;
- the human-review step is written down.

### 3. Close the injection and access holes

**What goes wrong without it.**
- The assistant has no input guard at all: `AssistantService` skips `LlmCallPolicy`, and #429 owns what replaces it.
- Bright Data LinkedIn text reaches it as tool results.
- Enrichment's grounded web pages reach the model unfiltered, and its summary, positives, negatives and source titles are stored exactly as the model wrote them, then shown to staff.
- Its source URLs are never checked against the grounding metadata, so an invented link survives.
- Separately, `GET /assistant/threads/{id}` checks only workspace membership, so someone taken off a mandate can still read its old threads.

**Done when:**
- the assistant has an input guard (#429);
- document and tool content is wrapped in delimiters;
- enrichment free text and source titles are length-capped and sanitised;
- enrichment sources are kept only when the grounding metadata cites them;
- the thread read re-checks project access.

### 4. Metrics and alerting on every AI call

**What goes wrong without it.** The heuristic fallback and a broad `RuntimeException` catch hide every failure behind one WARN line. An expired credential, a quota limit or a schema change can leave Vertex dead for weeks while the screens look fine. Spend is learned from the invoice.

**Done when:**
- Micrometer timers and counters are tagged by `prompt`: latency, outcome (ok / fallback / blocked / rate-limited / error) and tokens;
- alerts fire on the fallback rate, the error rate and daily token spend;
- auth and quota errors log at ERROR, distinct from an ordinary fallback.

Metrics are unreachable today; see `production-observability.md`, gap 4.

**Next after these: evals.** Each feature needs a golden set, run against live Vertex on every prompt or model change. Without one, no prompt edit can be judged safe, but its absence does not cause a day-one incident.

---

## 2. The matrix

✅ in place · ⚠️ partial · ❌ missing. **P0** before GA (the must-haves above, plus evals), **P1** soon after, **P2** hardening.

| # | Dimension | What production grade means | Position extraction | Candidate AI enrich | Assistant | Pri |
|---|---|---|---|---|---|---|
| 1 | **Evaluation / quality** | Golden datasets with expected output; a scored run on every prompt or model change; a regression gate | ❌ Four PDF fixtures, but only the heuristic path is asserted | ❌ None | ❌ None; the stub never exercises tool choice | **P0** |
| 2 | **Structured output & validation** | Schema, post-validation, a repair loop, bounds and enums enforced in code | ✅ Schema + repair; verbatim-snippet check; enums; save-limit truncation | ⚠️ Schema has no min/max/enum; Java clamps; free text uncapped | ⚠️ Card figures come from tools only; the answer is free text | P1 |
| 3 | **Grounding / hallucination** | Claims traceable to a source; unsupported claims dropped | ✅ Snippet must appear in the document | ⚠️ Source URLs not checked against grounding metadata | ✅ No fact unless a tool returned it; the card is built server-side | P1 |
| 4 | **Prompt injection, direct** | Input guard, delimiters, instructions kept apart from data | ⚠️ 6-phrase blocklist; no delimiters | ⚠️ Blocklist on profile text | ❌ Guard skipped (#429) | **P0** |
| 5 | **Prompt injection, indirect** | Tool, web and vendor content treated as data; limited blast radius | ✅ Writes nothing; a person confirms | ❌ Grounding pages unfiltered; free text stored verbatim | ⚠️ Vendor LinkedIn text reaches the model; a person files the card | **P0** |
| 6 | **PII minimisation** | Send the minimum; redact; never log content | ✅ `PositionDocumentRedactor`; people's names still sent | ⚠️ `CandidateDossier` allowlist; name and LinkedIn sent; `TextPseudonymiser` unused | ❌ Brief and persona sent unredacted | P1 |
| 7 | **Data residency & DPA** | Region matches client contracts; Vertex data-use terms recorded | ⚠️ `us-central1` for everyone | ⚠️ Same | ⚠️ Same | **P0** |
| 8 | **Bias, fairness & legal** | No protected-attribute inference without consent; human review; explainability | n/a | ❌ Gender/nationality inferred into real columns; no opt-out, confidence or evidence; scoring unreviewed | ⚠️ Ranks companies, not people | **P0** |
| 9 | **AuthZ & tenant isolation** | Every endpoint action-gated; tools take ids from the server only | ✅ `PROJECT_EDIT`; document workspace-scoped | ✅ `WORK_EXECUTE`; assessment off `CandidateResponse` | ⚠️ Tools use server-side context; the thread read skips the project check | **P0** |
| 10 | **Timeouts & deadlines** | A per-call timeout **and** an overall budget; client timeout and cancel | ⚠️ Four parallel calls × (retries + repair) can pass Cloud Run's 60s; no `AbortSignal` | ⚠️ No run deadline; the SPA polls with no timeout | ❌ No cap on model rounds; no cancel; a closed tab is still billed | P1 |
| 11 | **Resilience / fallback** | Circuit breaker, graceful degradation, distinct errors | ✅ Heuristic fallback; ⚠️ the broad catch hides auth/quota | ⚠️ `ai_enrich_failed_at`; no breaker | ⚠️ `ASSISTANT_UNAVAILABLE`; no breaker | P1 |
| 12 | **Async durability & idempotency** | Durable queue, job retry, in-flight dedupe, survives a restart | n/a (synchronous) | ❌ `@Async` on the default executor; lost on restart; a double press bills twice | ⚠️ The answer outlives the SSE stream in memory only | P1 |
| 13 | **Rate limiting & cost control** | Shared across instances; per workspace and per month; counts billed calls or tokens | ⚠️ Per user, per instance | ⚠️ Same; grounded capture runs on by default | ⚠️ Same + `Semaphore(4)` per instance | **P0** |
| 14 | **Token / cost metering** | Tokens and cost stored per call, workspace and feature | ❌ Logs + billing label | ❌ Same; grounding not tracked | ❌ Same | P1 |
| 15 | **Caching** | Identical input is not billed twice | ❌ Same document re-billed per click | ⚠️ Fills only empty fields; no cache | n/a | P2 |
| 16 | **Observability** | Latency, error, fallback, blocked and token metrics; traces of tool rounds; dashboards and alerts | ❌ WARN logs | ❌ Logs | ❌ Logs + `steps` JSON | **P0** |
| 17 | **Audit trail** | Who asked, which model, what was written | ✅ `POSITION_DOCUMENT_EXTRACTED` (no model/tokens) | ⚠️ Button path only; capture runs and AI writes unaudited | ✅ `ASSISTANT_ASKED` | P1 |
| 18 | **Human in the loop & transparency** | AI output marked; undo; a person confirms what matters | ✅ Sparkle, snippet popover, Undo | ✅ "AI" badge until confirmed; ⚠️ no confidence or evidence | ✅ Card filed once, by a person | ✅ |
| 19 | **Feedback loop** | Corrections and ratings captured as eval data | ⚠️ Undo not recorded | ⚠️ Researcher edits not recorded | ❌ Only `proposal_accepted` | P1 |
| 20 | **Model & prompt lifecycle** | Pinned model version; versioned prompts; canary and rollback; plan for model retirement | ⚠️ Alias `gemini-2.5-flash`; no prompt version in logs or audit | ⚠️ Same | ⚠️ `ASSISTANT_MODEL` env; same | P1 |
| 21 | **Feature flag / kill switch** | Each surface can be turned off at runtime | ✅ `position.extraction.enabled` | ⚠️ Capture only; the button has none | ❌ None | **P0** |
| 22 | **Retention & deletion** | A retention policy; delete on request | ✅ Only `source` outlives the tab | ⚠️ `ai_assessment` kept indefinitely | ❌ Threads undeletable; no retention; no length limits | P1 |
| 23 | **Context management** | Token-aware history; input caps | ✅ 40k chars / 60 pages / 10MB | ⚠️ Input text uncapped | ⚠️ 12 turns by count, no token budget | P2 |
| 24 | **Security testing** | Injection red-team set; abuse cases; load test | ❌ | ❌ | ❌ | P1 |
| 25 | **Operations** | SLOs; a runbook for a Vertex outage, a quota 429, a cost spike | ❌ | ❌ | ❌ | P1 |
| 26 | **Error contract** | Stable RFC 9457 codes for AI failures | ⚠️ Generic codes; a blocked prompt falls back silently | ⚠️ Generic codes | ✅ Four `ASSISTANT_*` codes; no Spring AI exception mapping | P2 |

---

## 3. Where each surface lives

| Surface | Code |
|---|---|
| Shared | `core/llm/**`, `core/ratelimit/service/LlmBudgetGuard`, `application.yml` (`spring.ai.*`, `lightmove.llm.*`, `lightmove.assistant.*`) |
| Position extraction | `position/service/Position*Proposer`, `PositionExtractionService`, `PositionDocumentRedactor`, `ExtractedFieldReader`, `prompts/position-extract-*` |
| Candidate AI enrich | `enrichment/candidate/service/CandidateAiEnricher`, `CandidateAiEnrichWorker`, `candidate/model/CandidateDossier`, `prompts/candidate-ai-enrich-*` |
| Assistant | `assistant/service/AssistantService`, `AssistantAskStream`, `assistant/tool/*`, `prompts/assistant-system.st` |

Related: `llm-guardrails.md`, `llm-google-genai-integration.md`, `assistant-tools.md`, `production-observability.md`.
