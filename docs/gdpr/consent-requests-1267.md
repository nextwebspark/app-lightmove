# GDPR: consent requests per purpose (#1267 phase 2)

Part of epic #917, step 7, after [privacy-notices-923.md](privacy-notices-923.md) (phase 1). A firm in consent mode asks a
person, per purpose, whether it may keep their details on file, send them market updates, or (phase 3) share their
profile with a client for one position. The person answers on their privacy page, and can withdraw any time.

Everything here sits behind `DATA_PROTECTION_ENABLED` and is staff-only: consents never reach `CandidateResponse`, the
talent map, the report, the public API or MCP.

## Decisions taken without counsel ⚖️

Each is a row of [governance-decisions.md](governance-decisions.md) (B7–B11) for counsel to overturn.

- **Warn, never block.** A person whose basis is consent with no keep-on-file consent in force gets `consentWarning` on
  the drawer. AI, outreach and client visibility carry on.
- **Silence is never consent.** A request nobody answers closes as `EXPIRED` after 30 days and changes nothing else:
  not the basis, not retention, not what a client sees.
- **Proof goes with the person.** Consents and their history are deleted on erasure (cascade); the audit trail keeps
  ids and purposes only.
- **Sent as the firm** through Uncava's sender, its privacy contact as reply-to, like a privacy notice.
- **A share names "a confidential client"** unless staff choose to name it (phase 3).

## The model (V223)

| Piece | What |
|---|---|
| `app_lm_workspace_candidate_privacy` | `privacy_mode` `NOTICES \| CONSENT`; the purposes offered (`consent_keep_on_file`, `consent_share_with_client`, `consent_marketing`); `keep_on_file_months` 12, 24 or 36; `consent_renewal_days` 0–90 (0 never asks again) |
| `app_lm_person_consent` | where each purpose stands: `REQUESTED`, `GIVEN`, `DECLINED`, `WITHDRAWN`, `EXPIRED`. No row is never asked. `request_expires_at` is set while a request is open; on a `GIVEN` row it is a renewal waiting. `wording` and `wording_version` keep the sentence answered |
| `app_lm_person_consent_event` | append-only: each `REQUESTED`, `ANSWERED`, `RECORDED` (by hand) and `EXPIRED`, with who acted (`STAFF`, `PAGE`, `BY_HAND`, `SYSTEM`) and the sentence shown |
| `app_lm_person_privacy_link` | gains `CONSENT` (a request's page, 30 days) and `MANAGE` (a confirmation's link to withdraw, as long as the longest consent in force, else 36 months) |

## How it works

- **Asking** (`PersonConsentService`): one person or a selection of up to 500, for keep on file and marketing (a share
  is asked from its position, phase 3). The firm must be in consent mode with its details set. A person is skipped
  when no privacy email may reach them (no email, do not contact, restricted, suppressed), when everything asked is
  already given beyond the renewal window (`already_given`), or when it was asked in the last 14 days
  (`recently_asked`). Requests and privacy notices share one daily budget and one lock (`PrivacyEmailBudget`).
  The email carries the notice's facts, so it marks the person told (`PRIVACY_EMAIL`).
- **A refused send** is undone by `ConsentRequestMailer`: a purpose the request created is deleted, any other put back
  as it stood, the person no longer marked told, `CONSENT_REQUEST_FAILED` on the timeline and a failed audit event.
- **Answering** (`PrivacyPageService.answer`): on a `CONSENT` link, `given` or `declined` while the request is open; on
  a `MANAGE` link, `withdrawn`. Answering twice changes nothing. A keep-on-file consent lasts the firm's months from
  the answer; a yes to a renewal extends it from the day it would have ended, and a no leaves it to run to that day.
  Every answer is confirmed by email, listing what was answered; while anything agreed to is in force the email
  carries a `MANAGE` link. Whoever asked, else the owner, gets a
  `CONSENT_ANSWERED` bell (under the replies preference).
