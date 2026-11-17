# Recently approached

Two researchers approaching the same executive, for two positions or by accident for one, reads as unprofessional
to the executive and to the client. Before an email, a sequence or an add to a position, Uncava says who on the team
has already reached the person and where else they are in process. Going ahead asks first: a dialog lists what is
known and a box must be ticked ("I have read this and want to go ahead with …") before the email goes or the person
is included. Nothing is blocked once it is ticked. The hard stops are unchanged: do not contact, a restriction of
processing, the suppression list, an email opt-out (#924), and already in a sequence on this position.

Mockups: `Outreach.dc.html?approached=true` (composer, Add to sequence),
`Candidates.dc.html?dialog=addToPosition`, `Position.dc.html` (Fatima's drawer), `Extension.dc.html?screen=person`,
and `Settings.dc.html` → Meetings & email.

## What counts as an approach

| Kind | Source |
|---|---|
| `EMAILED` | `app_lm_outreach_message`: outbound, sent or scheduled, any position |
| `REPLIED` | `app_lm_outreach_message`: an inbound reply, credited to the mailbox it reached |
| `IN_SEQUENCE` | a live (`SCHEDULED`/`ACTIVE`) enrollment on another position. Ongoing, so it ignores the window |
| `CALL_LOGGED`, `LINKEDIN_LOGGED`, `WHATSAPP_LOGGED`, `MEETING_LOGGED`, `EMAIL_LOGGED` | a live logged note of that kind (`app_lm_person_note`), dated by `occurred_at`, else when it was written |
| `WHATSAPP_REPLIED` | a WhatsApp log whose outcome is They replied: their answer, so it ranks with `REPLIED` (`ApproachKind.isReply`) on its own line |

A logged call or WhatsApp message carries its outcome (`outcome` on the touch), and the line says it: "Sara called
(left voicemail)", "Sara sent a WhatsApp message", "Replied to Sara on WhatsApp".

These touches are left out:
- a call logged as **No answer** or **Wrong number** (#1337): it never reached the person, so it warns nobody, in the
  window or before it (`RecentApproachReader.UNANSWERED`, filtered in both note queries so an unanswered call never
  hides an older one that connected). Last call still names it;
- the caller's own touches on the position they work from, or logged on no position, which are already in their
  conversation. Their own touches on another position are kept ("You emailed …") and ask like a teammate's: people
  forget;
- a live sequence on this position, which is `ALREADY_IN_SEQUENCE`'s refusal.

A touch on an archived position still counts: the person was contacted, whatever became of the position. Only its
mapping goes quiet (below).

Each teammate, kind and position is one line, its newest, so a sequence's step emails never fill the ten lines and
hide the rest of the team. A logged note is credited to whoever logged it. Nothing reads or returns a subject, body or
note text.

Where else the person is mapped comes from `CandidateRepository.findPositionsOfPeople`:
- In process (any status that has not left the running, a retired one included) on a position that is not archived
  is amber. V163's seeded Placed (`CandidateStatusDefinition.isPlacement`, by its key, so a rename keeps it) reads
  "Placed on …". A status a workspace adds in its stead does not.
- Out of process shows as a quiet line, the newest three.
- **A position the caller does not work on as staff (`WORK_EXECUTE`) stays unnamed, on the server.** A client seat
  there is not enough: it reads the position as a client does, without the team's outreach or the people hidden from
  clients. A touch there carries `positionHidden` and no project id or title; a mapping there carries `visible: false`,
  its stage label and whether they are in process, and no id, title, status, placed or who added them. The screen says
  "another position". A visible one carries its title, status and a link. The teammate's name is always given, so the
  two can talk. The rule is `PositionRedaction` / `PositionVisibility.isHidden`.

## Tiers and window

- **`CAUTION`** (amber, `u-signal`): a touch inside the window, or in process elsewhere.
- **`INFO`** (a quiet `text3` line): only an approach older than the window (`lastApproach`), or out of process
  elsewhere.
- **`NONE`**: nothing to say.

The window is `app_lm_workspace.recently_approached_days` (V222). It defaults to 90, runs from 0 to 365, and 0 turns
the warning off. It is set in Settings → Meetings & email (`PUT /api/v1/workspace/recently-approached`,
`WORKSPACE_MANAGE`, audited `WORKSPACE_UPDATED` / `recentlyApproached`).

## Where it is built

- `candidate/service/RecentApproachReader` is the one read. It takes a batch of people, the caller and the current
  position. Outreach's half arrives through the `PersonApproachHistory` seam, which `outreach/service/
  OutreachApproachHistory` implements, so `candidate` never depends on `outreach`. The reads are bounded by the
  window. Only for someone with nothing inside it does a second read fetch, per teammate and position, the newest
  touch before it, for `lastApproach`. Add to sequence asks only about the people who can be added. A skipped row
  carries no `recentApproach`.
- `RecentApproachResponse` is staff-only. It rides `ConversationComposerResponse.recentApproach` and
  `EnrollmentCandidateResponse.recentApproach`, and never `CandidateResponse`.
- SPA: `RecentApproachNote` is one line in `ReplyComposer`'s footer. Send now or Schedule at `CAUTION` opens
  `RecentApproachConfirm`, and Send anyway or Schedule anyway stays disabled until the box is ticked. It asks once for
  what it showed (`approachKeyOf`: kind, teammate and position, never a time). A poll answering the same, or a
  scheduled email going out, stays quiet. A new teammate, kind or position asks again. Send anyway is disabled while
  the email cannot go. In Add
  to sequence, a row at `CAUTION` starts unticked. Ticking it opens the same dialog (Include). The dialog keeps
  choices, not flips of the live tier: someone a refetch turns `CAUTION` is unticked until confirmed, and one left out
  or confirmed stays so. A flagged row keeps its email address beside the note. Past Choose, only the people reviewed
  there go on. Start's summary names the one person, or
  counts those included and left out (`EnrolDialog`). The dialog's links open a new tab, so a draft is never lost.
  Wording is in `features/candidates/lib/recentApproach.ts`.
- The tick is not sent to the server. The email itself already lands on the timeline with its sender.

## Next (PR 2: adding to a position)

These pieces are not built yet:
- The drawer's Positions (`PersonRecordService.positionsOf`) still names every position to every staff member. It
  should follow the same rule, through `PositionRedaction`.
- `outOfProcess` on `PersonPositionResponse`, for the pool's Add to position line.
- The executive drawer's header line.
- The extension's held pre-check, which gains where else the person is in process.
- The bulk filing toasts' count.
