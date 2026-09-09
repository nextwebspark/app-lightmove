import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { homeFor } from "../../../app/routes";
import { Logo } from "../../../components/ui";
import { setAccessToken } from "../../../lib/apiClient";
import { useAuth } from "../AuthProvider";
import * as authApi from "../api/authApi";
import { type OAuthOutcome, reportOAuthOutcome, takeHandshakeId } from "../oauthPopup";
import { takeReturnTo } from "../returnTo";

/**
 * Where an identity provider sends the browser back to — for both outcomes, and for both ways of
 * running the flow.
 *
 * **In a popup** this page is a bridge and nothing else: it reports the outcome to the tab that
 * opened it and closes itself. It deliberately does not establish the session — the opener does
 * that, because the opener is the tab that stays.
 *
 * **In a full-page redirect** it does what it always has. The server has already minted our tokens
 * by this point: the refresh token arrived as an httpOnly cookie, and the access token is in the URL
 * **fragment**. The fragment matters. A query string is sent to the server, lands in access logs,
 * leaks through the Referer header, and syncs into browser history. A fragment does none of that —
 * it never leaves the browser. We read it, put it in memory, and scrub it from the address bar
 * immediately.
 *
 * A refusal arrives here as `?error=` rather than at `/login`, so that this one route owns both
 * outcomes: a failure sent straight to the login screen would render it inside a 500×640 popup.
 * Outside a popup this page forwards to `/login?error=` and the user sees what they always saw.
 */
export function OAuthCallbackPage() {
  const navigate = useNavigate();
  const { adopt } = useAuth();
  const handled = useRef(false);
  const [isBridgingPopup, setIsBridgingPopup] = useState(false);

  useEffect(() => {
    if (handled.current) {
      return;
    }
    handled.current = true;

    const refusalCode = new URLSearchParams(window.location.search).get("error");
    const accessToken = new URLSearchParams(window.location.hash.slice(1)).get("token");
    const handshakeId = takeHandshakeId();

    if (handshakeId) {
      setIsBridgingPopup(true);
      bridgeToOpener(handshakeId, outcomeOf(refusalCode, accessToken));
      return;
    }

    if (refusalCode) {
      navigate(`/login?error=${encodeURIComponent(refusalCode)}`, { replace: true });
      return;
    }

    if (!accessToken) {
      navigate("/login?error=OAUTH_FAILED", { replace: true });
      return;
    }

    setAccessToken(accessToken);

    // Wipe the token out of the address bar before anything can screenshot, bookmark or share it.
    scrubCredentialsFromUrl();

    void (async () => {
      try {
        // One round trip. This used to call me(), then reload() — which refreshes and calls me() again:
        // three requests to learn one thing we were about to be told anyway.
        const user = await authApi.me();
        adopt(accessToken, user);

        // The router's own answer to "where does this user belong", not a second copy of it: a user
        // who signed in with a provider may still be an invitee or hold an unfinished wizard, and
        // homeFor already encodes that ordering — which is load-bearing.
        const returnTo = takeReturnTo();
        navigate(returnTo && user.workspace ? returnTo : homeFor(user), { replace: true });
      } catch {
        setAccessToken(null);
        navigate("/login?error=OAUTH_FAILED", { replace: true });
      }
    })();
  }, [navigate, adopt]);

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-4 p-4 sm:p-6">
      <Logo />
      <p className="font-mono text-xs text-text3">
        {isBridgingPopup ? "You can close this window." : "Signing you in…"}
      </p>
    </div>
  );
}

/**
 * Reports to the opener and closes.
 *
 * The URL is scrubbed first: the access token in the fragment is not forwarded and not used here, but
 * this window is visible until it closes and the address bar is the one place it would be readable.
 * Closing is deferred a tick so both channels have handed the message off before the document goes.
 */
function bridgeToOpener(handshakeId: string, outcome: OAuthOutcome): void {
  scrubCredentialsFromUrl();
  reportOAuthOutcome(handshakeId, outcome);

  // If close() is refused the window stays put, which is why the page says so in words.
  window.setTimeout(() => window.close(), 0);
}

function outcomeOf(refusalCode: string | null, accessToken: string | null): OAuthOutcome {
  if (refusalCode) {
    return { status: "error", code: refusalCode };
  }
  return accessToken ? { status: "success" } : { status: "error", code: "OAUTH_FAILED" };
}

/** Drops the fragment and the query, leaving the path — history entry replaced, not added. */
function scrubCredentialsFromUrl(): void {
  window.history.replaceState(null, "", window.location.pathname);
}
