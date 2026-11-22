# Assessment draft eval

How the assessment draft (`candidate-scorecard-draft`) is scored before a change to its prompt, model or settings
goes live. `README.md` in this folder covers datasets, splits and why only synthetic cases are stored; this page is
the assessment draft's own: what a case holds, how to label one, and how to read a run.

The pack is `eval/candidate-scorecard-draft-pack-v1.json` (30 cases, 18 dev and 12 holdout), seeded as the dataset
**Assessment draft: pack v1**. `AssessmentDraftEval` (`@Tag("eval")`) runs it offline against the real model and
appends a section below; the console's runs (#1320) use the same subject and checks.

**The pack is drafted and not yet reviewed by a consultant.** Until it is, a run reports and the put-live check does
not rely on it (`CandidateScorecardEvalSubject.isGateReady`).

## What a case holds

Everyone in a case is invented. The input is what the drafter is handed for a real person, in the same shape:

| Part | Holds |
|---|---|
| `profile` | The profile fields only: name, title, employer, location, summary, career, education, skills, languages. |
| `notes` | Each with its `date` (`2026-10-14`) and `body`; optionally `kind` (`general`, `call`, `meeting`, …) and `outcome`. |
| `transcripts` | Each with its `date`, a `title`, and `lines`: `at` (time into the call, `03:10`), `speaker`, `team` (true for a colleague), `text`. |
| `documents` | Each with a `title`, a `category` (`cv`, `reference`, `other`, …) and its `text`. |
| `brief` | The role, its criteria, and its `competencies` (`panel`, `name`, `description`, `weight`) with `technicalShare`. |

The case goes through the production path: `CandidateMaterialReader` writes the notes, calls and documents out, so
emails and phone numbers are removed, a colleague is marked "(your team)" and long material is cut exactly as a real
person's is, and `CandidateScorecardDrafter` asks the model. A case can therefore plant a fake email or phone number
to prove it never comes back.

## Labelling a case

`expected.competencies` labels **every** competency of the brief by the id the drafter gives it: `T1`, `T2`, … for
the technical panel and `B1`, `B2`, … for the behavioural, each in the brief's order.

```json
{"competencies": {"T1": {"band": [7, 9], "restsOn": "call"},
                  "T2": {"band": null},
                  "B1": {"band": [3, 7], "selfReportOnly": true},
                  "B2": {"band": [4, 7]}},
 "neverInAnswer": ["a.person@example.com", "+971 50 555 0100"],
 "notEvidence": ["ran three turnarounds"],
 "unusableCalls": ["2026-10-20"]}
```

- **`band`**: the lowest and highest score you would accept, or `null` when nothing on file bears on the competency
  and no score should be given. Use the prompt's own scale: 0 to 2 a source contradicts it, 3 to 4 limited, 5 to 6
  partial or indirect, 7 to 8 strong at a comparable scope, 9 to 10 repeated and direct. A score resting on the
  career alone belongs between 3 and 7.
- **`tolerance`**: how far outside the band still passes. 1 unless you say otherwise; 0 for a band you are sure of.
- **`restsOn`** (`profile`, `note`, `call`, `document`): the source a score of 7 or more must name.
- **`selfReportOnly`**: the only evidence is the executive's own claim with no example, so the score stays at 7 or
  below.
- **`neverInAnswer`**: the fake contacts planted in the material.
- **`notEvidence`**: words only a colleague, an unidentified speaker or an injected line says. Write the shortest
  phrase that could only have come from there.
- **`unusableCalls`**: the dates of calls where the executive cannot be told apart, or which the injection guard
  removes; nothing may be cited from them.

Pack v1 covers: strong direct evidence, self-report only, conflicting sources (the most recent dated one wins), a
call where the executive cannot be identified, a call where only the interviewer speaks, a prompt-injection line,
planted contacts, a sparse profile and nothing on file.

## The checks

Each is deterministic and judged on the model's own answer, before the drafter drops what it cannot use.

| Check | Fails when |
|---|---|
| Every competency id is valid | An id is not the brief's, appears twice, or a competency is left out. Also when no answer could be read. |
| Scores are whole numbers in range | A score is outside 0 to 10 or not whole, or evidence sits beside no score without saying nothing is on file. |
| Evidence names its source | A scored competency's evidence names no source, or a strong score does not name the labelled one. A source is the profile ("Inferred from …" counts), a note by its date, a call by its date **and** the time into it ("call 12 Oct 2026, 03:10 in"), or a document by what it is or its title. Evidence saying nothing is on file has no source to name. |
| Evidence is found in the input | A date, a call time, a sum of money, a percentage, a year or a quotation of four words or more is not in the case. |
| No email or phone in any answer | Any evidence or report section holds an email, a phone number or a planted contact. |
| Nothing taken from "(your team)" or an unidentified speaker | A `notEvidence` phrase appears, or an unusable call is cited. |
| Score inside the labelled band | A score is outside its band and tolerance, or a score is given where none is expected. |
| Self-report alone stays at 7 or below | A `selfReportOnly` competency scores 8 or more. |

