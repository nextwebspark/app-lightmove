# Spreadsheet import and per-project custom columns

## Context

A mandate's Companies grid is filled one row at a time: Strategy's per-row add, the picker over the
Apollo universe, a company typed in by hand, or the browser plugin. A researcher who arrives with a
list — one a client sent, one bought from a vendor, one mapped in Excel — had no way in at all.
`app_lm_project_candidate.source` has reserved `'CSV'` for this import since V36, and nothing wrote it.

Two problems, and the second is the one that shaped the design.

**A file's headers will not match ours.** "Organisation", "Employer" and "Company Name" all mean
`company_name`; "E-mail" and "Work Email" both mean `email`. A mapping has to be proposed and then
confirmed, because a wrong mapping applied silently writes a consultant's data into the wrong fields
and reports success.

**A file carries columns this application has never heard of** — ethnicity, a client's own ranking, a
notice period quoted in weeks. Dropping them loses the half of the list that was worth importing.

## What was built

### Custom columns are rows, not DDL

The obvious answer to the second problem is a column per tenant, and it is the wrong one: a table
whose shape depends on which mandate is asking cannot be migrated or indexed, and it needs the runtime
role to hold the `CREATE ON SCHEMA public` privilege `ops/cloudsql/harden.sql` exists to revoke.

So **V42** holds the definitions (`app_lm_project_custom_column`) and **V43** puts the values in a
`custom_fields jsonb` bag on both `app_lm_project_triage_company` and `app_lm_project_candidate`. The
grid renders a column per definition, so a custom column is real to the user and invisible to the
schema. Scoped to the project, which is what was asked for and what makes sense: a new mandate starts
from the built-in columns alone, and a mandate that imported a file carrying Ethnicity keeps an
Ethnicity column for as long as it runs.

`field_key` and `label` are two columns on purpose, and only one of them moves. The key is slugged once
from the label the column was created with and never rewritten, because every value already stored
points at it; renaming changes the header a user reads and nothing else. Collapsing the two would
orphan a mandate's data the first time somebody fixed a typo in a header.

`CustomColumnService.applyTo` is what the rest of the application calls. The bag is open, so without it
any caller could write any key into a row and the "columns" would be whatever happened to be in the
map. It drops keys the project has not defined, checks each value against its column's declared type,
and merges over what the row already held.

**Deleting a column removes the definition and not the values.** The rows keep them, unrendered, so a
column deleted by mistake comes back with its data when it is defined again under the same name. A
sweep that erased them would make one misclick unrecoverable for no benefit beyond a smaller document.

A per-project ceiling (`lightmove.custom-column.max-per-project`, default 40) exists because the
columns come from whatever a spreadsheet's header row happens to say — one mis-mapped import of a very
wide file would otherwise leave a mandate with a grid nobody can read across.

### The mapping reuses the LLM integration that already exists

`ColumnMappingProposer` injects the `ChatClient` bean from `core/llm`, loads
`classpath:prompts/import-column-mapping-system.st`, sets it per call, and binds a structured answer —
the same four lines of wiring `CandidateShortlistService` uses, with a different prompt. Nothing about
the model, its temperature, its credentials or its advisors is restated; those stay in
`application.yml` and `ChatClientConfig`.

**No cell values are sent.** A spreadsheet of executives is exactly the client and candidate PII that
PR #148's review stripped out of `SimpleLoggerAdvisor`'s logging. The request carries each column's
header and a **locally computed value shape** — `looks like an email`, `looks numeric`, `looks like a
date` — worked out by `ValueShapes` from the parsed rows and never leaving the process. That carries
nearly all the signal a mapping needs: a column headed "Contact" is ambiguous, and a column headed
"Contact" that holds email addresses is not. `lightmove.spreadsheet-import.send-sample-values`
(default `false`) exists so an operator can make the opposite trade knowingly rather than by editing
code.

**The answer is checked, never trusted.** Every entry is matched back to a real header **by name**,
not by position — a model that drops, reorders or invents an entry would otherwise shift every mapping
after it onto the wrong column, the one failure mode where a plausible answer writes a whole file into
the wrong fields. Unknown tokens are dropped, and a column the model wanted to discard is kept when it
has values in it.

A field two columns both claim goes to the first — but *first* is decided in two passes, not by
position in the sheet. The model's own answers are applied and claim their fields before any column
the model did not answer for falls back to the header matcher. Without that, a fuzzy guess on an early
column would outrank the model's explicit verdict on a later one, losing exactly the column the call
was made to disambiguate. The loser, whichever it is, keeps its data as a custom column rather than
silently overwriting the winner on every row.

