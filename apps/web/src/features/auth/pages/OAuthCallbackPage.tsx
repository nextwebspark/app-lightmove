import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Logo } from "../../../components/ui";
import { setAccessToken } from "../../../lib/apiClient";
import { useAuth } from "../AuthProvider";
import * as authApi from "../api/authApi";
import { type OAuthOutcome, reportOAuthOutcome, takeHandshakeId } from "../oauthPopup";
import { TWO_FACTOR_SIGN_IN_PATH } from "./TwoFactorChallengePage";
import { landingAfterSignIn, takeReturnTo } from "../returnTo";

/**
 * The provider's return page. In a popup it only reports the outcome and closes (the opener makes the session); in a
 * redirect it reads the access token from the URL fragment (never a query: logs, Referer, history) and scrubs it.
 * A refusal lands here as `?error=` so a popup never renders the login screen; outside one it forwards to `/login?error=`.
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

    const query = new URLSearchParams(window.location.search);
    const refusalCode = query.get("error");
    const owesSecondFactor = query.get(SECOND_FACTOR_PARAM) === SECOND_FACTOR_VALUE;
    const accessToken = new URLSearchParams(window.location.hash.slice(1)).get("token");
    const handshakeId = takeHandshakeId();

    if (handshakeId) {
      setIsBridgingPopup(true);
      bridgeToOpener(handshakeId, outcomeOf(refusalCode, owesSecondFactor, accessToken));
      return;
    }

    // A person with two-factor on: the server set the challenge cookie and no session, so on to the code.
    if (!refusalCode && owesSecondFactor) {
      const returnTo = takeReturnTo();
      navigate(TWO_FACTOR_SIGN_IN_PATH, { replace: true, state: returnTo ? { from: returnTo } : undefined });
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
        navigate(landingAfterSignIn(user, returnTo), { replace: true });
      } catch {
        setAccessToken(null);
        navigate("/login?error=OAUTH_FAILED", { replace: true });
      }
    })();
  }, [navigate, adopt]);

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-4 p-4 sm:p-6">
      <Logo />
      <p className="font-mono text-xs text-u-text3">
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

function outcomeOf(refusalCode: string | null, owesSecondFactor: boolean, accessToken: string | null): OAuthOutcome {
  if (refusalCode) {
    return { status: "error", code: refusalCode };
  }
  if (owesSecondFactor) {
    return { status: "secondFactor" };
  }
  return accessToken ? { status: "success" } : { status: "error", code: "OAUTH_FAILED" };
}

/** The server's mark, in place of a token, that the provider's sign-in still owes the person's code. */
const SECOND_FACTOR_PARAM = "challenge";
const SECOND_FACTOR_VALUE = "two-factor";

/** Drops the fragment and the query, leaving the path — history entry replaced, not added. */
function scrubCredentialsFromUrl(): void {
  window.history.replaceState(null, "", window.location.pathname);
}
