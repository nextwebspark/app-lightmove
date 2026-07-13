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
  adopt: (token: string, user: User) => void;
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

  /**
   * Re-reads the session from the server — <b>token first</b>, then user.
   *
   * <p>The refresh is not optional, and this is the subtle part. The tenant claims (`wsId`, `role`)
   * are baked into the access token when it is minted, so anything that changes a user's membership
   * leaves the token they are holding describing a world that no longer exists. Creating a workspace
   * mints nothing: the caller still carries a token that says they have none, and the very next
   * workspace-scoped call — sending the step-3 invites — fails `requireWorkspaceId()`. The same is true
   * of an admin approving a join request: `/auth/me` would happily report the new workspace while every
   * request made with the old token was still refused.
   *
   * <p>`/auth/refresh` re-reads the membership from the database and mints a token that tells the
   * truth. Only then is it worth asking who the user is.
   */
  const reload = useCallback(async () => {
    try {
      await restoreSession();
      const fresh = await authApi.me();
      setUser(fresh);
      return fresh;
    } catch {
      setUser(null);
      return null;
    }
  }, []);

  /**
   * Takes on a session that was established somewhere other than here — currently the Google callback,
   * which is handed a token by the server rather than exchanging credentials for one.
   *
   * <p>Takes the token and user separately rather than an {@code AuthResponse}, because the OAuth
   * callback never sees one: it is given a bare token in the URL fragment and has to ask who it belongs
   * to. Making it fabricate a response object to hand back would be a lie for the type's benefit.
   */
  const adopt = useCallback((token: string, adopted: User) => {
    setAccessToken(token);
    setUser(adopted);
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
