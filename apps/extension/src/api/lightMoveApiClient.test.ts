import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from "vitest";
import { ApiRequestError, createLightMoveApiClient } from "./lightMoveApiClient";

/**
 * The 401 retry, the CSRF-free credential posture, and the refusal shape the popup switches on.
 *
 * This module's own doc says it is free of every `chrome.*` API so it can be tested without a browser;
 * it was not being. The retry in particular is the difference between a popup that recovers from an
 * expired access token and one that reports a refusal to a consultant who is perfectly well signed in.
 */
const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

let currentAccessToken: Mock<() => Promise<string | null>>;
let renewAccessToken: Mock<() => Promise<string | null>>;

function headersOf(request: RequestInit | undefined): Record<string, string> {
  return (request?.headers ?? {}) as Record<string, string>;
}

function client() {
  return createLightMoveApiClient({
    baseOrigin: "https://app.lightmove.example",
    currentAccessToken,
    renewAccessToken,
  });
}

beforeEach(() => {
  currentAccessToken = vi.fn(async () => "access-1");
  renewAccessToken = vi.fn(async () => "access-2");
  vi.stubGlobal("fetch", vi.fn(async () => jsonResponse({ ok: true })));
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("sending a request", () => {
  it("prefixes /api/v1, carries the bearer, and never asks for cookies", async () => {
    await client().request("/projects");

    const [url, request] = vi.mocked(fetch).mock.calls[0];
    expect(String(url)).toBe("https://app.lightmove.example/api/v1/projects");
    expect(headersOf(request)["Authorization"]).toBe("Bearer access-1");
    // "include" would reintroduce the cross-origin cookie problem pairing exists to avoid.
    expect(request?.credentials).toBe("omit");
  });

  it("sends a body as JSON and reads a 204 as nothing", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 204 }));

    await expect(client().request("/projects/p1/candidates/c1", { method: "DELETE" })).resolves.toBeUndefined();
  });
});

describe("an expired access token", () => {
  it("renews once and replays the request with the new token", async () => {
    vi.mocked(fetch)
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(jsonResponse({ id: "p1" }));

    await expect(client().request("/projects")).resolves.toEqual({ id: "p1" });

    expect(renewAccessToken).toHaveBeenCalledTimes(1);
    const [, retried] = vi.mocked(fetch).mock.calls[1];
    expect(headersOf(retried)["Authorization"]).toBe("Bearer access-2");
  });

  it("gives up after a second 401 rather than spending refresh tokens in a loop", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 401 }));

    await expect(client().request("/projects")).rejects.toBeInstanceOf(ApiRequestError);
    expect(renewAccessToken).toHaveBeenCalledTimes(1);
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("does not retry when there is no session left to renew", async () => {
    renewAccessToken.mockResolvedValue(null);
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 401 }));

    await expect(client().request("/projects")).rejects.toBeInstanceOf(ApiRequestError);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
});

describe("a refusal", () => {
  it("carries the server's own code, which is what the popup switches on", async () => {
    vi.mocked(fetch).mockResolvedValue(
      jsonResponse({ code: "TRIAGE_COMPANY_ALREADY_HELD", detail: "Already held." }, 409),
    );

    await expect(client().request("/projects/p1/triage/capture", { method: "POST", body: {} }))
      .rejects.toMatchObject({ code: "TRIAGE_COMPANY_ALREADY_HELD", message: "Already held." });
  });

  // A proxy's HTML error page would otherwise surface as "Unexpected token < in JSON".
  it("turns a non-JSON failure into a problem rather than a parse error", async () => {
    vi.mocked(fetch).mockResolvedValue(new Response("<html>502</html>", { status: 502 }));

    await expect(client().request("/projects")).rejects.toMatchObject({ problem: { status: 502 } });
  });
});
