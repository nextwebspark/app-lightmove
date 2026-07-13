import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiRequestError, request, restoreSession, setAccessToken } from "./apiClient";

/**
 * The client's job is not "call fetch". It is to hold the session together — and the two behaviours
 * tested here are the ones that go wrong invisibly.
 */
describe("apiClient", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockReset();
    setAccessToken(null);
    document.cookie = "XSRF-TOKEN=test-csrf";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const json = (status: number, body: unknown): Response =>
    ({
      ok: status >= 200 && status < 300,
      status,
      headers: new Headers({ "content-type": "application/json" }),
      json: async () => body,
    }) as Response;

  it("attaches the access token as a bearer header", async () => {
    setAccessToken("token-abc");
    fetchMock.mockResolvedValueOnce(json(200, { ok: true }));

    await request("/auth/me");

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.Authorization).toBe("Bearer token-abc");
    // credentials:include is what lets the httpOnly refresh cookie ride along at all. Without it the
    // session silently cannot survive a reload, and nothing else would look wrong.
    expect(init.credentials).toBe("include");
  });

  it("refreshes once on a 401 and retries the original request", async () => {
    setAccessToken("expired");

    fetchMock
      .mockResolvedValueOnce(json(401, { code: "UNAUTHORIZED", detail: "expired" })) // original
      .mockResolvedValueOnce(json(200, { accessToken: "fresh" })) // refresh
      .mockResolvedValueOnce(json(200, { id: "u1" })); // retry

    const result = await request<{ id: string }>("/auth/me");

    expect(result).toEqual({ id: "u1" });
    expect(fetchMock).toHaveBeenCalledTimes(3);

    // The retry must carry the NEW token. Retrying with the old one would 401 again, and the client
    // would look like it were refreshing correctly while getting nowhere.
    const [, retryInit] = fetchMock.mock.calls[2];
    expect(retryInit.headers.Authorization).toBe("Bearer fresh");
  });

  /**
   * The single most important behaviour in this file.
   *
   * Refresh tokens rotate, and presenting one that has already been rotated away is exactly what a
   * *stolen* token looks like — the server responds by revoking the entire session. So if five
   * requests all hit a 401 at once and each fires its own refresh, four of them present a token the
   * first has already spent. The server, correctly, concludes the token was stolen and logs the user
   * out. The app would destroy its own session by being eager.
   */
  it("coalesces concurrent 401s into a single refresh", async () => {
    setAccessToken("expired");

    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
      if (url.includes("/auth/refresh")) {
        return json(200, { accessToken: "fresh" });
      }
      const authorized = (init?.headers as Record<string, string>)?.Authorization === "Bearer fresh";
      return authorized
        ? json(200, { ok: true })
        : json(401, { code: "UNAUTHORIZED", detail: "expired" });
    });

    await Promise.all([request("/a"), request("/b"), request("/c"), request("/d"), request("/e")]);

    const refreshCalls = fetchMock.mock.calls.filter(([url]) => String(url).includes("/auth/refresh"));
    expect(refreshCalls).toHaveLength(1);
  });

  it("sends the CSRF header on cookie-authenticated routes", async () => {
    fetchMock.mockResolvedValueOnce(json(204, null));

    await request("/auth/logout", { method: "POST", withCsrf: true });

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers["X-XSRF-TOKEN"]).toBe("test-csrf");
  });

  it("surfaces the server's error code and field errors", async () => {
    fetchMock.mockResolvedValueOnce(
      json(400, {
        code: "EMAIL_NOT_WORK_ADDRESS",
        detail: "Please sign up with your work email.",
        status: 400,
        fieldErrors: { email: "That doesn't look like a valid email" },
      }),
    );

    await expect(request("/auth/signup", { method: "POST", anonymous: true })).rejects.toSatisfy(
      (error: unknown) =>
        error instanceof ApiRequestError &&
        error.code === "EMAIL_NOT_WORK_ADDRESS" &&
        error.fieldErrors.email === "That doesn't look like a valid email",
    );
  });

  it("does not attempt a refresh for an anonymous request", async () => {
    // A 401 from login means the password was wrong, not that a session expired. Refreshing would be
    // nonsense, and would fire a pointless request at every failed sign-in.
    fetchMock.mockResolvedValueOnce(json(401, { code: "INVALID_CREDENTIALS", detail: "nope" }));

    await expect(request("/auth/login", { method: "POST", anonymous: true })).rejects.toThrow(
      ApiRequestError,
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("restoreSession returns null when there is no session, rather than throwing", async () => {
    // The ordinary case for a first-time visitor. Throwing here would make every cold load of the
    // login page an unhandled rejection.
    fetchMock.mockResolvedValue(json(401, { code: "REFRESH_TOKEN_INVALID", detail: "none" }));

    await expect(restoreSession()).resolves.toBeNull();
  });
});
