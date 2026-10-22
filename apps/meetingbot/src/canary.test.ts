import { describe, expect, it, vi } from "vitest";
import { canaryApi, reportFinding } from "./canary.js";

describe("canaryApi", () => {
  it("asks the bot to leave once it has recorded long enough, and calls a run that heard audio healthy", async () => {
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
  });

  it("names what went wrong: the bot's own failure, never getting in, or silence", async () => {
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
