import { describe, expect, it } from "vitest";
import { RunGoneError, type BotState, type FailureReason, type NotetakerApi, type PieceKind } from "./api.js";
import type { BotConfig } from "./config.js";
import type { MeetScreen } from "./platforms/meetScreen.js";
import type { MeetingPage } from "./platforms/platform.js";
import { ALONE_MS, OVERRUN_MS, PIECE_MS, consentLine, runBot, type RunnerDeps } from "./runner.js";

const config: BotConfig = {
  runId: "run-1", runToken: "token-1", tokenAudience: null, joinUrl: "https://meet.google.com/abc-defg-hij", platform: "GOOGLE_MEET",
  botName: "Uncava Notetaker", apiBaseUrl: "http://api", lobbyTimeoutMs: 10 * 60_000, maxDurationMs: 60 * 60_000,
  endsAtMs: null,
};

interface Script {
  join?: "requested" | "blocked" | "devices-on";
  chatWorks?: boolean;
  /** How many of the run's own calls (reports, complete) fail before one goes through. */
  callFailures?: number;
  /** What the page shows at each moment of the run, in milliseconds from its start. */
  screen: (elapsed: number) => MeetScreen;
  leaveAt?: number;
  openFails?: boolean;
  crashAt?: number;
}

function harness(script: Script) {
  let now = 0;
  const reports: Array<[BotState, FailureReason | undefined]> = [];
  const uploads: Array<[PieceKind, number]> = [];
  const chat: string[] = [];
  const events: string[] = [];
  const logs: string[] = [];
  let onAudio: (piece: Uint8Array, startedAt: Date) => void = () => undefined;
  let recording: ReturnType<typeof setInterval> | null = null;

  let callFailuresLeft = script.callFailures ?? 0;
  const flaky = () => {
    if (callFailuresLeft > 0) {
      callFailuresLeft--;
      throw new Error("502 Bad Gateway");
    }
  };
  const api: NotetakerApi = {
    async report(state, reason) {
      flaky();
      reports.push([state, reason]);
    },
    async heartbeat() {
      return { leave: script.leaveAt !== undefined && now >= script.leaveAt };
    },
    async upload(kind, sequence) {
      uploads.push([kind, sequence]);
    },
    async complete() {
      flaky();
      events.push("complete");
    },
  };
  const page: MeetingPage = {
    requestToJoin: async () => script.join ?? "requested",
    async screen() {
      if (script.crashAt !== undefined && now >= script.crashAt) {
        throw new Error("Target closed");
      }
      return script.screen(now);
    },
    announce: async (message) => {
      if (script.chatWorks === false) {
        return false;
      }
      chat.push(message);
      return true;
    },
    turnOnCaptions: async () => undefined,
    captions: async () => [{ speaker: "Priya", text: `said something at ${Math.floor(now / 10_000)}` }],
    async startAudio() {
      events.push("audio started");
      recording = null;
    },
    async stopAudio() {
      onAudio(new Uint8Array(4), new Date(0));
      events.push("audio stopped");
    },
    leave: async () => {
      events.push("left");
    },
    close: async () => {
      events.push("closed");
    },
  };
  const deps: RunnerDeps = {
    api,
    async openPage(handler) {
      if (script.openFails) {
        throw new Error("no chromium");
      }
      onAudio = handler;
      return page;
    },
    now: () => now,
    async sleep(ms) {
      const before = now;
      now += ms;
      // A piece every 30 s, as the page's recorder would hand one over.
      if (events.includes("audio started") && !events.includes("audio stopped")
          && Math.floor(now / PIECE_MS) > Math.floor(before / PIECE_MS)) {
        onAudio(new Uint8Array(8), new Date(0));
      }
    },
    log: (message) => {
      logs.push(message);
    },
  };
  void recording;
  return { deps, reports, uploads, chat, events, logs, elapsed: () => now };
}

const inCall = (participants: number): MeetScreen => ({ stage: "in-call", participants });
const lobby: MeetScreen = { stage: "lobby", participants: null };

