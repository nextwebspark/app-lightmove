# Guardrails on every model call

## Context

`docs/llm-google-genai-integration.md` stood up the `ChatClient` and proved it worked. What it
deliberately left for later is everything that decides how a call behaves when the model, the network
or the caller misbehaves — and by the time a second feature wanted the model, the gap had a shape.

`CandidateShortlistService` put a **job brief and a candidate profile into the prompt as free text**,
with no guard on what that text could say, no bound on how long the call could take, and no handling of
a reply that came back wrong. And the call had no bound of any kind: a hung Vertex held a request
thread, and the user's browser, for as long as it liked.

Left alone, the next feature to call the model would have inherited all of it and probably copied
whatever the previous one happened to do. So the policy belongs in `core/llm`, applied per prompt from
a spec, rather than assembled by hand at each call site.

## What was built

### `LlmCallPolicy` — the advisors every call gets

One method, `forPrompt(spec)`, returning the advisors and the log attribution as `.advisors(...)` takes them:

```java
this.guardedAdvisors = llmCalls.forPrompt(PromptGuardSpec.prose(PROMPT_ID));   // in the constructor
chatClient.prompt().advisors(guardedAdvisors).system(prompt).call().content();
```

- **`SafeGuardAdvisor`** refuses text that reads like an instruction before the call is made. The
  phrases are configuration (`lightmove.llm.injection-phrases`), not constants in whichever service
  needed them first, and a prompt with dangerous vocabulary of its own **adds** to the baseline through
  `spec.alsoRefusing(...)` rather than replacing it.
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

So the safety net is in the spec instead. `PromptGuardSpec` refuses, at construction, any blocked
answer that does not carry `BlockedAnswer.MARKER`, and requires a structured prompt to name its own.
Without that marker a canned refusal reads as the model's own verdict — which on the shortlist prompt
would be an assessment of a candidate that nobody made. `LlmCallPolicy.requireModelAnswer` owns that
translation so no caller has to remember it, and refuses an empty reply for the same reason
(`content()` is nullable).

**Be clear about what this does not do: the guard is opt-in and cannot be otherwise.** The shared
`ChatClient` is a bean of a framework type, so any feature can inject it and call the model unguarded.
Nothing in `core/llm` prevents that; a reviewer noticing a `chatClient.prompt()` with no spec is the
only check. The marker is guessable too — detection is a substring match, so a user who writes it into
their own text can make their own request look blocked. That costs them their own call and nothing
more, but no trust decision may rest on it.

### Bounding the call

- **Timeout** through the provider's own `HttpOptions.timeout` (`lightmove.llm.request-timeout-ms`,
  default 20s). **Chat only.** Embeddings never touch this `Client`:
  `GoogleGenAiEmbeddingConnectionAutoConfiguration` builds its own connection details from
  `spring.ai.google.genai.embedding.*`, with no timeout and no seam this bean reaches. So
  `POST /api/v1/llm/embed` is budgeted but still unbounded, and bounding it is separate work. Neither Spring AI's connection properties nor `GoogleGenAiChatOptions` expose one, so
  `GoogleGenAiClientConfig` replaces the `@ConditionalOnMissingBean` `com.google.genai.Client` to set
  it. It reproduces the **Vertex** path only — the one this deployment uses — and refuses an `api-key`
  configuration loudly rather than quietly building the wrong client for someone who meant the Gemini
  Developer API. It is gated on `spring.ai.model.chat`, because an ungated bean would resolve
  Application Default Credentials in every `@SpringBootTest`.
- **Retry: there is none, and `spring.ai.retry.*` cannot give us one.** This was configured here at
  first and the config was inert, which is worth writing down so nobody adds it back. Three things,
  each checked against the 2.0.1 jars rather than the docs: `max-attempts` binds to
  `RetryPolicy.maxRetries`, which counts retries *after* the first call, so the numbers mean one more
  than they look; `exclude-on-http-codes` is read only by a `ResponseErrorHandler` wired into providers
  built on Spring's `RestClient`, and the Google SDK is not one; and `GoogleGenAiChatModel` wraps every
  SDK failure in a bare `RuntimeException`, which matches neither `TransientAiException` nor
  `ResourceAccessException` in the policy's `includes`. So nothing was ever retried on this provider —
  the timeout below is what actually bounds the call. Giving this provider a real retry means
  publishing our own `RetryTemplate` bean (it is `@ConditionalOnMissingBean`) whose policy includes
  what the model actually throws, and deciding what is safe to retry when everything arrives as the
  same exception type. That is its own change, not a property file.

### What every call logs

`SimpleLoggerAdvisor`'s formatters live in `ChatCallLog`, because the defaults dump prompt
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

- `LlmCallPolicyTest` — the configured phrases guard a prompt that named none of its own; extra phrases add
  to the baseline rather than replacing it; a prose prompt gets no validator; a document prompt re-asks
  an answer that does not fit; a spec whose blocked answer could not be recognised is refused where it
  is written.
- `GoogleGenAiClientConfigTest` — the timeout reaches the provider's `HttpOptions`, and every property
  this bean does not honour (`api-key`, `credentials-uri`, `vertex-ai`) is refused rather than ignored.
- `ChatClientLoggingTest` — the log line carries prompt id, token counts and finish reason, and carries
  **no** prompt or response content.
- `CandidateShortlistServiceTest` — both fields reach the prompt. The blocked and empty cases are
  pinned on `LlmCallPolicy.requireModelAnswer`, which owns that translation for every caller.
- The whole suite must stay green **with no GCP credentials**, which is the real check on the
  conditional wiring: `cd apps/api && ./mvnw test`.
