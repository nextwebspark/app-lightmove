# Evals

How a change to an AI prompt, its model or its settings is shown to be no worse before it serves. Two things live
here and share one scoring code:

- **Offline suites** (`@Tag("eval")`, excluded from `./mvnw test`): run by an engineer against the real model, each
  appending counts to its report in this folder. They can read a private golden set of real profiles.
- **Datasets in the platform console** (Platform → Evals, epic #1187): synthetic cases stored in the database, which
  a super admin's eval run answers with a candidate version and the live one.

| Report | Suite | Reads |
|---|---|---|
| `nationality-eval.md` | `NationalityEval` | `eval/nationality-synthetic.jsonl`, or a private set |
| `position-extraction-eval.md` | `PositionExtractionEval` | a private folder of documents |
| `assistant-eval.md` | `AssistantEval` | `eval/assistant-cases.json` |
| `interview-copilot-eval.md` | `InterviewCopilotEval` | fixtures in the suite |
| `mcp-eval.md` | `ops/eval/mcp/run.mjs` | `ops/eval/mcp/cases.json` |

Run one with Application Default Credentials:

```bash
cd apps/api && ./mvnw test -Dgroups=eval -DexcludedGroups= -Dtest=NationalityEval
```

## Where the code is

The scoring is main code, so a suite and the console cannot drift apart:

- `GoldenRowScorer`, `GoldenRow`, `GoldenRowPrediction`, `GoldenRows` (`enrichment/candidate`): the nationality and
  seniority golden set, its arithmetic and its report.
- `LooseTextMatch` (`core/text`): whether a proposed line says what a labelled one does.
- `EvalSubject` (`core/llm`): one per prompt that can be run, beside its caller. It checks a case's shape and asks the
  model with a case's input **through the feature's own code**, so the user message, the redaction, the injection
  guard, the schema check and the repair are production's. A subject reads no table.
- `prompteval`: datasets, cases, the import and the seed.

Subjects today: `candidate-nationality` and `candidate-ai-enrich` (seniority). A prompt without one lists its
dataset but takes no import and cannot be run (`EVAL_SUBJECT_UNAVAILABLE`).

## Datasets, cases and splits

A **dataset** (`app_lm_eval_dataset`) belongs to one prompt. A **case** (`app_lm_eval_case`) is one input in that
prompt's own prepared shape, with what a careful reader expects of the answer.

- **A case is added and retired, never edited.** A run names the cases it scored, so a case that changed afterwards
  would make the run unreadable. To change one, retire it and add another under a new key; a retired key is never
  reused. A database trigger refuses any other change.
- **Split.** `DEV` cases are for working on a prompt. `HOLDOUT` cases are kept back: the check before a version goes
  live scores on them, so a prompt cannot be tuned to pass it. Look at holdout cases as little as possible while
  editing a prompt.
- **Seeded sets.** At boot `EvalDatasetSeed` records the committed synthetic sets as datasets, adding each case once
  by its key. A later release never rewrites a case or brings back one a person retired.

## Only synthetic cases

**No real person's profile, note, transcript, document, contact or compensation enters these tables.** The platform
role sits above every workspace and inside none, so an eval never reads a tenant table either.

- `source` has one value, `SYNTHETIC`, held by a CHECK. The import refuses any row that says otherwise.
- A case is written by staff, or drafted by a model from a one-line scenario and **read and edited by a person
  before it is imported**. Fictional people and companies only.
- A production failure becomes a case by being **recreated by hand** as a synthetic one, from counts and finish
  reasons. No content is stored to copy from.
- Real golden sets stay offline: `ops/eval/export-golden.sh` writes one to a private path (`eval-data/` is
  gitignored), an authorised engineer runs the suite on it, and the report holds counts only.
  `TextPseudonymiser` hides only what it is told about, so career text stays identifying and cannot be treated as
  anonymous.

## Writing cases for import

A file is JSONL: one case a line. Blank lines and lines starting `//` are skipped. At most 500 cases and 5 MB.

```json
{"key":"NAT-013","name":"Schooling abroad, career at home","tag":"schooling","split":"DEV","source":"SYNTHETIC","input":{"profile":{"fullName":"…"}},"expected":{"nationality":"Unknown"}}
```

| Field | Rule |
|---|---|
| `key` | Required, unique in the dataset for ever. Letters, digits, `.`, `-`, `_`; at most 40. |
| `name` | Required: what the case tests, in a few words. At most 200. Never a real name. |
| `tag` | Optional, at most 40: the scenario family, for reading a run. |
| `split` | `DEV` or `HOLDOUT`. |
| `source` | `SYNTHETIC`. |
| `input` | The prompt's own shape; its subject checks it. |
| `expected` | What a careful reader expects. Left out for a prompt only a judge can score. |

The import previews first and writes nothing: it names every row at fault by its line, and counts the new cases and
the rows whose key the dataset already holds (those are left alone). The commit re-reads the same file and adds its
new cases, all or nothing.

Per prompt:

- `candidate-nationality`: `input.profile` is the golden row's profile (`eval/golden-row.schema.json`), nothing
  else; `expected.nationality` is a nationality group or `Unknown`.
- `candidate-ai-enrich`: the same `input.profile`; `expected.seniority` is `Board`, `C-Suite`, `N-1`, `N-2` or `N-3`.
