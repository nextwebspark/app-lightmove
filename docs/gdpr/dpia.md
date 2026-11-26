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
| R14 | A dashboard chart singles out a person in a small group, or states an AI guess as fact | L | M | library queries only, no free-form builder; gender counted from researcher-recorded values only, and a level suppressed server-side when under 3 are recorded or any gender in it counts 1 or 2; workspace charts seat-gated; no nationality chart; staff only ([custom-dashboards.md](../custom-dashboards.md)) | — | L |

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

## UAE addendum (2026-11-19)

_Draft for counsel; not legal advice. Sources: the Alac Partners thread to 2026-11-19
([Uncava_Data_Protection_Raw_Emails.md](../GDPR-discusstion/Uncava_Data_Protection_Raw_Emails.md)), message 7 for the
agreed changes, and the code and docs as read on 2026-11-19. Nothing above this heading has changed. "Current" is what
the repository documents today; "target" is what was agreed on 2026-11-19 and is **not yet built** unless said otherwise.
Legal readings are working assumptions [Counsel to confirm]._

### 1. Scope: three regimes, one standard

The lawyer's recommendation is to build to the highest common standard: the PDPL's consent approach (which has no
legitimate-interests basis) combined with the DIFC and ADGM requirements on service providers, documentation, transfers
and AI. §1 above (a DPIA is required) applies unchanged; this addendum adds the UAE tests to §4 and §5. The lawyer
also lists a DPIA for the AI features as one of the documents shown to regulators and large clients, drafted from her AI
inventory (message 6).

### 2. Automated processing and AI: PDPL Art. 18 and DIFC Regulation 10

| # | Test | Applies to | Analysis | Status |
|---|---|---|---|---|
| U1 | PDPL Art. 18: a person's right related to decisions made by automated processing, including profiling [Counsel to confirm the exact scope, and whether a human review removes the right] | assessment draft and scorecard; Find executives ranking; the shortlist verdict (§3 below) | The lawyer flagged the assessment as "an assessment of a person that affects decisions" and named the risk of one-click approval and of scores with no interview evidence. Positive: a manual path exists, and the AI never overwrites the recruiter's scores. Ranking decides who is added, never who is rejected | **Open** ⚖️ |
| U2 | DIFC Regulation 10: AI systems processing personal data (enforced from January 2026) [Counsel to confirm the duties that apply, for example transparency, human oversight, an assessment before deployment, and whether they apply to Uncava as processor, to the customer, or to both] | assessment draft; nationality and gender readings (to be removed); Notetaker summary; live copilot | The lawyer named it for the assessment and for the AI-in-meetings features. The AI inventory she holds is incomplete: see §5 | **Open** ⚖️ |
| U3 | Special-category or sensitive data | nationality reading | She treats "South Asian" as an ethnic-origin category, so sensitive, and inference from a name as a discrimination risk. The existing §4 "Art. 9" paragraph (options for counsel) is **superseded by the agreed removal** below | Agreed 2026-11-19 |
| U4 | Consent to recording and to real-time analysis | Notetaker and live copilot | She states recording without consent may be a criminal offence in the UAE [Counsel to confirm] | Agreed 2026-11-19; see §3 |
| U5 | Purpose limitation and privacy by default | live copilot | It switches on with the Notetaker, which she says goes against privacy by default | Agreed 2026-11-19; see §3 |

### 3. Changes agreed on 2026-11-19: current state against target

