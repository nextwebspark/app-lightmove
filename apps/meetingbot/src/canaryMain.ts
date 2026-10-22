import { canaryApi, reportFinding, type CanaryFinding } from "./canary.js";
import type { BotConfig } from "./config.js";
import { runLogger } from "./log.js";
import { openCall } from "./platforms/openCall.js";
import { runBot } from "./runner.js";
import { identityToken } from "./runToken.js";

/**
 * The daily canary (#956), one Cloud Run Job execution a day: for each standing test meeting it is given, the real bot
 * joins, says what it is, records for a minute and leaves, and the API is told whether it could. A failure there is
 * a Meet or Teams change caught before a customer's call meets it.
 */
const environment = process.env;
const apiBaseUrl = (environment.NOTETAKER_API_BASE_URL ?? "").trim().replace(/\/+$/, "");
const audience = environment.NOTETAKER_TOKEN_AUDIENCE?.trim() || apiBaseUrl;
const recordMs = Number(environment.CANARY_RECORD_SECONDS ?? "60") * 1_000;
const executablePath = environment.CHROME_PATH ?? "/usr/bin/chromium";
const meetings: { platform: BotConfig["platform"]; joinUrl: string | undefined }[] = [
  { platform: "GOOGLE_MEET", joinUrl: environment.CANARY_MEET_URL?.trim() },
  { platform: "MICROSOFT_TEAMS", joinUrl: environment.CANARY_TEAMS_URL?.trim() },
];

if (!apiBaseUrl) {
  throw new Error("NOTETAKER_API_BASE_URL is not set");
}
let unreported = 0;
for (const { platform, joinUrl } of meetings) {
  if (!joinUrl) {
    continue;
  }
  const log = runLogger(`canary-${platform}`);
  const canary = canaryApi(() => Date.now(), recordMs);
  const config: BotConfig = {
    runId: `canary-${platform}`, runToken: null, tokenAudience: audience, joinUrl, platform,
    botName: environment.NOTETAKER_BOT_NAME?.trim() || "Uncava Notetaker", apiBaseUrl,
    lobbyTimeoutMs: 5 * 60_000, maxDurationMs: recordMs + 5 * 60_000, endsAtMs: null,
  };
  let finding: CanaryFinding;
  try {
    await runBot(config, {
      api: canary.api,
      openPage: (onAudioPiece) => openCall(platform, joinUrl, onAudioPiece, executablePath),
      now: () => Date.now(),
      sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
      log,
    });
    finding = canary.finding();
  } catch (error) {
    log(`the canary run broke: ${error}`, "ERROR");
    finding = { healthy: false, reason: "CRASHED" };
  }
  log(finding.healthy ? "canary passed" : `canary FAILED: ${finding.reason}`, finding.healthy ? "INFO" : "ERROR");
  try {
    await reportFinding(apiBaseUrl, await identityToken(audience), platform, finding);
  } catch (error) {
    unreported++;
    log(`the finding could not be reported: ${error}`, "ERROR");
  }
}
process.exit(unreported > 0 ? 1 : 0);
