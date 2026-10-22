import { mkdirSync, writeFileSync } from "node:fs";
import { createServer } from "node:http";
import { join } from "node:path";
import { httpApi } from "../api.js";
import type { BotConfig } from "../config.js";
import { openMeet } from "../platforms/meetPage.js";
import { runBot } from "../runner.js";

/**
 * Tries the bot on a real call with no Uncava behind it: a stub of the run's API on this machine prints every
 * report and saves every piece under `try-output/`. Somebody has to admit "Uncava Notetaker (try)" from the call.
 *
 *   CHROME_PATH=/path/to/chrome npm run try --workspace=apps/meetingbot -- https://meet.google.com/abc-defg-hij
 */
const joinUrl = process.argv[2];
if (!joinUrl) {
  console.error("Usage: npm run try -- <meet link>");
  process.exit(2);
}
const output = join(process.cwd(), "try-output", new Date().toISOString().replace(/[:.]/g, "-"));
mkdirSync(output, { recursive: true });
let leave = false;
process.on("SIGINT", () => {
  console.log("Ctrl+C: the bot will leave on its next heartbeat, as if removed");
  leave = true;
});

const server = createServer((request, response) => {
  const chunks: Buffer[] = [];
  request.on("data", (chunk: Buffer) => chunks.push(chunk));
  request.on("end", () => {
    const body = Buffer.concat(chunks);
    const piece = /\/media\/(audio|captions)\/(\d+)$/.exec(request.url ?? "");
    if (piece) {
      const name = `${piece[1]}-${piece[2].padStart(5, "0")}.${piece[1] === "audio" ? "webm" : "json"}`;
      writeFileSync(join(output, name), body);
      console.log(`piece ${name} (${body.length} bytes)`);
    } else if (request.url?.endsWith("/heartbeat")) {
      response.setHeader("Content-Type", "application/json");
      response.end(JSON.stringify({ leave }));
      return;
    } else {
      console.log(`${request.method} ${request.url} ${body.toString()}`);
    }
    response.statusCode = 204;
    response.end();
  });
});
await new Promise<void>((resolve) => server.listen(0, "127.0.0.1", resolve));
const port = (server.address() as { port: number }).port;

const config: BotConfig = {
  runId: "try", runToken: "try", joinUrl, platform: "GOOGLE_MEET", botName: "Uncava Notetaker (try)",
  apiBaseUrl: `http://127.0.0.1:${port}`, lobbyTimeoutMs: 10 * 60_000, maxDurationMs: 60 * 60_000, endsAtMs: null,
};
const outcome = await runBot(config, {
  api: httpApi(config.apiBaseUrl, config.runId, config.runToken),
  openPage: (onAudioPiece) => openMeet(joinUrl, onAudioPiece, process.env.CHROME_PATH ?? "/usr/bin/chromium"),
  now: () => Date.now(),
  sleep: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  log: (message) => console.log(message),
});
console.log(`finished: ${outcome}; pieces in ${output}`);
server.close();
process.exit(0);
