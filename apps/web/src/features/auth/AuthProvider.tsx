import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { onSessionExpired, restoreSession, setAccessToken } from "../../lib/apiClient";
import * as authApi from "./api/authApi";
import type { AuthResponse, User } from "./api/types";

/**
 * Who is signed in, for the whole app.
 *
 * The access token is deliberately *not* here. It lives inside apiClient, in a module variable that no
 * component can reach — which means no component can accidentally put it in a log, a URL, or
 * localStorage. This context holds only the user, which is what the UI actually renders.
 */

interface AuthContextValue {
  user: User | null;
  /** True until the initial session restore settles. Render nothing routing-dependent before then. */
  loading: boolean;

  signIn: (email: string, password: string) => Promise<User>;
  signUp: (fullName: string, email: string, password: string) => Promise<User>;
  signOut: () => Promise<void>;

  /** Re-reads the user from the server. Call after anything that changes their workspace or role. */
  reload: () => Promise<User | null>;
  /** Adopts a session established elsewhere — currently the Google OAuth callback. */
  adopt: (session: AuthResponse) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  /**
   * Restore the session on boot.
   *
   * The access token died with the page; the httpOnly refresh cookie did not. Exchanging it here is
   * what makes a hard refresh keep you signed in — the entire reason the token is allowed to be
   * memory-only in the first place.
   */
  useEffect(() => {
    let cancelled = false;

    void (async () => {
      const token = await restoreSession();

      if (!token) {
        if (!cancelled) setLoading(false);
        return;
      }

      try {
        const restored = await authApi.me();
        if (!cancelled) setUser(restored);
      } catch {
        // The cookie was good enough to mint a token but /me failed — the account is gone or
        // suspended. Treat it as no session rather than a half-signed-in state.
        setAccessToken(null);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  /**
   * The apiClient discovers an expired session on a background request, long after any component has
   * stopped watching. This is how it tells the UI.
   */
  useEffect(() => {
    onSessionExpired(() => setUser(null));
  }, []);

  const signIn = useCallback(async (email: string, password: string) => {
    const session = await authApi.login({ email, password });
    setUser(session.user);
    return session.user;
  }, []);

  const signUp = useCallback(async (fullName: string, email: string, password: string) => {
    const session = await authApi.signup({
      fullName,
      email,
      password,
      // The form will not submit without the box ticked; sending it explicitly keeps the server the
      // one that decides, rather than trusting the button to have been disabled.
      termsAccepted: true,
    });
    setUser(session.user);
    return session.user;
  }, []);

  const signOut = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      // Even if the call failed, this browser is done with the session. Clearing locally regardless
      // means a network blip cannot leave someone stuck looking at a page they meant to leave.
      setAccessToken(null);
      setUser(null);
    }
  }, []);

  const reload = useCallback(async () => {
    try {
      const fresh = await authApi.me();
      setUser(fresh);
      return fresh;
    } catch {
      setUser(null);
      return null;
    }
  }, []);

  const adopt = useCallback((session: AuthResponse) => {
    setAccessToken(session.accessToken);
    setUser(session.user);
  }, []);

  const value = useMemo(
    () => ({ user, loading, signIn, signUp, signOut, reload, adopt }),
    [user, loading, signIn, signUp, signOut, reload, adopt],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside <AuthProvider>");
  }
  return context;
}