**The model is only asked when a header is actually in doubt.** `HeuristicColumnMatcher` reports not
just *what* a header matched but *how sure* it is: a hit in the synonym table, or an exact match for a
column this project already has, is `certain`; a fuzzy token-overlap hit is not, and neither is a
header nothing recognised. When every column is certain, `ColumnMappingProposer` returns the
heuristic's answer and never calls Vertex — which is the common case for a file built from the
template, and for a second import of a shape a mandate has seen before. An unfamiliar header does
bring the model back, deliberately: it may be a field we hold under a name we do not know ("Firma",
"Raison sociale"), and that is precisely what the model is worth paying for.

The preview reports which of the three produced the mapping — `exactHeaders`, `model` or
`headerMatcher` — and the dialog says so under the file name, because they are worth different
amounts of scrutiny.

### When the model misbehaves, or cannot be reached

The mapping step is the real backstop — nothing the model says is written, and a person confirms every
column. Behind that:

- **Any failure falls back to the header matcher**, reported as `headerMatcher` so the dialog says
  "check these". Missing credentials, quota, network, and an answer that will not bind all land here.
- **Retry is Spring AI's own**, configured rather than defaulted. `spring-ai-autoconfigure-retry`
  rides in on the GenAI starter gated only by `@ConditionalOnClass`, so its `RetryTemplate` was
  *already* active at its default of **ten** attempts — which, over a call that had no timeout, is how
  one unreachable Vertex became a very long wait on a spinner. Now two attempts, with 400/401/403/404/429
  excluded: those never come good for this request, so retrying only delays the fallback.
- **The call is bounded**, through the provider's own `HttpOptions.timeout`
  (`lightmove.llm.request-timeout-ms`, default 20s). Neither Spring AI's connection properties nor
  `GoogleGenAiChatOptions` expose a timeout, so `GoogleGenAiClientConfig` replaces the
  `@ConditionalOnMissingBean` client to set one. It reproduces the **Vertex** path only and refuses an
  api-key configuration loudly rather than quietly building the wrong client — and it is gated on
  `spring.ai.model.chat`, because an ungated bean would resolve ADC in every `@SpringBootTest`, which
  is the failure PR #148 hit with the embedding connection.
- **Temperature 0 on this call**, set per call rather than on the shared bean, whose other caller is
  the shortlist prompt. Mapping has one right answer; variance buys only answers that will not bind.
- **An answer that does not fit the shape gets one corrected try**, through Spring AI's own
  `StructuredOutputValidationAdvisor`: it validates the reply against a JSON schema and re-prompts with
  the validation error attached. Two things it needed to be usable here. Its default of **three**
  repeats would make four paid calls of an import that is about to fall back anyway, so it is set to
  one — the same budget as the transport retry. And it is given **our own schema**
  (`prompts/import-column-mapping-schema.json`) rather than one generated from `ModelMappingAnswer`,
  because the generated one marks every record component `required`: under it, a perfectly good answer
  that omits `targetField` because the column is a custom one counts as malformed, and *every* correct
  call would be paid twice. Its order is left at the default, which places it inside `SafeGuardAdvisor`
  — in front, it would re-ask the block sentinel as though the model had answered badly.
  Note what it does *not* do: after the last attempt it returns the still-invalid answer rather than
  throwing, so it is a bounded self-correction loop and the `catch` below it is still the backstop.

There is no fallback chat client, degraded-response advisor or circuit breaker in Spring AI 2.0.1.
The catch-and-degrade above is the substitute, and it is deliberate rather than a gap.

**None of that policy lives here.** It is `core/llm`'s `LlmCallPolicy`, applied from a `PromptGuardSpec`,
because none of it is a property of the mapping prompt. The two callers had opposite halves of it: the
import had a guard and a validator, while the shortlist prompt had neither and takes a job brief and a
candidate profile as *free text* — a far larger injection surface than a spreadsheet header. Now both
go through the same line, and a call that answers in prose simply supplies no schema.

The one thing that cannot be shared is what a block answers *with*: the guard replies in place of the
model, so the answer has to bind to whatever that call expects back. `PromptGuardSpec` therefore
requires a structured prompt to name its own, and refuses any blocked answer that does not carry
`BlockedAnswer.MARKER` — without which a canned refusal is passed off as the model's verdict, which on the
shortlist prompt would be an assessment nobody made.

### Prompt injection

The only caller-controlled text reaching the model is the **headers** — no cell values are sent. Two
layers, and neither is the interesting one on its own:

**Sanitising is what actually closes the hole.** Headers reached the prompt raw and unbounded: a
first-row cell holds 32,767 characters in Excel, and a newline in a header would end its `- "…"` list
item and let the rest read as a line of its own — forging extra columns, or something shaped like an
instruction. `promptSafe` strips control characters and quotes, collapses whitespace and caps at 100
characters. Deterministic, with nothing to rephrase around. It applies to the **prompt only**: the
stored header keeps its text, because the mapping step shows it back and the model's answer is matched
against it.

