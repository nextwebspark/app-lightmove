import { existsSync } from "node:fs";
import { reportFinding, runCanaryOn } from "./canary.js";
import { transcribePiece } from "./canary/hearing.js";
import { audibleSecondsOf } from "./canary/loudness.js";
import { CANARY_CLIP_PATH } from "./canary/phrase.js";
import { joinAsSpeaker } from "./canary/speaker.js";
import type { BotConfig } from "./config.js";
import { runLogger } from "./log.js";
import { openCall } from "./platforms/openCall.js";
import { runBot } from "./runner.js";
import { identityToken } from "./runToken.js";

/**
 * The daily canary (#956, #978), one Cloud Run Job execution a day: for each standing test meeting it is given, a
 * speaker joins saying the canary phrase, the real bot joins, says what it is, records for a minute and leaves, and
 * the API is told whether the bot's pieces heard the phrase. A failure there is a Meet or Teams change caught before a
 * customer's call meets it.
 */
const environment = process.env;
const apiBaseUrl = (environment.NOTETAKER_API_BASE_URL ?? "").trim().replace(/\/+$/, "");
const audience = environment.NOTETAKER_TOKEN_AUDIENCE?.trim() || apiBaseUrl;
const recordSeconds = Number(environment.CANARY_RECORD_SECONDS ?? "60");
const executablePath = environment.CHROME_PATH ?? "/usr/bin/chromium";
const meetings: { platform: BotConfig["platform"]; joinUrl: string | undefined }[] = [
  { platform: "GOOGLE_MEET", joinUrl: environment.CANARY_MEET_URL?.trim() },
  { platform: "MICROSOFT_TEAMS", joinUrl: environment.CANARY_TEAMS_URL?.trim() },
];

if (!apiBaseUrl) {
  throw new Error("NOTETAKER_API_BASE_URL is not set");
}
// Without its clip the speaker says nothing, and every platform would fail NO_SPEECH and be routed around.
if (!existsSync(CANARY_CLIP_PATH)) {
  throw new Error(`The canary's clip is missing at ${CANARY_CLIP_PATH}`);
}
// A number that is not one would leave the bot in the call until the job's own timeout, reporting nothing.
if (!Number.isFinite(recordSeconds) || recordSeconds <= 0) {
  throw new Error(`CANARY_RECORD_SECONDS must be a positive number of seconds, not ${environment.CANARY_RECORD_SECONDS}`);
}
const recordMs = recordSeconds * 1_000;
let unreported = 0;
for (const { platform, joinUrl } of meetings) {
  if (!joinUrl) {
    continue;
  }
  const log = runLogger(`canary-${platform}`);
  const config: BotConfig = {
    runId: `canary-${platform}`, runToken: null, tokenAudience: audience, joinUrl, platform,
    botName: environment.NOTETAKER_BOT_NAME?.trim() || "Uncava Notetaker", apiBaseUrl,
    lobbyTimeoutMs: 5 * 60_000, maxDurationMs: recordMs + 5 * 60_000, endsAtMs: null,
  };
  const finding = await runCanaryOn(recordMs, {
    joinSpeaker: () => joinAsSpeaker(platform, joinUrl, CANARY_CLIP_PATH, executablePath),
    runBot: (api) => runBot(config, {
      api,
      openPage: (onAudioPiece) => openCall(platform, joinUrl, onAudioPiece, executablePath),
      now: () => Date.now(),
      sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
      log,
    }),
    audibleSecondsOf: (pieces) => audibleSecondsOf(executablePath, pieces),
    transcribe: async (piece) => transcribePiece(apiBaseUrl, await identityToken(audience), piece),
    now: () => Date.now(),
    log,
  });
  log(finding.healthy ? "canary passed" : `canary FAILED: ${finding.reason}`, finding.healthy ? "INFO" : "ERROR");
  try {
    await reportFinding(apiBaseUrl, await identityToken(audience), platform, finding);
  } catch (error) {
    unreported++;
    log(`the finding could not be reported: ${error}`, "ERROR");
  }
}
process.exit(unreported > 0 ? 1 : 0);
