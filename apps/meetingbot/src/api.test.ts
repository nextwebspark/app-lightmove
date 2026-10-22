import { describe, expect, it } from "vitest";
import { RunGoneError, httpApi } from "./api.js";

function fakeFetch(status: number, body = "{}") {
  const requests: Array<{ url: string; init: RequestInit }> = [];
  const fetcher = (async (url: string, init: RequestInit) => {
    requests.push({ url, init });
    return new Response(status === 204 ? null : body, { status });
  }) as unknown as typeof fetch;
  return { fetcher, requests };
}

describe("httpApi", () => {
  it("calls the run's own routes with its token in its own header, never as a bearer", async () => {
    const { fetcher, requests } = fakeFetch(204);
    const api = httpApi("https://beta.uncava.com", "run-1", "token-1", fetcher);
    await api.report("FAILED", "NOT_ADMITTED");
    await api.upload("audio", 3, new Uint8Array([1, 2]), new Date("2026-10-22T10:00:30Z"));

    expect(requests[0].url).toBe("https://beta.uncava.com/api/v1/internal/notetaker/runs/run-1/status");
    expect(requests[0].init.body).toBe('{"state":"FAILED","reason":"NOT_ADMITTED"}');
    const headers = requests[0].init.headers as Record<string, string>;
    expect(headers["X-Notetaker-Run-Token"]).toBe("token-1");
    expect(headers.Authorization).toBeUndefined();
    expect(requests[1].url).toBe("https://beta.uncava.com/api/v1/internal/notetaker/runs/run-1/media/audio/3");
    expect(requests[1].init.method).toBe("PUT");
    expect((requests[1].init.headers as Record<string, string>)["X-Notetaker-Piece-Started-At"])
      .toBe("2026-10-22T10:00:30.000Z");
  });

  it("reads the heartbeat's answer", async () => {
    const { fetcher } = fakeFetch(200, '{"leave":true}');
    expect(await httpApi("http://api", "run-1", "token-1", fetcher).heartbeat()).toEqual({ leave: true });
  });

  it("reads a 404 as a run the server has closed", async () => {
    const { fetcher } = fakeFetch(404);
    await expect(httpApi("http://api", "run-1", "token-1", fetcher).heartbeat()).rejects.toBeInstanceOf(RunGoneError);
  });

  it("reads any other refusal as a failure to retry", async () => {
    const { fetcher } = fakeFetch(503);
    await expect(httpApi("http://api", "run-1", "token-1", fetcher).complete()).rejects.not.toBeInstanceOf(
      RunGoneError);
  });
});
