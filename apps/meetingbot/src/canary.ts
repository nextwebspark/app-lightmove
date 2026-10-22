import type { BotState, FailureReason, NotetakerApi } from "./api.js";
import { hearingOf } from "./canary/phrase.js";
import type { CanarySpeaker } from "./canary/speaker.js";
import type { Severity } from "./log.js";

/** What one canary run found on a platform, as the API's platform health records it. */
export interface CanaryFinding {
  healthy: boolean;
  reason: string | null;
}

/** Below this, a piece holds no voice worth a transcriber's time. */
const AUDIBLE_SECONDS = 2;
const PIECES_TO_TRANSCRIBE = 2;
const SPEAKER_ATTEMPTS = 2;

/**
 * The bot's API, kept in the canary's own memory: the canary runs the real bot against a standing test meeting, asks
 * it to leave once it has recorded for `recordMs`, and keeps its audio pieces only until it has listened to them.
 */
export function canaryApi(now: () => number, recordMs: number) {
  let recordingSince: number | null = null;
  let failure: FailureReason | null = null;
  const audio: Uint8Array[] = [];
  const api: NotetakerApi = {
    async report(state: BotState, reason?: FailureReason) {
      if (state === "RECORDING" && recordingSince === null) {
        recordingSince = now();
      }
      if (state === "FAILED") {
        failure = reason ?? "CRASHED";
      }
    },
    async heartbeat() {
      return { leave: recordingSince !== null && now() - recordingSince >= recordMs };
    },
    async upload(kind, _sequence, content) {
      if (kind === "audio" && content.byteLength > 0) {
        audio.push(content);
      }
    },
    async complete() {},
  };
  const finding = (): CanaryFinding => {
    if (failure) {
      return { healthy: false, reason: failure };
    }
    if (recordingSince === null) {
      return { healthy: false, reason: "NOT_RECORDING" };
    }
    if (audio.length === 0) {
      return { healthy: false, reason: "NO_AUDIO" };
    }
    return { healthy: true, reason: null };
  };
  return { api, finding, audio: () => [...audio] };
}

export interface CanaryDeps {
  joinSpeaker(): Promise<CanarySpeaker>;
  runBot(api: NotetakerApi): Promise<unknown>;
  audibleSecondsOf(pieces: Uint8Array[]): Promise<(number | null)[]>;
  transcribe(piece: Uint8Array): Promise<string>;
  now(): number;
  log(message: string, severity?: Severity): void;
}

/**
 * One platform's canary (#978): the speaker joins and says the canary phrase on a loop, the real bot joins and
 * records, and the finding is healthy only once the bot's pieces are heard saying it. A bot that never got in fails
 * as itself; a speaker that never got in `SPEAKER_NOT_JOINED`; silent pieces `NO_SPEECH`; and words that are not the
 * phrase `WRONG_SPEECH`.
 */
export async function runCanaryOn(recordMs: number, deps: CanaryDeps): Promise<CanaryFinding> {
  const { log } = deps;
  const speaker = await joinSpeaker(deps);
  const canary = canaryApi(deps.now, recordMs);
  let speaking = false;
  try {
    await deps.runBot(canary.api);
    speaking = speaker !== null && await speaker.stillSpeaking().catch(() => false);
  } catch (error) {
    log(`the canary run broke: ${error}`, "ERROR");
    return { healthy: false, reason: "CRASHED" };
  } finally {
    await speaker?.leave().catch((error) => log(`the speaker could not leave: ${error}`, "WARNING"));
  }
  const found = canary.finding();
  if (!found.healthy) {
    return found;
  }
  // A speaker that dropped out or went quiet would read as the bot hearing nothing.
  if (!speaking) {
    log(speaker ? "the speaker was no longer sending sound into the call" : "the speaker never got in", "ERROR");
    return { healthy: false, reason: "SPEAKER_NOT_JOINED" };
  }
  return listen(canary.audio(), deps);
}

async function joinSpeaker(deps: CanaryDeps): Promise<CanarySpeaker | null> {
  for (let attempt = 1; attempt <= SPEAKER_ATTEMPTS; attempt++) {
    try {
      return await deps.joinSpeaker();
    } catch (error) {
      deps.log(`the speaker could not join (attempt ${attempt}): ${error}`, "WARNING");
    }
  }
  return null;
}

/**
 * Silence fails without asking the model. Should the transcriber be out of reach, the speaker being loud in the
 * pieces is taken as heard — the platform is not routed around for our own model's outage.
 */
async function listen(pieces: Uint8Array[], deps: CanaryDeps): Promise<CanaryFinding> {
  const { log } = deps;
  let seconds: (number | null)[];
  try {
    seconds = await deps.audibleSecondsOf(pieces);
  } catch (error) {
    log(`the pieces' loudness could not be measured: ${error}`, "WARNING");
    seconds = pieces.map(() => null);
  }
  log(`audible seconds per piece: ${seconds.map((piece) => piece ?? "?").join(", ")}`);
  const measured = seconds.some((piece) => piece !== null);
  const candidates = pieces
    .map((piece, index) => ({ piece, seconds: seconds[index] ?? null }))
    .filter((candidate) => !measured || (candidate.seconds ?? 0) >= AUDIBLE_SECONDS)
    .sort((first, second) => (second.seconds ?? 0) - (first.seconds ?? 0))
    .slice(0, PIECES_TO_TRANSCRIBE);
  if (candidates.length === 0) {
    return { healthy: false, reason: "NO_SPEECH" };
  }
  let unheard = 0;
  let otherWords = false;
  for (const { piece } of candidates) {
    let transcript: string;
    try {
      transcript = await deps.transcribe(piece);
    } catch (error) {
      log(`a piece could not be transcribed: ${error}`, "WARNING");
      unheard++;
      continue;
    }
    const hearing = hearingOf(transcript);
    if (hearing === "phrase") {
      return { healthy: true, reason: null };
    }
    otherWords ||= hearing === "other-words";
  }
  if (unheard === candidates.length) {
    log(measured ? "the speaker is loud in the pieces, but the phrase could not be checked"
      : "neither the pieces' loudness nor their words could be checked", "WARNING");
    return { healthy: true, reason: null };
  }
  return { healthy: false, reason: otherWords ? "WRONG_SPEECH" : "NO_SPEECH" };
}

/** Tells the API what the canary found, with the job's own ID token as the credential. */
export async function reportFinding(apiBaseUrl: string, idToken: string,
  platform: "GOOGLE_MEET" | "MICROSOFT_TEAMS", finding: CanaryFinding, fetcher: typeof fetch = fetch): Promise<void> {
  const response = await fetcher(`${apiBaseUrl}/api/v1/internal/notetaker/canary`, {
    method: "POST",
    headers: { "X-Notetaker-Bot-Identity": idToken, "Content-Type": "application/json" },
    body: JSON.stringify({ platform, healthy: finding.healthy, ...(finding.reason ? { reason: finding.reason } : {}) }),
    signal: AbortSignal.timeout(30_000),
  });
  if (!response.ok) {
    throw new Error(`the canary report answered ${response.status}`);
  }
}
