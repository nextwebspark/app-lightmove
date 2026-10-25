# Uncava Notetaker

A consultant sends a [Recall.ai](https://www.recall.ai) bot into a call with an executive. After the call, the
transcript becomes one **Meeting** note on the person, summarised against the position's brief. The transcript itself
can be read from the meeting's row.

Recall is the only recorder for now. A bot of our own can come back later behind the same seam (`RecallBotApi`).

## How a call is recorded

1. **Record with Notetaker.** The button sits on a meeting in the executive drawer's Meetings section. It is
   offered when:
   - the call is still to come, under way, or ran over its booked end by no more than 20 minutes (`OVERRUN_GRACE`), so
     a consultant who forgot can send it mid-call — it then joins at once. Past the booked end it is offered only while
     no later call on the consultant's calendar has started on the same link, since a personal room used back to back
     would otherwise put the next interview on this person;
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
   - the bot name "UNCAVA Notetaker", spelled as the wordmark;
   - Uncava's card as its camera (`automatic_video_output`): the mark, the name and a Recording / Not recording yet
     pill, two 1280×720 JPEGs in `apps/api/src/main/resources/notetaker`, drawn by `ops/notetaker-bot-card/render.cjs`
     from `bot-card.svg`;
   - a chat line on join, to everyone, naming the bot as the consultant's AI notetaker, saying the call is recorded and
     transcribed for their notes (and followed live by an AI assistant when the copilot is on), that the recording stays
     with their team, and that anyone who would rather not be recorded need only say so;
   - Recall's own transcription (`recallai_streaming`, accuracy mode, separate streams per participant where the
     platform gives them);
   - leave rules tighter than Recall's defaults, since every minute in a call is billed (`automatic_leave`): fifteen
     minutes unadmitted in a waiting room or with nobody joined, ten in the call without leave to record, ten of
     silence or two with only other bots left once the call is ten minutes old, and thirty seconds after everyone else
     has gone;
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
8. **Suggest changes.** A recording whose notes are filed and whose transcript is still kept offers **Suggest changes**
   on its row. The press opens "Changes from the call" and reads the transcript then — never on its own — with one
   Gemini call (`CallChangeSuggester`, `prompts/call-changes-*`, Spring AI's structured output behind the usual guard
   and schema repair). The model gets the redacted transcript and what the record holds now for the notice period,
   location, relocation tag and motivation — **never the package on file**, which is compared in code, since the model
   sees no compensation figure (the `CandidateDossier` rule) — and proposes only what the executive said:
   - **notice period**, folded onto one of the five only where it is an exact equivalent (`NoticePeriod.ofPair`);
   - **current package** — base, bonus and allowances in one ISO currency, monthly figures annualised with the working
     shown, a bonus in months read off the base; no currency stated means no suggestion, and one that would relabel a
     figure held in another currency is not offered;
   - **location** (a country the catalog resolves), **Open to relocate** (the workspace's live tag labelled
     "Open to relocate", only when they said they would move — found by its label, since tags carry no stable key: a
     workspace that renames or retires the starter tag is offered no relocation change, and one that names another
     tag that gets it), **motivation** and **expected package** (added below the Assessment's
     Motivation section under "From the call on …", never over it), and the consultant's own **next steps** as tasks.

   Anything equal to what is on file is dropped. The suggestions are kept on the recording (V152 `suggestions`,
   staff-only, cleared with the transcript), so reopening costs nothing; each has a random id, and asking again once
   every one is decided adds the new ones beside the decided ones; a press is one `AI_ENRICH` use, recorded only
   when something was found. A claim (`suggesting_since`) keeps two presses to one model call.

   The review is **one list and one save**, the convention for suggested record updates from a call: every change on
   its row — what the record holds now, what the call suggests, the words it rests on and their time into the call —
   ticked to keep unless the record no longer lets it in, editable in place, a package ticked figure by figure.
   **Apply N changes** (`POST …/suggestions/review`) applies what is ticked as it reads and skips the rest; Not now
   writes nothing and leaves them waiting. The review is one transaction on the recording row locked first, so a
   second tab finds nothing waiting (`NOTETAKER_SUGGESTION_DECIDED`); every choice is checked before anything is
   written, and the Motivation section — the one write that can still refuse, when it is too full — goes first, so a
   refused change writes nothing at all and is named on its own row (`fieldErrors` keyed by the suggestion's id).
   Every apply writes through the field's own owner (`CandidateCallUpdates`: `Person.recordFromCall` for the notice
   period, package and location together, the scorecard for the Motivation section, the tag catalogue; `TaskService`
   for each task) with one `UPDATED_FROM_CALL` Activity line per write naming the researcher, the fields and the call
   — never the values — and a `NOTETAKER_SUGGESTION_APPLIED` / `_SKIPPED` audit event per suggestion beside the
   owner's own. **There is no Undo**: once reviewed a change is the record's, and a correction is made there. The
   row then reads "N changes applied" (or "No changes made") as plain text, and Activity holds the history. A model
   that fails or answers off-schema is `AI_UNAVAILABLE` (Try again), a transcript the guard stops is
   `VALIDATION_FAILED`, and neither stores or charges anything.

