import { describe, expect, it } from "vitest";
import { describe as describeRun, readConfig } from "./config.js";

const environment = {
  NOTETAKER_RUN_ID: "run-1",
  NOTETAKER_RUN_TOKEN: "secret-token",
  NOTETAKER_JOIN_URL: "https://meet.google.com/abc-defg-hij",
  NOTETAKER_PLATFORM: "GOOGLE_MEET",
  NOTETAKER_BOT_NAME: "Uncava Notetaker",
  NOTETAKER_API_BASE_URL: "https://beta.uncava.com/",
  NOTETAKER_LOBBY_TIMEOUT_MS: "600000",
};

describe("readConfig", () => {
  it("reads the run the API started the bot with", () => {
    const config = readConfig(environment);
    expect(config.apiBaseUrl).toBe("https://beta.uncava.com");
    expect(config.lobbyTimeoutMs).toBe(600_000);
    expect(config.maxDurationMs).toBe(3 * 60 * 60_000);
  });

  it("refuses to start without a run token or with a platform it does not join", () => {
    expect(() => readConfig({ ...environment, NOTETAKER_RUN_TOKEN: " " })).toThrow("NOTETAKER_RUN_TOKEN");
    expect(() => readConfig({ ...environment, NOTETAKER_PLATFORM: "ZOOM" })).toThrow("ZOOM");
  });

  it("never puts the token in what a log line may say", () => {
    expect(describeRun(readConfig(environment))).not.toContain("secret-token");
  });
});