describe("runBot", () => {
  it("waits to be let in, says what it is, records in pieces and completes when the call ends", async () => {
    const run = harness({ screen: (t) => (t < 20_000 ? lobby : t < 125_000 ? inCall(3) : { stage: "ended",
      participants: null }) });

    expect(await runBot(config, run.deps)).toBe("completed");

    expect(run.reports).toEqual([["WAITING_ROOM", undefined], ["RECORDING", undefined]]);
    expect(run.chat).toEqual([consentLine("Uncava Notetaker")]);
    const audio = run.uploads.filter(([kind]) => kind === "audio").map(([, sequence]) => sequence);
    expect(audio).toEqual([1, 2, 3, 4, 5]);
    expect(run.uploads.some(([kind]) => kind === "captions")).toBe(true);
    expect(run.events.slice(-4)).toEqual(["audio stopped", "left", "complete", "closed"]);
  });

  it("reports a guest join the call refuses, without waiting", async () => {
    const run = harness({ join: "blocked", screen: () => lobby });
    expect(await runBot(config, run.deps)).toBe("failed");
    expect(run.reports).toEqual([["FAILED", "JOIN_BLOCKED"]]);
  });

  it("gives up as not admitted once the lobby timeout passes", async () => {
    const run = harness({ screen: () => lobby });
    expect(await runBot(config, run.deps)).toBe("failed");
    expect(run.reports.at(-1)).toEqual(["FAILED", "NOT_ADMITTED"]);
    expect(run.elapsed()).toBeGreaterThanOrEqual(config.lobbyTimeoutMs);
    expect(run.events).toContain("closed");
  });

  it("leaves within one heartbeat of Remove notetaker, and still hands over what it made", async () => {
    const run = harness({ screen: () => inCall(3), leaveAt: 61_000 });
    expect(await runBot(config, run.deps)).toBe("removed");
    expect(run.elapsed()).toBeLessThanOrEqual(61_000 + 5_000 + 2_000);
    expect(run.events).toContain("complete");
  });

  it("leaves a call everyone else has left once three minutes pass", async () => {
    const run = harness({ screen: (t) => inCall(t < 30_000 ? 2 : 1) });
    expect(await runBot(config, run.deps)).toBe("completed");
    expect(run.elapsed()).toBeGreaterThanOrEqual(30_000 + ALONE_MS);
    expect(run.elapsed()).toBeLessThan(30_000 + ALONE_MS + 10_000);
  });

  it("stops at the cap whatever the calendar said", async () => {
    const run = harness({ screen: () => inCall(4) });
    expect(await runBot({ ...config, maxDurationMs: 90_000 }, run.deps)).toBe("completed");
    expect(run.elapsed()).toBeLessThan(100_000);
  });

  it("records a call whose chat will not take the notice, and says so in its log", async () => {
    const run = harness({ chatWorks: false, screen: () => inCall(3) });
    expect(await runBot({ ...config, maxDurationMs: 60_000 }, run.deps)).toBe("completed");
    expect(run.reports.map(([state]) => state)).toContain("RECORDING");
    expect(run.reports.map(([state]) => state)).not.toContain("FAILED");
    expect(run.events).toContain("audio started");
    expect(run.logs.some((line) => line.includes("recording notice could not be posted"))).toBe(true);
  });

  it("never joins with a microphone or camera it could not turn off", async () => {
    const run = harness({ join: "devices-on", screen: () => lobby });
    expect(await runBot(config, run.deps)).toBe("failed");
    expect(run.reports).toEqual([["FAILED", "DEVICES_NOT_OFF"]]);
  });

  it("rides out a lost answer to its own reports and its end, rather than losing the call", async () => {
    const run = harness({ callFailures: 3, screen: (t) => (t < 60_000 ? inCall(3) : { stage: "ended",
      participants: null }) });
    expect(await runBot(config, run.deps)).toBe("completed");
    expect(run.reports.map(([state]) => state)).toEqual(["WAITING_ROOM", "RECORDING"]);
    expect(run.events).toContain("complete");
  });

  it("leaves half an hour past the calendar's end when it cannot count the room", async () => {
    const run = harness({ screen: () => ({ stage: "in-call", participants: null }) });
    expect(await runBot({ ...config, endsAtMs: 20 * 60_000 }, run.deps)).toBe("completed");
    expect(run.elapsed()).toBeGreaterThanOrEqual(20 * 60_000 + OVERRUN_MS);
    expect(run.elapsed()).toBeLessThan(20 * 60_000 + OVERRUN_MS + 10_000);
  });

  it("reports a browser that never opened as a failed launch", async () => {
    const run = harness({ openFails: true, screen: () => lobby });
    expect(await runBot(config, run.deps)).toBe("failed");
    expect(run.reports).toEqual([["FAILED", "LAUNCH_FAILED"]]);
  });

  it("reports a crash mid-call, after handing over the pieces already made", async () => {
    const run = harness({ screen: () => inCall(3), crashAt: 70_000 });
    expect(await runBot(config, run.deps)).toBe("failed");
    expect(run.reports.at(-1)).toEqual(["FAILED", "CRASHED"]);
    expect(run.uploads.filter(([kind]) => kind === "audio")).toHaveLength(2);
    expect(run.events).toContain("closed");
  });

  it("stops quietly when the server has closed the run", async () => {
    const run = harness({ screen: () => inCall(3) });
    run.deps.api.heartbeat = async () => {
      throw new RunGoneError("closed");
    };
    expect(await runBot(config, run.deps)).toBe("removed");
  });
});
