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
| `assessment-draft-eval.md` | `AssessmentDraftEval` | `eval/candidate-scorecard-draft-pack-v1.json` |

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
- `EvalCheck` (`core/llm`): one deterministic rule an answer is held to (passed, failed, or nothing to judge on this
  case). A subject lists its checks, how many times a case is answered at the least, and whether its cases have been
  reviewed well enough for a run to count when a version is put live (`isGateReady`).
- `prompteval`: datasets, cases, the import and the seed; runs, their scoring and the check at put live; the judge,
  its rubrics and the labelling that decides whether a rubric counts.

Subjects today: `candidate-nationality`, `candidate-ai-enrich` (seniority), `candidate-scorecard-draft` (the
assessment draft, `assessment-draft-eval.md`) and `outreach-opener`, which has no checks and is scored by the judge
alone. A prompt without one lists its dataset but takes no import and cannot be run (`EVAL_SUBJECT_UNAVAILABLE`).

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

## Runs

A **run** has two versions of a prompt answer the same cases: a candidate, and a baseline that is the version serving
unless another is named. Each answers on its own model and settings, so a model change is compared the same way as a
text change.

- **Repeats.** A case is answered once. It is answered three times when either version's temperature is above 0,
  and never fewer times than the prompt's subject asks (three for the assessment draft, whose answers vary even at
  temperature 0).
- **Cost.** A run makes cases × 2 × repeats calls, plus one for every answer the feature's repair puts back to the
  model. The estimate is shown before a start. A run planning more than `lightmove.platform.eval.max-calls-per-run`
  (200) is refused, and one that reaches it stops with no result.
- **One at a time.** A second start while a run is queued or going is refused. A run keeps going when its page is
  closed; cancelling stops it before its next call and keeps the answers it has. A cancelled or failed run gives no
  result.
- **Scoring.** Every answer gets a verdict from each of the prompt's checks. A check's score is the share of answers
  that pass, taken within each repeat and averaged. The candidate is **no worse** when no check fell by more than the
  prompt's tolerance (`EvalSubject.tolerancePoints`: 3 points for the assessment draft, 0 where nothing is set).
- **An outage is not an answer.** A call the model never answered is asked three times, then the run fails. It is
  never scored: both versions failing alike would otherwise read as no worse.

## The judge

Free text has no one right answer, so no check can score it, and a check cannot say whether evidence is balanced. For
those, a dataset carries a **rubric**: a few criteria, each a name and one sentence on what the better answer does. On
a run, the judge (the registry prompt `eval-judge`, on the model `lightmove.platform.eval.judge-model` names, at
temperature 0) is shown the case and the two
versions' answers as A and B, **in a random order that is stored**, and picks the better on each criterion, or a tie,
with one sentence of reason. One call per pair of answers covers every criterion; the calls are counted in the run's
estimate, cost and cap. Results are counted in pairs, never as a score: per criterion, how many pairs the candidate
won, the baseline won and tied, and the net in points. Past 10 points against the candidate, a criterion is worse.

**A rubric's criteria are always judged and shown. They count only once the judge is calibrated:**

1. Finish a run on the dataset's **dev** cases. The pairs to label are drawn from its answers, so holdout cases stay
   unseen.
2. In the dataset's Rubric view, **Label pairs**: 20 pairs, one at a time. For each criterion, pick A better, Tie or B
   better, as you would judge it yourself. You are not told which version wrote which answer, and the judge's picks
   stay hidden. Each pair is saved as you go.
3. **Check the judge**: it makes its own picks on the same 20 pairs (20 calls). The rubric then shows its agreement
   with yours on each criterion and the picks where the two differ, with the judge's reason.

At **80%** agreement or more, counted over every pick, the rubric **counts**: a criterion that is worse fails a run as
a check does. Below it, or before it is measured, the criteria are **shown only** and change no result. A new live
version of the judge, or a changed label, makes the rubric shown only until the judge is checked again on the same
labels; nothing needs labelling again. A rubric ships with its pack (`eval/*-rubric.json`) and is not edited in the
console; a changed file is a new version, which starts with no labels. Whether the rubric counted is fixed when a run
finishes: labelling later changes no earlier run. A run is judged throughout by the judge that was live when it began,
and its criteria count only if that version of the judge was the one checked.

A prompt with no checks is scored by the judge alone. While its rubric does not count, its run finishes with **no
result**, and put live treats that as no run: a pass with nothing counted would let a version through on no evidence.

Rubrics today: the assessment draft's evidence (specific and tied to a named source, balanced, nothing over-claimed
from self-report, no invented facts) and the outreach opener (specific to the person, claims nothing the profile does
not show, plain and brief). Both packs still wait for a person's review, so neither gates yet whatever its rubric says.

## The check at put live

Putting a version live asks for a written reason unless that version has a finished run that:

1. scored the **holdout** cases,
2. against the version serving **at that moment** (a run against an earlier live version no longer counts),
3. for a prompt whose subject lets its runs count (`EvalSubject.isGateReady` when the run started), and
4. found it no worse, on its checks and, while the dataset's rubric counts, on its judged criteria.

`isGateReady` is the prompt's, not a dataset's: once a subject opens it, a holdout run on any dataset of that
prompt counts, one a super admin imported included.

The latest such run decides. A failed one can be put live with a reason, recorded as `EVAL_GATE_OVERRIDDEN`. A
rollback always asks for a reason. A prompt with no dataset, or no subject, has no such run, so it asks as it always
has; so does a run that finished with no result.

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
- `candidate-scorecard-draft`: a profile, notes, transcripts, documents and a brief, with a band for every
  competency. See `assessment-draft-eval.md`.
