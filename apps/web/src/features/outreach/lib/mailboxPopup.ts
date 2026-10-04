import { deleteCookie, readCookie, writeCookie } from "../../../lib/cookies";
import { codeOf } from "../../../lib/errorCodes";

/**
 * Connecting a mailbox in a popup, both ends of it — `features/auth/oauthPopup.ts`'s shape, for its
 * reasons, with one difference: the consent screen's address comes from the server (it carries a
 * state the server minted), so the popup is opened blank in the click and pointed there once the
 * address arrives. Anything awaited before `window.open` would get the popup blocked.
 *
 * Only an outcome crosses between the windows. The server has already redeemed the code and stored
 * the mailbox by the time the popup lands, so the opener simply reads its mailbox again.
 */

const OUTCOME_MESSAGE = "lightmove.mailbox.outcome";
const BROADCAST_CHANNEL_NAME = "lightmove.mailbox";

/**
 * Marks the popup as one, in the two carriers `oauthPopup.ts` explains: sessionStorage (inherited by
 * the popup, per attempt) and a cookie (survives a browsing-context swap on the way back).
 */
const HANDSHAKE_COOKIE = "lm_mailbox_popup";
const HANDSHAKE_STORAGE_KEY = "lightmove.mailbox.handshake";
const RETURN_TO_STORAGE_KEY = "lightmove.mailbox.returnTo";
const HANDSHAKE_TTL_SECONDS = 600;

export const MAILBOX_CALLBACK_PATH = "/outreach/mailbox/callback";

const ABANDONMENT_POLL_INTERVAL_MS = 400;
const ATTEMPT_TIMEOUT_MS = 10 * 60 * 1000;
const POPUP_WIDTH = 520;
const POPUP_HEIGHT = 680;

/** `code` is an `ErrorCode` name the SPA maps to a sentence. */
export type MailboxOutcome = { status: "connected" } | { status: "error"; code: string };

interface MailboxOutcomeMessage {
  message: typeof OUTCOME_MESSAGE;
  handshakeId: string;
  outcome: MailboxOutcome;
}

export interface MailboxConnectHandlers {
  onConnected: () => void;
  onError: (code: string) => void;
  /** Backed out or walked away: not an error, so say nothing. */
  onCancel: () => void;
}

/**
 * Runs one connection attempt. Call synchronously in the click. Returns a function that abandons the
 * attempt, for an unmounting page.
 *
 * Falls back to a full-page redirect when the popup is blocked; the landing page then sends the
 * browser back to where it started.
 */
export function connectMailboxInPopup(
  authorizationUrl: () => Promise<string>,
  handlers: MailboxConnectHandlers,
): () => void {
  const handshakeId = mintHandshakeId();
  rememberHandshake(handshakeId);
  const popup = window.open("about:blank", "_blank", popupFeatures());
  let abandon = () => {};
  let isAbandoned = false;

  void authorizationUrl().then(
    (url) => {
      if (isAbandoned) {
        closeQuietly(popup);
        return;
      }
      if (!popup) {
        forgetHandshake();
        rememberReturnTo();
        window.location.assign(url);
        return;
      }
      popup.location.href = url;
      abandon = listenForOutcome(popup, handshakeId, handlers);
    },
    (error: unknown) => {
      forgetHandshake();
      closeQuietly(popup);
      if (!isAbandoned) {
        handlers.onError(codeOf(error) ?? "MAILBOX_CONNECT_FAILED");
      }
    },
  );

  return () => {
    isAbandoned = true;
    abandon();
  };
}

function listenForOutcome(popup: Window, handshakeId: string, handlers: MailboxConnectHandlers): () => void {
  const broadcast = openBroadcastChannel();
  let isSettled = false;

  const stop = () => {
    window.removeEventListener("message", onWindowMessage);
    broadcast?.close();
    window.clearInterval(abandonmentPoll);
    window.clearTimeout(timeout);
  };

  const settle = (deliver: () => void) => {
    if (isSettled) {
      return;
    }
    isSettled = true;
    stop();
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
      if (outcome.status === "connected") {
        handlers.onConnected();
      } else if (outcome.code === "MAILBOX_CONNECT_CANCELLED" || outcome.code === "ZOOM_CONNECT_CANCELLED") {
        handlers.onCancel();
      } else {
        handlers.onError(outcome.code);
      }
    });
  };

  const onWindowMessage = (event: MessageEvent) => {
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
    isSettled = true;
    stop();
  };
}