- **By hand**: a colleague records an answer given another way, with its day (today or earlier).
- **The daily scan** (`ConsentExpiryScanner`, `lightmove.data-protection.consent-scan`, 03:20 UTC): requests past their
  date close (`EXPIRED`, or a renewal simply closes on a still-given consent); consents past `valid_until` become
  `EXPIRED`; keep-on-file consents ending within `consent_renewal_days` are asked once more, within the day's budget.
- **Retention**: a keep-on-file consent in force holds the person out of the review list, and consent lines on the
  timeline never reset its clock, so asking someone does not keep them.
- **Quick views**: `consent_asked` (an open request), `consent_ending` (ends within 30 days), `consent_refused`
  (declined or withdrawn).
- **Subject access**: `consents` and `consentHistory` sections.

## Endpoints

| Route | Gate | Body → answer |
|---|---|---|
| `POST /api/v1/candidates/{personId}/consent-requests` | `CANDIDATE_POOL_MANAGE` | `{purposes: [keep_on_file \| marketing]}` → the person record |
| `POST /api/v1/candidates/bulk/consent-requests` | `CANDIDATE_POOL_MANAGE` | `{personIds, purposes}` → `{sent, skipped: [{personId, reason}]}` |
| `POST /api/v1/candidates/{personId}/consents/recorded` | `CANDIDATE_POOL_MANAGE` | `{purpose, projectId?, answer: given \| declined \| withdrawn, answeredOn}` → the person record |
| `POST /api/v1/privacy/links/{token}/consents` | the link | `{answers: [{consentId, answer}]}` → 204 |
| `GET /api/v1/privacy/links/{token}` | the link | gains `purpose` and `consents` |
| `PUT /api/v1/workspace/candidate-privacy` | `WORKSPACE_MANAGE` | gains `privacyMode`, `keepOnFile`, `keepOnFileMonths`, `shareWithClient`, `marketing`, `renewalDaysBefore` (each optional; left out keeps what is held) |

## Words

`ConsentWording` (version `2026-11-16`) and `EmailTemplates` hold the copy `Privacy.dc.html` draws (uncava-design#4),
word for word: the page's sentence per purpose (the one an answer keeps), a renewal's sentence with its dates, each
email bullet, and the confirmation's label per purpose. The SPA's `consentPreview.ts` and `consentPageCopy.ts` mirror
them; a change is made in both.

## Screens

- **Settings → Data & retention → Candidate privacy** (`CandidatePrivacyCard`): Mode (Notices · Notices and consent
  requests), locked until the firm's details are saved; in consent mode the three purposes, "Valid for" and "Ask
  again N days before consent ends".
- **Candidates drawer** (`PersonPrivacySection`, consent mode only): a row per purpose offered or ever asked, its pill
  (Not asked, Asked, Given, Renewal asked, Declined, Withdrawn, Expired) and line; the No consent recorded warning;
  Ask for consent and Record an answer, also in the ⋯ menu. The selection bar's Ask for consent; the quick views
  Asked, no answer · Consent ending in 30 days · Declined or withdrawn.
- **Dialogs** (`features/candidates/components/consent/`): `RequestConsentDialog` (one person, purposes already given
  or asked in the last 14 days locked; a selection answers who it skipped and why after it sends) with the email
  previewed word for word; `RecordConsentDialog`.
- **The public page** (`PrivacyLinkPage` → `ConsentAnswers`): a request asks Yes or No per purpose with nothing
  selected; a renewal asks once; answered lists what was said; a closed request says nothing changed. A manage link
  lists what is in force, each withdrawn after one confirmation.
- The bell names `consent_answered` as "answered your consent request".

## Left open

- The emails' footer is the shared on-behalf footer ("Sent by {firm} with Uncava"), not the mockup's "{firm} is
  responsible for your data. Sent by Uncava, the software they use." — changing it changes the privacy notice too.
- The bulk dialog cannot say who will be skipped before it sends (the mockup's "{n} will be asked · {n} skipped");
  it says who was, after.
- Share with client, its prompt on the client-visibility switch, and the consent register export: phase 3.
- Marketing consent is recorded only; nothing sends marketing.
