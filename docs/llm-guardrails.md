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

### A structured caller asks for JSON natively

The advisor validates the answer; it does not clean it. It hands the assistant text to Jackson
verbatim — no trim, no fence strip — so a reply wrapped in a ```` ```json ```` fence fails on the
leading backtick and burns the repair attempt re-asking a question whose answer comes back fenced
identically. `BeanOutputConverter` cleans the text again before binding, so the mapping was right and
the cost was invisible: twice the Vertex spend and about eight seconds a preview.

So a structured call names `responseMimeType("application/json")` in its own
`GoogleGenAiChatOptions`, and Gemini emits no fence to strip. Three things about that:

- **At the call site, not in `LlmCallPolicy`.** A policy method would have shared a line without
  enforcing it: a caller can forget to call it exactly as easily as it can forget the option.
- **Not the global `spring.ai.google.genai.chat.response-mime-type`.** That one is real and it does
  merge (below), but it would put the shortlist prompt, which asks for prose, into JSON mode.
- **`responseSchema` is deliberately unset.** Gemini's is a restricted OpenAPI subset that rejects
  the `$schema` and union types our answer schemas use, and the validating advisor already holds the
  full schema.
  Measured on 2026-10-14 after rewriting our schemas into its shape: 9 of 12 prompts went unanswered at
  the 40-second timeout with it, each answering in one to two seconds without. An invalid escape the model
  writes (it once wrote `"\协"` in a snippet) is mended instead by `JsonEscapeRepairAdvisor`, just inside
  the validating advisor.

### Where a default belongs, and which of the two layers merges

Spring AI has two that look alike:

- **The ChatModel's options**, bound from `spring.ai.google.genai.chat.*`, are the **base**:
  `DefaultChatClientUtils` builds every request as
  `chatModel.getOptions().mutate().combineWith(perCallOptions)`. `combineWith` copies each non-null
  field of the call over the base, and merges `labels` and `safetySettings` by key rather than
  replacing them. A caller therefore inherits everything it does not name, and adding a `prompt`
  label keeps the application's `app` one.
- **Options on the shared `ChatClient` bean** do **not** merge with a call's.
  `ChatClient.Builder.defaultOptions(b)` delegates to the same `optionsCustomizer` field a per-call
  `.options(b)` assigns, so the first call to name any option silently drops all of them.

So model, temperature, `max-output-tokens` and the billing label live in `application.yml`, and
`ChatClientConfig` sets no options at all — it contributes the logging advisor and nothing else.
A call names only what its prompt fixes (a structured prompt: temperature 0, no thinking, JSON) and inherits
the rest; `VertexGeminiChatModelProvider` sets a field only when the call names it, for that reason.

One trap if a default is ever put back on the bean: it must be typed `GoogleGenAiChatOptions`.
`combineWith` reads the provider half of a builder only when the builder handed to it is one of its
own, so a provider field set on the portable `ChatOptions` builder is dropped rather than refused.

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
  default 40s). **Chat only.** Embeddings never touch this `Client`:
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

### Where a call's prompt, model and settings come from (#1316)

No caller reads a prompt file or names a vendor class. Each asks `PromptCalls.prepare(spec)` **per call** and
gets the system text, the provider's options and the wiring that counts the call:

- **`PromptSource.current(promptId, kind)`** answers the version serving now (`RegistryPromptSource`, its one
  implementation). With `lightmove.ai.prompt-registry.enabled` off it is the bundled `prompts/*.st`
  (`BundledPrompts`), today's path. On, **only an `EDITED` production version overrides the build's file**:
  while production is a `BUNDLED` version, each instance serves its own bundled text and looks up that text's
  version by its SHA-256. The `production` label is one row for every revision running, so during a rollout or
  a traffic split it cannot say which shipped text an instance should answer with; the build can. Held in
  memory per instance. It is never the reason a call fails: a registry that cannot be read serves the bundled
  file and logs it, and a text the sync has not recorded yet is served with no version (and so not counted).
- **The boot sync** (`PromptRegistrySync`) records each bundled text as a `BUNDLED` version once, under an
  advisory lock. Production follows the build's own text unless an `EDITED` version is live, which stays live.
  For shipped texts that label is the record the console reads, not what serves, so two revisions moving it
  between them change no answer. A sync that recorded or moved anything bumps the reload, for an instance that
  served before it.
- **The model and settings are the version's.** `provider` + `model_id` null means the configured model
  (`LLM_CHAT_MODEL`, or `ASSISTANT_MODEL` for the assistant). A setting left unset is the calling feature's own
  default, declared in its `PromptCallSpec`, so a version that sets nothing behaves as the code always has.
  A version naming a model past its retirement date, one `AiModelCatalog` no longer lists, or one that lacks
  what the prompt's kind needs (`PromptKind.needs()`: tool calling for the assistant) is answered by the
  configured model and logged once.
- **`ChatModelProvider`** is the one seam to a vendor: `VertexGeminiChatModelProvider` owns the JSON mime type,
  the thinking budget and the labels (`prompt`, and `prompt_version` when the registry served the text). A
  second provider is a second implementation and catalogue entries.
- **The reload** is `PlatformConfigReload`: the transaction that moves `production` bumps the `prompts` counter
  and notifies on `lm_platform_config` through `core/stream`'s listener; every instance drops its cache. Each
  also re-reads the counters every `lightmove.platform.config-reload-check` (30 s), for a notification it
  missed. The channel is generic (`core/reload`), so its key is the platform's, not the registry's.
- **Every call is counted** against the version and model that answered, in
  `app_lm_platform_prompt_call_daily` (`PromptCallCounter`, innermost advisor: a schema repair's second ask and
  each round of the assistant's tool loop are each a call). The write is off the calling thread and a failed
  one is logged, never thrown. That table is the record; the label and the log line are secondary.

The answer schema (`-schema.json`), the answer format and the user message stay in code: code parses the
answer against the first two, and the third decides which data reaches the model.

**Try it** (#1317) runs one version without serving it: `PromptTrialRunner` binds the version to the calling
thread, `PromptCalls.prepare` answers that prompt with it for the length of the run, and the model's answer and
the bound record are kept. A `PromptTrial` sits beside its caller and hands it a fictional input written in
code, so the user message, the guard, the schema check and the repair are production's own. It reads no table.

Rollout: ship off; turn `AI_PROMPT_REGISTRY_ENABLED` on per environment, check the per-version counts, then
default it on (#1324).

### What every call logs

`SimpleLoggerAdvisor`'s formatters live in `ChatCallLog`, because the defaults dump prompt
and response in full and both are client and candidate PII. What is logged is metadata:

```
chat request  prompt=recruiter-shortlist promptVersion=3 model=gemini-3.5-flash temperature=0.4
chat response id=… model=… inputTokens=1200 outputTokens=340 totalTokens=1540 finish=STOP
```

`promptVersion` appears only when the registry served the text.

`finish` is the field separating a good answer from a silently truncated one: a `MAX_TOKENS` or
`SAFETY` stop returns a partial body over an otherwise successful call. A zero input count means the
provider reported none — Spring AI substitutes an empty usage — so it reads as "not reported", never
as "free".

### `LlmBudgetGuard`

The per-user cap on model calls, lifted out of `CandidateLlmController`'s private method into
`core/ratelimit` so the next caller does not reimplement it. Without a cap an authenticated caller can
loop requests and run up the project's GCP bill; the seat check gates *who* may call, not *how often*.

Meters: shortlist, embedding, the import's column mapping, and each position-extraction step. Every one
but shortlist and embedding shares `default-requests-per-minute` — one deliberate click either way, and
a second knob per meter is a second thing to get wrong — but each still its own budget
(`LlmBudget`/`LlmBudgetGuard.require`), so none of them can spend another's calls. The import's commit is
not budgeted: it calls no model.

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
- `CandidateShortlistServiceTest` — both fields reach the prompt, and a call naming only its prompt still
  inherits the configured model, temperature and ceiling. The blocked and empty cases are
  pinned on `LlmCallPolicy.requireModelAnswer`, which owns that translation for every caller.
- `PromptRegistryIntegrationTest` (registry on) — every prompt served equals its shipped file; a changed
  shipped prompt moves production and an edited live one stays; another revision's shipped text is never
  served here; a sync that changed something bumps the reload; two boots record one version; a version put
  live serves, model included, after the backstop check alone, and after the notification alone; calls are
  counted per version; a failed count does not fail the call; versions and moves cannot be rewritten.
- `RegistryPromptSourceTest`, `PromptCatalogsTest` — off and unreadable serve the bundled file; two revisions
  on one registry each serve their own text; a read a reload overtook is not kept; a retired, unlisted or
  unable model falls back; every catalogue entry is fully described; the Vertex adapter's options.
- The whole suite must stay green **with no GCP credentials**, which is the real check on the
  conditional wiring: `cd apps/api && ./mvnw test`.
