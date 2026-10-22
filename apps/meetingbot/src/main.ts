import { httpApi } from "./api.js";
import { describe, readConfig } from "./config.js";
import { openCall } from "./platforms/openCall.js";
import { runBot } from "./runner.js";

/** The bot's entry point: one process, one call, started by the API's runner with its run in the environment. */
const config = readConfig(process.env);
const executablePath = process.env.CHROME_PATH ?? "/usr/bin/chromium";
const log = (message: string) => console.log(`[notetaker ${config.runId}] ${message}`);

const api = httpApi(config.apiBaseUrl, config.runId, config.runToken);

log(`starting ${describe(config)}`);
const outcome = await runBot(config, {
  api,
  openPage: (onAudioPiece) => openCall(config.platform, config.joinUrl, onAudioPiece, executablePath),
  now: () => Date.now(),
  sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  log,
});
log(`finished: ${outcome}`);
process.exit(outcome === "failed" ? 1 : 0);
