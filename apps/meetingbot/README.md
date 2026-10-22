# Uncava Notetaker bot

The headless browser the API sends into a consultant's call (epic #949). One process, one call: it asks to join as
"Uncava Notetaker", waits to be let in, says in the chat that it is recording, then records the call's audio and the
platform's own captions in 30-second pieces and uploads each one as it is made. It leaves when the call ends,
everyone else has gone for three minutes, a consultant presses Remove notetaker, the cap is reached, or — when it
cannot count who is left — half an hour past the calendar's end.

Three things it will not do, each a failed run rather than a degraded one:

- **Record unannounced.** The chat notice is posted, with retries, before it reports `RECORDING`; if it never lands
  the bot leaves and fails the run `CONSENT_NOT_SHOWN`.
- **Join with a device on.** Chromium gets no fake microphone or camera and Meet no media permission; the bot joins
  "without microphone and camera", turns off whatever Meet still shows on, and fails `DEVICES_NOT_OFF` rather than
  ask to join while either is.
- **Leave Meet.** A join link off `https://meet.google.com` is refused before the browser starts, and the page may
  only navigate to Meet and Google's sign-in pages; the cloud metadata hosts are blocked outright.

Pieces overlap by a second (the next one starts before the last stops), so a word at a boundary is in one of them
whole. Every API call is retried with backoff before the run gives up on it.

It holds no credential but its run's token (`NOTETAKER_RUN_TOKEN`), and it only ever calls
`/api/v1/internal/notetaker/runs/{id}/*`: `status`, `heartbeat` every five seconds, `media/{audio|captions}/{n}` for
each piece, and `complete`. The server names every storage key.

| Path | What |
|---|---|
| `src/runner.ts` | The run's state machine — join, lobby, record, leave — over an injected page and API |
| `src/platforms/meetScreen.ts` | Reading Meet's page; self-contained functions sent to the page as source text |
| `src/platforms/meetPage.ts` | Puppeteer and Chromium: the flags, the user agent, the page's actions |
| `src/platforms/meetLink.ts` | Which links the bot opens and where its page may go |
| `src/capture/audioRecorderScript.ts` | Installed before Meet's code: mixes the remote audio, records WebM pieces |
| `src/capture/captionTracker.ts` | Turns repeated readings of the captions into lines |
| `src/pieces.ts` | Uploads pieces in order, retrying one that failed |

```bash
npm run test --workspace=apps/meetingbot                                          # jsdom, no browser
CHROME_PATH=/path/to/chrome npm run test:browser --workspace=apps/meetingbot     # the recorder in a real Chromium
CHROME_PATH=/path/to/chrome npm run try --workspace=apps/meetingbot -- <meet link> # a real call, no Uncava behind it
```

`npm run try` stands up a stub of the run's API on this machine and saves every piece under `try-output/`; somebody in
the call admits "Uncava Notetaker (try)", and Ctrl+C makes the bot leave as if removed. It is how a change to the
screen readers is checked against a live Meet before it ships — their fixtures are hand-trimmed, not saved from a call.

Under `npm run dev` the API starts the bot as a local process with `NOTETAKER_ENABLED=true NOTETAKER_RUNNER=local`
(build it first with `npm run build --workspace=apps/meetingbot`; its output lands in `.data/notetaker/<run>.log`).
Deployed, `deploy.yml` builds `Dockerfile` and deploys it as the Cloud Run Job `lightmove-meetingbot` when
`MEETINGBOT_ENABLED` is on, and the API runs one execution per call.

Teams is #954; until then a Teams run reports a failed launch and exits.
