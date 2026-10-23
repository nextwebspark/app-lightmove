# Uncava Notetaker

A bot of our own joins a consultant's call with an executive and hears it. Afterwards the call becomes one Meeting
note on the position, with its transcript beside it. Recall.ai stands behind it, but only in a workspace whose admin
turned that on. This page covers how it fits together, why a run ends the way it does, and what to do when it breaks.
The phases are epic #949's sub-issues #950–#956.

## How a call is recorded

1. **A call to join.** `NotetakerScheduler` runs every minute. A kept meeting (`app_lm_person_meeting`, read through
   `outreach`'s `KeptMeetings`) gets a bot when all of these hold:
   - it starts within `join-ahead` (2 min) or began within `join-late` (10 min);
   - the workspace's, the consultant's and the meeting's switches are all on;
   - nobody on it is marked do not contact;
   - some bot joins its platform;
   - the workspace has notetaker minutes left this billing month (fair use, below).

   `MeetingRecordingClaims` claims the call once across calendars and instances. One call is one
   `app_lm_meeting_recording` row: a run.
2. **Sending the bot.** `ChainedMeetingRecorder` sends ours: a Cloud Run Job execution of `apps/meetingbot`, one per
   call, never retried. Recall's bot is sent instead (below) when the workspace has it on and either our bot cannot
   be started, or the platform is one ours does not join or whose canary failed.
3. **The bot's credential.** The bot starts with its run id and no token. It asks the metadata server for an ID token
   of `MEETINGBOT_SA` minted for the API, and trades it once for the run token at `POST …/runs/{id}/token`. That
   token is the only credential on every later call (`X-Notetaker-Run-Token`). Off Cloud Run (`npm run dev`) the token
   rides the environment.
4. **In the call.** The bot asks to join as a guest, waits up to `lobby-timeout` to be let in, says in the chat what it
   is, then records 30-second audio pieces and the platform's captions. It leaves when any of these happens:
   - the call ends;
   - it has been alone for 3 minutes;
   - someone pressed Remove notetaker (it hears that on its 5-second heartbeat);
   - the cap is reached.

   Pieces upload as they are made.
5. **The note.** `MeetingNotesProcessor` transcribes each piece while the call goes. Once the bot leaves it stitches
   the pieces, names the voices from the captions, summarises against the position's brief and files one `MEETING`
   note. Every step runs in its own transaction, with no model call inside one.

## Recall.ai, the backup (#955)

**Off until a workspace's admin turns it on** (Settings → Integrations → Uncava Notetaker), because a call's audio
then goes to a third party. Where it is on, Recall's bot steps in in four cases:
- **Our bot fails.** `FallbackPolicy` covers our software failing: `LAUNCH_FAILED`, `LAUNCH_TIMEOUT`, `CRASHED`,
  `HEARTBEAT_LOST`. A bot nobody admitted (`NOT_ADMITTED`) or one the host or tenant kept out (`JOIN_BLOCKED`) is
  followed only under `fallback-on-not-admitted` / `fallback-on-join-blocked`, because each is someone's decision.
- **Our bot drops out mid-call.** The same run is handed over: what ours captured stays, and the note says where the
  gap was.
- **A Zoom call.** Recall is the only recorder for Zoom until our bot joins it, on the consultant's own Zoom account.
- **A platform whose latest canary failed** (below).

Recall reports on `/api/v1/notetaker/webhooks/recall`, Svix-signed. Its transcript arrives as one more piece of the
run, and Recall's copy is deleted the moment it is ours.

## Why a run ended

A run that never got a note carries `end_reason`. The drawer's chip says each in words (`lib/notetakerChip.ts`).

| Reason | Means | Usually |
|---|---|---|
| `DO_NOT_CONTACT` | Someone on the call is marked do not contact; skipped | Working as intended |
| `FAIR_USE_REACHED` | The workspace used this month's minutes; skipped | Raise the ceiling or wait for the month |
| `TRIAL_ENDED` | An unpaid trial ended; skipped | The workspace chooses a plan |
| `ZOOM_NOT_CONNECTED` | A Zoom call and the consultant's Zoom is not connected; skipped | They connect Zoom on the Outreach page |
| `LAUNCH_FAILED` | The bot could not be started (Cloud Run refused, Chromium did not open) | Check the job, its image, quotas |
| `LAUNCH_TIMEOUT` | Started, never reported within `launch-timeout` | Execution logs; the token exchange |
| `JOIN_BLOCKED` | The host or tenant lets no guest in | The organiser's settings; not ours |
| `NOT_ADMITTED` | Nobody let it in within `lobby-timeout` | Usually the host's choice |
| `CONSENT_NOT_SHOWN` | Older runs only: it could not post its notice, so it left unrecorded. The bot now records on and logs a WARNING | — |
| `DEVICES_NOT_OFF` | It could not be sure its microphone and camera were off | A page change; check the canary |
| `CRASHED` | The bot fell over (reported, or its process died) | Execution logs |
| `HEARTBEAT_LOST` | It went silent for `heartbeat-timeout` | Execution killed or the network |
| `RECORDING_NOT_ALLOWED` | Recall was let in but the host never let it record | The host's choice (Zoom) |
| `BACKUP_FAILED` | Recall's bot failed too, or was never heard from again | Recall's dashboard |
| `PROCESSING_FAILED` | Recorded, but the transcript or note could not be made in three tries | Try again from the drawer; model logs |

`PLATFORM_UNHEALTHY` is never an end reason. It is the `fallback_reason` of a call that went straight to Recall
because the platform's canary had failed.

## The daily canary (#956)

**What it checks.** Once a day the job `lightmove-meetingbot-canary` (the same image, `dist/canaryMain.js`) runs the
real bot against two standing test meetings, one on Meet and one on Teams. First a second guest, "Uncava Canary
Speaker", joins with its microphone on: a Chromium whose fake microphone loops `apps/meetingbot/assets/canary-phrase.wav`
("Purple giraffes read quiet library books every morning."). Then the bot joins, posts its notice, records a minute
and leaves. Its pieces are decoded and measured in Chromium, and the most audible two are sent to
`POST /api/v1/internal/notetaker/canary/hearing`, which runs them through the notes' own transcriber. The canary
reports to `POST /api/v1/internal/notetaker/canary`. Both calls are signed with the ID token of a service account of its
own (`NOTETAKER_CANARY_SA`). It is never `MEETINGBOT_SA`, because a meeting bot runs on a third party's page and must
not be able to report a platform down, or hide an outage.

The report passes only when the bot got in and its pieces say the phrase; a notice it could not post is a WARNING in
its log, not a failure. Otherwise it gives the failure:

| Reason | Means |
|---|---|
| A bot end reason | The bot itself failed: `JOIN_BLOCKED`, `NOT_ADMITTED`, `DEVICES_NOT_OFF`, … |
| `NOT_RECORDING` | The bot never reached recording |
| `NO_AUDIO` | No audio piece came out at all |
| `SPEAKER_NOT_JOINED` | The canary's own speaker never got in (after two tries), or stopped sending sound before the bot left |
| `NO_SPEECH` | The pieces are silent, or the transcriber heard no words: a capture regression (a muted or wrong track) |
| `WRONG_SPEECH` | The transcriber heard words, but not the phrase |

A transcriber that cannot be reached (a 502 from the hearing route, say) does not fail the canary when the pieces were
audible: the platform is not routed around for our own model's outage, and the bot logs a WARNING. The bot's log names
how many seconds of each piece were audible and never a word it heard.

A Meet or Teams page change that breaks joining, or a capture change that records silence, is caught within a day.

**What a failure does.** It is written to `app_lm_notetaker_platform_health` (V156) and logged at ERROR:
`Notetaker canary FAILED on <platform>`. While it stands, that platform's calls go to Recall in every workspace that
has the backup on. Elsewhere our bot is still sent, since an attempt beats none. The next passing canary clears it.
`lightmove.notetaker.platform-health: false` turns the rerouting off.

**Setting it up**, once per project:

1. **Two test meetings that let a guest in with nobody there.**
   - Meet: a recurring event on a Google Workspace test account with Access type **Open**, so anyone with the link
     joins without knocking, and host management leaving guests able to turn on their microphone.
   - Teams: a recurring meeting on a test tenant with **Who can bypass the lobby: Everyone**, anonymous join allowed,
     meeting chat on for guests, and **Allow mic for attendees** on.

   Two guests join each meeting, the speaker and the bot, so the job runs two Chromiums at once.

   Put their links in the deploy variables `NOTETAKER_CANARY_MEET_URL` and `NOTETAKER_CANARY_TEAMS_URL`, with
   `NOTETAKER_CANARY_ENABLED=true`, `PUBLIC_BASE_URL`, and `NOTETAKER_CANARY_SA`. The canary's service account is made
   like `MEETINGBOT_SA`: no role, just an identity.
2. **The trigger.** Cloud Scheduler runs the job daily, as a service account allowed to run it:
   ```bash
   gcloud scheduler jobs create http notetaker-canary --location "$REGION" --schedule "15 6 * * *" \
     --uri "https://run.googleapis.com/v2/projects/$PROJECT/locations/$REGION/jobs/lightmove-meetingbot-canary:run" \
     --http-method POST --oauth-service-account-email "$SCHEDULER_SA"
   gcloud run jobs add-iam-policy-binding lightmove-meetingbot-canary --region "$REGION" \
     --member "serviceAccount:$SCHEDULER_SA" --role roles/run.invoker
   ```
3. **The alerts.** Two log-based alert policies in Cloud Monitoring:
   - one on any entry matching `textPayload:"Notetaker canary FAILED"` (or `jsonPayload.message` for the bot's own
     line);
   - one on the *absence* of `Notetaker canary passed` for 26 hours, so a canary that stopped running is noticed too.

**When it fires**, follow this runbook:
1. Read the failing reason, in the bot's log for the canary execution (`jsonPayload.runId="canary-<PLATFORM>"`).
2. Reproduce locally with `npm run try:canary -- <test meeting link>` in `apps/meetingbot`: the speaker and the bot
   join, the pieces land in `try-output/` with their audible seconds printed. The phrase is checked too with
   `NOTETAKER_API_BASE_URL` and `CANARY_ID_TOKEN` set, the latter from
   `gcloud auth print-identity-token --impersonate-service-account="$NOTETAKER_CANARY_SA" --audiences="$PUBLIC_BASE_URL" --include-email`.
   `npm run try` is the bot alone, admitted by hand.
3. Fix the page driver (`meetPage.ts`, `teamsPage.ts`, and the screen readers) against a fresh fixture.
4. Deploy, then run the job by hand:

   ```bash
   gcloud run jobs execute lightmove-meetingbot-canary --region "$REGION"
   ```

   A pass clears the platform.

## Watching it

Every number is a Micrometer meter, tagged with fixed vocabularies only, never a run, a person or a call:

| Meter | Tags | Answers |
|---|---|---|
| `notetaker.joins` | `platform`, `recorder` (`OWN`/`RECALL`), `outcome` (`JOINED` or an end reason) | Join success rate per platform: `JOINED` over all |
| `notetaker.time_to_admit` | `platform`, `recorder` | How long bots wait to be let in (a histogram) |
| `notetaker.fallbacks` | `platform`, `reason` | Fallback rate: over `notetaker.joins` |
| `notetaker.canary` | `platform`, `outcome` (`PASSED`/`FAILED`) | The canary's history |

The meters reach a dashboard once `docs/production-observability.md`'s gap 4 (Prometheus scraping) is closed; its
"Notetaker" section has the queries. Until then the same facts are in the logs:
- the API's `Notetaker fallback to the backup recorder on …` and `Notetaker canary …` lines;
- the bot's JSON entries (`component: "meetingbot"`, `runId`, `severity`, `message`).

**No log line carries a word of a call** — no transcript, no caption, no join link, no token. The bot logs states and
errors only (`log.ts`), and the API logs ids.

## Keeping nothing longer than needed

- **Audio** is deleted as soon as its piece is transcribed. Every piece of every run, audio and captions, is deleted
  once it is a day old (`media-max-age`), whatever became of the run. Audio no pass transcribed in that day becomes
  a gap the note owns up to, as a piece three passes could not hear does.
- **Remove notetaker** deletes what was captured on the next pass, and Recall's copy at once.
- **A failed run's transcribed text** is kept `failed-media-retention` (7 days) for Try again.
- **A transcript** lives as long as its note. Once `PersonNotePurge` has deleted the note for good (30 days after it
  was removed), the transcript goes on the next pass.
- **The bucket** has a lifecycle rule deleting `notetaker/` objects older than 2 days, as a backstop
  (`docs/integrations/registration.md`).

## Fair use

Recorded minutes are a fair-use kind per staff seat (`lightmove.billing.fair-use.meeting-minute-per-seat`, 3,000 a
month). Before a bot is sent the scheduler asks `FairUseGuard`. A workspace with any minute left gets its bot, and the
minutes it records count once the note is filed (`MEETING_MINUTES` usage). One past the ceiling, or whose trial has
ended, has the call skipped (`FAIR_USE_REACHED` / `TRIAL_ENDED`). Settings → Billing shows the month's minutes against
the ceiling.

## What the call is told

- **Our bot's chat line, as it is let in** (`runner.ts`'s `consentLine`, the same words for Recall's bot):
  > Uncava Notetaker is recording and transcribing this call to take notes for Uncava. If you'd rather not be
  > recorded, ask the host to remove it.
- **Best-effort, by decision.** Our bot that cannot post that line — a call whose guests cannot chat, such as a
  personal Teams meeting — records anyway and logs a WARNING, `the recording notice could not be posted in the chat`.
- **Recall's bot is best-effort too.** It posts the same line but cannot confirm it was shown, and records either way.
  The Integrations card says so before an admin turns it on.
- **Before the call.** An invite Uncava sends (Book a call) does not mention the notetaker today. Each consultant
  decides per call from the drawer, and anyone on the call may ask the host to remove it.

## The summary's quality

`MeetingSummaryEval` (`@Tag("eval")`) scores the summary against golden calls kept outside the repository: facts
carried, points mentioned, inventions, and competencies placed. It appends counts to
`docs/eval/meeting-summary-eval.md`. See that page for how to run it.
