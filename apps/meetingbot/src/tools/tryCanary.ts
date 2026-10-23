import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { runCanaryOn } from "../canary.js";
import { transcribePiece } from "../canary/hearing.js";
import { audibleSecondsOf } from "../canary/loudness.js";
import { CANARY_CLIP_PATH } from "../canary/phrase.js";
import { joinAsSpeaker } from "../canary/speaker.js";
import type { BotConfig } from "../config.js";
import { openCall } from "../platforms/openCall.js";
import { isTeamsLink } from "../platforms/teamsLink.js";
import { runBot } from "../runner.js";

/**
 * Tries the canary on a real test meeting from this machine: the speaker and the bot join, the pieces are saved under
 * `try-output/` and their loudness printed, and nothing is reported. The phrase is checked only when
 * `NOTETAKER_API_BASE_URL` and `CANARY_ID_TOKEN` (the canary account's ID token for that API) are set.
 *
 *   CHROME_PATH=/path/to/chrome npm run try:canary --workspace=apps/meetingbot -- https://meet.google.com/abc-defg-hij
 */
const joinUrl = process.argv[2];
if (!joinUrl) {
  console.error("Usage: npm run try:canary -- <meet or teams link>");
  process.exit(2);
}
const executablePath = process.env.CHROME_PATH ?? "/usr/bin/chromium";
const apiBaseUrl = (process.env.NOTETAKER_API_BASE_URL ?? "").trim().replace(/\/+$/, "");
const idToken = process.env.CANARY_ID_TOKEN?.trim();
const recordMs = Number(process.env.CANARY_RECORD_SECONDS ?? "60") * 1_000;
const output = join(process.cwd(), "try-output", `canary-${new Date().toISOString().replace(/[:.]/g, "-")}`);
mkdirSync(output, { recursive: true });

const platform: BotConfig["platform"] = isTeamsLink(joinUrl) ? "MICROSOFT_TEAMS" : "GOOGLE_MEET";
const log = (message: string, severity = "INFO") => console.log(`${severity} ${message}`);
const config: BotConfig = {
  runId: "try-canary", runToken: "try", tokenAudience: null, joinUrl, platform, botName: "Uncava Notetaker - try",
  apiBaseUrl: "http://127.0.0.1", lobbyTimeoutMs: 5 * 60_000, maxDurationMs: recordMs + 5 * 60_000, endsAtMs: null,
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
  audibleSecondsOf: async (pieces) => {
    pieces.forEach((piece, index) =>
      writeFileSync(join(output, `audio-${String(index + 1).padStart(5, "0")}.webm`), piece));
    return audibleSecondsOf(executablePath, pieces);
  },
  transcribe: async (piece) => {
    if (!apiBaseUrl || !idToken) {
      throw new Error("no API to ask: set NOTETAKER_API_BASE_URL and CANARY_ID_TOKEN to check the phrase");
    }
    return transcribePiece(apiBaseUrl, idToken, piece);
  },
  now: () => Date.now(),
  log,
});
console.log(`finding: ${JSON.stringify(finding)}; pieces in ${output}`);
process.exit(0);
