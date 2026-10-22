/** What the API started this bot with (`NotetakerBotEnvironment` on the server side). */
export interface BotConfig {
  runId: string;
  /** Null on Cloud Run: the bot exchanges its own ID token, minted for `tokenAudience`, for it (`runToken.ts`). */
  runToken: string | null;
  tokenAudience: string | null;
  joinUrl: string;
  platform: "GOOGLE_MEET" | "MICROSOFT_TEAMS";
  botName: string;
  apiBaseUrl: string;
  lobbyTimeoutMs: number;
  maxDurationMs: number;
  /** When the calendar says the call ends; null when it did not say. */
  endsAtMs: number | null;
}

const PLATFORMS = ["GOOGLE_MEET", "MICROSOFT_TEAMS"] as const;

export function readConfig(environment: Record<string, string | undefined>): BotConfig {
  const required = (name: string): string => {
    const value = environment[name];
    if (!value || !value.trim()) {
      throw new Error(`${name} is not set`);
    }
    return value.trim();
  };
  const platform = required("NOTETAKER_PLATFORM");
  if (!(PLATFORMS as readonly string[]).includes(platform)) {
    throw new Error(`NOTETAKER_PLATFORM ${platform} is not one this bot joins`);
  }
  const runToken = environment.NOTETAKER_RUN_TOKEN?.trim() || null;
  const tokenAudience = environment.NOTETAKER_TOKEN_AUDIENCE?.trim() || null;
  if (!runToken && !tokenAudience) {
    throw new Error("NOTETAKER_RUN_TOKEN is not set, and neither is NOTETAKER_TOKEN_AUDIENCE to exchange for it");
  }
  return {
    runId: required("NOTETAKER_RUN_ID"),
    runToken,
    tokenAudience,
    joinUrl: required("NOTETAKER_JOIN_URL"),
    platform: platform as BotConfig["platform"],
    botName: environment.NOTETAKER_BOT_NAME?.trim() || "Uncava Notetaker",
    apiBaseUrl: required("NOTETAKER_API_BASE_URL").replace(/\/+$/, ""),
    lobbyTimeoutMs: positiveNumber(environment.NOTETAKER_LOBBY_TIMEOUT_MS, 10 * 60_000),
    maxDurationMs: positiveNumber(environment.NOTETAKER_MAX_DURATION_MS, 3 * 60 * 60_000),
    endsAtMs: instant(environment.NOTETAKER_ENDS_AT),
  };
}

function instant(value: string | undefined): number | null {
  const parsed = value ? Date.parse(value) : Number.NaN;
  return Number.isFinite(parsed) ? parsed : null;
}

function positiveNumber(value: string | undefined, fallback: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

/** Never the token: this is what a log line may carry. */
export function describe(config: BotConfig): string {
  return `run ${config.runId} on ${config.platform} as "${config.botName}"`;
}