9. **Prepare questions.** Every upcoming call on the drawer's Meetings section offers **Prepare questions**, which
   opens the Interview plan and, the first time, drafts it with one Gemini call (`InterviewPlanner`,
   `prompts/interview-plan-*`, the same structured-output path behind the guard and schema repair). The model gets the
   brief's `AssessmentBrief` with each competency as an id, the `CandidateDossier`, the notes and documents Assess with
   AI reads (`ExecutiveReading`, redacted), which competencies the scorecard already evidences (a score of 6 or more
   with evidence written), and **which** facts the record is missing — notice, package, location, relocation, motivation,
   expected package — never what a held one says and never a package figure. Code keeps only what it can place: a
   competency id the brief has (at most two questions each), a gap that was listed (one each), at most two clarifiers.
   The plan is kept per call (V154 `app_lm_interview_plan`, matched on the call's start and title as every teammate's
   copy of it is), edited, ticked and saved through `PUT …/meetings/{meetingId}/plan`; Regenerate redrafts the model's
   questions and keeps the consultant's own. A press is one `AI_ENRICH` use and its own `LlmBudget.INTERVIEW_PLAN` meter;
   a claim (`drafting_since`) keeps two presses to one call, and a failed or empty draft stores and charges nothing.
10. **Live copilot.** Under the Notetaker's switch sits **Live copilot** — on a meeting's row and in Book a call's Recording
    card, where switching it on switches the Notetaker on too and switching the Notetaker off takes it with it — chosen before the bot joins, because Recall
    streams a transcript only to a bot made for it: sent with `realtime_endpoints` (`transcript.data`,
    `transcript.partial_data`) pointing at `/api/v1/notetaker/webhooks/recall/live`, in `recallai_streaming`'s
    `prioritize_low_latency` mode, which transcribes English only — a bot without the copilot keeps the accurate mode.
    While the bot is still scheduled the switch changes it at Recall (`PATCH /api/v1/bot/{id}/`, its consent line with
    it); once it is joining it is `NOTETAKER_COPILOT_LOCKED`. Only the bot's sender switches it
    (`NOTETAKER_COPILOT_SWITCH_NOT_YOURS`; the row offers the switch to them alone, `mayChangeCopilot`), since it
    reconfigures their bot and spends their AI use. Switching it on checks fair use first, so a refusal is the billing
    sheet before the call, never during it. A copilot bot's consent line adds that an AI assistant follows the call live.
    - **The record stays accurate.** The low-latency stream is never the call's record: once the call is over
      (`bot.done`, or the stream's own `transcript.done`) `FinalTranscriptRequester` asks Recall for an accurate
      transcript of the recording (`POST /api/v1/recording/{id}/create_transcript/`, `recallai_async`, language
      detected), one request at a time by claim (`final_transcript_requested_at`), and keeps its id
      (`final_transcript_id`). Only that transcript's `transcript.done` — found by its id, since such a delivery may not
      name the bot — files the note, so the note, Suggest changes and Assess with AI read the same quality, in any
      language, as a call without the copilot. `NotetakerReconcile` asks again for one never asked, and reads back one
      never announced. Recall bills the second transcription, for copilot calls only.
    - **The feed.** Each bot is given its own URL, `…/live?t=<token>`, the token's SHA-256 kept on the recording
      (`live_token_hash`, new on every switch): a delivery is taken with that token, which feeds its own recording and no
      other, or with a Svix signature under the bot secret. Anything else is `NOTETAKER_WEBHOOK_REJECTED`. Words so far
      are announced to the open windows at most every half second. `NotetakerLiveFeed` keeps a line only for a bot
      we hold, in its call, with the copilot listening: a finished utterance once per Recall participant and first-word
      offset (a retry is the same line), a speaker's words so far replaced in place (V154
      `app_lm_meeting_live_line`, staff-only). The lines go when the call ends, fails or is cancelled, and when the
      copilot is stopped, so only the final transcript outlives the call.
    - **The coach.** `InterviewCoachWorker` runs `InterviewCoach` (`prompts/interview-coach-*`) once enough new talk is
      in — four lines, or twenty seconds of speech, or a line left unread for thirty — never sooner than
      `copilot-coach-interval` (15 s) after the last run, up to `copilot-max-coach-runs` (240) a call; a sweep on the
      same interval catches a call that went quiet. One run at a time per call by claim (`coaching_since`). It sends the
      brief, the plan's ticked questions, the missing facts, what it found so far and the transcript (redacted, the
      team's voices marked "(your team)" in code), and folds the answer forward only: a competency goes from not yet to
      touched to evidenced and never back, a gap heard stays heard, a question asked stays asked, at most three nudges
      show, and one the consultant used or dismissed is never suggested again — what they did while a run was reading
      wins over that run. A failed run keeps what the window shows and reads the same lines again; three in a row pause
      it until more is said. A stretch the guard stops is skipped. Past the cap the reading freezes. A live call is one
      `AI_ENRICH` use, counted once its first reading is on file: a call no run answered costs nothing. A question ticked
      as asked must be one of the call's plan.
    - **The window.** **Open copilot ↗** on the row opens it in a Document Picture-in-Picture window in Chrome and Edge —
      always on top, dragged over Zoom, Meet or Teams, rendered from the drawer's page so it needs no sign-in of its own
      — or a popup of `/copilot/:projectId/:candidateId/:recordingId` elsewhere (not on top; it says so), or the drawer's
      **Copilot tab** when the browser opens neither. That tab comes first in the executive drawer, set apart by a rule,
      only while a call's copilot is joining or listening (`liveCopilotOf`), and stays while it is open after the copilot
      stops; it is never opened unasked and never remembered. It draws the same panel at the drawer's width, headed
      like the window but by the copilot rather than the executive the drawer already shows (the call, Pop out, ⋯, then
      Live · Plan · Transcript), counts the suggestions waiting while
      another tab is open, and while the copilot is in its window says so with Show here rather than drawing a copy. It reads `GET …/notetaker/{recordingId}/copilot?after=` (the lines after the last
      one it holds) whenever `…/copilot/stream` says something changed (Postgres `lm_copilot_stream`), and every 15 s
      regardless; a hidden tab keeps its stream, since the consultant is looking at the call. The window's header is the
      executive — their stored photo, name, the call and its clock — then ⋯ (Mute suggestions, Show in Uncava — the
      window closes and the drawer opens on the Copilot tab —, Stop copilot; closed by a click elsewhere, Escape or the
      window losing focus) and ✕, which closes only that window. Each transcript line carries its speaker's face (the
      executive's photo, a teammate's sign-in picture), and the foot who is watching (each stream stamps
      `app_lm_meeting_copilot_viewer`). The view and Mute are one per call across the tab, the window and the popup
      (`lib/copilotView.ts`, a `BroadcastChannel` for the popup), so moving between them loses neither. **Stop copilot** (`POST …/copilot/stop`) is the
      switcher's, the sender's, a lead's or an admin's (`NOTETAKER_COPILOT_NOT_YOURS`): it stops the feed and the coach
      for everyone, deletes the lines, and leaves the bot recording. Every switch, stop and first opening is audited.

