import { describe, expect, it, vi } from "vitest";
import { readConfig } from "./config.js";
import { METADATA_IDENTITY, runTokenOf } from "./runToken.js";

const base = {
  NOTETAKER_RUN_ID: "run-1",
  NOTETAKER_JOIN_URL: "https://meet.google.com/abc-defg-hij",
  NOTETAKER_PLATFORM: "GOOGLE_MEET",
  NOTETAKER_API_BASE_URL: "https://beta.uncava.com",
};

describe("runTokenOf", () => {
  it("uses the token the bot was started with, asking nobody", async () => {
    const fetcher = vi.fn();
    const config = readConfig({ ...base, NOTETAKER_RUN_TOKEN: "started-with" });

    expect(await runTokenOf(config, fetcher as unknown as typeof fetch)).toBe("started-with");
    expect(fetcher).not.toHaveBeenCalled();
  });

  it("exchanges an ID token minted for the API's audience for the run's token", async () => {
    const fetcher = vi.fn(async (url: string, init?: RequestInit) => {
      if (url.startsWith(METADATA_IDENTITY)) {
        return new Response("id-token-1");
      }
      expect(url).toBe("https://beta.uncava.com/api/v1/internal/notetaker/runs/run-1/token");
      expect(new Headers(init?.headers).get("X-Notetaker-Bot-Identity")).toBe("id-token-1");
      return Response.json({ runToken: "exchanged" });
    });
    const config = readConfig({ ...base, NOTETAKER_TOKEN_AUDIENCE: "https://beta.uncava.com" });

    expect(await runTokenOf(config, fetcher as unknown as typeof fetch)).toBe("exchanged");
    const [metadataUrl, metadataInit] = fetcher.mock.calls[0] as [string, RequestInit];
    expect(metadataUrl).toContain("audience=https%3A%2F%2Fbeta.uncava.com");
    expect((metadataInit.headers as Record<string, string>)["Metadata-Flavor"]).toBe("Google");
  });

  it("gives up when the exchange is refused", async () => {
    const fetcher = vi.fn(async (url: string) =>
      url.startsWith(METADATA_IDENTITY) ? new Response("id-token-1") : new Response("", { status: 404 }));
    const config = readConfig({ ...base, NOTETAKER_TOKEN_AUDIENCE: "https://beta.uncava.com" });

    await expect(runTokenOf(config, fetcher as unknown as typeof fetch)).rejects.toThrow("404");
  });
});
