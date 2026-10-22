import type { BotState, FailureReason, NotetakerApi } from "./api.js";

/** What one canary run found on a platform, as the API's platform health records it. */
export interface CanaryFinding {
  healthy: boolean;
  reason: string | null;
}

/**
 * The bot's API, kept in the canary's own memory: the canary runs the real bot against a standing test meeting, asks
 * it to leave once it has recorded for `recordMs`, and keeps nothing it captured — only whether it got in, said what it
 * was, and heard audio.
 */
export function canaryApi(now: () => number, recordMs: number) {
  let recordingSince: number | null = null;
  let failure: FailureReason | null = null;
  let audioBytes = 0;
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
      if (kind === "audio") {
        audioBytes += content.byteLength;
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
    if (audioBytes === 0) {
      return { healthy: false, reason: "NO_AUDIO" };
    }
    return { healthy: true, reason: null };
  };
  return { api, finding };
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
