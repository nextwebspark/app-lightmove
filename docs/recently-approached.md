# Recently approached

Two researchers approaching the same executive, for two positions or by accident for one, reads as unprofessional
to the executive and to the client. Before an email, a sequence or an add to a position, Uncava says who on the team
has already reached the person and where else they are in process. It is a warning. It never holds a send, a
sequence start or an add. The hard stops are unchanged: do not contact, a restriction of processing, the suppression
list, and already in a sequence on this position.

Gap #1 of `docs/spott-gap-analysis.md`. Mockups: `Outreach.dc.html?approached=true` (composer, Add to sequence),
`Candidates.dc.html?dialog=addToPosition`, `Position.dc.html` (Fatima's drawer), `Extension.dc.html?screen=person`,
and `Settings.dc.html` → Meetings & email.

## What counts as an approach

| Kind | Source |
|---|---|
| `EMAILED` | `app_lm_outreach_message`: outbound, sent or scheduled, any position |
| `REPLIED` | `app_lm_outreach_message`: an inbound reply, credited to the mailbox it reached |
| `IN_SEQUENCE` | a live (`SCHEDULED`/`ACTIVE`) enrollment on another position. Ongoing, so it ignores the window |
| `CALL_LOGGED`, `LINKEDIN_LOGGED`, `WHATSAPP_LOGGED`, `MEETING_LOGGED`, `EMAIL_LOGGED` | a live logged note of that kind (`app_lm_person_note`), dated by `occurred_at`, else when it was written |

These touches are left out:
- the caller's own touches on the position they work from, which are already in their conversation;
- a live sequence on this position, which is `ALREADY_IN_SEQUENCE`'s refusal.

A logged note is credited to whoever logged it. Nothing reads or returns a subject, body or note text.

Where else the person is mapped comes from `CandidateRepository.findPositionsOfPeople`:
- In process on a position that is not archived is amber. A `placed` status reads "Placed on …".
- Out of process shows as a quiet line, the newest three.
- A position title shows even without a seat on it, as the drawer's Positions does; `workable` says whether it opens.

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
  OutreachApproachHistory` implements, so `candidate` never depends on `outreach`.
- `RecentApproachResponse` is staff-only. It rides `ConversationComposerResponse.recentApproach` and
  `EnrollmentCandidateResponse.recentApproach`, and never `CandidateResponse`.
- SPA: `RecentApproachNote` (`features/candidates/components`) sits in `ReplyComposer`'s footer. In Add to sequence,
  a row at `CAUTION` starts **unticked but tickable** and names the newest line. Start's summary counts those people
  included and left out (`EnrolDialog`). Wording is in `features/candidates/lib/recentApproach.ts`.

## Next (PR 2: adding to a position)

These pieces are not built yet:
- `outOfProcess` on `PersonPositionResponse`, for the pool's Add to position line.
- The executive drawer's header line.
- The extension's held pre-check, which gains where else the person is in process.
- The bulk filing toasts' count.
