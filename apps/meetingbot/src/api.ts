/** The bot's side of `/api/v1/internal/notetaker/runs/{id}`: its run token is its only credential. */
export type BotState = "WAITING_ROOM" | "RECORDING" | "FAILED";
export type FailureReason = "LAUNCH_FAILED" | "JOIN_BLOCKED" | "NOT_ADMITTED" | "CRASHED" | "CONSENT_NOT_SHOWN"
  | "DEVICES_NOT_OFF";
export type PieceKind = "audio" | "captions";

export interface NotetakerApi {
  report(state: BotState, reason?: FailureReason): Promise<void>;
  /** `true` when somebody pressed Remove notetaker, or the run is no longer ours to work. */
  heartbeat(): Promise<{ leave: boolean }>;
  /** `startedAt` is when an audio piece began, which the transcript is stitched on. */
  upload(kind: PieceKind, sequence: number, content: Uint8Array, startedAt?: Date): Promise<void>;
  complete(): Promise<void>;
}

export class RunGoneError extends Error {}

export function httpApi(baseUrl: string, runId: string, runToken: string, fetcher: typeof fetch = fetch): NotetakerApi {
  const base = `${baseUrl}/api/v1/internal/notetaker/runs/${runId}`;
  const send = async (method: string, path: string, body?: BodyInit, contentType?: string,
    extraHeaders: Record<string, string> = {}): Promise<Response> => {
    const response = await fetcher(`${base}${path}`, {
      method,
      body,
      headers: {
        "X-Notetaker-Run-Token": runToken,
        ...(contentType ? { "Content-Type": contentType } : {}),
        ...extraHeaders,
      },
      signal: AbortSignal.timeout(30_000),
    });
    // A 404 is the server's answer for a run that ended or a token it no longer holds: nothing more is ours to do.
    if (response.status === 404) {
      throw new RunGoneError(`${method} ${path}: the run is no longer open`);
    }
    if (!response.ok) {
      throw new Error(`${method} ${path} answered ${response.status}`);
    }
    return response;
  };
  return {
    async report(state, reason) {
      await send("POST", "/status", JSON.stringify(reason ? { state, reason } : { state }), "application/json");
    },
    async heartbeat() {
      const response = await send("POST", "/heartbeat");
      const answer = (await response.json()) as { leave?: boolean };
      return { leave: answer.leave === true };
    },
    async upload(kind, sequence, content, startedAt) {
      await send("PUT", `/media/${kind}/${sequence}`, content as BodyInit, "application/octet-stream",
        startedAt ? { "X-Notetaker-Piece-Started-At": startedAt.toISOString() } : {});
    },
    async complete() {
      await send("POST", "/complete");
    },
  };
}
