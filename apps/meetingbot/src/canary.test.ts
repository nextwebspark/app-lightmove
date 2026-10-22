import { describe, expect, it, vi } from "vitest";
import type { NotetakerApi } from "./api.js";
import { canaryApi, reportFinding, runCanaryOn, type CanaryDeps } from "./canary.js";
import { CANARY_PHRASE } from "./canary/phrase.js";

describe("canaryApi", () => {
  it("asks the bot to leave once it has recorded long enough, and calls a run that captured audio healthy", async () => {
    let clock = 0;
    const canary = canaryApi(() => clock, 60_000);
    await canary.api.report("WAITING_ROOM");
    expect(await canary.api.heartbeat()).toEqual({ leave: false });
    await canary.api.report("RECORDING");
    await canary.api.upload("audio", 1, new Uint8Array([1, 2, 3]));
    clock = 59_000;
    expect(await canary.api.heartbeat()).toEqual({ leave: false });
    clock = 60_000;
    expect(await canary.api.heartbeat()).toEqual({ leave: true });

    expect(canary.finding()).toEqual({ healthy: true, reason: null });
    expect(canary.audio()).toEqual([new Uint8Array([1, 2, 3])]);
  });

  it("names what went wrong: the bot's own failure, never getting in, or no audio at all", async () => {
    const failed = canaryApi(() => 0, 60_000);
    await failed.api.report("FAILED", "JOIN_BLOCKED");
    expect(failed.finding()).toEqual({ healthy: false, reason: "JOIN_BLOCKED" });

    expect(canaryApi(() => 0, 60_000).finding()).toEqual({ healthy: false, reason: "NOT_RECORDING" });

    const silent = canaryApi(() => 0, 60_000);
    await silent.api.report("RECORDING");
    await silent.api.upload("captions", 1, new Uint8Array([1]));
    expect(silent.finding()).toEqual({ healthy: false, reason: "NO_AUDIO" });
  });
});

describe("runCanaryOn", () => {
  const LOUD = new Uint8Array([1]);
  const QUIET = new Uint8Array([2]);

  const recordingBot = (pieces: Uint8Array[]) => async (api: NotetakerApi) => {
    await api.report("RECORDING");
    for (const [index, piece] of pieces.entries()) {
      await api.upload("audio", index + 1, piece);
    }
  };

  const deps = (overrides: Partial<CanaryDeps> = {}) => {
    const speaker = { stillSpeaking: vi.fn(async () => true), leave: vi.fn(async () => undefined) };
    const transcribe = vi.fn(async () => CANARY_PHRASE);
    const all: CanaryDeps = {
      joinSpeaker: async () => speaker,
      runBot: recordingBot([QUIET, LOUD]),
      audibleSecondsOf: async (pieces) => pieces.map((piece) => (piece === LOUD ? 18 : 0)),
      transcribe,
      now: () => 0,
      log: () => undefined,
      ...overrides,
    };
    return { all, speaker, transcribe };
  };

  it("passes once the loudest piece is heard saying the phrase, and the speaker has left", async () => {
    const { all, speaker, transcribe } = deps();

    expect(await runCanaryOn(60_000, all)).toEqual({ healthy: true, reason: null });
    expect(transcribe.mock.calls).toEqual([[LOUD]]);
    expect(speaker.leave).toHaveBeenCalledOnce();
  });

  it("fails silent pieces as NO_SPEECH without asking the transcriber", async () => {
    const { all, transcribe } = deps({ audibleSecondsOf: async (pieces) => pieces.map(() => 0) });

    expect(await runCanaryOn(60_000, all)).toEqual({ healthy: false, reason: "NO_SPEECH" });
    expect(transcribe.mock.calls).toEqual([]);
  });

  it("fails WRONG_SPEECH when the pieces hold words that are not the phrase, and NO_SPEECH when they hold none", async () => {
    expect(await runCanaryOn(60_000, deps({ transcribe: async () => "Can everyone hear me now?" }).all))
      .toEqual({ healthy: false, reason: "WRONG_SPEECH" });
    expect(await runCanaryOn(60_000, deps({ transcribe: async () => "" }).all))
      .toEqual({ healthy: false, reason: "NO_SPEECH" });
  });

  it("fails SPEAKER_NOT_JOINED when the speaker never got in, but the bot's own failure comes first", async () => {
    const noSpeaker = async () => {
      throw new Error("no pre-join screen");
    };
    expect(await runCanaryOn(60_000, deps({ joinSpeaker: noSpeaker }).all))
      .toEqual({ healthy: false, reason: "SPEAKER_NOT_JOINED" });

    const blocked = async (api: NotetakerApi) => api.report("FAILED", "JOIN_BLOCKED");
    expect(await runCanaryOn(60_000, deps({ joinSpeaker: noSpeaker, runBot: blocked }).all))
      .toEqual({ healthy: false, reason: "JOIN_BLOCKED" });
  });

  it("fails SPEAKER_NOT_JOINED when the speaker dropped out or went quiet before the bot left, not NO_SPEECH", async () => {
    const quiet = { stillSpeaking: async () => false, leave: async () => undefined };

    expect(await runCanaryOn(60_000, deps({ joinSpeaker: async () => quiet,
      audibleSecondsOf: async (pieces) => pieces.map(() => 0) }).all))
      .toEqual({ healthy: false, reason: "SPEAKER_NOT_JOINED" });
  });

  it("tries the speaker's join a second time before giving up on it", async () => {
    const { all, speaker } = deps();
    const joins = vi.fn(async () => speaker);
    joins.mockRejectedValueOnce(new Error("no pre-join screen"));

    expect(await runCanaryOn(60_000, { ...all, joinSpeaker: joins })).toEqual({ healthy: true, reason: null });
    expect(joins).toHaveBeenCalledTimes(2);
  });

  it("takes a loud piece as heard while the transcriber is out of reach, rather than route the platform around", async () => {
    const unreachable = async (): Promise<string> => {
      throw new Error("the canary hearing answered 502");
    };
    expect(await runCanaryOn(60_000, deps({ transcribe: unreachable }).all)).toEqual({ healthy: true, reason: null });
  });

  it("asks the transcriber about every piece when their loudness could not be measured", async () => {
    const { all, transcribe } = deps({ audibleSecondsOf: async (pieces) => pieces.map(() => null) });

    expect(await runCanaryOn(60_000, all)).toEqual({ healthy: true, reason: null });
    expect(transcribe.mock.calls).toEqual([[QUIET]]);
  });

  it("fails CRASHED when the bot's run throws, and still sends the speaker away", async () => {
    const { all, speaker } = deps({ runBot: async () => {
      throw new Error("the browser died");
    } });

    expect(await runCanaryOn(60_000, all)).toEqual({ healthy: false, reason: "CRASHED" });
    expect(speaker.leave).toHaveBeenCalledOnce();
  });
});

describe("reportFinding", () => {
  it("posts the finding with the job's ID token", async () => {
    const fetcher = vi.fn(async () => new Response(null, { status: 204 }));
    await reportFinding("https://beta.uncava.com", "id-token-1", "MICROSOFT_TEAMS",
      { healthy: false, reason: "NOT_ADMITTED" }, fetcher as unknown as typeof fetch);

    const [url, init] = fetcher.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("https://beta.uncava.com/api/v1/internal/notetaker/canary");
    expect(new Headers(init.headers).get("X-Notetaker-Bot-Identity")).toBe("id-token-1");
    expect(JSON.parse(String(init.body))).toEqual({ platform: "MICROSOFT_TEAMS", healthy: false,
      reason: "NOT_ADMITTED" });
  });
});