**`SafeGuardAdvisor` sits behind that** as a second layer, attached per call with a short list of
phrases with no business in a header. Kept short deliberately — every entry is also a header somebody
could legitimately have, and blocking a real import is its own failure. Its `failureResponse` is a
JSON sentinel rather than a sentence, because it answers *in place of* the model and a sentence would
not bind to `ModelMappingAnswer`: a block would surface as a binding error indistinguishable from an
outage. As shaped, a block degrades to the header matcher and logs what it was.

Worth keeping in proportion: the model has **no tools**, its output is a typed record, the answer is
structurally validated, and a person confirms before any write. The worst a successful injection
achieves is a wrong dropdown the user can see.

### The sample file

`GET /projects/{projectId}/import/template` returns a blank CSV: the twelve fields people actually
fill in, then **this mandate's own custom columns**, then one example row. Optional — the import maps
whatever headers arrive — but every header it emits is one the matcher knows for certain, so a file
built from it maps with no model call at all. `ImportTemplateWriterTest` pins exactly that property,
because a label edited out of step with the synonym table would quietly cost one call per import and
nothing else would notice. It is project-scoped rather than static precisely so the custom columns
travel with it, which is what keeps the second import free too.

**`HeuristicColumnMatcher` is not a nicety.** Vertex AI needs Application Default Credentials on every
path including a plain `npm run dev` — there is no emulator — so for anyone without them it is the only
mapper that ever runs. It seeds the model's request and answers alone when the call fails, and the
preview response says which of the two produced the mapping, because they are worth different amounts
of scrutiny.

### What every call logs

`SimpleLoggerAdvisor`'s formatters (in `ChatClientConfig`) were already replaced to keep prompt and
response content out of the log. They now also carry what a production LLM integration actually gets
asked:

```
chat request  prompt=import-column-mapping model=gemini-2.5-flash temperature=0.0
chat response id=… model=gemini-2.5-flash inputTokens=1200 outputTokens=340 totalTokens=1540 finish=STOP
```

- **`prompt=`** is the calling feature, passed per call site through the advisor context
  (`ChatCallLog.PROMPT_ID_ATTRIBUTE`) and read back in the request formatter. The `ChatClient` is shared, so
  without it every line says only that *something* called Gemini — and "which prompt" is the first
  question when a bill jumps. A call site that sets none logs `unattributed` rather than failing.
- **Token counts** come from the provider, so they are the figures the bill is computed from. One
  trap: Spring AI substitutes an *empty* usage when a provider reports none, so an unreported count
  arrives as `0` rather than null. A zero input count means "not reported", not "free".
- **`finish=`** is the field that separates a good answer from a silently truncated one — a
  `MAX_TOKENS` or `SAFETY` stop returns a partial body over a perfectly successful call, which then
  fails to parse downstream for reasons that look nothing like the cause.

The advisor only calls its formatters when its own logger is at `DEBUG`, which is worth knowing before
going looking for these lines in a deployed environment.

Preview is capped per user through `LlmBudgetGuard`, which is
`CandidateLlmController`'s former private `checkRateLimit` lifted into `core/ratelimit` rather than
copied into a second controller. It stays separate from `RateLimitGuard`, which is auth-shaped (IP +
email, a security audit row) and answers a different question.

### The importer writes nothing itself

`ProjectImportService` builds the very requests the Companies drawer posts — a `CaptureCompanyRequest`,
an `EditTriageCompanyRequest`, a `SaveCandidateRequest` — and hands them to `TriageCompanyService` and
`CandidateService`. Every scope check, duplicate rule, source resolution, snapshot and audit event
therefore stays in the one place that already owns it, and an import cannot drift from what the screen
does. The only genuinely new seams are two reads: find a company of this project by name, find a person
of this project by email or by name at a company.

Three consequences worth naming:

- **A blank cell never clears a stored value.** Both update paths replace a row whole, so an update is
  built from what the row already holds with the file's non-blank cells laid over it. A second file
  carrying only names and countries must not empty out the headcounts a researcher typed.
- **A company taken from the Apollo universe keeps the export's figures.** `edit` refuses one outright
  — that is what the Source badge promises — so an import fills only its custom columns and the summary
  counts it separately, because "12 updated" for twelve untouched snapshots would be a lie.
- **A spreadsheet's own "status" column is never imported.** That is the sender's pipeline, not this
  mandate's, and overwriting a researcher's decision with it would undo their work.

### Neither import method is `@Transactional`

