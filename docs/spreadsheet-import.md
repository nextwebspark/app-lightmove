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
and reports success. `HeuristicColumnMatcher` proposes it here — exact match, then a synonym table,
then token overlap — and reports whether it was certain or guessing, so the person confirming knows
which columns to actually read. Resolving the headers it cannot is a separate change.

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

### The sample file

`GET /projects/{projectId}/import/template` returns a blank CSV: the twelve fields people actually
fill in, then **this mandate's own custom columns**, then one example row. Optional — the import maps
whatever headers arrive — but every header it emits is one the matcher knows for certain, so a file
built from it maps with nothing left to correct. `ImportTemplateWriterTest` pins exactly that
property, because a label edited out of step with the synonym table would quietly turn every template
import into a mapping somebody has to fix and nothing else would notice. It is project-scoped rather
than static precisely so the custom columns travel with it, which is what keeps the second import
clean too.

**The preview says which of the two cases it is in** — every header known, or at least one guessed —
because they are worth different amounts of scrutiny.

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
data the file carried, and the row error names what was missed. Preview writes nothing at all, so it
stays out of a transaction entirely.

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
  certain.
- `SpreadsheetImportIntegrationTest` — companies-only, candidates-only and combined files; a re-import
  that updates rather than duplicates; a blank cell that does not clear a stored value; an unknown
  header becoming a custom column and the same header not creating a second one on the next import; a
  bad row reported while the rest import.
- `CustomColumnIntegrationTest` — the key surviving a rename, hiding keeping values, deleting keeping
  values, an undefined key being dropped, a value checked against its type, tenant isolation.

By hand: `POST /projects/{id}/import/preview` with a CSV of companies and people, then `/commit` with
the mapping it returns, and confirm the rows land in the grid carrying a `csv` source.
