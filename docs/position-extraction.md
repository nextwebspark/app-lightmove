# Reading a position description into step-one proposals

Implements issue #279 (epic #278): the Position wizard's dropzone stops being decorative. Uploading a
position description has stored the file and nothing more since V16 — V39 deliberately dropped the
extraction columns it never got, saying the real feature "wants no migration at all." This is that
feature's first slice: **Read from document** proposes step one's fields (role title, department,
location, employment type, seniority, responsibilities, narrative), one row at a time, with a
confidence and the sentence it came from. Nothing is written until a row is accepted.

## Context

Three decisions were made before any code, and they shaped everything below:

- **No RAG.** One document per mandate, comfortably inside Gemini's context window (the four sample
  fixtures extract to 3.4–19 KB of text). Chunking would actively hurt: seniority is read from the
  document's overall register, and a field the model wants may be a clause on page one while the
  responsibilities are on page three.
- **Pseudonymise, don't de-identify.** The client's registered name and domain are known-token
  substitution — the mandate already knows them. Contact details are removed by a **contact-block
  stripper** (a line matching an email/phone, plus its neighbours), not by name detection: a
  capitalised-bigram detector in a document full of Title Case headings ("Chief Financial Officer")
  would redact the very content being extracted. **An unnamed third party's name in prose reaches
  Vertex** — a stated trade, not an oversight.
- **A deterministic fallback is required, not optional.** Vertex needs Application Default
  Credentials on every path including a plain `npm run dev` — there is no emulator. A feature whose
  demo needs `gcloud auth application-default login` is one most of the team never sees.

## What was built

### Reading the bytes: one reader per format, none of them known to the orchestrator

Only PDF and `.docx` are implemented today, but the position description will not stay that way —
`.xlsx` and `.pptx` briefs are named as coming. `PositionDocumentTextReader` is therefore an
**orchestrator, not a format reader**: it holds no format-specific code at all, only the two things
every format shares regardless of which one answered — the empty-text-layer refusal and the character
cap. Each format is a small, independent `PositionDocumentFormatReader`:

```java
interface PositionDocumentFormatReader {
    boolean supports(byte[] content);
    String extractText(byte[] content, PositionExtractionSettings settings);
}
```

Spring collects every bean of that type into a `List<PositionDocumentFormatReader>`, constructor-
injected into the orchestrator and tried in `@Order`. The first whose `supports` answers `true` wins:

| Reader | `@Order` | Signature | Notes |
|---|---|---|---|
| `PdfFormatReader` | 100 | `%PDF` | `PDFTextStripper` with `setSortByPosition(true)` — what turns a multi-column table into something read in roughly the right order. Refuses an encrypted file or one over the page cap rather than reading it in part. |
| `LegacyOfficeFormatReader` | 200 | OLE2 compound file | Shared by legacy `.doc`, `.xls` and `.ppt`; always refused, naming the fix, because telling them apart needs parsing the file's own storage directory and reading any of them needs `poi-scratchpad`, which is not on the classpath. |
| `DocxFormatReader` | 300 | ZIP **and** a `word/document.xml` entry | The ZIP signature alone is not enough — `.xlsx` and `.pptx` are ZIPs too — so this reader also checks for the one entry name that is specifically Word's, via a streaming `ZipInputStream` scan rather than fully opening the archive. |
| `PlainTextFormatReader` | `LOWEST_PRECEDENCE` | none — always `true` | The catch-all, ordered last on purpose: every other reader answers a real signature check, and a format reader added later only ever needs to sit ahead of this one. |

