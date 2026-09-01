# Guardrails on every model call

## Context

`docs/llm-google-genai-integration.md` stood up the `ChatClient` and proved it worked. What it
deliberately left for later is everything that decides how a call behaves when the model, the network
or the caller misbehaves — and by the time a second feature wanted the model, the gap had a shape.

`CandidateShortlistService` put a **job brief and a candidate profile into the prompt as free text**,
with no guard on what that text could say, no bound on how long the call could take, and no handling of
a reply that came back wrong. Meanwhile Spring AI's retry autoconfiguration was **already active and
nobody had chosen its settings**: `spring-ai-autoconfigure-retry` rides in on the GenAI starter gated
only by `@ConditionalOnClass`, publishes a `RetryTemplate` at a default of **ten attempts**, and
`GoogleGenAiChatModel` takes one through an `ObjectProvider`. Ten attempts over a call with no timeout
is how one unreachable Vertex becomes a very long wait on a spinner, holding a request thread the whole
time.

Left alone, the next feature to call the model would have inherited all of it and probably copied
whatever the previous one happened to do. So the policy belongs in `core/llm`, applied per prompt from
a spec, rather than assembled by hand at each call site.

## What was built

### `LlmGuards` — the advisors every call gets

One method, `on(spec)`, returning the advisors and the log attribution as `.advisors(...)` takes them:

```java
this.guarded = guards.on(LlmPromptSpec.of(PROMPT_ID));   // in the constructor
chatClient.prompt().advisors(guarded).system(prompt).call().content();
```

- **`SafeGuardAdvisor`** refuses text that reads like an instruction before the call is made. The
  phrases are configuration (`lightmove.llm.injection-phrases`), not constants in whichever service
  needed them first, and a prompt with dangerous vocabulary of its own **adds** to the baseline through
  `spec.refusing(...)` rather than replacing it.
- **`StructuredOutputValidationAdvisor`**, only when the spec carries a schema. It validates the reply
  and re-prompts with the validation error attached, `lightmove.llm.answer-repair-attempts` times
  (default 1 — its own default of 3 means four paid calls). A prompt that answers in prose supplies no
  schema and gets no validator.
- **The prompt id** in the advisor context, so the log line says which feature made the call.

Two ordering facts, both load-bearing:

- The validator's default order places it **inside** the guard. In front, a blocked call's canned
  answer would be re-asked as though the model had answered badly.
- Callers resolve their spec **in the constructor**. The schema is read there, so one that will not
  load fails the context at startup rather than every request that needed it.

### Why not `defaultAdvisors` on the shared `ChatClient`

Tempting, because it would make the guard impossible to forget. It does not work: a guard answers *in
place of* the model, so its canned answer has to bind to whatever **that** call expects back, and one
default cannot serve both a prose reply and a typed record.

So the safety net is in the spec instead. `LlmPromptSpec` refuses, at construction, any blocked answer
that does not carry `BLOCKED_MARKER`, and requires a structured prompt to name its own. Without that
marker a canned refusal reads as the model's own verdict — which on the shortlist prompt would be an
assessment of a candidate that nobody made. That call now throws rather than returning one.

### Bounding the call

- **Timeout** through the provider's own `HttpOptions.timeout` (`lightmove.llm.request-timeout-ms`,
  default 20s). Neither Spring AI's connection properties nor `GoogleGenAiChatOptions` expose one, so
  `GoogleGenAiClientConfig` replaces the `@ConditionalOnMissingBean` `com.google.genai.Client` to set
  it. It reproduces the **Vertex** path only — the one this deployment uses — and refuses an `api-key`
  configuration loudly rather than quietly building the wrong client for someone who meant the Gemini
  Developer API. It is gated on `spring.ai.model.chat`, because an ungated bean would resolve
  Application Default Credentials in every `@SpringBootTest`.
- **Retry** configured rather than defaulted: two attempts, with 400/401/403/404/429 excluded. Those
  are permanent for the request in hand, so retrying them only delays the answer the caller was always
  going to get. `SpringAiRetryConfigTest` pins that the autoconfigured template is actually consumed —
  the property file would otherwise be inert and nothing would say so.

### What every call logs

`SimpleLoggerAdvisor`'s formatters are replaced in `ChatClientConfig`, because the defaults dump prompt
and response in full and both are client and candidate PII. What is logged is metadata:

```
chat request  prompt=recruiter-shortlist model=gemini-2.5-flash temperature=0.8
chat response id=… model=… inputTokens=1200 outputTokens=340 totalTokens=1540 finish=STOP
```

`finish` is the field separating a good answer from a silently truncated one: a `MAX_TOKENS` or
`SAFETY` stop returns a partial body over an otherwise successful call. A zero input count means the
provider reported none — Spring AI substitutes an empty usage — so it reads as "not reported", never
as "free".

### `LlmBudgetGuard`

The per-user cap on model calls, lifted out of `CandidateLlmController`'s private method into
`core/ratelimit` so the next caller does not reimplement it. Without a cap an authenticated caller can
loop requests and run up the project's GCP bill; the seat check gates *who* may call, not *how often*.

## What Spring AI does not offer

Checked against every jar in the 2.0.1 line: there is **no** fallback chat client, degraded-response
advisor, or circuit-breaker integration. `spring-ai-retry` classifies transport failures and
`SafeGuardAdvisor` short-circuits a request, but a content-level fallback is the caller's to write.
Both current callers do, differently and correctly: the shortlist throws, and the import degrades to a
deterministic matcher.

## Verification

- `LlmGuardsTest` — the configured phrases guard a prompt that named none of its own; extra phrases add
  to the baseline rather than replacing it; a prose prompt gets no validator; a document prompt re-asks
  an answer that does not fit; a spec whose blocked answer could not be recognised is refused where it
  is written.
- `GoogleGenAiClientConfigTest` — an `api-key` configuration is refused loudly, and the timeout reaches
  `HttpOptions`.
- `SpringAiRetryConfigTest` — the autoconfigured `RetryTemplate` really is consumed, at the configured
  number of attempts rather than the default ten.
- `ChatClientLoggingTest` — the log line carries prompt id, token counts and finish reason, and carries
  **no** prompt or response content.
- `CandidateShortlistServiceTest` — both fields reach the prompt, and a blocked call is refused rather
  than returned as an assessment.
- The whole suite must stay green **with no GCP credentials**, which is the real check on the
  conditional wiring: `cd apps/api && ./mvnw test`.
