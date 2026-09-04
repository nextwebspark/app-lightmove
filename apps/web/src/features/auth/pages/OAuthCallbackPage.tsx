import { useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { homeFor } from "../../../app/routes";
import { Logo } from "../../../components/ui";
import { setAccessToken } from "../../../lib/apiClient";
import { useAuth } from "../AuthProvider";
import * as authApi from "../api/authApi";
import { takeReturnTo } from "../returnTo";

/**
 * Where an identity provider sends the browser back to.
 *
 * The server has already minted our own tokens by this point: the refresh token arrived as an httpOnly
 * cookie, and the access token is in the URL **fragment**.
 *
 * The fragment matters. A query string is sent to the server, lands in access logs, leaks through the
 * Referer header, and syncs into browser history. A fragment does none of that — it never leaves the
 * browser. We read it, put it in memory, and scrub it from the address bar immediately.
 */
export function OAuthCallbackPage() {
  const navigate = useNavigate();
  const { adopt } = useAuth();
  const handled = useRef(false);

  useEffect(() => {
    if (handled.current) {
      return;
    }
    handled.current = true;

    const token = new URLSearchParams(window.location.hash.slice(1)).get("token");

    if (!token) {
      navigate("/login?error=OAUTH_FAILED", { replace: true });
      return;
    }

    setAccessToken(token);

    // Wipe the token out of the address bar before anything can screenshot, bookmark or share it.
    window.history.replaceState(null, "", window.location.pathname);

    void (async () => {
      try {
        // One round trip. This used to call me(), then reload() — which refreshes and calls me() again:
        // three requests to learn one thing we were about to be told anyway.
        const user = await authApi.me();
        adopt(token, user);

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
      <p className="font-mono text-xs text-text3">Signing you in…</p>
    </div>
  );
}
