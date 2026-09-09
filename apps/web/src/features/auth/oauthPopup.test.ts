import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { readCookie } from "../../lib/cookies";
import { reportOAuthOutcome, startOAuthSignIn, takeHandshakeId } from "./oauthPopup";

const HANDSHAKE_COOKIE = "lm_oauth_popup";

/**
 * The popup handshake, which is the part of sign-in with no server to check it.
 *
 * Two properties carry the security weight and are asserted directly: an outcome is only accepted
 * from this app's own origin, and only when it quotes the handshake id this attempt minted — the
 * broadcast reaches every open tab, so without the id one tab's sign-in would resolve another's.
 */
describe("oauthPopup", () => {
  const handlers = () => ({ onSuccess: vi.fn(), onError: vi.fn(), onCancel: vi.fn() });

  let popup: { closed: boolean; close: ReturnType<typeof vi.fn> };
  let open: ReturnType<typeof vi.spyOn>;
  let assign: ReturnType<typeof vi.fn>;

  const outcomeMessage = (handshakeId: string, outcome: unknown) => ({
    message: "lightmove.oauth.outcome",
    handshakeId,
    outcome,
  });

  const deliver = (data: unknown, origin = window.location.origin) => {
    window.dispatchEvent(new MessageEvent("message", { data, origin }));
  };

  const currentHandshakeId = () => {
    const handshakeId = readCookie(HANDSHAKE_COOKIE);
    if (!handshakeId) {
      throw new Error("the attempt wrote no handshake cookie");
    }
    return handshakeId;
  };

  beforeEach(() => {
    vi.useFakeTimers();
    popup = { closed: false, close: vi.fn() };
    open = vi.spyOn(window, "open").mockReturnValue(popup as unknown as Window);

    assign = vi.fn();
    vi.spyOn(window, "location", "get").mockReturnValue({
      ...window.location,
      origin: "https://app.lightmove.test",
      assign,
    } as unknown as Location);
  });

  afterEach(() => {
    document.cookie = `${HANDSHAKE_COOKIE}=; Path=/; Max-Age=0`;
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it("opens the provider's authorisation path, which is named by the registration id", () => {
    startOAuthSignIn("linkedin", handlers());

    expect(open).toHaveBeenCalledWith(
      "/oauth2/authorization/linkedin",
      "_blank",
      expect.stringContaining("popup=yes"),
    );
  });

  it("leaves a handshake id for the popup to quote back", () => {
    startOAuthSignIn("linkedin", handlers());

    expect(currentHandshakeId()).toMatch(/^[0-9a-f]{32}$/);
  });

  it("falls back to a full page redirect when the browser blocks the popup", () => {
    open.mockReturnValue(null);
    const listeners = handlers();

    startOAuthSignIn("google", listeners);

    expect(assign).toHaveBeenCalledWith("/oauth2/authorization/google");
    // This document is on its way out; reporting an outcome into it would be reporting into nothing.
    expect(listeners.onCancel).not.toHaveBeenCalled();
    expect(listeners.onError).not.toHaveBeenCalled();
    // And no handshake is left behind to make the redirect's landing believe it is a popup.
    expect(readCookie(HANDSHAKE_COOKIE)).toBeNull();
  });

  it("reports success once the popup says so, and closes it", () => {
    const listeners = handlers();
    startOAuthSignIn("linkedin", listeners);

    deliver(outcomeMessage(currentHandshakeId(), { status: "success" }));

    expect(listeners.onSuccess).toHaveBeenCalledTimes(1);
    expect(popup.close).toHaveBeenCalled();
  });

  it("reports the refusal code the popup carries back", () => {
    const listeners = handlers();
    startOAuthSignIn("linkedin", listeners);

    deliver(outcomeMessage(currentHandshakeId(), { status: "error", code: "OAUTH_CANCELLED" }));

    expect(listeners.onError).toHaveBeenCalledWith("OAUTH_CANCELLED");
  });

  it("ignores an outcome quoting a handshake id it did not mint", () => {
    const listeners = handlers();
    startOAuthSignIn("linkedin", listeners);

    deliver(outcomeMessage("a-different-tabs-attempt", { status: "success" }));

    expect(listeners.onSuccess).not.toHaveBeenCalled();
  });

  it("ignores an outcome posted from another origin", () => {
    const listeners = handlers();
    startOAuthSignIn("linkedin", listeners);

    deliver(outcomeMessage(currentHandshakeId(), { status: "success" }), "https://evil.example");

    expect(listeners.onSuccess).not.toHaveBeenCalled();
  });

  it("ignores a payload that is not an outcome at all", () => {
    const listeners = handlers();
    startOAuthSignIn("linkedin", listeners);

    deliver("lightmove.oauth.outcome");
    deliver({ message: "lightmove.oauth.outcome", handshakeId: currentHandshakeId() });

    expect(listeners.onSuccess).not.toHaveBeenCalled();
    expect(listeners.onError).not.toHaveBeenCalled();
  });

  it("treats a popup closed without an answer as a cancellation, and says nothing else", () => {
    const listeners = handlers();
    startOAuthSignIn("linkedin", listeners);

    popup.closed = true;
    vi.advanceTimersByTime(500);

    expect(listeners.onCancel).toHaveBeenCalledTimes(1);
    expect(listeners.onError).not.toHaveBeenCalled();
  });

  it("delivers one outcome only, however many arrive", () => {
    const listeners = handlers();
    startOAuthSignIn("linkedin", listeners);
    const handshakeId = currentHandshakeId();

    deliver(outcomeMessage(handshakeId, { status: "success" }));
    deliver(outcomeMessage(handshakeId, { status: "success" }));
    popup.closed = true;
    vi.advanceTimersByTime(500);

    expect(listeners.onSuccess).toHaveBeenCalledTimes(1);
    expect(listeners.onCancel).not.toHaveBeenCalled();
  });

  it("stops listening once the attempt is abandoned", () => {
    const listeners = handlers();
    const abandon = startOAuthSignIn("linkedin", listeners);
    const handshakeId = currentHandshakeId();

    abandon();
    deliver(outcomeMessage(handshakeId, { status: "success" }));
    popup.closed = true;
    vi.advanceTimersByTime(500);

    expect(listeners.onSuccess).not.toHaveBeenCalled();
    expect(listeners.onCancel).not.toHaveBeenCalled();
  });

  it("gives up on an attempt nobody ever finishes, rather than waiting forever", () => {
    const listeners = handlers();
    startOAuthSignIn("linkedin", listeners);

    vi.advanceTimersByTime(11 * 60 * 1000);

    expect(listeners.onCancel).toHaveBeenCalledTimes(1);
  });

  describe("the popup's side", () => {
    it("consumes the handshake id, so the next redirect sign-in is not mistaken for a popup", () => {
      startOAuthSignIn("linkedin", handlers());

      expect(takeHandshakeId()).toMatch(/^[0-9a-f]{32}$/);
      expect(takeHandshakeId()).toBeNull();
    });

    it("posts the outcome to the opener, pinned to this exact origin", () => {
      const postMessage = vi.fn();
      vi.stubGlobal("opener", { postMessage });

      reportOAuthOutcome("handshake-1", { status: "error", code: "OAUTH_CANCELLED" });

      expect(postMessage).toHaveBeenCalledWith(
        outcomeMessage("handshake-1", { status: "error", code: "OAUTH_CANCELLED" }),
        // A wildcard here would hand the outcome to whatever page happens to be the opener.
        "https://app.lightmove.test",
      );
      vi.unstubAllGlobals();
    });

    it("still reports when the opener link has been severed", () => {
      vi.stubGlobal("opener", {
        postMessage: () => {
          throw new Error("opener severed by Cross-Origin-Opener-Policy");
        },
      });

      expect(() => reportOAuthOutcome("handshake-1", { status: "success" })).not.toThrow();
      vi.unstubAllGlobals();
    });
  });
});
