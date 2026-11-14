# Data protection impact assessment: the candidate database

_Draft for counsel (#930, #922, epic #917). Engineering facts read from the code on 2026-11-13; not legal advice. The
likelihood and severity scores are engineering's first estimate for counsel to revise._

## 1. Why a DPIA is required

Art. 35(3) and the EDPB criteria point the same way: the candidate database evaluates and scores people (AI
assessment drafts, rule-based ranking), infers attributes that can reveal ethnic origin (nationality), is built
largely from data collected from third parties without the person's knowledge, is held at scale across many firms,
and is used to approach people in bulk. At least four criteria are met, so a DPIA is required before the processing
is scaled. This document covers the processing as a whole; §4 is the profiling chapter #922 asked for.

## 2. Systematic description

**Controllers and processor.** Each customer firm controls its own workspace; Uncava is its processor and controls
its own vendor cache ([ropa.md](ropa.md)).

**Data flows.**

```
Vendors (Bright Data, HarvestAPI, ContactOut) ─┐
Capture extension (name + profile URL) ────────┤
Spreadsheet import, manual entry ──────────────┼─► app_lm_person (one per workspace) ─► mapped to positions
Find executives / People Search (rules) ───────┘          │
                                                         ├─► Vertex AI: background, nationality, assessment draft,
                                                         │   call notes, openers (contacts redacted)
                                                         ├─► Outreach: Gmail / Graph / Nylas ─► replies read back
                                                         ├─► Notetaker: Recall.ai audio ─► transcript ─► notes
                                                         └─► Client seat (read-only report), CSV / API / MCP
```

**Scale.** One record per executive per workspace; the vendor cache is shared by every workspace for 30 days.

**Personal data.** [ropa.md](ropa.md) §2; every table in [retention-schedule.md](retention-schedule.md).

## 3. Necessity and proportionality

| Question | Answer from the product |
|---|---|
| Lawful basis | Legitimate interests for mapping, sourcing, outreach and the cache ([lia.md](lia.md)); no Art. 9 condition yet for nationality ⚖️ |
| Minimisation | The extension sends a name and URL only; import shows the model headers only; models never see contacts, compensation or custom columns (`ContactRedaction`, `CandidateDossier`); contacts are looked up only on request |
| Accuracy | Every AI value is flagged in `ai_inferred_fields` until a consultant confirms or changes it; the drawer shows it as the AI's |
| Storage limitation | The firm's candidate period with admin review; fixed periods for system rows; the cache 30 days; the workspace purge ([retention-schedule.md](retention-schedule.md)) |
| Transparency | The subject access export explains each AI use (`AiUse`); the Art. 14 notice (#923) and the outreach footer with its opt-out (#924) are built behind `DATA_PROTECTION_ENABLED` |
| Rights | Access, erasure, restriction, objection with suppression, portability (the export's JSON) ([use-cases.md](use-cases.md)) |
| Processors | [subprocessors.md](subprocessors.md); transfers in [tia.md](tia.md) |

## 4. Profiling and automated decisions (#922)

| Use | What it does | Who decides | Controls |
|---|---|---|---|
| Nationality reading (`AiUse.NATIONALITY`) | places a person in one of eleven groups, or Unknown, with evidence for and against | a consultant accepts or changes it; only a high-confidence reading fills the field, flagged | `ai_infer_nationality` (off for new workspaces); turning it off clears unconfirmed values and readings; never uses appearance, religion or inferred ethnicity |
| Gender, years of experience, seniority (`AiUse.BACKGROUND`) | proposes a missing value | a consultant confirms or changes it | `ai_infer_gender`; never replaces a value already held |
| Assessment draft (`AiUse.ASSESSMENT_DRAFT`) | drafts evidence and 0–10 scores per competency; software computes the weighted total | the consultant edits and submits; a score from career alone is held between 3 and 7 | `ai_draft_assessments`; refused for a restricted person |
| Find executives ranking (`SuggestionJudge`, `SeatFit`) | fixed rules choose which search hits are filed as suggestions | a consultant decides whom to approach | no model ranks people; the brief becomes search words (`SearchWordsDrafter`) |
| Diversity chapter of the report (`DiversityReporter`) | counts gender and nationality per position | — | **counts values still in `ai_inferred_fields`**, and a client seat reads the report (#922's open code item) |

**Art. 22.** No use produces a decision with legal or similarly significant effect without a human: every output is a
draft a consultant reviews (`AiUse.NO_SOLELY_AUTOMATED_DECISION`). Whether the rule-based ranking is an automated
decision about the people it leaves out is open ⚖️: it decides who is added, never who is rejected, and the consultant
can search again.

**Art. 9.** A nationality group can reveal ethnic origin. Options for counsel ⚖️: keep it off by default with an
Art. 9(2) condition the firm must hold; drop it for people in the EU, EEA and UK; or drop it entirely.

## 5. Risks and measures

Likelihood and severity: L(ow), M(edium), H(igh). Residual risk assumes the measures marked *planned* ship.

| # | Risk to people | L | S | Measures in place | Planned | Residual |
|---|---|---|---|---|---|---|
| R1 | Collected without being told (Art. 14) | H | M | subject access export; erasure and objection; #923 notice; #924 footer (the first email tells them) | #1267 consent phases | L |
| R2 | Nationality reveals ethnic origin; discrimination in selection | M | H | switch off for new workspaces; clearing on off; evidence rules; human review | an Art. 9 decision ⚖️; diversity report counting confirmed values only | M |
| R3 | Inaccurate AI value treated as fact | M | M | flags until confirmed; correction; export shows the logic | — | L |
| R4 | A scored assessment unfairly excludes someone | L | H | human submits; total computed by software; inferred scores capped | Art. 22 position ⚖️ | L |
| R5 | Unwanted bulk outreach; objection ignored | M | M | do not contact, restriction and suppression stop sends; #924 footer opt-out and one-click `List-Unsubscribe` (not from Microsoft mailboxes) | — | L |
| R6 | A person objects to Uncava but stays in other workspaces | M | M | erasure removes the shared cache entry | the request procedure; cross-workspace suppression (#920) ⚖️ | M |
| R7 | Cross-tenant disclosure | L | H | per-query workspace filter; forced row-level security; tests | — | L |
| R8 | Uncava staff read tenant data directly | M | H | audit trail for app actions | remove standing grants, break-glass access (data-protection-plan 1.1); Data Access audit logs (1.2) | L |
| R9 | US transfer; government access | M | M | encryption in transit and at rest; contacts redacted from model input | mechanisms and TIA ([tia.md](tia.md)); Vertex pinned (#925) | M ⚖️ |
| R10 | Data lost or kept too long | M | M | retention review; system sweeps; workspace purge | backups and PITR on; `RETENTION_ENABLED` on after sign-off | L |
| R11 | A breach goes unnoticed | M | H | lockout, rate limits, ERROR/5xx alerts | audit-event alerts; [breach-runbook.md](breach-runbook.md) | M |
| R12 | Personal data in logs outlives an erasure | M | L | `PersonalDataLogMasker`; ids-only audit; 30-day logs | the `?q=` request-log decision ⚖️ (#927) | L |
| R13 | Client representative reads staff-only data | L | M | client seats never receive notes, documents, tasks, assessments, AI readings or contacts | hide restricted people from client seats ⚖️ (#920) | L |

## 6. Consultation and sign-off

- **Data subjects' views (Art. 35(9)):** [whether and how to seek them ⚖️].
- **DPO advice:** [the DPO's view, once the DPO decision is made; see governance-decisions.md].
- **Prior consultation (Art. 36):** required only if a high residual risk remains. R2, R6, R9 and R11 stay Medium on
  this draft; counsel decides whether any is high ⚖️.
- **Review:** on any new AI use (a new prompt fails `AiLogicCatalogTest` until it is explained), a new sub-processor,
  or once a year.

| Version | Date | By | Sign-off |
|---|---|---|---|
| Draft 1 | 2026-11-13 | engineering | [counsel] |
