import { deleteCookie, readCookie, writeCookie } from "../../lib/cookies";

/**
 * Provider sign-in in a popup, falling back to a redirect when blocked; both handshake ends live here so they agree.
 * Only an outcome crosses, never a credential (the opener mints from the refresh cookie; the fragment's token is dropped).
 * Sent on postMessage and a BroadcastChannel (a COOP can sever `window.opener`); the handshake id ties each to its attempt.
 */

/** Identifies our messages on a channel we do not exclusively own. */
const OAUTH_OUTCOME_MESSAGE = "lightmove.oauth.outcome";

const BROADCAST_CHANNEL_NAME = "lightmove.oauth";

/**
 * The popup's handshake nonce, kept twice: `sessionStorage` (inherited by the popup and per tab, so concurrent tabs don't
 * overwrite each other as a per-origin cookie would) and this cookie, the fallback when a COOP swaps the browsing context.
 */
const HANDSHAKE_COOKIE = "lm_oauth_popup";
const HANDSHAKE_STORAGE_KEY = "lightmove.oauth.handshake";

/** Long enough to read a consent screen and type a password; short enough that an abandoned attempt expires. */
const HANDSHAKE_TTL_SECONDS = 600;

/** Nothing fires when someone closes a popup, so the only way to notice is to look. */
const ABANDONMENT_POLL_INTERVAL_MS = 400;

/** An attempt nobody finished. Releases the button rather than leaving the app waiting forever. */
const ATTEMPT_TIMEOUT_MS = 10 * 60 * 1000;

const POPUP_WIDTH = 500;
const POPUP_HEIGHT = 640;

/**
 * What the popup reports back. `code` is an `ErrorCode` name the SPA maps to a sentence; `secondFactor` is a person
 * with two-factor on, whose challenge cookie the server set in place of a session.
 */
export type OAuthOutcome = { status: "success" } | { status: "secondFactor" } | { status: "error"; code: string };

interface OAuthOutcomeMessage {
  message: typeof OAUTH_OUTCOME_MESSAGE;
  handshakeId: string;
  outcome: OAuthOutcome;
}

export interface OAuthSignInHandlers {
  /** The provider signed the user in. The session is established server-side; adopt it. */
  onSuccess: () => void;
  /** The provider proved who they are, and their code is still owed: on to the code step, as after a password. */
  onSecondFactor: () => void;
  /** The provider or our own checks refused. `code` is an `ErrorCode` name. */
  onError: (code: string) => void;
  /** The user backed out, or walked away. Not an error — say nothing and release the button. */
  onCancel: () => void;
}

/** Where a sign-in with this provider begins. Spring owns the path, one per configured registration id. */
export function authorizationUrl(providerId: string): string {
  return `/oauth2/authorization/${encodeURIComponent(providerId)}`;
}

/**
 * Opens the provider's consent popup; returns an abandon function to call on unmount. A blocked popup falls back to a
 * full-page redirect, and then no handler is ever called.
 */
export function startOAuthSignIn(providerId: string, handlers: OAuthSignInHandlers): () => void {
  const url = authorizationUrl(providerId);
  const handshakeId = mintHandshakeId();

  // Written before the window is opened, and it has to be: a popup inherits its opener's
  // sessionStorage as it is created, so an id written afterwards would never reach it.
  rememberHandshake(handshakeId);

  // Synchronously, in the click that called us. Anything awaited first — even a resolved promise —
  // breaks the user-gesture tie and the popup is blocked.
  const popup = window.open(url, "_blank", popupFeatures());

  if (!popup) {
    // Nothing must be left telling the redirect's landing page it is a popup — it would close the
    // user's own tab instead of signing them in.
    forgetHandshake();
    window.location.assign(url);
    return () => {};
  }

  return listenForOutcome(popup, handshakeId, handlers);
}

