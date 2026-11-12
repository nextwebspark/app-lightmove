# GDPR: AI on people — switches and the demographics scope (#922)

The engineering half of #922, step 4 of epic #917. The other half is the DPIA and the Art. 22 write-up, which go
with #930 (step 7).

| Piece | Migration | Where |
|---|---|---|
| Four per-workspace AI switches | V213 | Settings → Data & retention → AI on people |
| Gender and nationality behind their own API scope | V214 | `candidates.demographics:read` on API keys and AI apps |

## The four switches

Each switch belongs to the workspace, not to a member. Changing one needs `WORKSPACE_MANAGE` (admins). Every change is
audited as `WORKSPACE_UPDATED` with `section=aiControls`, one `on`/`off` detail per switch that moved, and the counts
cleared (`genderCleared`, `nationalityCleared`). Details hold no names, as `AuditDetailKeysTest` requires.

| Switch | Column | While it is off |
|---|---|---|
| Infer gender | `ai_infer_gender` | The background call is never asked for a gender, and an answered one is discarded (`BackgroundNeeds`, `CandidateAiEnricher`) |
| Infer nationality | `ai_infer_nationality` | The nationality classifier is never asked; `GET …/nationality-reading` answers 204 |
| Draft assessments with AI | `ai_draft_assessments` | Assess with AI is refused (`AI_ASSESSMENT_DISABLED`, 409); the drawer hides the button |
| AI-enrich on capture | `ai_enrich_automatically` | `CandidateAiEnrichWorker` runs nothing after a capture or a Find executives filing |

`CANDIDATE_AI_ENRICH_ON_CAPTURE` stays as the platform's kill-switch above the last one. Years of experience and
seniority are still proposed while gender is off: neither is special-category data.

### Turning gender or nationality off clears what the AI left

The clear runs in the same transaction as the switch (`InferredBackgroundEraser`, declared in `workspace` and
implemented in `candidate`), for every person in the workspace:

- **Gender:** a gender still listed in `ai_inferred_fields` is nulled and its flag removed.
- **Nationality:** the same for nationality, and `ai_nationality_reading` is nulled on every person. The reading is
  itself an inference, even when it said Unknown.
- V91's frozen copy on `app_lm_project_candidate` is cleared alongside.
- The person's `version` is bumped, so a drawer save racing the clear fails rather than writing the value back.

A value a consultant typed, imported or confirmed is never touched: confirming a value removes its flag. The clear
cannot be undone, so the screen asks first and says how many candidates it touches
(`GET /workspace/ai-controls/inferred-background`).

Existing assessment drafts are not cleared when Draft assessments goes off. They are a consultant's work in progress,
and every drafted value stays marked as the AI's.

### Defaults ⚖️

- Every workspace that existed before V213 keeps all four switches **on**, so the release changes nothing for
  existing customers.
- Every workspace founded since starts with all four **off**.
- The switches are **not** behind `DATA_PROTECTION_ENABLED`. They only reduce processing and depend on none of that
  flag's go-live steps. Data & retention is therefore reachable whatever the flag says; the retention, suppression and
  export cards still show only while it is on.

This is the "off for new workspaces" option the epic named. Counsel may still ask for existing workspaces to be
switched off too; that would be one migration.

## The demographics scope

Gender and nationality leave over `/api/v1/public/**` and MCP only for a key or an AI app holding
`candidates.demographics:read`, never implied by `candidates:read`. Without it both fields are `null`. A value the
model proposed and nobody confirmed was already `null` on the API, and still is.

Existing keys and AI-app connections do not hold the new scope, so they stop receiving both fields. An admin who needs
them makes a key with the scope. The consent screen offers only the scopes an AI app registered with
(`OAuthConsentService`), so an app registered before V214 must register again before the scope can be ticked.

## DSAR "logic involved" text

`AiUse` (the subject access export's Art. 15(1)(h) text) now says which switch governs each use: nationality, the
gender proposal, and the assessment draft.

## Left for #930 and later ⚖️

- The DPIA (`docs/gdpr/` once written) and the Art. 22 position: AI output is a draft and a consultant decides. That
  is already true on screen and stated in `AiUse.NO_SOLELY_AUTOMATED_DECISION`.
- Whether nationality inference stays at all for people in the EU/EEA/UK.
- `SourcedHitRanking` picks which search hits Find executives files. It decides who is *added*, never who is
  rejected, but counsel should confirm that is not an automated decision about a person.
- The report's diversity chapter still counts an unconfirmed AI nationality while the switch is on
  (`docs/ai-production-readiness.md` §2 asks it not to).