**Adding `.xlsx` or `.pptx` is writing one new class, not touching this list or the orchestrator.**
An `XlsxFormatReader` checks the ZIP signature plus a `xl/workbook.xml` entry (mirroring
`DocxFormatReader`'s own disambiguation exactly), extracts text however that format calls for — a
sheet-row cap can go straight into the same `settings` parameter every reader already receives, with
no interface change — and is `@Component`-annotated with an `@Order` somewhere ahead of 
`PlainTextFormatReader`. Nothing else in the codebase needs to know it exists.

Encrypted PDFs and PDFs with no text layer are refused loudly rather than returned as an empty answer.
Every cap here — pages, characters — exists because parsing an untrusted document in-process is a new
attack surface: a decompression bomb, a deeply nested object graph, an encrypted file whose password
prompt would otherwise hang.

### Redaction: a generic engine plus a feature vocabulary

`core/llm/service/TextPseudonymiser` is the mechanism, and knows nothing about clients or contacts —
it redacts a caller-supplied map of literal terms and regex patterns, minting one placeholder per
distinct value, and hands back a `Pseudonyms` vocabulary that can put them back. Two rules are
load-bearing:

- **Escape before mint.** A literal `[[`/`]]` already in the source is escaped to a private sentinel
  *before* anything of the pseudonymiser's own is minted. Without this, a hostile document containing
  the exact syntax of a placeholder we would mint (`[[COMPANY_1]]`) would be indistinguishable from a
  real one, and re-hydration would forge the client's own name back into the model's answer.
- **The residue check is generic, not just "was this call's own map."** `Pseudonyms.hasResidue`
  matches the *shape* of a placeholder (`\[\[[A-Z]+_\d+]]`), not only the specific tokens this call
  minted — a model can answer a bracketed token it invented as readily as one it echoed back, and
  both are the same failure to a reviewer: a placeholder where a real value belongs.

`position/service/PositionDocumentRedactor` is the vocabulary: the client's registered name, its
common corporate-suffix variants ("Acme Holdings Group" → also "Acme Holdings"), and its domain,
resolved through the same `ClientRepository.findByIdAndWorkspaceId` edge `PositionBriefLoader` already
uses. Email/URL patterns are ordinary regexes. The **phone pattern never matches a token preceded by a
currency code or containing a decimal point** — without that guard, "AED 1,200,000" becomes a phone
placeholder and step four loses the only figure it wanted; none of the four sample documents states
compensation any other way.

The **contact-block stripper** deletes a whole contiguous, blank-line-bounded block of at most six
lines once any line inside it looks like an email or a genuine phone number, plus two lines either
side of the block. Verified against the real CFO-brochure fixture: PDFBox's own text extraction
collapses the two side-by-side contact columns onto single merged rows (`"Hakan Alac Farah Abdul
Rehman"`, `"Mobile: +971… Mobile: +91…"`), so one blank-line-bounded block correctly sweeps both
consultants' names, titles, mobiles and emails — with no name detection at all.

### The deterministic fallback

`HeuristicBriefReader` runs four independent rules with no model call:

1. **Key-value header block** — a label (`Job Title`, `Location`, …) followed by a colon **or a run of
   two-or-more spaces or a tab**. The second form is not optional: `JD_CEO.pdf`'s table survives text
   extraction with no colon at all, only the gap a table cell leaves. A captured value is cut at the
   *next* such gap, so a neighbouring column's label extracted onto the same line does not bleed into
   this one's value.
2. **Section bullets** — found via a keyword search (`responsibilities`, `accountabilities`, `duties`,
   `key focus areas`, `the position`, `core responsibilities`) that only needs to *appear* in a
   short line, not be the whole line: real documents phrase the lead-in as a sentence
   ("Specifically, responsibilities include the following:"), not a bare heading. Each subsequent line
   starts a new item when it carries a bullet marker, when the previous line was blank, or —
   **only once the section's own first line establishes a real indent above column zero** — when its
   indentation returns to that opening level.
3. **Employment type by keyword**, five rules (`FULL_TIME_PERMANENT`, `FIXED_TERM_CONTRACT`,
   `PART_TIME`, `INTERIM`, `RETAINED_ADVISORY`), with `"permanent"` checked ahead of the bare word
   `"contract"` so "this is a permanent contract" resolves correctly.
4. **Seniority by reusing the shipped template catalog** — the heuristic title is run through
   `PositionTemplateService.matching`, and the matched template's seniority is taken rather than a
   second curated mapping being built by hand. `LOW` confidence when it rode the
   `generic-executive` fallback, `MEDIUM` when a real keyword matched.

This reader's answer **never reaches the model** — it is the fallback when the model cannot be
reached or is blocked, and it feeds one cross-check: agreement with the model's own role title
upgrades that field to `HIGH` confidence.

> A rule here shipped once, was validated with real PDFBox output (not just a text-layout
> approximation), and turned out to be wrong: an indentation-only "continuation line sits deeper than
> its bullet" signal, which real extraction of the GM-IT fixture does not carry at all — both a bullet
> line and its wrapped continuation extract at column zero, distinguished only by the bullet glyph
> itself. The rule above is the fixed version; the trap is worth knowing before touching this method.

### The model call

`PositionDetailsProposer` is the structural twin of `dataimport`'s `ColumnMappingProposer`: the same
shared `ChatClient`, a system prompt and JSON schema of its own
(`prompts/position-extract-details-{system.st,schema.json}`), temperature 0, no thinking budget,
native JSON response type, and the same broad `catch (RuntimeException e)` falling back to the
heuristic reader — every way the call can fail (no credentials, no quota, a network that cannot reach
Vertex, an answer that will not bind) has the same right answer.

For every field the model answers, `fieldFrom` re-hydrates the value and its snippet, sweeps a
redaction leak (drops the field entirely), and — if the snippet does not literally occur in the
original document, whitespace-normalised and case-insensitive — drops just the snippet and downgrades
confidence to `LOW` rather than the whole field. `employmentType`/`seniority` resolve by **enum name**,
never `Enum.valueOf`: an unknown token ("Permanent", "C-Level") is dropped, not thrown. Every value is
pre-truncated to `PutPositionDetailsRequest`'s own ceilings (title/department 160, location 120,
responsibilities ≤20 × ≤200 chars, narrative 4000) before the response leaves the service, on **both**
the model and the heuristic path, so accepting a proposal can never 400 the autosave it is handed to.