/**
 * Subscribes to both channels, watches for the popup being closed, and gives up after
 * {@link ATTEMPT_TIMEOUT_MS}. Every path runs through `settle`, so a second outcome — a broadcast
 * arriving just after the closed-window poll fired, say — cannot deliver a handler twice.
 */
function listenForOutcome(popup: Window, handshakeId: string, handlers: OAuthSignInHandlers): () => void {
  const broadcast = openBroadcastChannel();
  let settled = false;

  const stop = () => {
    window.removeEventListener("message", onWindowMessage);
    broadcast?.close();
    window.clearInterval(abandonmentPoll);
    window.clearTimeout(timeout);
  };

  const settle = (deliver: () => void) => {
    if (settled) {
      return;
    }
    settled = true;
    stop();

    // The attempt is over, so nothing may still answer for it. A handshake left behind outlives its
    // popup for the rest of its TTL, and the next top-level arrival at the callback route — a
    // redirect sign-in, a popup the browser turned into an ordinary tab — would read it, believe it
    // is a popup, and close the user's real tab without ever establishing the session.
    forgetHandshake();

    closeQuietly(popup);
    deliver();
  };

  const accept = (data: unknown) => {
    const outcome = outcomeFrom(data, handshakeId);
    if (!outcome) {
      return;
    }
    settle(() => {
      if (outcome.status === "success") handlers.onSuccess();
      else if (outcome.status === "secondFactor") handlers.onSecondFactor();
      else handlers.onError(outcome.code);
    });
  };

  const onWindowMessage = (event: MessageEvent) => {
    // Two checks, and the second is the exact one. The popup lands back on our own origin, so
    // anything from elsewhere is not ours to read; and `source` is the window we opened, which no
    // other tab's popup and no other attempt can impersonate. The id `accept` goes on to check adds
    // nothing here — it is what the broadcast channel, which carries no source, has to rely on.
    if (event.origin === window.location.origin && event.source === popup) {
      accept(event.data);
    }
  };

  window.addEventListener("message", onWindowMessage);
  if (broadcast) {
    broadcast.onmessage = (event) => accept(event.data);
  }

  const abandonmentPoll = window.setInterval(() => {
    if (isClosed(popup)) {
      settle(handlers.onCancel);
    }
  }, ABANDONMENT_POLL_INTERVAL_MS);

  const timeout = window.setTimeout(() => settle(handlers.onCancel), ATTEMPT_TIMEOUT_MS);

  return () => {
    settled = true;
    stop();
  };
}

/**
 * The handshake id this document was opened under, consumed (a leftover would make the next redirect sign-in think it is
 * a popup). The tab-scoped copy first, the cookie when the context was swapped; null for an ordinary redirect sign-in.
 */
export function takeHandshakeId(): string | null {
  const handshakeId = readSessionValue(HANDSHAKE_STORAGE_KEY) ?? readCookie(HANDSHAKE_COOKIE);
  forgetHandshake();
  return handshakeId || null;
}

/**
 * Whether this document is a sign-in popup back from the provider, read without consuming the
 * handshake. Only the callback path counts: the opener holds the same handshake for the whole attempt.
 */
export function isReturningSignInPopup(): boolean {
  const handshakeId = readSessionValue(HANDSHAKE_STORAGE_KEY) ?? readCookie(HANDSHAKE_COOKIE);
  return window.location.pathname === "/auth/callback" && Boolean(handshakeId);
}

/** Records an attempt in both carriers. See {@link HANDSHAKE_COOKIE} for why it takes two. */
function rememberHandshake(handshakeId: string): void {
  writeSessionValue(HANDSHAKE_STORAGE_KEY, handshakeId);
  writeCookie(HANDSHAKE_COOKIE, handshakeId, HANDSHAKE_TTL_SECONDS);
}

/** Leaves nothing that could make a later document believe it is answering for an attempt. */
function forgetHandshake(): void {
  removeSessionValue(HANDSHAKE_STORAGE_KEY);
  deleteCookie(HANDSHAKE_COOKIE);
}

