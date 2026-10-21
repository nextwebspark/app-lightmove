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
| ☐ Invite team rows | email per row, no cap | `List<@Valid InviteRequest>`, no cap | banner, row unnamed |
| ☐ Organisation name | trimmed 1–160, no `maxLength` | `@NotBlank @Size(160)` | banner |

### Settings
| Field | Client | Server | Shown as |
|---|---|---|---|
| ☐ Default region / currency | select | unchecked | toast |
| ☐ Persona chips | ≤ 20 items, no item length | ≤ 20 × 96/160/64 | toast |
| Candidate tag label | trimmed, ≤ 40 | `@NotBlank @Size(40)` | toast |
| API key name | trimmed, ≤ 80 | `@NotBlank @Size(80)` | banner |
| Task preset name | trimmed, ≤ 80, one line | `@NotBlank @Size(80)`, no line break | toast |
| Pipeline automation status | live statuses of active stages only; the switch turns on only with one chosen | `@NotNull enabled`; on needs a `statusId` (`VALIDATION_FAILED` on `statusId`); the status the workspace's (404), of an active stage (`VALIDATION_FAILED`), live when switched on (`PIPELINE_STATUS_RETIRED`) | toast |
| Retiring a stage or status | asks first when anyone holds it or an automation points at it | a stage with live statuses needs `retireStatuses` (`PIPELINE_STAGE_NOT_EMPTY`); never the closed or the last active stage (`PIPELINE_STAGE_REQUIRED`), nor the last live active status (`PIPELINE_STATUS_REQUIRED`) | toast |
| ☐ Own app secret / expiry | ≤ 2048, any date | `@Size(2048)`, any date | field |
| ☐ Invitation role | always sent | not required (500 when absent) | — |

### Projects and clients
| Field | Client | Server | Shown as |
|---|---|---|---|
| Position title | trimmed, ≤ 160 | `@NotBlank @Size(160)` | field |
| Archive business unit / client: name typed back | as the position's; off while any of its positions is live, which it lists | `@NotBlank @Size(200)`; `ConfirmationName`; `CLIENT_HAS_LIVE_POSITIONS` (409, `positionIds`) | banner |
| Archive position: title typed back | Archive stays off until it matches — case, spacing and dashes forgiven (`confirmationNameMatches`) | `@NotBlank @Size(200)`; `ConfirmationName`, same rule → `ARCHIVE_NAME_MISMATCH` (400) | banner |
| Delivery / mapping target date | ordered | `ProjectTimeline` | field |
| Start / delivery / mapping / target date | ordered | 2000–2100 (`@PlausibleDate`) | field |
| ☐ Client contact position | required | optional | banner |
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
| Bonus | % or months: two decimals, ≤ 100 % / 24 months; fixed: ≤ 15 digits | same, by `BonusBasis.admits` | toast + rail |
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
| ☐ People chips | ≤ 50 items, no item length | ≤ 50 × 160/500 | toast |
| ☐ People: file selected | everyone loaded | 1–25 | toast |

### Companies, executives, candidates
| Field | Client | Server | Shown as |
|---|---|---|---|
| Company name, industry, country, city | ≤ 200/200/100/100 | same | banner |
| ☐ Custom field values | no length | TEXT unbounded, DATE unchecked, NUMBER accepts NaN | toast |
| ☐ Custom column label | no `maxLength` | ≤ 60 | toast |
| Executive name | trimmed, ≤ 200 | `@NotBlank @Size(200)` | banner |
| ☐ Executive currency | picker | `@Size(3)` | banner |
| ☐ Languages | one string ≤ 1200 | ≤ 20 × 60 | banner |
| ☐ Phone | none | a digit | banner |
| ☐ Candidates search `q`, `page` | none | unbounded `q`; `page × size` overflows | — |
| Next action title | trimmed, ≤ 200, one line, required | `@NotBlank @Size(200)`, no line break (it becomes an email subject) | banner |
| Next action detail | trimmed, ≤ 500 | `@Size(500)` | banner |
| Note body | the note editor's Markdown, trimmed, mentions written as tokens, ≤ 10000 stored (held under the box) | `@NotBlank @Size(10000)`; V142's CHECK | toast |
| Note format | `markdown` from the editor | `ApiValueEnum`, plain when absent (a door's note is always plain): 400 "That is not a valid note format" | toast |
| Note kind | four chips | `ApiValueEnum`: 400 "That is not a valid note kind" | toast |
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
| Own email: subject | a first email's Send now and Schedule stay disabled until it has one; a reply shows its thread's, read-only | required for a first email, ≤ 200, one line; a reply keeps its thread's | field |
| Own email: body | Send now and Schedule stay disabled while it is empty or past 10,000 characters, which says so under the editor | 1–10,000, Markdown; raw HTML sent as text, links http/https/mailto | field |
| Own email: send at | a day and a half hour in the mailbox's zone; the picker's button refuses no day, a time already past and one more than 60 days ahead | ahead of now, ≤ 60 days; none is now, refused at the day's cap (`OUTREACH_DAILY_CAP_REACHED`) | field / sentence |
| Own email: reply in thread | offered only where the conversation read names a reply subject (`composer.replySubject`) | a thread this mailbox wrote to them in and can still write into (`OUTREACH_THREAD_UNAVAILABLE`) | sentence |
| ☐ Assistant question | no `maxLength` | ≤ 4000 | nothing shown |