### Backfilling from the matched template

A field neither the model nor the heuristic found anything for isn't necessarily left blank.
`PositionDetailsProposer.finish` — after either path has produced its fields, and once a `roleTitle`
is among them — matches that title against the same catalog `HeuristicBriefReader`'s seniority rule
draws from (`PositionTemplateService.matching`) and proposes the matched template's own value, at flat
`LOW` confidence with no snippet, for any of `department`, `employmentType`, `seniority`, `narrative`
or `responsibility` still absent. Never `roleTitle` itself (it's the match key) and never `location`
(a template carries neither by design, per `PositionTemplateBody`'s own class doc). A field the
document *did* supply is never touched, however thin; `responsibility` backfills only when **none**
were found at all, not to top up a partial list. Each backfilled field carries `ProposalOrigin.TEMPLATE`
(wire token `"template"`) rather than `DOCUMENT`, and the panel marks it "From template" — reviewed and
accepted through the exact same row every document-sourced proposal is, never applied automatically.

This is deliberately narrower than `#283`'s later "suggest this template" checkbox, which offers the
*whole* template as one wholesale, opt-in brief redraft. Backfill only fills in the gaps a reading
already found nothing for, one field at a time.

### The orchestrator and the endpoint

`PositionExtractionService` loads the brief, reads the already-attached document
(`PositionDocumentRepository.findByPositionId`, the same accessor download already uses), calls the
proposer, and records `POSITION_DOCUMENT_EXTRACTED`. Reading the document and calling the model both
happen **outside any transaction** — only the brief/document lookup (`ExtractionDocumentLoader`) is
`@Transactional(readOnly = true)`, kept deliberately short so a slow parse or a slow model call never
pins a database connection. Extraction is also **deliberately not folded into
`PositionDocumentService#attach`'s write transaction** — it's its own explicit call, never a side
effect of uploading or replacing a document, or every Replace would re-bill. `PositionExtractionController`
exposes `POST
/api/v1/projects/{projectId}/position/document/extract/details`, gated `PROJECT_EDIT` — not
`WORK_VIEW` like the document's own download, because a read-only client seat must not be able to run
up a billed model call.

### The screen (superseded, kept as a record)

> **2026-09-20.** The renewed Position screen (#442) draws no review panel, and the compensation route
> was retired with it (#395). The section below describes the review-then-accept panel exactly as it
> first shipped, against the old five-field wizard — it is no longer built. **The second slice below
> ("Fill, markers, undo") describes what replaced it.**

`PositionDocumentDropzone` grew a **Read from document** button beside Replace/Remove, shown only
once a document is attached. `PositionExtractionPanel` rendered one row per proposal — an
inline-editable value, a confidence pill, a disclosure for the source snippet, Accept/Dismiss, and
Accept all — none of which exists on the new screen.

## The second slice: fill, markers, undo (epic #393, PRs #397/#398)

The review panel earned its keep on nothing here: every field on the new five-step brief is already an
autosaved draft with a text box beside it, so the cheapest undo already existed, and thirty clicks to
accept thirty rows was the wrong price for that. The replacement is **fill → mark → undo**: attaching
the document (or pressing **Extract with AI** on the file card, or **Read from document** on the
Reporting/Assessment step header) reads all four sections at once and writes the result straight into
the brief's own fields — through the ordinary autosave channels a keystroke uses, never a second write
path.

**The fill engine** (`apps/web/src/features/position/lib/documentFill.ts`, PR #396) is a pure library —
a snapshot of the six drafts and the four settled section readings in, the next snapshot plus a set of
changed screens and a *receipt* per screen out. Nothing in it renders or schedules a save; that is
`PositionPage.tsx`'s `readDocument` mutation (PR #397), which folds `fillBrief`'s result into local
state, schedules the autosave channels the changed screens own, and flushes them **sequentially** —
`PUT /context` is never sent before `PUT /details` has resolved, because `BaseEntity`'s `@Version`
makes two concurrent writes to the same row an optimistic-lock 409.

**Merge policy** is source-aware, not "overwrite everything": a scalar already marked `MANUAL`
(V67's persisted provenance) is left untouched; anything else (`TEMPLATE`, a previous reading's own
`DOCUMENT`, or unset) is replaced and stamped `DOCUMENT`. A repeatable list — responsibilities,
priorities, criteria, both competency panels — keeps every `MANUAL` row, drops everything else
(`TEMPLATE` and the previous reading's `DOCUMENT` rows), and appends the new reading's rows up to the
brief's own per-field ceiling. The org chart gets its own merge (`lib/orgChart.ts#mergeReportingProposals`):
a manager with no name yet is minted, a `MANUAL` manager is left exactly as typed, and a direct report
with children of its own is never dropped even if it isn't `MANUAL` — dropping it would orphan its own
children. **The location line is split server-side**, because the reader answers one line of prose
("Abu Dhabi, United Arab Emirates") and the brief stores two halves (`locationCity`/`locationCountry`,
V66). `LocationLine` does it in `PositionDetailsProposer#finish` — the one seam the model path and the
heuristic path both pass through — so each half arrives as its own proposal and fills, marks and undoes
on its own. **The catalog decides, never the comma:** a tail `Countries.resolveSpelling` cannot place
keeps the whole line as the city, since "Chicago, IL" is one place a person will finish rather than a
city in Israel, and that method refuses a bare alpha-2 code for exactly that reason. A line naming only
a country proposes only the country, and the country arrives spelled as the catalog spells it — which
is what the picker beside it reads.

**The marker** (`components/ProvenanceMarker.tsx`) is the only visible provenance UI: a small sparkle
on a `DOCUMENT` value, nothing on `TEMPLATE` or `MANUAL`. Hover or focus opens a small hand-rolled
popover — no library — with the confidence, the quoted snippet, and an Undo; hovering has to be
tracked on the wrapper around both the glyph *and* the popover panel together, not the glyph alone, or
moving the pointer from one to the other fires the glyph's own `mouseleave` first and closes the
popover before Undo is reachable. With no receipt for the field (a reload, or the per-screen strip
dismissed) the popover degrades to "From the document" alone — no confidence, no snippet, no Undo —
because the value really did come from a document even once the session has forgotten the details.

**Session versus persisted state** is the whole of what a reload loses: `source` (`TEMPLATE` /
`DOCUMENT` / `MANUAL`) is the one thing V67 persists, and it is what the sparkle itself is drawn from.
Everything else — the confidence, the snippet, the exact Undo, the per-screen strip's count, the
rail's "N filled" badge — lives only in `PositionPage.tsx`'s `receipts` state for the running session,
cleared on a reload or when the strip is dismissed. A brief finished a month ago never nags about
fields nobody has re-read since.

**Undo** reverses one field (`undoScalar`/`undoListItem`) or a whole screen's reading at once
(`undoStep`, or — for the Role Brief, whose one receipt spans three draft objects — a per-field walk
that calls `undoScalar`/`undoListItem` against whichever object owns each key). Every undo function is
a no-op once the field no longer reads `DOCUMENT`: a person's own edit since the fill always wins.

The **Suggested seats** row under the org chart offers the matched template's own usual direct reports
the chart does not already carry (`lib/orgChart.ts#suggestedSeats`); a click adds it as `MANUAL` — a
person chose it, so a later re-read must not drop it. The **suggested-template banner** under the file
card offers to draft from a template the document's role reads like when the brief was not already
drafted from it; applying it redrafts the brief first and re-reads the document after, so the reading's
own values still win over whatever the template just seeded.

## Traps worth keeping in mind

- **Never `Enum.valueOf` a model token.** See `PositionDetailsProposer.enumFieldFrom`.
- **The residue sweep must be generic**, not scoped to what this call's own map registered — see the
  `Pseudonyms.hasResidue` note above. A model can hallucinate placeholder-shaped text it was never
  given.
- **Redaction runs on the original text for the heuristic, and on the redacted text for the model** —
  the heuristic needs the real title for the cross-check, and never reaches the prompt itself.
- **Parsing untrusted documents happens outside every transaction.** `PositionExtractionService` is
  no longer transactional itself; `ExtractionDocumentLoader` holds the one short transaction the brief
  lookup needs, separate from `PositionDocumentService#attach`'s write.
- **Template backfill checks presence, not confidence.** A `LOW`-confidence document-sourced field
  still counts as "found" and is never replaced by the template's guess — `backfillFromTemplate` only
  fills a `fieldKey` that is entirely absent from the list.
- **A new `PositionDocumentFormatReader` must be ordered ahead of `PlainTextFormatReader`.** That
  catch-all answers `supports()` `true` unconditionally, so a new reader added at a lower priority (a
  higher `@Order` number) than `LOWEST_PRECEDENCE` never gets a turn — its bytes are silently read as
  UTF-8 text instead. There is no compiler or test that catches this by construction; a new reader's
  own test should assert it wins against the plain-text fallback on a real sample of its format, the
  way `aNonWordZipIsNotTreatedAsDocx` pins `DocxFormatReader`'s own disambiguation.
- **A shared byte signature is not a shared format.** Every OOXML file (`.docx`, `.xlsx`, `.pptx`) is a
  ZIP, so `supports()` for any of them must check for the format's own entry name
  (`word/document.xml`, `xl/workbook.xml`, `ppt/presentation.xml`) and not stop at the ZIP signature —
  see `DocxFormatReader`. The same is true of the legacy binary family: `.doc`, `.xls` and `.ppt` all
  share the OLE2 compound-file signature `LegacyOfficeFormatReader` refuses on sight, and telling them
  apart (to give each its own message, say) needs reading the file's own storage directory, which
  nothing here does yet.

## Out of scope (this slice)

- `.xlsx` and `.pptx` themselves. `PositionDocumentTextReader`'s reader-per-format design (see above)
  is what makes adding them later a new class rather than a rewrite, but no `XlsxFormatReader` or
  `PptxFormatReader` exists yet, and legacy `.doc`/`.xls`/`.ppt` stay refused rather than parsed.
- A rate-limit field of its own per extraction step — `LlmBudget.POSITION_EXTRACT`'s meter is sized off
  `defaultRequestsPerMinute()`, shared with the import's column-mapping budget and every other
  extraction step, each still counted against its own meter.
- Detecting an unnamed third party's name in prose. Stated as a trade above, not attempted.

## Verification

```
cd apps/api && ./mvnw test     # needs Docker for Testcontainers
cd apps/web && npm run build && npx vitest
```

- `PositionDocumentTextReaderTest` — all four real fixtures, an encrypted PDF, a PDF with no text
  layer, a legacy `.doc` by its OLE2 signature, the page and character caps, and that a bare ZIP
  carrying an `xl/workbook.xml` entry (i.e., something that is not a Word document) is not claimed by
  `DocxFormatReader` on the ZIP signature alone.
- `TextPseudonymiserTest` — the escape-before-mint ordering, one placeholder per distinct value, a
  regex match redacted the same way, whole-word matching.
- `HeuristicBriefReaderTest` — a colon-separated header, a wide-gap table row, the column-gap cutoff,
  bulleted and wrapped-continuation responsibilities, all five employment-type keywords, seniority via
  a real template match versus the generic fallback.
- `PositionDetailsProposerTest` — the load-bearing privacy assertion (`sendsNoIdentifiers`), fallback
  on an outage and on a blocked/injected document, an unverifiable snippet downgrading rather than
  dropping the field, a residual placeholder dropping the field, role-title corroboration upgrading
  confidence, the output ceilings.
- `PositionExtractionIntegrationTest` — the GM-IT fixture end to end under the test profile's stub
  model (proving the degraded path is also the useful one), the writes-nothing assertion, 400 with no
  document attached, 400 on a legacy `.doc`, 403 for a seat without `PROJECT_EDIT`, 404 for a foreign
  project.
- `lib/documentFill.test.ts` (#396) — the scalar keep/replace matrix, list drop/keep/append/de-dupe/
  ceilings, competency weight balancing, undo one/all/after-edit, the `location`→`locationCity` undo
  key mismatch, NaN-guarded notice parsing.
- `lib/orgChart.test.ts` (#396) — the reporting merge (manager mint/rename, direct-report drop/keep/
  de-dupe, coordinates preserved), `suggestedSeats`/`addSuggestedSeat`.
- `PositionPage.test.tsx` (#397/#398) — attaching fires the four reads and PUTs each changed step in
  order; a `MANUAL` field survives a fill; Undo restores a field's previous value; a suggested seat
  adds as `MANUAL`; the suggested-template banner applies and re-reads.
- `ProvenanceMarker.test.tsx` (#398) — keyboard open/close, the no-receipt degradation, the low-
  confidence colour.

All 661 backend tests and the full frontend suite pass with this change; regexes and the block-based
redaction logic were validated against a real `PDFTextStripper` run over all four sample fixtures, not
only against a text-layout approximation.