/**
 * Reports the outcome to the tab that opened this popup, on both channels.
 *
 * `postMessage` is pinned to this exact origin. A wildcard target would hand the outcome to whatever
 * page happens to be the opener, which is a real leak the moment this app is ever framed.
 */
export function reportOAuthOutcome(handshakeId: string, outcome: OAuthOutcome): void {
  const message: OAuthOutcomeMessage = { message: OAUTH_OUTCOME_MESSAGE, handshakeId, outcome };

  try {
    window.opener?.postMessage(message, window.location.origin);
  } catch {
    // A severed opener is exactly what the broadcast below is for.
  }

  const broadcast = openBroadcastChannel();
  try {
    broadcast?.postMessage(message);
  } finally {
    broadcast?.close();
  }
}

/** Narrows an untrusted payload to an outcome this attempt asked for. */
function outcomeFrom(data: unknown, expectedHandshakeId: string): OAuthOutcome | null {
  if (typeof data !== "object" || data === null) {
    return null;
  }

  const { message, handshakeId, outcome } = data as Partial<OAuthOutcomeMessage>;

  // The id is what stops a second tab's sign-in resolving this one's: every open tab of this app
  // receives the broadcast, only the tab that minted the id issued the request.
  if (message !== OAUTH_OUTCOME_MESSAGE || handshakeId !== expectedHandshakeId) {
    return null;
  }

  if (outcome?.status === "success") {
    return { status: "success" };
  }
  if (outcome?.status === "secondFactor") {
    return { status: "secondFactor" };
  }
  if (outcome?.status === "error" && typeof outcome.code === "string") {
    return { status: "error", code: outcome.code };
  }
  return null;
}

/**
 * Storage can be refused outright — private mode, a browser set to block site data — and there the
 * accessor itself throws. A refused read is simply "no tab-scoped copy", which falls through to the
 * cookie; a refused write costs the tab-scoping and nothing else. Neither may take sign-in down.
 */
function readSessionValue(key: string): string | null {
  try {
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeSessionValue(key: string, value: string): void {
  try {
    sessionStorage.setItem(key, value);
  } catch {
    // The cookie still carries the attempt.
  }
}

function removeSessionValue(key: string): void {
  try {
    sessionStorage.removeItem(key);
  } catch {
    // Nothing was stored, or storage is refused. The cookie is cleared either way.
  }
}

function openBroadcastChannel(): BroadcastChannel | null {
  return typeof BroadcastChannel === "undefined" ? null : new BroadcastChannel(BROADCAST_CHANNEL_NAME);
}

/**
 * `closed` and `close()` both read the opener link, which a `Cross-Origin-Opener-Policy` in the round
 * trip may have severed — the access then throws, or `closed` reads false forever. Treating that as
 * "not closed" is the safe answer: the timeout still releases the button, and the broadcast channel
 * still carries a real outcome.
 */
function isClosed(popup: Window): boolean {
  try {
    return popup.closed;
  } catch {
    return false;
  }
}

function closeQuietly(popup: Window): void {
  try {
    popup.close();
  } catch {
    // Already gone, or out of reach. Either way there is nothing to do about it.
  }
}

/**
 * Centred on the window the user is actually looking at, not on the primary display —
 * `screenX`/`outerWidth` are what make that right on a second monitor.
 */
function popupFeatures(): string {
  const left = Math.round(window.screenX + Math.max(0, (window.outerWidth - POPUP_WIDTH) / 2));
  const top = Math.round(window.screenY + Math.max(0, (window.outerHeight - POPUP_HEIGHT) / 2));

  return `popup=yes,width=${POPUP_WIDTH},height=${POPUP_HEIGHT},left=${left},top=${top}`;
}

/**
 * `crypto.randomUUID` is unavailable outside a secure context; `getRandomValues` is not. This is a
 * correlation id rather than a secret, but it is generated the same way regardless — guessing it
 * should not be a way to interfere with someone else's sign-in.
 */
function mintHandshakeId(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}