This is the design, not an omission. A thousand-row file will have a bad row in it, and the useful
answer is to import the other nine hundred and ninety-nine and say which one failed. One transaction
around the whole commit cannot do that: **Spring marks a transaction rollback-only on any unchecked
exception, `ApiException` included** — the trap `java-spring-development` already documents — so the
first refused row poisons the transaction and the commit that follows throws
`UnexpectedRollbackException`, losing every row and telling the caller nothing useful. This was not
theoretical; it is what the integration suite caught on its first run.

Each call into `TriageCompanyService` and `CandidateService` therefore runs in its own transaction,
which is the granularity the row loop actually needs. The accepted consequence is that a row whose
company is written and whose person is then refused leaves the company behind — that company is real
data the file carried, and the row error names what was missed. Preview stays out of a transaction for
a second reason: it calls Vertex, and an open transaction must not wait on a network round trip.

### Two calls, and no import session between them

`POST /import/preview` reads the file and proposes a mapping. `POST /import/commit` takes the same file
back with the mapping a person confirmed. The browser still holds the `File`, so re-posting it costs
one parse of a ≤5000-row sheet and saves a staging table, an expiry policy, and a sweeper for the
imports nobody came back to finish. Commit does not re-run the proposer: re-deciding after a user has
corrected a column would silently overrule them.

`@RequestPart("mapping")` is the first in the codebase — every other upload uses
`@RequestParam("file")` — because this request carries a file *and* a JSON document that has to be
bound and validated as one, which a form field of JSON text could not be.

### Reading the file

`SpreadsheetReader` decides the format from the **bytes**, not the declared content type: browsers send
`application/vnd.ms-excel` for a `.csv` and `application/octet-stream` for an `.xlsx`, so the allowlist
in `SpreadsheetImportSettings` only keeps obviously wrong uploads out. CSV goes through `commons-csv`
(BOM stripped, quoting honoured, delimiter sniffed — Excel writes semicolons in every locale with a
decimal comma, and a file read with the wrong one parses as a single very wide column); XLSX and the
legacy XLS go through `poi-ooxml`, first sheet only.

A formula is read as its **cached result and never evaluated**: evaluating means running arbitrary
spreadsheet logic, external links included, out of a file an untrusted caller uploaded, and the value a
consultant saw when they saved is the value they meant to send.

A row count over `lightmove.spreadsheet-import.max-rows` is refused whole rather than truncated —
taking the first N would silently decide which half of a consultant's list got imported.

## Out of scope

- **Matching an imported company back to an Apollo account.** Imported companies are `source = 'CSV'`
  with `apollo_account_id` NULL, deduped by name exactly as a hand-typed company is. Fuzzy-resolving a
  spreadsheet name against 71,822 Apollo rows is its own feature with its own failure modes.
- Export; column types beyond TEXT/NUMBER/DATE/BOOLEAN (an option list needs a second table, an editor
  for it, and a rule for rows holding an option somebody deleted); inline editing in the grid.
- Any change to `app_lm_apollo_companies`, which stays read-only to the application.

## Verification

`cd apps/api && ./mvnw test` — needs Docker for Testcontainers. Covering:

- `SpreadsheetReaderTest` — a BOM, a semicolon delimiter, quoted commas, a repeated header, a blank
  header, a numeric cell Excel stored as a double, a workbook mislabelled `text/csv`, the row cap.
- `HeuristicColumnMatcherTest` — the header spellings real files carry.
- `ImportTemplateWriterTest` — that every header the template emits is one the matcher knows for
  certain, so a file built from it costs no model call.
- `ColumnMappingProposerTest` — against a recording chat model of its own: that the prompt carries
  headers and
  shapes and **no cell values** (sample values included, when an operator turns them on), that answers
  are matched by header rather than position, that a model's answer outranks a heuristic guess on an
  earlier column, that a thrown call falls back to the heuristic, and that the call is tagged
  `import-column-mapping`.
- `SpringAiRetryConfigTest` — that the provider's own retry knobs are the ones this application set.
- `ChatClientLoggingTest` — the log line, driven through a real `ChatClient` and the real advisor:
  model, both token counts, the finish reason, the prompt id from each call site, an unattributed
  call, a response carrying no metadata, and that no prompt or response content is ever logged.
- `SpreadsheetImportIntegrationTest` — companies-only, candidates-only and combined files; a re-import
  that updates rather than duplicates; a blank cell that does not clear a stored value; an unknown
  header becoming a custom column and the same header not creating a second one on the next import; a
  bad row reported while the rest import.
- `CustomColumnIntegrationTest` — the key surviving a rename, hiding keeping values, deleting keeping
  values, an undefined key being dropped, a value checked against its type, tenant isolation.

The path worth exercising by hand is the one **without** credentials: run `npm run dev` with no
`gcloud auth application-default login`, preview a file, and confirm the mapping still comes back and
says the header matcher produced it.
