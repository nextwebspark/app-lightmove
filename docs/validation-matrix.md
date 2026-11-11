# Validation matrix

What every form checks, where it checks it, and what the user sees when a value is refused. This is QA's checklist
for issue #858: every row is a case to try. Update the rows a change touches in the same PR.

**Columns:** *Client* is what the screen stops before sending. *Server* is the request DTO or the service; it is the
rule that counts. *Shown as* is where a server refusal lands: **field** (under the input), **banner** (the form's
`FormError`), **toast**, or **rail** (the brief's save status).

## How every refusal reaches the user

| Case | API answer | SPA |
|---|---|---|
| Bean Validation on a body (`@Size`, `@NotBlank`, `@Email`, …) | 400 `VALIDATION_FAILED`, `fieldErrors: {path: message}` | `fieldErrorsFrom` puts it under its input; anywhere else `messageFor` reads "Field label: message" |
| A field the request does not take | 400 `VALIDATION_FAILED`, `fieldErrors: {path: "This request does not take that field"}` | as above. `lightmove.web.refuse-unknown-request-fields: false` is the kill switch |
| A value of the wrong shape (unknown enum token, bad date, text for a number) | 400, `fieldErrors: {path: "Not a valid value"}` | as above |
| Malformed JSON | 400 "Request body could not be read", no field | banner or toast |
| An enum token a service parses (`ApiValueEnum`) | 400 "That is not a valid *label*" | the sentence itself |
| A value too long for its column, or a number out of range | 400 `VALIDATION_FAILED`, no field named (the driver reports a column, not a request path); logged at warn, since validation missed it | generic sentence |
| A CHECK the database refuses | unchanged: 409 `CONFLICT` — the server writes values no caller chose, so a broken CHECK is ours | generic sentence |
| JSON body over `lightmove.web.max-json-body-size` (2 MB) | 413 `REQUEST_TOO_LARGE` | "That request is too large" |
| A multipart part missing | 400 `VALIDATION_FAILED` | generic sentence |
| Autosave refused | a 4xx is **not** resent; a 5xx, 408, 429 or network failure is resent after 2 s, 5 s, 15 s | toast once, rail reads error with Retry |

Every number typed into a `FigureInput` stops at 15 digits — past that a JavaScript number no longer holds what was
typed. Chips typed into a `TagListInput` stop at the server's item length where the screen passes `maxItemLength`.

## Screens

Rows marked ☐ are open in #858; a PR that closes one ticks it and states the rule.

### Auth and onboarding
| Field | Client | Server | Shown as |
|---|---|---|---|
| Signup full name | 1–160 | `@NotBlank @Size(160)`, trimmed | field |
| Signup email | trimmed, email | normalised, `@Email @Size(255)`, work domain, MX | field |
| Signup password | 8+, a digit, ≤ 72 bytes | same; bytes in `PasswordPolicy` | field |
| Signup terms | — | `@AssertTrue` | banner (was swallowed) |
| ☐ Invite accept full name | 1–160, not trimmed | `@NotBlank @Size(160)` | banner |
| ☐ Invite team rows | email per row, an address listed once, no cap | `List<@Valid InviteRequest>`, at most 50 a request; no role is MEMBER; a repeat is one invitation; every row checked before any is sent; seats (an invoiced workspace, `SEAT_LIMIT_REACHED`) before the hourly budget, so a batch refused for seats spends none; per hour, 100 addresses a workspace and 5 sends an address across workspaces (`RATE_LIMITED`) | row (client), banner, row unnamed (server) |
| ☐ Invitation resend | — | the outstanding invitation only (`NOT_FOUND` once accepted or withdrawn, and the row re-reads); a lapsed one takes a seat again (`SEAT_LIMIT_REACHED`); the hourly budget; a refused email `INVITATION_EMAIL_NOT_SENT` | toast |
| ☐ Roster role change | one staff role, from the picker | staff roles only, `@NotEmpty`, no null entry (`VALIDATION_FAILED`); CLIENT refused; a CLIENT role the member already holds is kept; the last admin kept (`LAST_ADMIN`) | toast |
| ☐ Organisation name | trimmed 1–160, no `maxLength` | `@NotBlank @Size(160)` | banner |
| Sign-in code (two-factor step) | six digits, spaces dropped; a recovery code instead is 16 Base32 characters, hyphens, spaces and case forgiven | `@NotBlank @Size(32)`; six digits within one 30-second step of now and after the last one used, or an unused recovery code; every refusal `MFA_CODE_INVALID`, counted towards the lock | banner |

### Settings
| Field | Client | Server | Shown as |
|---|---|---|---|
| ☐ Default region / currency | select | unchecked | toast |
| ☐ Persona chips | ≤ 20 items, no item length | ≤ 20 × 96/160/64 | toast |
| Profile timezone | a region's zone or UTC, from the grouped list; the saved value passes unchanged (kept in the list if the browser lists none) | `@NotBlank @Size(64)`; a new choice must be a region's own zone or `UTC` the JDK knows, never a legacy alias like `EST` (`VALIDATION_FAILED` on `timezone`) — a zone saved before V148 saves unchanged | field |
| Profile phone | optional; trimmed, ≤ 40; digits, spaces and `+ ( ) - .` only, a plus only first, six digits at least | `@Size(40)`; left out is unchanged, blank clears it; the same character rule (`VALIDATION_FAILED` on `phone`) | field |
| Profile LinkedIn | optional; trimmed, ≤ 255; a `linkedin.com/in/…` address, scheme optional | `@Size(255)`; left out is unchanged, blank clears it; a member profile on a LinkedIn host (`VALIDATION_FAILED` on `linkedinUrl`), stored as `https://www.linkedin.com/in/<slug>` | field |
| Profile photo | any picture the browser opens, framed square in the crop dialog and drawn as a ≤ 512px JPEG; one it cannot open is said in the dialog | ≤ the configured cap, 5 MB by default (`lightmove.avatars.max-upload-bytes`, named in the message), decoded as JPEG or PNG and re-encoded, anything else refused (`VALIDATION_FAILED` on `file`); 20 changes an hour per person, 300 per address | dialog banner |
| Email signature name | ≤ 60; blank is named for its layout | `@NotBlank @Size(60)` | field |
| Email signature fields | ≤ 160 name and company, 120 title, 40 phone, 255 LinkedIn and website, 200 address; blank follows the profile or workspace | the same `@Size`s; a website must be an http(s) address with a dotted host (`VALIDATION_FAILED` on `fields.website`), a LinkedIn a profile or company page (`fields.linkedinUrl`), a phone the profile's rule (`fields.phone`) | field |
| Email signature default | "Use as my default" is fixed on while a signature is the default | exactly one while any exist: the first is it, `isDefault: false` on the default is ignored, deleting it hands the mark to the oldest left | — |
| Email signature body and disclaimer | ≤ 2,000 (Custom only; counted under the editor and Save held past it) / ≤ 1,000 | `@Size(2000)` / `@Size(1000)`; a body is kept only for Custom | field |
| Email signatures kept | New signature and Duplicate off at 10 | at most 10 per person per workspace (`EMAIL_SIGNATURE_LIMIT_REACHED`) | toast |
| Email template name | Save held while blank; ≤ 80 by `maxLength` | `@NotBlank @Size(80)` | field |
| Email template subject | optional; ≤ 200 by `maxLength`; a line break typed or pasted becomes a space | `@Size(200)`; one line (`VALIDATION_FAILED` on `subject`); blank stored as none | field |
| Email template body | Save held while blank, past 10,000, or while it holds a variable below | `@NotBlank @Size(10000)` | field |
| Email template variables | the line under the subject or the email names the first refused variable and Save waits | only the sequence's six and `bookingLink` (where booking pages are offered); `{{opener}}`, `{{signature}}`, an unknown variable and a fallback on `bookingLink` are `VALIDATION_FAILED` on `subject` or `body` | field |
| Email templates kept | New template and Duplicate off at 100 of one's own | at most 100 per person per workspace, by create or duplicate (`EMAIL_TEMPLATE_LIMIT_REACHED`) | toast |
| Email template edit | a colleague's shared one offers Duplicate alone | owner or `WORKSPACE_MANAGE` only (`EMAIL_TEMPLATE_NOT_YOURS`); another's personal one is missing (`EMAIL_TEMPLATE_NOT_FOUND`); a stale `version` is `EMAIL_TEMPLATE_STALE` | toast |
| Candidate tag label | trimmed, ≤ 40 | `@NotBlank @Size(40)` | toast |
| API key name | trimmed, ≤ 80 | `@NotBlank @Size(80)` | banner |
| Suppression list entry: LinkedIn profile, email (Settings → Data & retention) | at least one (Add to list disabled until then); the link names a member profile (`linkedin.com/in/…`, scheme optional), ≤ 500; email ≤ 320 | at least one (`VALIDATION_FAILED` on `linkedinUrl`); the link must name a member profile, a missing `https://` forgiven (`VALIDATION_FAILED` on `linkedinUrl`); email normalised, `@Email @Size(320)`; `@Size(500)` on the link; refused with no suppression keyset (`SUPPRESSION_UNAVAILABLE`) | field (banner for the keyset) |
| Suppression list entry: reason, note | reason Added by an admin (default) or Objected; note ≤ 500, counted | reason `manual` (default) or `objected`, never `erased` (`VALIDATION_FAILED` on `reason`); note trimmed, `@Size(500)` | field |
| Candidate retention period (Settings → Data & retention) | one of 12, 24, 36, 60 months or Never delete, from a select | one of 12, 24, 36, 60, or null for never (`VALIDATION_FAILED` on `months`); the column holds 12–120 by CHECK | toast |
| Due for deletion: page, people to keep or delete (Settings → Data & retention) | the list draws 200 at a time; Delete sends the ticked people 25 to a request | `limit` 1–500, `offset` ≥ 0 (`VALIDATION_FAILED` on `limit` / `offset`); at least one person, ≤ 200 to keep and ≤ 50 to delete (`VALIDATION_FAILED` on `personIds`) | toast |
| Delete workspace: name typed back (Settings → General) | Delete workspace wakes only on the name, case and outer spaces forgiven | `confirmName` equals the name, case-insensitively, trimmed (`WORKSPACE_NAME_MISMATCH`) | banner |
| Delete my account: proof (Settings → Profile) | a password account: Delete my account wakes once the password is typed (and the code, where two-factor is on); an account with none: once the preview says it signed in within 10 minutes, its provider's popup offered until then; the only admin of a workspace or only lead of a live position is shown what to hand over instead | a password account: the password (`VALIDATION_FAILED` on `currentPassword` when blank; `CURRENT_PASSWORD_INVALID` on `currentPassword`), and with two-factor on the app's code or a recovery code (`MFA_CODE_INVALID` on `code`); an account with none: a sign-in within `lightmove.auth.mfa.recent-sign-in` (`ACCOUNT_SIGN_IN_AGAIN`); refused while it is the only admin of a workspace or the only lead of a live position (`ACCOUNT_HANDOVER_REQUIRED`, naming them); `@Size(1024)` password, `@Size(32)` code | field (banner for `ACCOUNT_SIGN_IN_AGAIN`; the hand-over view for `ACCOUNT_HANDOVER_REQUIRED`) |
| Export workspace data (Settings → Data & retention) | Start export disabled while one is building | one build at a time per workspace (`WORKSPACE_EXPORT_RUNNING`); a download only of a ready one before it expires (`WORKSPACE_EXPORT_UNAVAILABLE`) | toast |
| Two-factor: current password | required where the account has a password; not drawn for one that has none | required where the account has one (`VALIDATION_FAILED` on `currentPassword` when blank); must match (`CURRENT_PASSWORD_INVALID` on `currentPassword`); an account with none turns it on only within `lightmove.auth.mfa.recent-sign-in` (10 min) of signing in (`MFA_SIGN_IN_AGAIN`), and off or new codes with the code alone | field (banner for `MFA_SIGN_IN_AGAIN`) |
| Two-factor: code to turn on | six digits, spaces dropped | `@NotBlank @Size(32)`; the app's code for the new secret (`MFA_CODE_INVALID` on `code`) | field |
| Two-factor: code to turn off or get new recovery codes | six digits or a recovery code | `@NotBlank @Size(32)`; the app's code or an unused recovery code (`MFA_CODE_INVALID` on `code`) | field |
| Audit log dates (Settings → Security) | the end picker starts at the start date and the start picker ends at the end date | `from` before `to`, `to` exclusive (`VALIDATION_FAILED`); an unknown category or outcome is `VALIDATION_FAILED` | inline line in place of the table |
| Audit log export | — | past `lightmove.export.max-audit-events` (10,000) the whole export is refused (`VALIDATION_FAILED`), never cut short | toast |
| Task preset name | trimmed, ≤ 80, one line | `@NotBlank @Size(80)`, no line break | toast |
| Pipeline automation status | live statuses in process only; the switch turns on only with one chosen | `@NotNull enabled`; on needs a `statusId` (`VALIDATION_FAILED` on `statusId`); the status the workspace's (404), in process (`VALIDATION_FAILED`) and live (`PIPELINE_STATUS_RETIRED`) when switched on, so a paused rule still switches off | toast |
| A status's ⋯ → Mark out of process | disabled on its stage's default; asks first when anyone holds it or an automation points at it | never on a stage's default, nor a flagged status made default (`PIPELINE_DEFAULT_IN_PROCESS`) | toast |
| Retiring a stage or status | asks first when anyone holds it or an automation points at it | a stage with live statuses needs `retireStatuses` (`PIPELINE_STAGE_NOT_EMPTY`); never the last live stage (`PIPELINE_STAGE_REQUIRED`), nor the last live status in process (`PIPELINE_STATUS_REQUIRED`) | toast |
| Working hours (Meetings & email, Availability) | each day on or off, start and end from the half-hour picker (end may be 23:59), end after start, at least one day | each day once, on the half hour or ending 23:59, end after start, ≥ 1 day (`VALIDATION_FAILED` on `weeklyHours`) | inline + toast |
| Daily email limit | whole number, 1 to the deployment's ceiling (workspace) or the workspace's (a consultant's) | 1..`max-daily-cap` for the workspace, 1..the workspace's for a consultant (`VALIDATION_FAILED` on `dailyEmailCap`) | inline + toast |
| Own app client ID (Settings → Integrations) | 1–255, `[A-Za-z0-9._-]`; Google ends in `.apps.googleusercontent.com`, Microsoft a GUID | the same (`@Size(255) @Pattern`, then `WorkspaceIntegrationService`) | field |
| Own app tenant (Microsoft) | required; a GUID or a domain with a dot — never `common`, `organizations` or `consumers` | the same | field |
| Own app secret | required in the set-up form and in Replace (the screen never sends a blank one); ≤ 2048; never trimmed, but any space, tab, line break, control or invisible format character (a zero-width space, a BOM) refused; for Microsoft a GUID (the Secret ID) refused | blank keeps the one held for the same client ID (an API caller's path; the screen no longer takes it); the rest the same, after `@Size(2048)` | field |
| Own app secret expiry | Microsoft only, and required with every new secret; from the browser's today to 24 months out | a date sent for Google or Zoom refused; Microsoft's optional, held to UTC today..+24 months with a day's slack either side (the browser's day may differ), unless the same app keeps its secret and its date | field |
| Own app keys, Save and verify / Verify again | — (the provider decides) | the provider's token endpoint is asked first, outside any transaction: a refusal is `INTEGRATION_KEYS_REFUSED` with the provider's code as `providerError`, no answer (or an Entra refusal not about the keys) `INTEGRATION_PROVIDER_UNREACHABLE`, past ten checks a minute per workspace on an instance `RATE_LIMITED`; none of them writes anything. Verify again keeps the answer either way | banner ("{Provider} refused these keys ({code}). Nothing was saved." / "… The saved secret is unchanged.") / toast |
| ☐ Invitation role | always sent | not required (MEMBER when absent) | — |

### Projects and clients
| Field | Client | Server | Shown as |
|---|---|---|---|
| Position title | trimmed, ≤ 160 | `@NotBlank @Size(160)` | field |
| New position: business unit / client | required; a new one ≤ 160; a database company already on the books is offered as that record, by company even when renamed | `clientId` or `newClient`, exactly one (`VALIDATION_FAILED` on `clientId`); a new one is made in the position's own write, so a refused position keeps none; a name or universe company already held is `CLIENT_ALREADY_EXISTS` with `clientId`, which the modal files under, an archived holder `CLIENT_NAME_ARCHIVED` / `CLIENT_COMPANY_ARCHIVED`; `newClient.customName` `@Size(160)` | field (length, none chosen), banner (archived) |
| New client: company from the database | a company already on the books — by company even when renamed, or by name — wears the CLIENT badge and is refused | the same holders as above: `CLIENT_ALREADY_EXISTS` (`clientId`), `CLIENT_NAME_ARCHIVED`, `CLIENT_COMPANY_ARCHIVED` | toast |
| Archive business unit / client: name typed back | as the position's; off while any of its positions is live, which it lists | `@NotBlank @Size(200)`; `ConfirmationName`; `CLIENT_HAS_LIVE_POSITIONS` (409, `positionIds`) | banner |
| Archive position: title typed back | Archive stays off until it matches — case, spacing and dashes forgiven (`confirmationNameMatches`) | `@NotBlank @Size(200)`; `ConfirmationName`, same rule → `ARCHIVE_NAME_MISMATCH` (400) | banner |
| Delivery / mapping target date | ordered | `ProjectTimeline` | field |
| Start / delivery / mapping / target date | ordered | 2000–2100 (`@PlausibleDate`) | field |
| ☐ Client contact position | required | optional | banner |
| ☐ Client contact invite, resend, cancel | name, title and work email | work email; the hourly invitation budget (`RATE_LIMITED`), whichever path it takes; resend and cancel only while the contact is invited (`NOT_FOUND`, and the row re-reads); a refused resend email `INVITATION_EMAIL_NOT_SENT` | banner / toast |
| ☐ Client name, sector, country, domain | no `maxLength`, no domain check | 160/96/64/160 | toast |
| ☐ Off-limits note | — | unbounded | toast |
| Business unit / client notes | the note editor's Markdown, ≤ 4000 held under the box (both drawers' Save waits on it); sent only when changed, so stored plain notes stay plain | `@Size(4000)`; `notesFormat` an `ApiValueEnum`, plain when absent | toast |

### Position brief
Limits mirrored in `features/position/lib/briefLimits.ts`. A blank role title, benefit, criterion or competency name
is held back rather than sent (the step does not save until it is named) and the field says so.

| Field | Client | Server | Shown as |
|---|---|---|---|
| Role title | ≤ 160; blank held, "Nothing on this step saves until it has one" | `@NotBlank @Size(160)` | field |
| Department, city, ideal profile | ≤ 160 / 120 / 4000 | same | toast + rail |
| Responsibilities | ≤ 20 × 200, no duplicates | ≤ 20 × 200 | toast + rail |
| Org chart seats | ≤ 60, no cycles; title / name ≤ 160 | 1–60, `OrgChartRules`, 160 | sentence |
| Notice period | five options | 0–999 | toast + rail |
| Target start | date picker | 2000–2100 (`@PlausibleDate`) | toast |
| Salary, incentive | ≤ 15 digits | ≥ 0 | toast + rail |
| Bonus | % or months: two decimals, ≤ 100 % / 24 months; fixed: ≤ 15 digits; a basis change clears a figure the new basis cannot read (money ↔ share, or past its ceiling) | same, by `BonusBasis.admits` (a template's too) | toast + rail |
| Currency | picker | ISO 4217 (`@CurrencyCode`) | toast + rail |
| Benefits | ≤ 20 × 120, blank held | ≤ 20 × 120, `@NotBlank` | toast + rail |
| Competency name / description | ≤ 120 / 300, blank name held | same | toast + rail |
| Competency weight, technical share | 0–100 | 0–100, DB CHECK | — |
| Criteria | ≤ 30 × 300, no duplicates, blank held | ≤ 30 × 300 | toast + rail |
| Document | `accept`; ≤ 10 MB before upload | ≤ 10 MB; name **and bytes** must agree on PDF / DOC / DOCX / TXT — the declared type decides nothing | sentence |

### Strategy
| Field | Client | Server | Shown as |
|---|---|---|---|
| ☐ Range min / max | none | `@AssertTrue` min ≤ max | toast + autosave |
| Saved search name | trimmed, ≤ 120 | `@NotBlank @Size(120)`, unique | sentence |
| People name (the toolbar's search box, not the rail) | ≤ 120 | `@Size(120)` | toast + autosave |
| ☐ People chips | ≤ 50 items, no item length | ≤ 50 × 160/500 | toast |
| ☐ People: file selected | everyone loaded | 1–25 | toast |

### Companies, executives, candidates
| Field | Client | Server | Shown as |
|---|---|---|---|
| Company name, industry, country, city | ≤ 200/200/100/100 | same | banner |
| ☐ Custom field values | no length | TEXT unbounded, DATE unchecked, NUMBER accepts NaN | toast |
| ☐ Custom column label | no `maxLength` | ≤ 60 | toast |
| Executive name | trimmed, ≤ 200 | `@NotBlank @Size(200)` | banner |
| Restrict processing: reason | Restrict wakes only with a non-blank reason; 500 characters, counted | `@Size(500)`; required when restricting (`VALIDATION_FAILED` on `reason`); a restricted person is refused at every use (`PERSON_PROCESSING_RESTRICTED`) | banner; the refusal as a toast where it is met |
| Delete person permanently: full name typed back | the button wakes only on a match (case, spacing and dashes forgiven, `confirmationNameMatches`) | `@NotBlank @Size(200)`; `ConfirmationName.matches` the person's name (`VALIDATION_FAILED` on `confirmName`); refused while an email to them is sending (`PERSON_ERASE_SEND_IN_FLIGHT`) | banner (an alert for the email) |
| Executive LinkedIn profile and emails, on every door | — | not on the workspace's suppression list (`PERSON_SUPPRESSED`): the add form, the plugin and a spreadsheet row are refused, and so is mapping someone the workspace already knows whose own profile or address is listed; an edit only for a profile or address it adds; Find executives, People Search and the Candidates page's Add to position leave the person out unfiled; a workspace holding entries the keyset cannot read is refused at every door (`SUPPRESSION_UNAVAILABLE`) | banner (row error on import; toast on Add to position) |
| ☐ Executive currency | picker | `@Size(3)` | banner |
| ☐ Languages | one string ≤ 1200 | ≤ 20 × 60 | banner |
| ☐ Phone | none | a digit | banner |
| Contact primary (per channel) | one star per channel; moving it clears the others | at most one `primary` per channel (`VALIDATION_FAILED` "Only one email can be primary"); none listed leaves the first of the ledger's order standing in | toast |
| ☐ Candidates search `q`, `page` | none | unbounded `q`; `page × size` overflows | — |
| ☐ Companies search box `search` (triage list, its people read, talent map, export; the Pipeline's `q`) | trimmed | trimmed, case-insensitive substring with `%` and `_` literal (`SearchText`), unbounded; notes and tags match only for `WORK_EXECUTE` | — |
| Next action title | trimmed, ≤ 200, one line, required | `@NotBlank @Size(200)`, no line break (it becomes an email subject) | banner |
| Next action detail | trimmed, ≤ 500 | `@Size(500)` | banner |
| Note body | the note editor's Markdown, trimmed, mentions written as tokens, ≤ 10000 stored (held under the box) | `@NotBlank @Size(10000)`; V142's CHECK | toast |
| Note format | `markdown` from the editor | `ApiValueEnum`, plain when absent (a door's note is always plain): 400 "That is not a valid note format" | toast |
| Note kind | none on the Notes tabs (plain); Call, LinkedIn, WhatsApp or Meeting in Log call / Log LinkedIn / Log WhatsApp / Log meeting; the extension's chips leave out Email | `ApiValueEnum`, plain when absent: 400 "That is not a valid note kind"; `email` on a new note, or onto a note that was not one, 400 on `kind` | toast |
| Log's when (call, LinkedIn, WhatsApp, meeting) | date and time, now unless changed; never past today in the picker, and a time later than now flagged under When with Save held | `occurredAt` no later than now + 5 min, never on a plain note: 400 on `occurredAt` / `kind` | toast |
| Log's title | optional, one line, ≤ 200 (line breaks become spaces) | `@Size(200)`, no line break: 400 on `title` | toast |
| Log meeting's "Which meeting" | the person's Uncava meetings already begun — calendar meetings and calls known only from their recording — newest first, or Outside Uncava; a logged call (a phone call) is asked none | `meetingId` or `recordingId`, never both, on a meeting note only, this person's, already begun; a recording only on a note about its own position: 400 on `meetingId` | toast |
| Note scope, a position's drawer | This position (default) or All positions, sent as `allPositions` | `allPositions: true` files it about no position; `projectId` not read; neither read on an edit; both together 400 | toast |
| Note position, Candidates page | "About" select of the person's positions | `projectId` needs that position's `WORK_EXECUTE` (403) and a mapping there (404); with `allPositions: true` 400 | toast |
| Another position's note, from a position | not listed | 404 on edit, pin, delete, restore and a task's `noteId` | toast |
| Company note body | the note editor's Markdown, ≤ 10000 stored (held under the box) | `@NotBlank @Size(10000)`; V143's CHECK; kind and format as a person's | toast |
| Company note, change / delete / restore | Edit, Delete (Undo) offered to its author or an admin | else `COMPANY_NOTE_NOT_YOURS` (403); a client seat 403 on every notes route | toast |
| Delete / restore a note | Delete at once, Undo on the toast | author or `WORKSPACE_MANAGE`, else `PERSON_NOTE_NOT_YOURS` (403); restore of a purged note 404; a live note answers as it is | toast |
| Assessment score | stepper 0–10 or unscored | `@Min(0) @Max(10)`, null allowed | toast |
| Assessment evidence | `maxLength` 2000 | `@Size(2000)` | toast |
| Assessment report section | `maxLength` 4000 | `@Size(4000)` each | toast |
| Assessment save | sends the version it opened | `CANDIDATE_ASSESSMENT_STALE` (409) on any other | banner, Reload, keeping mine |
| Assess with AI | disabled while changes are unsaved or nothing is left open | `CANDIDATE_ASSESSMENT_NO_CRITERIA`, `CANDIDATE_ASSESSMENT_NOTHING_OPEN` (409); `AI_UNAVAILABLE` (502); a blocked prompt `VALIDATION_FAILED` | toast |
| Ranking technical share | slider 0–100, steps of 5 (the brief's own share kept exactly) | `@Min(0) @Max(100)` | — |
| Ranking competency weight | slider 0–100 per competency; a changed group must total 100% | `@Min(0) @Max(100)` each; a changed group off 100 is `VALIDATION_FAILED` on `weights` | inline under the group, Save held |
| Ranking scenario rationale | `maxLength` 1000 | `@Size(1000)`, blank kept as none | toast |
| Ranking scenario save | sends the version it opened and every competency of the brief | `RANKING_SCENARIO_STALE` (409) on another version; `RANKING_BRIEF_CHANGED` (409) when the competencies differ | banner, Reload, keeping mine / reload and re-seed |

### Import, export, outreach, assistant
| Field | Client | Server | Shown as |
|---|---|---|---|
| Import file | — | ≤ 10 MB, ≤ 5000 rows, type by bytes | sentence |
| ☐ Import cells | — | bypass the drawer's rules; dropped silently | row list (leaks internal text) |
| Export cells | — | formula starters defused | — |
| ☐ Import template headers | — | not defused | — |
| Sequence steps | ≤ 3 | 1–3, delays, window ordering | field |
| ☐ Sequence subject | — | CR/LF accepted | fails at send |
| Own email: to | one of the person's addresses, offered as a list (one is shown as text); the composer locks with none on file or do not contact | ≤ 320; on the person's record (`OUTREACH_ADDRESS_NOT_ON_FILE`), not do not contact (`OUTREACH_DO_NOT_CONTACT`) | sentence |
| Own email: subject | a first email's Send now and Schedule stay disabled until it has one; a reply shows its thread's, read-only | required for a first email, ≤ 200, one line; a reply keeps its thread's; a first email's variables filled on one line (`renderLine`), and refused if that leaves it empty | field |
| Own email: body | Send now and Schedule stay disabled while it is empty or past 10,000 characters, which says so under the editor | 1–10,000, Markdown; raw HTML sent as text, links http/https/mailto | field |
| Own email: send at | a day and a half hour in the sender's profile zone; the picker's button refuses no day, a time already past and one more than 60 days ahead | ahead of now, ≤ 60 days; none is now, refused at the day's cap (`OUTREACH_DAILY_CAP_REACHED`) | field / sentence |
| Own email: attachments | Attach offers their documents and an upload (the picker filters to the accepted types); the chips say past the count, a file's size or the total, and Send now and Schedule stay disabled until it is fixed | ≤ `max-files` (5, `OUTREACH_TOO_MANY_ATTACHMENTS`), each ≤ `max-file-bytes` (3,000,000, `FILE_TOO_LARGE`), together ≤ `max-total-bytes` (10 MB, `OUTREACH_ATTACHMENTS_TOO_LARGE`); an upload's bytes match its name (`UNSUPPORTED_FILE_TYPE`); a document version on this person (404) | sentence |
| Own email: variables | the line under the editor names each variable no email fills and each with nothing on file and no fallback ("Fill in or remove 1 variable to send: {{location}} has nothing on file"), from the conversation read's `composer.tokens`, and Send now and Schedule wait | the six and `bookingLink` only, in a first email's subject and the body; an unknown one, `{{opener}}`, or one with no value and no fallback is `OUTREACH_TOKENS_UNFILLED` (409, `tokens` naming them), after which the composer reads the conversation again | sentence |
| Own email: template | Templates applies one: into an empty editor it is the body, into words it goes in at the cursor; a first email takes its subject only while its own is empty, a reply never | `templateId` recorded only where the sender may see that template in this workspace, otherwise null — never a refusal | — |
| Own email: reply in thread | offered only where the conversation read names a reply subject (`composer.replySubject`) | a thread this mailbox wrote to them in and can still write into (`OUTREACH_THREAD_UNAVAILABLE`) | sentence |
| Book a call: time | dragged on the week or typed into Pick a time; the footer says why Send invite is off (off the five-minute mark, under 5 min or over 8 hr, past, beyond the last day, over a busy time) and notes a time outside working hours without refusing it | a five-minute mark, 5–480 minutes, ≥ 5 minutes ahead, ≤ 6 months (`MEETING_SLOT_INVALID`); clear of the calendar (`MEETING_SLOT_TAKEN`) | footer sentence / toast |
| Booking link: time | the page's own half hours | on the half hour, inside the consultant's working hours, ≥ 1 hour ahead, ≤ 60 days (`MEETING_SLOT_INVALID`); clear of the calendar (`MEETING_SLOT_TAKEN`) | sentence |
| Notetaker: Send now / Join at start (the call's Notetaker & questions panel) | Send now on a call not in `callDayMeetingIds` (its day not begun in the caller's zone) asks first; Join at start only while the start is more than 2 minutes away | the call still open (`NOTETAKER_MEETING_OVER`); Send now: no earlier call or other bot on the link (`NOTETAKER_LINK_IN_USE`), a free at-once bot (`NOTETAKER_BUSY`) | toast |
| Notetaker: Start now instead (Join now) | only on a bot scheduled ahead, its `join_at` more than 2 minutes away, on the call's day | the same, plus a bot still waiting (`NOTETAKER_NOT_CHANGEABLE`) | toast |
| Notetaker: End & file notes | only while recording, asked first | recording (`NOTETAKER_NOT_RECORDING`); Recall took it out (`NOTETAKER_END_FAILED`); a Remove after it is `NOTETAKER_NOT_CHANGEABLE` | toast |
| Suggest changes: notice period (edited) | the five options as a select | one of None, 1, 2, 3 or 6 months | under the change's row |
| Suggest changes: location (edited) | city input (`maxLength` 120) and the country box | country the catalog resolves; city ≤ 120 | under the change's row |
| Suggest changes: motivation, expected package, task (edited) | text box (`maxLength` 1,000; a task one line, 200) | not blank; ≤ 1,000 (a task ≤ 200, one line); the Motivation section ≤ 4,000 after the call's words are added | under the change's row; nothing is saved |
| Suggest changes: package | one tick per figure; unticking every figure unticks the change | at least one figure; a known three-letter currency; figures ≥ 0; not over a figure held in another currency (`NOTETAKER_SUGGESTION_UNAVAILABLE`) | under the change's row / toast |
| Interview plan: a question | text box in place, `maxLength` 140, one row | not blank; ≤ 400 (a plan saved before the one-line rule still saves); ≤ 40 questions in a plan; a drafted question is cut to 120 at a word; Save stays off while one is blank | under the field / toast |
| ☐ Assistant question | no `maxLength` | ≤ 4000 | nothing shown |
