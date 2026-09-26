import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  ApiRequestError,
  onSessionExpired,
  onWorkspaceMoved,
  request,
  restoreSession,
  setAccessToken,
  switchWorkspaceSession,
} from "./apiClient";

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

  /**
   * The same failure, one scope wider — and it is not hypothetical: it signed a real session out during
   * local testing.
   *
   * The in-module guard above dedupes refreshes within one page. A second *tab* is a second JS context
   * with its own module state and the same cookie jar, so both tabs boot, both read the same refresh
   * cookie, and both refresh. One wins; the other presents a token that was rotated away a few
   * milliseconds earlier, the server reads that as a stolen token being replayed, and revokes the whole
   * family. Opening a second tab logged you out of both.
   *
   * Web Locks are cross-tab, which is the scope that matters. This asserts the refresh actually goes
   * through one — a lock nobody takes is decoration.
   */
  it("takes a cross-tab lock before refreshing, so a second tab cannot trip theft detection", async () => {
    const held: string[] = [];
    const locks = {
      request: vi.fn(async (name: string, work: () => Promise<unknown>) => {
        held.push(name);
        return work();
      }),
    };
    vi.stubGlobal("navigator", { ...navigator, locks });

    setAccessToken("expired");
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
      if (url.includes("/auth/refresh")) {
        return json(200, { accessToken: "fresh" });
      }
      const authorized = (init?.headers as Record<string, string>)?.Authorization === "Bearer fresh";
      return authorized ? json(200, { ok: true }) : json(401, { code: "UNAUTHORIZED", detail: "expired" });
    });

    await request("/a");

    expect(locks.request).toHaveBeenCalledOnce();
    expect(held).toEqual(["lm-refresh"]);

    vi.unstubAllGlobals();
  });

  it("sends the CSRF header on cookie-authenticated routes", async () => {
    fetchMock.mockResolvedValueOnce(json(204, null));

    await request("/auth/logout", { method: "POST", withCsrf: true });

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers["X-XSRF-TOKEN"]).toBe("test-csrf");
  });

  /**
   * The QA report that prompted this: a 503 on GET /auth/csrf. Nothing in the API can answer 503, so
   * the cause was upstream — but the *consequence* was ours. A csrf request that does not land leaves
   * the cookie unset, so the header goes unsent, so the refresh is refused; and a refused refresh is
   * how this client decides a session is over. A blip on the one request whose whole job is to be
   * retried used to sign the user out.
   */
  it("recovers when the CSRF request fails, rather than losing the session", async () => {
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";

    let csrfCalls = 0;
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
      if (url.includes("/auth/csrf")) {
        csrfCalls += 1;
        // Down on the first ask, up on the second — the transient failure the report describes.
        if (csrfCalls === 1) return json(503, null);
        document.cookie = "XSRF-TOKEN=fresh-csrf";
        return json(204, null);
      }
      const sent = (init?.headers as Record<string, string>)?.["X-XSRF-TOKEN"];
      return sent
        ? json(200, { accessToken: "fresh" })
        : json(403, { code: "CSRF_TOKEN_INVALID", detail: "invalid token", status: 403 });
    });

    await expect(restoreSession()).resolves.toBe("fresh");
    expect(csrfCalls).toBe(2);
  });

  /** One retry, not a loop: a token refused twice is a genuine refusal and must reach the caller. */
  it("gives up after one CSRF retry", async () => {
    fetchMock.mockImplementation(async (url: string) =>
      url.includes("/auth/csrf")
        ? json(204, null)
        : json(403, { code: "CSRF_TOKEN_INVALID", detail: "invalid token", status: 403 }),
    );

    await expect(request("/auth/logout", { method: "POST", withCsrf: true })).rejects.toThrow(
      ApiRequestError,
    );

    const logoutCalls = fetchMock.mock.calls.filter(([url]) => String(url).includes("/auth/logout"));
    expect(logoutCalls).toHaveLength(2);
  });

  /** A 403 that is not a CSRF refusal is a permissions answer, and must not provoke a retry. */
  it("does not retry a 403 that is an ordinary refusal", async () => {
    fetchMock.mockResolvedValue(json(403, { code: "FORBIDDEN", detail: "nope", status: 403 }));

    await expect(request("/auth/logout", { method: "POST", withCsrf: true })).rejects.toSatisfy(
      (error: unknown) => error instanceof ApiRequestError && error.code === "FORBIDDEN",
    );
    expect(fetchMock).toHaveBeenCalledTimes(1);
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

  describe("switchWorkspaceSession", () => {
    const session = (accessToken: string) => ({ accessToken, expiresIn: 900, user: { id: "u1" } });

    it("sends the bearer under the cross-tab lock", async () => {
      const locks = { request: vi.fn(async (_name: string, work: () => Promise<unknown>) => work()) };
      vi.stubGlobal("navigator", { ...navigator, locks });
      setAccessToken("current");
      fetchMock.mockResolvedValueOnce(json(200, session("in-w2")));

      await switchWorkspaceSession("w2");

      expect(locks.request).toHaveBeenCalledWith("lm-refresh", expect.any(Function));
      const [url, init] = fetchMock.mock.calls[0];
      expect(url).toBe("/api/v1/auth/switch-workspace");
      expect(init.headers.Authorization).toBe("Bearer current");
      expect(JSON.parse(init.body)).toEqual({ workspaceId: "w2" });
    });

    it("renews an expired token inside the lock and switches once more", async () => {
      const locks = { request: vi.fn(async (_name: string, work: () => Promise<unknown>) => work()) };
      vi.stubGlobal("navigator", { ...navigator, locks });
      setAccessToken("expired");
      fetchMock
        .mockResolvedValueOnce(json(401, { code: "UNAUTHORIZED", detail: "expired" }))
        .mockResolvedValueOnce(json(200, { accessToken: "fresh" }))
        .mockResolvedValueOnce(json(200, session("in-w2")));

      await expect(switchWorkspaceSession("w2")).resolves.toMatchObject({ accessToken: "in-w2" });

      // One lock, not two: taking it again from inside would wait on itself.
      expect(locks.request).toHaveBeenCalledOnce();
      expect(fetchMock.mock.calls[1][0]).toBe("/api/v1/auth/refresh");
      expect(fetchMock.mock.calls[2][1].headers.Authorization).toBe("Bearer fresh");
    });

    it("leaves the session alone when the switch is refused", async () => {
      const lost = vi.fn();
      onSessionExpired(lost);
      setAccessToken("current");
      fetchMock
        .mockResolvedValueOnce(json(404, { code: "NOT_A_MEMBER", detail: "Workspace not found" }))
        .mockResolvedValueOnce(json(200, { ok: true }));

      await expect(switchWorkspaceSession("w9")).rejects.toSatisfy(
        (error: unknown) => error instanceof ApiRequestError && error.code === "NOT_A_MEMBER",
      );
      expect(lost).not.toHaveBeenCalled();

      await request("/after");
      expect(fetchMock.mock.calls[1][1].headers.Authorization).toBe("Bearer current");
    });
  });

  describe("a refresh that lands in another workspace", () => {
    const tokenIn = (workspaceId: string) =>
      `h.${btoa(JSON.stringify({ sub: "u1", wsId: workspaceId })).replace(/=+$/, "")}.s`;

    it("is reported, with the user it now belongs to", async () => {
      const moved = vi.fn();
      onWorkspaceMoved(moved);
      setAccessToken(tokenIn("w1"));
      fetchMock
        .mockResolvedValueOnce(json(401, { code: "UNAUTHORIZED", detail: "expired" }))
        .mockResolvedValueOnce(json(200, { accessToken: tokenIn("w2"), user: { id: "u1", workspace: { id: "w2" } } }))
        .mockResolvedValueOnce(json(200, { ok: true }));

      await request("/a");

      expect(moved).toHaveBeenCalledWith({ id: "u1", workspace: { id: "w2" } });
    });

    it("is not reported when the session stays put, or had no workspace to leave", async () => {
      const moved = vi.fn();
      onWorkspaceMoved(moved);
      setAccessToken(tokenIn("w1"));
      fetchMock.mockResolvedValueOnce(json(200, { accessToken: tokenIn("w1"), user: { id: "u1" } }));
      await restoreSession();

      setAccessToken(null);
      fetchMock.mockResolvedValueOnce(json(200, { accessToken: tokenIn("w2"), user: { id: "u1" } }));
      await restoreSession();

      expect(moved).not.toHaveBeenCalled();
    });
  });
});
