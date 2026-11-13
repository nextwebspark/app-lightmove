# GDPR: candidate privacy notices and the lawful basis (#923, #1267 phase 1)

Step 6 of epic #917. Every person a workspace holds was sourced, never asked, so the firm (the controller) owes them
an Art. 14 notice within a month, or at the first contact, and a recorded lawful basis. This piece lets a firm record
both and send the notice through Uncava in its own name. #924 (the outreach footer and opt-out) builds on it.

Everything here sits behind `DATA_PROTECTION_ENABLED` (`lightmove.data-protection.enabled`) like the other people
controls, and is staff-only: it never reaches `CandidateResponse`, the talent map, the report, the public API or MCP.

| Piece | Where | Migration |
|---|---|---|
| The firm's privacy details | Settings → Data & retention → Candidate privacy | V218 |
| A lawful basis per person | Candidates drawer → Contact → Privacy | V218 |
| Send privacy notice, one or many | the drawer's ⋯, the Candidates selection bar | V218 |
| Mark as notified | the drawer's ⋯ and Privacy block | V218 |
| The public page a notice links to | `/privacy/:token` | V218 |
| Not notified quick view | Candidates | V218 |

## The firm's details

`app_lm_workspace_candidate_privacy`, one row per workspace: the firm's legal name (named as controller), its privacy
contact (replies go there), its notice link (https only), and the default lawful basis (`LEGITIMATE_INTEREST` unless an
admin picks `CONSENT` or `CONTRACT`).

- `GET /api/v1/workspace/candidate-privacy` (`CANDIDATE_POOL_MANAGE`) and `PUT` (`WORKSPACE_MANAGE`).
- A change is audited `WORKSPACE_UPDATED` with `section=candidatePrivacy` and `changed` naming the fields that moved,
  never their values. A save that changes nothing records nothing.
- No notice is sent until the name, the contact and the link are all set (`CANDIDATE_PRIVACY_NOT_SET_UP`).
- The row goes with the workspace's purge (`WorkspacePurgeTables` finds it by its `workspace_id`).

## A person's lawful basis and notice record

`app_lm_person` gains:

| Column | Meaning |
|---|---|
| `lawful_basis` | the basis a colleague recorded; null follows the workspace's default |
| `lawful_basis_set_by`, `_set_at` | who and when, kept only while a basis is recorded |
| `privacy_notified_at`, `privacy_notice_channel` | when the person was last told, and how: `PRIVACY_EMAIL`, `OUTREACH_EMAIL` (#924), `MARKED_BY_HAND` |

- `PUT /api/v1/candidates/{id}/lawful-basis` `{"lawfulBasis":"consent"}`, or `null` for the default.
- `POST /api/v1/candidates/{id}/privacy-notice/marked` `{"notifiedOn":"2026-03-03"}`: told another way. Only the day is
  kept, never what was said. A later telling never winds the date back.
- Each is a timeline line (`LAWFUL_BASIS_CHANGED`, `PRIVACY_NOTICE_MARKED`) and an audit event by id.
- The drawer reads both through `PersonRecordResponse.privacy`.

## Sending a privacy notice

- `POST /api/v1/candidates/{id}/privacy-notice` for one person, `POST /api/v1/candidates/bulk/privacy-notice` for up to
  500. A single send refuses with the reason; a bulk send skips and names who it could not reach.
- **Skipped:** no email on file, do not contact, processing restricted, suppressed, sent a notice in the last 14 days,
  or the workspace's daily cap reached (`lightmove.data-protection.privacy-notices-per-day`, 500).
- **The email** goes through Uncava's own sender (Resend), shown as "{firm} via Uncava" from `EMAIL_FROM`, with
  `reply_to` the firm's privacy contact. It names the firm as controller, where the details came from
  (`PrivacyNoticeWording`, by the person's first door), the basis, the firm's notice link, and a button to the person's
  page. It is sent after the transaction commits.
- **The record:** a row in `app_lm_person_privacy_link` (the link's token stored as SHA-256 only, valid 30 days, who sent
  it), `privacy_notified_at` set with `PRIVACY_EMAIL`, a `PRIVACY_NOTICE_SENT` line and a `PERSON_PRIVACY_NOTICE_SENT`
  audit event. The address it went to is never recorded beside it.

## The public page

`/privacy/:token`, read with no session. The token's hash is resolved to its workspace by
`app_lm_resolve_person_privacy_link` (V189's idiom), and everything else is read under that workspace's fence.

- `GET /api/v1/privacy/links/{token}`: the firm, its contact and notice link, the basis, the first door, when the person
  was first held, and the **kinds** of data held (`name_and_role`, `career`, `work_email`, `notes`, …). Never a value,
  and never the person's name: a link can be forwarded.
- `POST …/objection`: do not contact ("Objected through their privacy page") and the suppression list as `OBJECTED`,
  in one transaction. A `PRIVACY_OBJECTED` line, a `PERSON_OBJECTED` audit event with no actor, and a bell and an email
  to every admin. Asking twice changes nothing.
- `POST …/requests` `{"kind":"copy"|"deletion"}`: a `PRIVACY_REQUEST_RECEIVED` line and a bell and email to every admin
  (`COPY_REQUESTED`, `DELETION_REQUESTED`). Nothing is exported or deleted from here: an admin answers through the
  subject access export (#919) or the erasure (#918). The same request within a day tells nobody again.
- Rate-limited per IP and token (`privacy-page-opens-per-minute` 30, `privacy-page-requests-per-hour` 10). An unknown
  token is `NOT_FOUND`; an expired one still names the firm to write to, and refuses requests with
  `PRIVACY_LINK_EXPIRED` (410).

The admins' notifications share one Settings → Notifications switch (`privacy_request`), offered to admins alone, and
are emailed on their own rather than waiting for the digest: each has a month's deadline.

## Not notified

The Candidates quick view `not_notified`: held more than 30 days and never told. It is a list to act on, not a rule:
nothing sends a notice on its own.

## Data protection of the feature itself

| Table | Retention | Subject access export |
|---|---|---|
| `app_lm_workspace_candidate_privacy` | while the workspace exists | not a person's |
| `app_lm_person_privacy_link` | with the person (cascade on erasure) | `privacyNotices`, without the token hash |

The person's new columns are exported with the rest of their record.

## Left open ⚖️

- Whether pure mapping (filed, never contacted) claims Art. 14(5)(b), or a notice must go automatically within 30 days
  (`governance-decisions.md` B2). Nothing sends one automatically today.
- Consent per purpose and share-with-client (#1267 phases 2 and 3).
- A dedicated sending subdomain for notices, so bulk cold notices never weigh on invitations' and resets' reputation.
  Today they go from `EMAIL_FROM`; the move is an ops change to Resend and `EMAIL_FROM` alone.
- A consent basis with no consent recorded only reads as such; nothing is blocked on it.

## Testing locally

1. `npm run dev` with `DATA_PROTECTION_ENABLED=true`. Mail goes to the API console.
2. As an admin, fill in Settings → Data & retention → Candidate privacy.
3. File someone with an email on a position, open them under Candidates → Contact → Privacy, and Send privacy notice.
4. Copy the `/privacy/…` link from the console's email and open it in a private window: object, or ask for a copy.
5. The admin's bell shows the request; the person is marked do not contact after an objection.
