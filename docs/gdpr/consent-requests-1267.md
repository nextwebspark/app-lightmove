# GDPR: consent requests per purpose (#1267 phases 2 and 3)

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
| `app_lm_person_consent` | where each purpose stands: `REQUESTED`, `GIVEN`, `DECLINED`, `WITHDRAWN`, `EXPIRED`. No row is never asked. `request_expires_at` is set while a request is open; on a `GIVEN` row it is a renewal waiting. `wording` and `wording_version` keep the sentence last answered; the open request's own sentence and terms (`request_wording`, `request_wording_version`, `request_client_named`, `request_months`) wait beside them and are copied across only by an answer, so asking again never rewrites the proof of a consent given |
| `app_lm_person_consent_event` | append-only: each `REQUESTED`, `ANSWERED`, `RECORDED` (by hand) and `EXPIRED`, with who acted (`STAFF`, `PAGE`, `BY_HAND`, `SYSTEM`) and the sentence shown |
| `app_lm_person_privacy_link` | gains `CONSENT` (a request's page, 30 days) and `MANAGE` (a confirmation's link to withdraw, as long as the longest consent in force; one with no end date, like market updates, counts as 36 months from the answer) |

## How it works

- **Asking** (`PersonConsentService`): one person or a selection of up to 500, for keep on file and marketing (a share
  is asked from its position, phase 3). The firm must be in consent mode with its details set. A person is skipped
  when no privacy email may reach them (no email, do not contact, restricted, suppressed), when everything asked is
  already given beyond the renewal window (`already_given`), or when it was asked, or declined or withdrawn, in the
  last 14 days (`recently_asked`) — whether or not that request is still open. Requests and privacy notices share one daily budget and one lock (`PrivacyEmailBudget`).
  The email carries the notice's facts, so it marks the person told (`PRIVACY_EMAIL`).
- **A refused send** is undone by `ConsentRequestMailer`: a purpose the request created is deleted, any other put back
  as it stood (only while the row is still that request's: an answer recorded by hand or a newer request since stands), the person no longer marked told, `CONSENT_REQUEST_FAILED` on the timeline and a failed audit event.
- **Answering** (`PrivacyPageService.answer`): on a `CONSENT` link, `given` or `declined` while the request is open; on
  a `MANAGE` link, `withdrawn`. Answering twice changes nothing. A keep-on-file consent lasts the months the request
  promised (`request_months`, set when it was sent), never the settings at the time of answering; a yes to a renewal extends it from the day it would have ended, and a no leaves it to run to that day.
  Every answer is confirmed by email, listing what was answered; while anything agreed to is in force the email
  carries a `MANAGE` link. A no to a renewal is confirmed as "Not renewed, ends on …". Whoever asked, else the owner,
  else whoever sent the link — the first still on the staff — gets a `CONSENT_ANSWERED` bell (under the replies
  preference). A reopened request link shows what was answered on it, read from the history.
- **By hand**: a colleague records an answer given another way, with its day (today or earlier, a day's slack for a
  browser ahead of UTC). It clears the request's `requested_at`, so a day before the request still leaves the
  consent due for renewal.
- **The daily scan** (`ConsentExpiryScanner`, `lightmove.data-protection.consent-scan`, 03:20 UTC): requests past their
  date close (`EXPIRED`, or a renewal simply closes on a still-given consent); consents past `valid_until` become
  `EXPIRED` (in batches of 500, up to 10,000 a run); keep-on-file consents ending within `consent_renewal_days` are
  asked once more, within the day's budget, paging past the people no email may reach so they never hold back the
  rest.
- **Retention**: a keep-on-file consent in force holds the person out of the review list, and consent lines on the
  timeline never reset its clock, so asking someone does not keep them.
- **Views** (Filter → Show): `consent_asked` (an open request), `consent_ending` (ends within 30 days), `consent_refused`
  (declined or withdrawn).
- **Subject access**: `consents` and `consentHistory` sections.

## Endpoints

| Route | Gate | Body → answer |
|---|---|---|
| `POST /api/v1/candidates/{personId}/consent-requests` | `CANDIDATE_POOL_MANAGE` | `{purposes: [keep_on_file \| marketing \| share_with_client], projectId?, nameClient?}` → the person record; a share needs `projectId`, a position they are on and the caller holds `WORK_EXECUTE` on |
| `POST /api/v1/candidates/bulk/consent-requests` | `CANDIDATE_POOL_MANAGE` | `{personIds, purposes}` → `{sent, skipped: [{personId, reason}]}` |
| `POST /api/v1/candidates/{personId}/consents/recorded` | `CANDIDATE_POOL_MANAGE` | `{purpose, projectId?, answer: given \| declined \| withdrawn, answeredOn}` → the person record; a share also needs `WORK_EXECUTE` on `projectId` |
| `POST /api/v1/privacy/links/{token}/consents` | the link | `{answers: [{consentId, answer}]}` → 204 |
| `GET /api/v1/privacy/links/{token}` | the link | gains `purpose` and `consents` |
| `GET /api/v1/projects/{projectId}/share-consents` | `WORK_EXECUTE` | → `{asksToShare, clientNamable, clientName, rows: [{candidateId, status, requestedAt, requestOpenUntil, answeredAt, clientNamed}]}` |
| `POST /api/v1/projects/{projectId}/candidates/{candidateId}/share-consent-request` | `WORK_EXECUTE` | `{nameClient?}` → the row; naming the client of a confidential position `SHARE_CLIENT_CONFIDENTIAL` |
| `GET /api/v1/candidates/export/consent-register` | `CANDIDATE_POOL_MANAGE` | `?position=` narrows to that position's shares → CSV, audited `CONSENT_REGISTER_EXPORTED` |
| `GET /api/v1/projects/{projectId}/export/consent-register` | `WORK_EXECUTE` | → the position's shares as CSV, audited |
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
  Ask for consent and Record an answer, also in the ⋯ menu. The selection bar's Ask for consent; the Show views
  Asked, no answer · Consent ending in 30 days · Declined or withdrawn.
- **Dialogs** (`features/candidates/components/consent/`): `RequestConsentDialog` (one person, purposes already given
  or asked, declined or withdrawn in the last 14 days locked; a selection answers who it skipped and why after it sends) with the email
  previewed word for word; `RecordConsentDialog`.
- **The public page** (`PrivacyLinkPage` → `ConsentAnswers`): a request asks Yes or No per purpose with nothing
  selected; a renewal asks once; answered lists what was said, on a reopened link too; a closed request says that
  anything unanswered changed nothing. A manage link
  lists what is in force, each withdrawn after one confirmation.
- The bell names `consent_answered` as "answered your consent request".

## Phase 3: share with client

- **What is asked.** Whether the firm may share the person's profile with the position's client, for that one
  position (`SHARE_WITH_CLIENT`, a row per person and position). From the Pipeline card's ⋯ (Ask to share with
  client…), from the client-visibility prompt's Ask first, or ticked beside the other purposes in the drawer's Ask for
  consent. Never in a bulk request: a share names one position.
