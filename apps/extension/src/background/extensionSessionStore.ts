import type { ExtensionSession, WorkspaceUser } from "../api/types";
import { workspaceOrigin } from "../workspaceOrigin";
import { SESSION_KEY, WORKSPACE_SCOPED_KEYS } from "./storageKeys";

/**
 * The paired session, at rest and on demand. The only module that holds a credential, and it runs in
 * the service worker alone; the access token is stored beside the refresh token rather than kept in
 * memory, because the worker is killed between events and every open would otherwise spend a rotation.
 */

/** Refreshed this long before expiry, so a request never races its own token running out. */
const RENEW_BEFORE_EXPIRY_MS = 60_000;

interface StoredSession {
  refreshToken: string;
  accessToken: string;
  accessTokenExpiresAt: number;
  user: WorkspaceUser;
}

/** Deduped within one service-worker lifetime; see the class note on why rotations are worth avoiding. */
let renewalInFlight: Promise<string | null> | null = null;

async function read(): Promise<StoredSession | null> {
  const stored = await chrome.storage.local.get(SESSION_KEY);
  return (stored[SESSION_KEY] as StoredSession | undefined) ?? null;
}

async function write(session: ExtensionSession): Promise<StoredSession> {
  const stored: StoredSession = {
    refreshToken: session.refreshToken,
    accessToken: session.accessToken,
    accessTokenExpiresAt: Date.now() + session.expiresIn * 1000,
    user: session.user,
  };
  await chrome.storage.local.set({ [SESSION_KEY]: stored });
  return stored;
}

async function clear(): Promise<void> {
  await chrome.storage.local.remove(WORKSPACE_SCOPED_KEYS);
}

/** Who the extension is paired as, or null if it is not paired. */
export async function pairedUser(): Promise<WorkspaceUser | null> {
  return (await read())?.user ?? null;
}

/**
 * Stores the session the workspace's connect page handed over.
 *
 * Validated here rather than trusted, even though the sender was already checked: this is the one
 * place a credential enters the extension, and a shape check at the boundary it is stored at cannot
 * be bypassed by a future second caller.
 */
export async function storePairedSession(session: ExtensionSession): Promise<void> {
  if (!isUsableSession(session)) {
    throw new Error("The workspace offered a session with no refresh token.");
  }
  // Belt to the server's braces: pairing revokes the account's live extension families anyway, but a
  // session stored here that the workspace never issued — a stale handover, a restored profile — is
  // one only this side knows about.
  await revokeStoredSession();
  await write(session);
}

/**
 * Ends the stored session server-side, best-effort.
 *
 * Never throws. Both callers have already done, or are about to do, the thing the consultant asked
 * for — signing out locally, or storing a new pairing — and neither should fail because the workspace
 * was unreachable. An unrevoked token expires on its own.
 */
async function revokeStoredSession(): Promise<void> {
  const previous = await read();
  if (!previous) {
    return;
  }
  try {
    await fetch(`${workspaceOrigin}/api/v1/auth/extension/logout`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "omit",
      body: JSON.stringify({ refreshToken: previous.refreshToken }),
    });
  } catch {
    // See above: unreachable workspace, or a token already dead.
  }
}

function isUsableSession(session: ExtensionSession | null | undefined): session is ExtensionSession {
  return Boolean(
    session
      && typeof session.refreshToken === "string" && session.refreshToken.length > 0
      && typeof session.accessToken === "string" && session.accessToken.length > 0
      && typeof session.expiresIn === "number"
      // `user` reaches CaptureHeader unguarded, and initialsOf throws on a missing name.
      && typeof session.user?.fullName === "string" && session.user.fullName.length > 0,
  );
}

/** The token to send now — refreshing first if the stored one is spent or nearly so. */
export async function currentAccessToken(): Promise<string | null> {
  const session = await read();
  if (!session) {
    return null;
  }
  if (session.accessTokenExpiresAt - RENEW_BEFORE_EXPIRY_MS > Date.now()) {
    return session.accessToken;
  }
  return renewAccessToken();
}

/**
 * Spends the refresh token for a new session and stores the rotated one.
 *
 * The old token is dead the moment the server answers, so the new one is written before anything else
 * happens. A failure means the session is over — the stored one is cleared rather than left to be
 * replayed, which the server would correctly read as theft and which would revoke the family for real.
 */
export function renewAccessToken(): Promise<string | null> {
  renewalInFlight ??= (async () => {
    try {
      const session = await read();
      if (!session) {
        return null;
      }
      const response = await fetch(`${workspaceOrigin}/api/v1/auth/extension/refresh`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        credentials: "omit",
        body: JSON.stringify({ refreshToken: session.refreshToken }),
      });
      // 401 only, and the narrowness is the point. That is the answer meaning the token is dead —
      // revoked, rotated away, logged out — and clearing is right. Every other non-2xx is the server
      // having a bad moment: a Cloud Run cold-start 503, a proxy 502, a 500. The token was never
      // spent in those cases, because the server never got far enough to rotate it, so wiping the
      // pairing would discard a live credential over a blip and send the consultant back through
      // /extension/connect. Returning null leaves the session alone and the next open retries.
      if (response.status === 401) {
        await clear();
        return null;
      }
      if (!response.ok) {
        return null;
      }
      const renewed = (await response.json()) as ExtensionSession;
      return (await write(renewed)).accessToken;
    } finally {
      renewalInFlight = null;
    }
  })();
  return renewalInFlight;
}

/**
 * Ends the extension's session. The workspace's own session in the browser is untouched — they are
 * separate token families precisely so that signing out of one is not signing out of the other.
 */
export async function signOut(): Promise<void> {
  await revokeStoredSession();
  await clear();
}
