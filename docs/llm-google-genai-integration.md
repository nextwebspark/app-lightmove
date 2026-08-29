# Spring AI + Google GenAI (Vertex) integration

## Context

LightMove had no LLM integration before this change: no Spring AI dependencies, no `core/llm`
package, no `.st` prompt files, and neither "company search" (plain `ILIKE`/array-overlap SQL over
`app_lm_apollo_companies`) nor "brief document upload" (raw byte storage, never read) called an
LLM. This was greenfield work, not an extension of something already wired up.

The goal was to stand up the reusable integration — a configured `ChatClient` backed by Google's
Gen AI SDK in Vertex AI mode, plus a `GoogleGenAiTextEmbeddingModel` — following the two-tier
`core/` architecture so any future feature (candidate shortlisting, brief auto-fill, semantic
company search) can depend on it without re-wiring credentials or prompt loading. Kept
deliberately simple: Spring AI's own built-in `SimpleLoggerAdvisor` rather than a custom audited
one, and one small demo endpoint to prove the wiring works end-to-end rather than leaving inert
config on the shelf. The system prompt is written for the eventual real use case — a recruitment
agency assistant that scans a candidate profile against a job brief and recommends shortlisting —
stored as a `.st` resource.

Two decisions were made explicitly narrow in scope:
- **No custom token-usage audit advisor.** Just Spring AI's built-in `SimpleLoggerAdvisor`. A
  DB-backed usage ledger (new migration, entity, async writer mirroring `AuditEventWriter`) is out
  of scope.
- **A minimal demo endpoint was included**, not just inert beans — proves the `ChatClient` +
  `.st` system prompt + `EmbeddingModel` actually work together.

## Library choice

