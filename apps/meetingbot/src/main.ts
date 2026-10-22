import { httpApi } from "./api.js";
import { describe, readConfig } from "./config.js";
import { runLogger } from "./log.js";
import { openCall } from "./platforms/openCall.js";
import { runBot } from "./runner.js";
import { runTokenOf } from "./runToken.js";

/** The bot's entry point: one process, one call, started by the API's runner with its run in the environment. */
const config = readConfig(process.env);
const executablePath = process.env.CHROME_PATH ?? "/usr/bin/chromium";
const log = runLogger(config.runId);

log(`starting ${describe(config)}`);
let runToken: string;
try {
  runToken = await runTokenOf(config);
} catch (error) {
  // Without its token the bot can tell the API nothing; the sweeper ends the run as never reported.
  log(`could not take the run's token: ${error}`, "ERROR");
  process.exit(1);
}
const api = httpApi(config.apiBaseUrl, config.runId, runToken);

const outcome = await runBot(config, {
  api,
  openPage: (onAudioPiece) => openCall(config.platform, config.joinUrl, onAudioPiece, executablePath),
  now: () => Date.now(),
  sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  log,
});
log(`finished: ${outcome}`);
process.exit(outcome === "failed" ? 1 : 0);