## What leaves Uncava

- **To Recall:** the call's join link, and the call's audio and video while the bot is in it. Recall's copy is
  deleted once the note is filed.
- **To Recall, with the live copilot:** the bot streams the transcript back to our own endpoint as it is spoken, and the
  recording is transcribed a second time after the call in the accurate mode, for the note.
- **To Gemini, for the interview copilot:** for Prepare questions, the brief's assessment seam, the profile, the notes and
  documents (redacted) and which record facts are missing; during a live call, every few seconds, the brief, the plan,
  which facts are missing and the redacted transcript so far. Never a package figure or a contact.
- **To Gemini:** the transcript with contacts redacted, and the brief's assessment seam — once to write the note, once
  per **Suggest changes** press (with the record's notice period, location, relocation tag and motivation — no package —
  never a contact), and again on each **Assess with AI** press on that position while the note is live (`MeetingTranscriptLookup`, read by
  `CandidateMaterialReader`, newest call first, 150k characters a call with its start and end kept, 250k in all). A
  speaker whose name is a staff member's or the scheduler's is marked "(your team)" in code. A recording whose summary
  failed has no note yet, so its transcript is not read until **Try again** files one. A transcript the prompt guard
  stops is dropped and the assessment asked again without transcripts, so a remark in a call never blocks it.
- **Kept:** the note, and the transcript and its suggestions (staff-only, on the recording row) — by default for as
  long as the recording row, which goes only with its person or position. A deployment that sets
  `NOTETAKER_TRANSCRIPT_RETENTION` (`lightmove.recall.transcript-retention`, e.g. `90d`) has `NotetakerTranscriptPurge`
  clear a transcript (with its suggestions and the live copilot's reading) nothing needs any more — its note purged for good, or a recording that never filed one — once it
  has sat untouched that long. Assess with AI still reads a transcript only while its note is live.

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