A check with nothing to judge on a case (no planted phrase, say) is left out of that case's count.

## Reading a run

- A check's **pass rate** is the share of answers that pass it, counted over every repeat.
- **Assessment answers vary** even at temperature 0, so the console answers every case three times and reports the
  spread. One repeat is enough to see whether a check can fail at all.
- A failed **band** check is first a question about the label: read the case before blaming the prompt. A failed
  contact, grounding or team-evidence check is the prompt's.
- **Dev** cases are for working on a prompt; score a finished change on **holdout**.

Every run of `AssessmentDraftEval` appends a section here: check counts and case keys only.
`LIGHTMOVE_EVAL_CASES=AD-005,AD-017 LIGHTMOVE_EVAL_SHOW=true` runs those cases and prints each answer, which is how
a label is checked against what the model wrote.

The two sections below are the first runs, on the dev cases. The **shipped** prompt fails three checks on some
cases: it takes a claim from an unidentified speaker or from the interviewer as the executive's (AD-005, AD-017,
AD-027), it sometimes names a call without the time into it (AD-007, AD-026), and five cases miss their band: two
because of those unidentified speakers' claims, three over whether "nothing on file" is a low score or no score
(AD-014, AD-018, AD-026; those labels wait on the consultant's review). The **broken** prompt, written to go wrong
in each way a check looks for, fails all eight.

## 2026-11-22 — shipped, gemini-3.5-flash, dev split, 18 cases × 1

| Check | Passed | Failed | Nothing to judge | Pass rate | Failed in |
|---|---|---|---|---|---|
| Every competency id is valid | 18 | 0 | 0 | 100% |  |
| Scores are whole numbers in range | 18 | 0 | 0 | 100% |  |
| Evidence names its source | 15 | 3 | 0 | 83% | AD-007, AD-026, AD-027 |
| Evidence is found in the input | 18 | 0 | 0 | 100% |  |
| No email or phone in any answer | 18 | 0 | 0 | 100% |  |
| Nothing taken from "(your team)" or an unidentified speaker | 3 | 3 | 12 | 50% | AD-005, AD-017, AD-027 |
| Score inside the labelled band | 13 | 5 | 0 | 72% | AD-005, AD-014, AD-018, AD-026, AD-027 |
| Self-report alone stays at 7 or below | 2 | 0 | 16 | 100% |  |

## 2026-11-22 — broken, gemini-3.5-flash, dev split, 18 cases × 1

| Check | Passed | Failed | Nothing to judge | Pass rate | Failed in |
|---|---|---|---|---|---|
| Every competency id is valid | 0 | 18 | 0 | 0% | AD-001, AD-003, AD-005, AD-007, AD-009, AD-010, AD-012, AD-014, AD-016, AD-017, AD-018, AD-019, AD-021, AD-024, AD-026, AD-027, AD-029, AD-030 |
| Scores are whole numbers in range | 0 | 18 | 0 | 0% | AD-001, AD-003, AD-005, AD-007, AD-009, AD-010, AD-012, AD-014, AD-016, AD-017, AD-018, AD-019, AD-021, AD-024, AD-026, AD-027, AD-029, AD-030 |
| Evidence names its source | 0 | 18 | 0 | 0% | AD-001, AD-003, AD-005, AD-007, AD-009, AD-010, AD-012, AD-014, AD-016, AD-017, AD-018, AD-019, AD-021, AD-024, AD-026, AD-027, AD-029, AD-030 |
| Evidence is found in the input | 0 | 18 | 0 | 0% | AD-001, AD-003, AD-005, AD-007, AD-009, AD-010, AD-012, AD-014, AD-016, AD-017, AD-018, AD-019, AD-021, AD-024, AD-026, AD-027, AD-029, AD-030 |
| No email or phone in any answer | 6 | 12 | 0 | 33% | AD-001, AD-005, AD-009, AD-010, AD-012, AD-014, AD-016, AD-017, AD-021, AD-026, AD-027, AD-029 |
| Nothing taken from "(your team)" or an unidentified speaker | 3 | 3 | 12 | 50% | AD-005, AD-017, AD-027 |
| Score inside the labelled band | 0 | 18 | 0 | 0% | AD-001, AD-003, AD-005, AD-007, AD-009, AD-010, AD-012, AD-014, AD-016, AD-017, AD-018, AD-019, AD-021, AD-024, AD-026, AD-027, AD-029, AD-030 |
| Self-report alone stays at 7 or below | 0 | 2 | 16 | 0% | AD-012, AD-029 |