| Area | Risk | Current (documented) | Target (agreed) |
|---|---|---|---|
| **Nationality** | High | `AiUse.NATIONALITY` places a person in one of eleven groups with evidence; field `ai_infer_nationality` (off for new workspaces); a consultant accepts or changes it ([§4](#4-profiling-and-automated-decisions-922) above) | **Remove AI inference of nationality and origin.** Manual **"Local national"** dropdown for Emiratisation and Saudisation: *UAE national / KSA national / Other GCC national / Not a local national / Not confirmed* (**default**), filled only from the candidate's own statement or CV. An **optional "Nationality" field** means citizenship, again only from the candidate's statement. Client reports show aggregated data only. Remove the readings and `ai_inferred_fields` entries already stored [open: migration of existing values, Counsel to confirm] |
| **Gender** | Medium | `ai_infer_gender` proposes a missing value; the diversity chapter counts values still in `ai_inferred_fields` | **Remove AI inference.** A manually filled field, shown only in aggregated reports, for clients who need diversity reports. Both nationality and gender may stay as options "for local companies or those who need diversity reports", filled in by a person |
| **Assessment** | High | `AiUse.ASSESSMENT_DRAFT`: the AI drafts evidence and 0 to 10 scores per competency; software computes the weighted total; a score from career alone is held between 3 and 7; the consultant edits and submits | (a) each AI score **confirmed individually** before submit; (b) behavioural, personality and motivation scores **only from notes or transcripts**, else "insufficient evidence"; (c) **sources shown for each score**; (d) a **log of differences between the AI draft and the final** version; (e) **rounded overall score**; (f) the **client report states the draft was AI-assisted** |
| **Notetaker (Recall.ai)** | High | The consultant "asks the executive first" (a prompt, not a record). On join the bot posts a chat line naming itself the AI notetaker, saying the call is recorded and transcribed and that anyone who would rather not be recorded need only say so; its camera card shows Recording status. Recall drops its copy after 7 days; the transcript's period is `lightmove.recall.transcript-retention` ([notetaker.md](../notetaker.md)) | Replace "ask first" with a **mandatory confirmation of the candidate's consent, saved in the consent register, before the bot joins**; the **bot introduces itself at the start of the call**; **default retention of recordings 90 days**. The consent register is not built, so this depends on #1267 |
| **Live copilot** | High | **On until switched off**, together with the Notetaker ([notetaker.md](../notetaker.md)); the join chat line mentions it when on | **Off by default, a separate switch**; when on, the candidate must know the call is analysed by AI in real time, as part of the consent confirmation |

Retention in the target row for recordings (90 days) differs from Recall's 7-day copy and from the transcript period:
which artefacts the 90 days covers (video, audio, transcript, summary) is to be settled [Counsel to confirm]. The lawyer's
inventory also lists the Gmail quick-reply suggestions shown under the last message (not sent): "Yes, I agree.", "Thanks
for the update.", "Good points."; they are low risk and not otherwise analysed here.

### 4. Risk register additions

Scoring is engineering's first estimate, as in §5 above (L, M, H).

| # | Risk to people | L | S | Measures in place | Target | Residual |
|---|---|---|---|---|---|---|
| R14 | A person is processed on a PDPL basis that does not exist (legitimate interests only; no consent recorded) | H | H | none in the UAE sense; GDPR basis only | consent register and statuses (#1267); a PDPL basis for every row of [ropa.md](ropa.md) UAE addendum | [open] ⚖️ |
| R15 | Ethnic-origin category inferred from a name | M | H | nationality reading off for new workspaces | AI inference removed; manual "Local national" field | L |
| R16 | An AI score drives a hiring decision with no interview evidence | M | H | human submits; inferred scores capped 3 to 7 | per-score confirmation, evidence-only behavioural scores, sources, draft-vs-final log, "AI-assisted" label | M ⚖️ (U1, U2) |
| R17 | A call is recorded or analysed without the candidate's consent | M | H | bot chat line on join; "ask first" prompt | consent saved before the bot joins; copilot off by default; 90-day retention | L |
| R18 | AI uses missing from the regulator-facing inventory | M | M | `AiLogicCatalogTest` (a new prompt fails until explained) | reconcile the inventory (§5) | L |

### 5. AI uses not in the lawyer's inventory

The lawyer's inventory (`Uncava_AI_inventory.xlsx`, message 5) was not opened for this addendum, so the gaps below are
from engineering's knowledge of the product, not a comparison against that file. Names are as given by engineering and
each should be checked against `AiLogicCatalogTest` and the code before the inventory is sent.

| Use | Where | What it does | Contacts seen by the model |
|---|---|---|---|
| Person shortlist verdict | `/api/v1/llm/shortlist` | gives a verdict on a person against a position | [to verify] |
| Embeddings | `/api/v1/llm/embed` | turns text into vectors for matching | [to verify] |
| Assistant chat | the assistant (`assistant-tools.md`) | answers a user's questions over their workspace through tools | [to verify] |
| Position extraction | `position-extraction.md` | reads a brief or document into position fields | [to verify] |
| Import column mapping | `spreadsheet-import.md` | the model sees headers only | no (per §3 above) |
| Search-words drafting | `SearchWordsDrafter` | turns a brief into search words | no [to verify] |
| Outreach opener | `outreach-conversations.md` | drafts an opening line for an email | redacted [to verify] |
| Call-change suggester | Notetaker | suggests changes to a record from a call | [to verify] |
| Meeting summary and live coach | Notetaker | summary after the call; live coaching during it | transcript content [to verify] |
| Interview plan | the interview copilot (`app_lm_interview_plan`) | questions for a call | [to verify] |

Several of these send personal data of mapped people to Vertex AI at `global` ([tia.md](tia.md)). Under DIFC Regulation 10
each is a candidate "AI system processing personal data" [Counsel to confirm].

| Version | Date | By | Sign-off |
|---|---|---|---|
| UAE addendum | 2026-11-19 | engineering | [counsel] |