- **Naming the client.** The email, the page and the confirmation say "a confidential client" unless staff tick Name
  the client; a position whose brief is marked confidential (`app_lm_position.confidential`) refuses it
  (`SHARE_CLIENT_CONFIDENTIAL`). `client_named` keeps the choice; the page names the client only when it is set and the
  search is still not confidential (`ShareTarget.clientNamedOrNull`). The share email never claims nothing was shared:
  visibility starts on.
- **The role takes its own article.** The mockups write "for a {role} role"; the email, the page's sentence and the
  SPA's preview pick "a" or "an" by how the title sounds (`IndefiniteArticle`, mirrored by `consentPreview.withArticle`),
  so an Operations Director is never "a Operations Director".
- **Asking from the person's record still needs the position.** The person-level doors (`consent-requests`,
  `consents/recorded`) ride the pool's `CANDIDATE_POOL_MANAGE`, so a share there also asks the named position's
  `WORK_EXECUTE`, as the position's own door does; the drawer lists only the positions the viewer works.
- **A no hides them from the client (#1488, B12).** A declined or withdrawn share hides the person from every read a
  client seat reaches on that position — list, drawer, photo, Companies grid, talent map, report, export, projects
  list counts, public API and MCP — whatever the team's switch says. The database keeps it: V246's
  `app_lm_project_candidate.client_hidden_reason`, written by triggers when a share's status changes, a restriction
  is set or lifted, or someone is mapped again, and every client read filters on the generated `shown_to_client`.
  Asking again after a no, or the request lapsing, keeps them hidden until a yes (the last answer is read, since a
  re-ask turns the row back to `REQUESTED`). The Pipeline card locks its switch off with "Hidden from {client}" and
  the badge says Said no to sharing · Withdrew sharing; the pipeline row's `clientHiddenReason` is staff-only.
- **The visibility switch warns, never blocks** for someone never asked or not yet answered (grey; B7 and B14 are with
  counsel). In consent mode with share offered, turning a person visible on the
  Pipeline card or the Outreach pane's Client visible switch without a given share consent opens "No consent to share
  with {client} yet" (Ask first · Show anyway · Cancel). Show anyway shows them; the timeline line and the audit event
  then carry `withoutShareConsent: true`. Hiding someone never asks. The Outreach pane's Ask first sends at once in the
  confidential wording; naming the client is the Pipeline card's dialog.
- **Badges.** Each Pipeline card says No consent to share · Consent asked · Consent given. Visibility defaults to on, so
  in a workspace that asks, most cards carry the first until people are asked.
- **The consent register.** Every request and answer as a CSV (name, person id, purpose, position, client named,
  status, asked on and by, answered on and how, valid until, wording version, wording), never a contact detail, formula
  cells defused, refused past `lightmove.export.max-candidates` lines. From Settings (the whole workspace) and the
  position's Advanced menu (that position's shares).
- **The drawer** lists a share row per position the person was asked about, or is on while share is offered.

## Left open

- The emails' footer is the shared on-behalf footer ("Sent by {firm} with Uncava"), not the mockup's "{firm} is
  responsible for your data. Sent by Uncava, the software they use." — changing it changes the privacy notice too.
- The bulk dialog cannot say who will be skipped before it sends (the mockup's "{n} will be asked · {n} skipped");
  it says who was, after.
- The Ask dialog does not pre-tick the share purpose, as the mockup does: sharing is a separate act for one position.
- Marketing consent is recorded only; nothing sends marketing.
