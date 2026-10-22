import { RunGoneError, type FailureReason, type NotetakerApi } from "./api.js";
import { CaptionTracker } from "./capture/captionTracker.js";
import type { BotConfig } from "./config.js";
import { PieceUploader } from "./pieces.js";
import type { MeetingPage } from "./platforms/platform.js";

export const TICK_MS = 2_000;
export const HEARTBEAT_MS = 5_000;
export const PIECE_MS = 30_000;
export const ALONE_MS = 3 * 60_000;

export type RunOutcome = "completed" | "removed" | "failed";

export interface RunnerDeps {
  api: NotetakerApi;
  openPage(onAudioPiece: (piece: Uint8Array) => void): Promise<MeetingPage>;
  now(): number;
  sleep(ms: number): Promise<void>;
  log(message: string): void;
}

/** Said in the call's chat as the bot is let in, before it records anything. */
export function consentLine(botName: string): string {
  return `${botName} is recording and transcribing this call to take notes for Uncava. `
    + "If you'd rather not be recorded, ask the host to remove it.";
}

/**
 * One bot, one call: ask to join, wait to be let in, say what it is, record in 30-second pieces, and leave when the
 * call ends, everyone else has gone for three minutes, somebody pressed Remove, or the cap is reached. Every piece
 * goes to the API as it is made, so a crash loses at most the one being recorded.
 */
export async function runBot(config: BotConfig, deps: RunnerDeps): Promise<RunOutcome> {
  const { api, log } = deps;
  const uploader = new PieceUploader(api, deps.sleep);
  const fail = async (reason: FailureReason): Promise<RunOutcome> => {
    log(`failing: ${reason}`);
    await api.report("FAILED", reason).catch((error) => log(`could not report the failure: ${error}`));
    return "failed";
  };

  let page: MeetingPage;
  try {
    page = await deps.openPage((piece) => uploader.enqueue("audio", piece));
  } catch (error) {
    log(`the browser could not open the call: ${error}`);
    return fail("LAUNCH_FAILED");
  }

  try {
    if ((await page.requestToJoin(config.botName)) === "blocked") {
      return await fail("JOIN_BLOCKED");
    }
    await api.report("WAITING_ROOM");

    const admitted = await waitToBeLetIn(config, deps, page);
    if (admitted === "removed") {
      await api.complete();
      return "removed";
    }
    if (admitted !== "in-call") {
      return await fail(admitted);
    }

    await api.report("RECORDING");
    await page.announce(consentLine(config.botName)).catch((error) => log(`no consent line in the chat: ${error}`));
    await page.turnOnCaptions().catch((error) => log(`captions could not be turned on: ${error}`));
    await page.startAudio(PIECE_MS);

    const ending = await recordUntilTheEnd(config, deps, page, uploader);
    log(`leaving: ${ending}`);
    await page.stopAudio().catch((error) => log(`the last audio piece may be lost: ${error}`));
    await page.leave();
    const undelivered = await uploader.drain();
    if (undelivered > 0) {
      log(`${undelivered} pieces could not be delivered`);
    }
    await api.complete();
    return ending === "removed" ? "removed" : "completed";
  } catch (error) {
    if (error instanceof RunGoneError) {
      log("the run was closed on the server; stopping");
      return "removed";
    }
    log(`the bot stopped working: ${error instanceof Error ? error.stack : error}`);
    await uploader.drain().catch(() => 0);
    return fail("CRASHED");
  } finally {
    await page.close().catch(() => undefined);
  }
}

async function waitToBeLetIn(config: BotConfig, deps: RunnerDeps, page: MeetingPage):
    Promise<"in-call" | "removed" | FailureReason> {
  const deadline = deps.now() + config.lobbyTimeoutMs;
  let lastHeartbeat = Number.NEGATIVE_INFINITY;
  for (;;) {
    const { stage } = await page.screen();
    if (stage === "in-call") {
      return "in-call";
    }
    if (stage === "blocked") {
      return "JOIN_BLOCKED";
    }
    if (stage === "denied" || stage === "ended" || deps.now() >= deadline) {
      return "NOT_ADMITTED";
    }
    if (deps.now() - lastHeartbeat >= HEARTBEAT_MS) {
      lastHeartbeat = deps.now();
      if (await toLeave(deps)) {
        return "removed";
      }
    }
    await deps.sleep(TICK_MS);
  }
}

async function recordUntilTheEnd(config: BotConfig, deps: RunnerDeps, page: MeetingPage, uploader: PieceUploader):
    Promise<"ended" | "alone" | "removed" | "cap"> {
  const captions = new CaptionTracker();
  const started = deps.now();
  let lastHeartbeat = started;
  let lastCaptionPiece = started;
  let aloneSince: number | null = null;
  try {
    for (;;) {
      await deps.sleep(TICK_MS);
      const now = deps.now();
      try {
        captions.observe(await page.captions(), new Date(now));
      } catch (error) {
        deps.log(`captions unreadable this tick: ${error}`);
      }
      const screen = await page.screen();
      if (screen.stage === "ended" || screen.stage === "denied") {
        return "ended";
      }
      if (screen.participants === 1) {
        aloneSince ??= now;
        if (now - aloneSince >= ALONE_MS) {
          return "alone";
        }
      } else {
        aloneSince = null;
      }
      if (now - lastHeartbeat >= HEARTBEAT_MS) {
        lastHeartbeat = now;
        if (await toLeave(deps)) {
          return "removed";
        }
      }
      if (now - started >= config.maxDurationMs) {
        return "cap";
      }
      if (now - lastCaptionPiece >= PIECE_MS) {
        lastCaptionPiece = now;
        uploader.enqueue("captions", captions.takePiece());
      }
    }
  } finally {
    uploader.enqueue("captions", captions.takePiece());
  }
}

/** A heartbeat the network loses is not a reason to leave; a run the server closed is. */
async function toLeave(deps: RunnerDeps): Promise<boolean> {
  try {
    return (await deps.api.heartbeat()).leave;
  } catch (error) {
    if (error instanceof RunGoneError) {
      return true;
    }
    deps.log(`heartbeat lost: ${error}`);
    return false;
  }
}
