import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  currentAccessToken,
  pairedUser,
  renewAccessToken,
  signOut,
  storePairedSession,
} from "./extensionSessionStore";
import { LAST_PROJECT_KEY, SESSION_KEY, SETTINGS_KEY } from "./storageKeys";

/**
 * The module that holds the credential, which is the one where a mistake is expensive: a wrongly
 * cleared session costs a re-pair, and a wrongly *kept* one leaves a dead token to be replayed as
 * theft. Both distinctions are argued at length in its comments and neither was pinned by a test.
 *
 * `chrome.storage.local` is a Map behind the real shape; `fetch` is stubbed per case.
 */
const storage = new Map<string, unknown>();

function aSession(overrides: Record<string, unknown> = {}) {
  return {
    accessToken: "access-1",
    expiresIn: 900,
    refreshToken: "refresh-1",
    user: { id: "u1", fullName: "Amira Haddad", email: "amira@example.ae" },
    ...overrides,
  };
}

beforeEach(() => {
  storage.clear();
  vi.stubGlobal("chrome", {
    storage: {
      local: {
        get: async (key: string) => (storage.has(key) ? { [key]: storage.get(key) } : {}),
        set: async (entries: Record<string, unknown>) => {
          Object.entries(entries).forEach(([key, value]) => storage.set(key, value));
        },
        remove: async (keys: string[]) => keys.forEach((key) => storage.delete(key)),
      },
    },
  });
  vi.stubGlobal("fetch", vi.fn(async () => new Response(null, { status: 204 })));
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("storing a paired session", () => {
  it("keeps who it is paired as", async () => {
    await storePairedSession(aSession());

    expect((await pairedUser())?.email).toBe("amira@example.ae");
  });

  it("refuses a handover with no refresh token rather than storing half a session", async () => {
    await expect(storePairedSession(aSession({ refreshToken: "" }))).rejects.toThrow();
    expect(await pairedUser()).toBeNull();
  });

  it("ends the session it replaces, so a stale one cannot outlive the pairing", async () => {
    await storePairedSession(aSession());
    await storePairedSession(aSession({ refreshToken: "refresh-2", accessToken: "access-2" }));

    const [url, request] = vi.mocked(fetch).mock.calls[0];
    expect(String(url)).toContain("/auth/extension/logout");
    expect(JSON.parse(String(request?.body))).toEqual({ refreshToken: "refresh-1" });
  });
});

describe("renewing the access token", () => {
  it("serves the stored token while it is still good", async () => {
    await storePairedSession(aSession());

    expect(await currentAccessToken()).toBe("access-1");
    expect(fetch).not.toHaveBeenCalled();
  });

  it("refreshes a token inside its last minute, and stores the rotated one", async () => {
    await storePairedSession(aSession({ expiresIn: 30 }));
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(aSession({ accessToken: "access-2", refreshToken: "refresh-2" })), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    expect(await currentAccessToken()).toBe("access-2");
    // The rotated token, not the spent one: presenting a spent token is what the server reads as theft.
    expect(storage.get(SESSION_KEY)).toMatchObject({ refreshToken: "refresh-2" });
  });

  it("clears the pairing on a 401, because that token is dead", async () => {
    await storePairedSession(aSession());
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 401 }));

    expect(await renewAccessToken()).toBeNull();
    expect(await pairedUser()).toBeNull();
  });

  // The distinction the comment argues for: a cold-start 503 never reached the rotation, so the token
  // is still live and wiping it would send the consultant back through /extension/connect over a blip.
  it("keeps the pairing on a 503, which never spent the token", async () => {
    await storePairedSession(aSession());
    vi.mocked(fetch).mockResolvedValue(new Response(null, { status: 503 }));

    expect(await renewAccessToken()).toBeNull();
    expect(await pairedUser()).not.toBeNull();
  });

  it("dedupes concurrent renewals into one rotation", async () => {
    await storePairedSession(aSession({ expiresIn: 30 }));
    vi.mocked(fetch).mockResolvedValue(
      new Response(JSON.stringify(aSession({ accessToken: "access-2" })), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    );

    await Promise.all([renewAccessToken(), renewAccessToken(), renewAccessToken()]);

    // Three rotations would spend two tokens the server has already replaced, and the second of those
    // replays is what revokes the family.
    expect(vi.mocked(fetch).mock.calls.filter(([url]) => String(url).endsWith("/refresh"))).toHaveLength(1);
  });
});

describe("signing out", () => {
  it("takes the workspace-scoped state with it, not just the token", async () => {
    await storePairedSession(aSession());
    storage.set(LAST_PROJECT_KEY, "project-1");
    storage.set(SETTINGS_KEY, { defaultProjectId: "project-1" });

    await signOut();

    // A shared laptop: the next consultant to pair must not open the popup holding the last one's
    // mandate ids, which useProjectSelection would offer as their default.
    expect(storage.has(SESSION_KEY)).toBe(false);
    expect(storage.has(LAST_PROJECT_KEY)).toBe(false);
    expect(storage.has(SETTINGS_KEY)).toBe(false);
  });
});
