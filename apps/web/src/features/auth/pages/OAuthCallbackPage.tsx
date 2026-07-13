import { useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { Logo } from "../../../components/ui";
import { setAccessToken } from "../../../lib/apiClient";
import { useAuth } from "../AuthProvider";
import * as authApi from "../api/authApi";

/**
 * Where Google sends the browser back to.
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
  const { reload } = useAuth();
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
        const user = await authApi.me();
        await reload();
        // A Google user who has never onboarded has no workspace, exactly like a password signup.
        navigate(user.workspace ? "/" : "/signup/workspace", { replace: true });
      } catch {
        setAccessToken(null);
        navigate("/login?error=OAUTH_FAILED", { replace: true });
      }
    })();
  }, [navigate, reload]);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 p-6">
      <Logo />
      <p className="font-mono text-xs text-text3">Signing you in…</p>
    </div>
  );
}