**Spring AI 2.0.1**, the current GA line (2.0.0 shipped June 2026, 2.0.1 is the latest patch at
the time of writing) — a better fit than the 1.1.x line here specifically: 2.0 is built for
Spring Boot 4.0/4.1 + Jackson 3, which is exactly this project's stack. A reported Boot-version
compatibility issue (spring-ai#6465) affects Boot **4.0.x** consumers, whose starters pull
Boot-4.1.0-level transitive dependencies — this repo is already on 4.1.0, so no workaround needed.

Spring AI 2.0 also removed the old `spring-ai-vertex-ai-gemini` module in favor of the unified
`spring-ai-starter-model-google-genai` / `-embedding` starters, which wrap Google's
`com.google.genai.Client` SDK and support both Vertex AI (GCP credentials) and Gemini Developer
API (API key) transports from the same property namespace (`spring.ai.google.genai.*`).

**Real API difference from the reference example used to scope this work:** Spring AI 2.0 changed
`ChatClient.Builder.defaultOptions(...)` to take a `ChatOptions.Builder`, not a built `ChatOptions`
instance — so `ChatClientConfig` passes `ChatOptions.builder().model(...).temperature(...)`
straight to `defaultOptions(...)` with **no** trailing `.build()`. The 1.x-style snippet that
originally scoped this work called `.build()` first, which no longer compiles against 2.0.
Likewise, `SimpleLoggerAdvisor` moved to a builder: `SimpleLoggerAdvisor.builder().build()` rather
than `new SimpleLoggerAdvisor()`.

## What was built

**`apps/api/pom.xml`**
- A `<dependencyManagement>` block importing `spring-ai-bom` 2.0.1 (the project had no
  `dependencyManagement` of its own before this).
- `spring-ai-starter-model-google-genai` (chat) and `spring-ai-starter-model-google-genai-embedding`.

**`app.lightmove.api.core.llm`** — new `core` module, type-subpackaged like every other:
- `config/ChatClientConfig.java` — the `ChatClient` bean: model `gemini-2.5-flash`, temperature
  `0.8`, the system prompt injected as a `Resource`
  (`@Value("classpath:prompts/recruiter-shortlist-system.st")`) via `.defaultSystem(Resource)`,
  and `SimpleLoggerAdvisor.builder().build()` as the sole default advisor. Deliberately **omits**
  the `@Primary ChatModel` passthrough bean from the reference example: with exactly one
  `ChatModel` in the context there is no ambiguity for `@Primary` to resolve — add it back if a
  second model (e.g. OpenAI) is ever wired in alongside this one.
- `service/CandidateShortlistService.java` — takes the `ChatClient`, `shortlist(jobBrief,
  candidateProfile)` renders both as template params in the user prompt and returns
  `.call().content()`. No persistence.
- `service/CandidateEmbeddingService.java` — thin wrapper over the auto-configured
  `EmbeddingModel`, `embed(text)` returning `float[]`. No persistence.
- `controller/LlmController.java` — `POST /api/v1/llm/shortlist` and `POST /api/v1/llm/embed`.
  Authenticated only (the existing `anyRequest().authenticated()` default in `SecurityConfig`) —
  no `@PreAuthorize` action, since the endpoint reads no workspace data and writes nothing; it's a
  stateless utility over caller-supplied text, unlike every other controller in the codebase.
- `dto/ShortlistRequest.java`, `dto/ShortlistResponse.java`, `dto/EmbedRequest.java`,
  `dto/EmbedResponse.java` — plain records, `@NotBlank`/`@Size` on the text fields.

**`apps/api/src/main/resources/prompts/recruiter-shortlist-system.st`** — the system prompt: an
internal recruitment-agency assistant that scans a candidate's profile against a job brief and
gives a SHORTLIST/DECLINE recommendation with reasoning and flagged gaps, scoped to stay on that
task.

**`apps/api/src/main/resources/application.yml`** — new `spring.ai.google.genai.*` block:
```yaml
spring:
  ai:
    google:
      genai:
        project-id: ${GOOGLE_CLOUD_PROJECT:hak-talent-mapping}   # same GCP project as Cloud SQL
        location: ${GOOGLE_CLOUD_LOCATION:us-central1}
        chat:
          model: gemini-2.5-flash
          temperature: 0.8
        embedding:
          project-id: ${GOOGLE_CLOUD_PROJECT:hak-talent-mapping}
          location: ${GOOGLE_CLOUD_LOCATION:us-central1}
          text:
            model: text-embedding-004
```
No API key anywhere — Vertex AI mode authenticates via Application Default Credentials, the same
`gcloud auth application-default login` already documented for `npm run dev:cloud`. Unlike the DB,
which only needs ADC on the `dev:cloud` path, this feature needs ADC on **every** path, including
plain `npm run dev` — there's no local emulator for Vertex AI. `application-local.yml.example`'s
header comment calls this out.

**`apps/api/src/test/resources/application-test.yml`** — disables the real auto-configuration:
```yaml
spring:
  ai:
    model:
      chat: none
      embedding:
        text: none
```
Without this, `spring-ai-starter-model-google-genai`'s auto-configuration tries to build a real
`com.google.genai.Client` for every `@SpringBootTest`, which fails with no GCP ADC in CI.

**This alone wasn't enough — CI still failed** after the PR went up, with `GoogleGenAiEmbeddingConnectionAutoConfiguration`
throwing `Failed to get application default credentials`. `spring.ai.model.embedding.text=none`
only disables `GoogleGenAiTextEmbeddingAutoConfiguration` (the `EmbeddingModel` bean); the
`Client`-building "connection details" bean lives in a sibling autoconfiguration class gated only
by `@ConditionalOnClass`, so it still ran unconditionally and resolved ADC eagerly in its
constructor. It passed locally only because the dev machine already had `gcloud auth
application-default login` set up — CI has none. The chat side has no equivalent gap:
`GoogleGenAiChatAutoConfiguration` builds its `Client` inside the same class
`spring.ai.model.chat=none` already disables. Fixed by explicitly excluding the connection class:
```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration
```
Verified by rerunning the full suite with ADC hidden locally (pointing `APPDATA` at an empty
directory) to genuinely reproduce the CI failure before and after this fix, rather than trusting a
local run that had working credentials the whole time.

**`apps/api/src/test/java/app/lightmove/api/StubChatModel.java` and `StubEmbeddingModel.java`** —
required in addition to disabling the real auto-configuration, not originally anticipated: turning
off `spring.ai.model.chat`/`spring.ai.model.embedding.text` removes the `ChatModel`/`EmbeddingModel`
beans, but `ChatClientConfig` and the new `llm` services still declare a hard dependency on them —
so every `@SpringBootTest` in the suite failed to load its context (352 test errors on the first
full run) until these two fixed-response test doubles were wired into the shared `IntegrationTest`
annotation, the same pattern `RecordingEmailSender`/`SynchronousAuditWrites` already use.

**Tests** — no network calls, no real credentials required:
- `CandidateShortlistServiceTest` — builds `ChatClient.builder(stubChatModel).build()` directly
  with a hand-rolled `ChatModel` test double, asserts the service renders both prompt fields and
  returns the model's content.
- `CandidateEmbeddingServiceTest` — same pattern with a stub `EmbeddingModel`.

**`apps/api/src/main/resources/postman/lightmove-llm.postman_collection.json`** — a Postman
collection with both requests pre-filled (a CFO job brief + candidate profile for `/shortlist`, a
short profile summary for `/embed`), a `baseUrl` variable, and a Bearer `accessToken` variable at
the collection level.

## Out of scope

- No token-usage audit advisor, no new Flyway migration, no new entity/table.
- No persistence of embeddings (no pgvector column, no vector store) — this only proves the
  `EmbeddingModel` bean works; wiring it into real semantic search over
  `app_lm_apollo_companies` is separate future work.
- No changes to the existing company-search or brief-upload features.

## Verification

1. `cd apps/api && ./mvnw test` — full suite green (60 test classes, 0 failures) including the two
   new unit tests, with no GCP credentials needed.
2. `gcloud auth application-default login`, and confirm `GOOGLE_CLOUD_PROJECT` has the Vertex AI
   API enabled (defaults to `hak-talent-mapping` / `us-central1`).
3. `npm run dev`, then exercise both endpoints (curl or the Postman collection above):
   - `POST /api/v1/llm/shortlist` — confirm a real Gemini response comes back and the
     `SimpleLoggerAdvisor` line appears in the API log.
   - `POST /api/v1/llm/embed` — confirm a 768-dim float vector comes back.