/**
 * Whether this document is the connect popup back from the provider, read without consuming the
 * handshake. `AuthProvider` asks it at boot: a popup that restored the session would rotate the
 * refresh token and close before the opener saw the new one, and the opener's next refresh would
 * read as token theft.
 */
export function isReturningMailboxPopup(): boolean {
  const handshakeId = readSessionValue(HANDSHAKE_STORAGE_KEY) ?? readCookie(HANDSHAKE_COOKIE);
  return window.location.pathname === MAILBOX_CALLBACK_PATH && Boolean(handshakeId);
}

/** The handshake this popup was opened under, consumed. Null for a full-page connection. */
export function takeMailboxHandshake(): string | null {
  const handshakeId = readSessionValue(HANDSHAKE_STORAGE_KEY) ?? readCookie(HANDSHAKE_COOKIE);
  forgetHandshake();
  return handshakeId || null;
}

/** Where a full-page connection started, consumed. */
export function takeMailboxReturnTo(): string | null {
  const returnTo = readSessionValue(RETURN_TO_STORAGE_KEY);
  removeSessionValue(RETURN_TO_STORAGE_KEY);
  return returnTo && returnTo.startsWith("/") && !returnTo.startsWith("//") ? returnTo : null;
}

export function reportMailboxOutcome(handshakeId: string, outcome: MailboxOutcome): void {
  const message: MailboxOutcomeMessage = { message: OUTCOME_MESSAGE, handshakeId, outcome };
  try {
    window.opener?.postMessage(message, window.location.origin);
  } catch {
    // A severed opener is what the broadcast is for.
  }
  const broadcast = openBroadcastChannel();
  try {
    broadcast?.postMessage(message);
  } finally {
    broadcast?.close();
  }
}

function outcomeFrom(data: unknown, expectedHandshakeId: string): MailboxOutcome | null {
  if (typeof data !== "object" || data === null) {
    return null;
  }
  const { message, handshakeId, outcome } = data as Partial<MailboxOutcomeMessage>;
  if (message !== OUTCOME_MESSAGE || handshakeId !== expectedHandshakeId) {
    return null;
  }
  if (outcome?.status === "connected") {
    return { status: "connected" };
  }
  if (outcome?.status === "error" && typeof outcome.code === "string") {
    return { status: "error", code: outcome.code };
  }
  return null;
}

function rememberHandshake(handshakeId: string): void {
  writeSessionValue(HANDSHAKE_STORAGE_KEY, handshakeId);
  writeCookie(HANDSHAKE_COOKIE, handshakeId, HANDSHAKE_TTL_SECONDS);
}

function forgetHandshake(): void {
  removeSessionValue(HANDSHAKE_STORAGE_KEY);
  deleteCookie(HANDSHAKE_COOKIE);
}

function rememberReturnTo(): void {
  writeSessionValue(RETURN_TO_STORAGE_KEY, window.location.pathname + window.location.search);
}

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
    // Nothing stored, or storage refused.
  }
}

function openBroadcastChannel(): BroadcastChannel | null {
  return typeof BroadcastChannel === "undefined" ? null : new BroadcastChannel(BROADCAST_CHANNEL_NAME);
}

function isClosed(popup: Window): boolean {
  try {
    return popup.closed;
  } catch {
    return false;
  }
}

function closeQuietly(popup: Window | null): void {
  try {
    popup?.close();
  } catch {
    // Already gone, or out of reach.
  }
}

function popupFeatures(): string {
  const left = Math.round(window.screenX + Math.max(0, (window.outerWidth - POPUP_WIDTH) / 2));
  const top = Math.round(window.screenY + Math.max(0, (window.outerHeight - POPUP_HEIGHT) / 2));
  return `popup=yes,width=${POPUP_WIDTH},height=${POPUP_HEIGHT},left=${left},top=${top}`;
}

function mintHandshakeId(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, "0")).join("");
}
