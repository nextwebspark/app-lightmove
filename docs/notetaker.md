# Uncava Notetaker

A consultant sends a [Recall.ai](https://www.recall.ai) bot into a call with an executive. After the call, the
transcript becomes one **Meeting** note on the person, summarised against the position's brief. The transcript itself
can be read from the meeting's row.

Recall is the only recorder for now. A bot of our own can come back later behind the same seam (`RecallBotApi`).

## How a call is recorded

1. **Record with Notetaker.** The button sits on an upcoming meeting in the executive drawer's Meetings section. It is
   offered when:
   - the meeting has a video link (an https join URL);
   - the call is the caller's own: on their calendar, or booked by them. A teammate's private call is never
     recorded by someone else (`NOTETAKER_NOT_YOUR_CALL`), the same line Book a call's move/cancel draws;
   - the person is not marked do not contact;
   - the deployment has a Recall account, and the workspace is within its AI fair use (`AI_ENRICH`).

   The request is `POST /api/v1/projects/{p}/outreach/candidates/{c}/meetings/{m}/notetaker`, gated by `WORK_EXECUTE`.
2. **The claim.** A `SCHEDULED` row of `app_lm_meeting_recording` (V151) is written first. A partial unique index on
   the call (workspace, join URL, start) keeps one live bot per call, whichever teammate presses.
3. **Sending the bot.** Recall is then asked, outside any transaction and never retried, with:
   - `join_at` one minute before the call (at once when the call is that close);
   - the bot name "Uncava Notetaker";
   - a chat line on join that says whose notes it records;
   - Recall's own transcription (`recallai_streaming`, accuracy mode, separate streams per participant where the
     platform gives them);
   - the recording's id as metadata.

   A refusal marks the row `FAILED` (`SEND_FAILED`), and the consultant can press again. A Remove pressed while Recall
   is still being asked calls the new bot straight back once its id arrives.

   A recording belongs to the position it was sent from. Every read, Remove and Try again is scoped to that
   position, so another position mapping the same person never sees its transcript.
4. **Webhook.** Recall reports on `/api/v1/notetaker/webhooks/recall`. The endpoint is public; the Svix signature
   under `lightmove.recall.bot-webhook-secret` is its credential. Each event moves the row's status:

   | Recall says | Status |
   |---|---|
   | `joining_call`, `in_waiting_room`, `in_call_not_recording` | `JOINING` |
   | `in_call_recording` | `RECORDING` |
   | `call_ended`, `done` after recording | `PROCESSING` |
   | `call_ended`, `done` without ever recording | `FAILED`, with Recall's sub-code (e.g. `timeout_exceeded_waiting_room`) |
   | `fatal`, `recording_permission_denied` | `FAILED` |
   | `transcript.done` | `PROCESSING`, and the note is made |
   | `transcript.failed` | `FAILED` (`TRANSCRIPT_FAILED`) |

   A status only moves forward, and an ended recording takes no more news.
5. **The note** (`MeetingNotesWorker`, after commit). It first claims the row (`processing_since`), so a transcript
   handed over twice (a Svix redelivery, or the reconcile) is summarised and filed once. It then runs these steps:
   1. Fetches the transcript. It follows a pre-signed link only to Recall's or AWS's hosts.
   2. Keeps the transcript on the row.
   3. Summarises it with `MeetingSummariser` (`prompts/meeting-summary-*`) against the brief's `AssessmentBrief`. That
      is role, criteria and competencies, never compensation. Every email address and phone number is redacted first.
   4. Files a Markdown `MEETING` note in the scheduler's name, through `PersonNoteService.fileMeetingNote`.
   5. Records one `AI_ENRICH` use against fair use.
   6. Asks Recall to **delete its copy of the call** (`delete_media`).

   No model or vendor call holds a transaction. A failed summary leaves `FAILED` (`SUMMARY_FAILED`) with Recall's
   copy kept, so **Try again** can read the transcript once more. Try again is a press, so it takes the
   `CANDIDATE_AI_ENRICH` meter and the fair-use check.
6. **Safety net.** `NotetakerReconcile` runs every `lightmove.recall.bot-reconcile-interval` (10 min). It claims any
   recording still live 30 minutes after its call ended (`reconciled_at`, `FOR UPDATE SKIP LOCKED`, so one instance
   reads each bot), reads it back from Recall and folds it on as the webhook would have. A recording still live a day
   after its call is `TIMED_OUT`.
7. **Remove notetaker.** Anyone on the position can remove a bot while it is scheduled, joining or recording. Recall
   deletes a bot that has not joined and is asked to make one in the call leave. Either way it is marked `CANCELLED`.

## What leaves Uncava

- **To Recall:** the call's join link, and the call's audio and video while the bot is in it. Recall's copy is
  deleted once the note is filed.
- **To Gemini:** the transcript with contacts redacted, and the brief's assessment seam.
- **Kept:** the note, and the transcript (staff-only, on the recording row) only as long as the note.
  `NotetakerTranscriptPurge` clears it once the note is purged for good (`lightmove.notes.purge-after`, 30 days after a
  delete), or from a recording that never filed a note once it has sat untouched that long.

Nothing about a Notetaker reaches `CandidateResponse`, the report, the public API or MCP.

## Turning it on

The Notetaker is offered where both `RECALL_API_KEY` and `RECALL_BOT_WEBHOOK_SECRET` are set: `deploy.yml`'s
`NOTETAKER_ENABLED`, and `docs/integrations/registration.md` for the Recall side.

On its own it does not move calendars to Recall. That still needs the calendar webhook secret (`RECALL_ENABLED`).

To try it locally:

1. Set `RECALL_API_KEY`, `RECALL_BOT_WEBHOOK_SECRET` and (for EU) `RECALL_BASE_URL` before `npm run dev`.
2. Expose `:8080` (cloudflared or ngrok) and point a Recall webhook endpoint at `/api/v1/notetaker/webhooks/recall`.
3. Book a Meet call in the drawer, press **Record with Notetaker**, then join the call, let the bot in and talk.

## Left for later

- Our own bot.
- Automatic joining and per-workspace switches.
- Fair-use minutes.
- Zoom's on-behalf-of token, which some Zoom accounts require.
- A stream event instead of the drawer's 30-second poll.
