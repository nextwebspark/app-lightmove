import { useQueryClient } from "@tanstack/react-query";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import {
  onSessionExpired,
  onWorkspaceMoved,
  restoreSession,
  setAccessToken,
  switchWorkspaceSession,
} from "../../lib/apiClient";
import { setDateTimePreferences } from "../../lib/datetime";
import { forgetOpenAssistant } from "../assistant/assistantStorage";
import { forgetScorecardDrafts } from "../candidates/lib/scorecardDrafts";
import { isReturningMailboxPopup } from "../outreach/lib/mailboxPopup";
import * as authApi from "./api/authApi";
import { isReturningSignInPopup } from "./oauthPopup";
import { isMfaChallenge, type MfaChallenge, type SignInAnswer, type User } from "./api/types";
import { describeWorkspaceMove, rememberWorkspaceMove } from "./workspaceMoveNotice";

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

  /** The user, or for a person with two-factor on a challenge: no session until `completeSecondFactor`. */
  signIn: (email: string, password: string) => Promise<User | MfaChallenge>;
  /** Finishes a challenged sign-in with a code from the authenticator app, or a recovery code. */
  completeSecondFactor: (code: string) => Promise<User>;
  signUp: (fullName: string, email: string, password: string) => Promise<User>;
  /** Creates the invited account and adopts the session it returns — the invitee is in immediately. */
  acceptInviteSignup: (token: string, fullName: string, password: string) => Promise<User>;
  /** Redeems a verification link and adopts the session it returns — proving the mailbox is the sign-in. */
  verifyEmail: (token: string) => Promise<User | MfaChallenge>;
  /**
   * Redeems a reset link and adopts the session it returns — no second sign-in with the new password. Someone with
   * two-factor on still owes the code: the link proves the mailbox, which alone is not meant to be enough.
   */
  resetPassword: (token: string, password: string) => Promise<User | MfaChallenge>;
  signOut: () => Promise<void>;
  /** Forgets a session the server has already ended — the account was deleted — without asking it again. */
  forgetDeletedAccount: () => void;

  /** Re-reads the user from the server. Call after anything that changes their workspace or role. */
  reload: () => Promise<User | null>;
  /**
   * Moves the session into another of the user's workspaces. The query cache is cleared: nothing in it
   * is keyed by workspace. Other tabs share the cookie and follow.
   */
  switchWorkspace: (workspaceId: string) => Promise<User>;
  /**
   * Accepts an invitation and moves into the workspace it joined. When the move fails the user is
   * re-read, so the joined workspace is listed and the spent invitation is not.
   */
  acceptAndSwitch: (accept: () => Promise<User>) => Promise<User>;
  /** Adopts a session established elsewhere — the OAuth callback, after a full-page redirect. */
  adopt: (token: string, user: User) => void;
  /**
   * Adopts a session the server set in another window (the popup sign-in): the popup discards its token and only the
   * refresh cookie is exchanged here, so no credential crosses the message channel. Null when there is none to adopt.
   */
  adoptRestoredSession: () => Promise<User | null>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const WORKSPACE_CHANNEL = "lm-workspace";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const queryClient = useQueryClient();

  // Before paint: every date reads in the signed-in person's zone and formats, and `DateTimeBoundary` redraws on a move.
  useLayoutEffect(() => setDateTimePreferences(user?.dateTime?.effective ?? null), [user]);

  /**
   * Server state belongs to whoever was signed in when it was fetched, so it dies with them. Otherwise
   * the cache outlives the session and the next user in this tab is served the last one's data.
   *
   * <p>Called *before* setUser, so the new user's first render sees an empty cache rather than the
   * previous user's answers.
   */
  const startFreshSession = useCallback(() => queryClient.clear(), [queryClient]);

  /**
   * Restore the session on boot from the httpOnly refresh cookie. Never inside a sign-in or mailbox popup: the popup
   * closed mid-refresh after the server rotated, the opener presented the spent token, read as reuse, and revoked the
   * new session. Read at render: the callback page consumes the handshake in its own effect, which runs first.
   */
  const [isSignInPopup] = useState(() => isReturningSignInPopup() || isReturningMailboxPopup());

  useEffect(() => {
    if (isSignInPopup) {
      setLoading(false);
      return;
    }

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
  }, [isSignInPopup]);

  /**
   * The apiClient discovers an expired session on a background request, long after any component has
   * stopped watching. This is how it tells the UI.
   */
  useEffect(() => {
    onSessionExpired(() => {
      startFreshSession();
      forgetScorecardDrafts();
      setUser(null);
    });
  }, [startFreshSession]);

  /**
   * Other tabs share the refresh cookie, so a switch here moves them too — at their next refresh, up to
   * fifteen minutes away, until which they would keep serving the old workspace's cache under the new
   * token. Told at once instead, they reload from the top, which is what a tab in the wrong tenant
   * should do.
   */
  const workspaceChannel = useRef<BroadcastChannel | null>(null);
  useEffect(() => {
    if (typeof BroadcastChannel === "undefined") return;
    const channel = new BroadcastChannel(WORKSPACE_CHANNEL);
    channel.onmessage = () => window.location.replace("/");
    workspaceChannel.current = channel;
    return () => {
      channel.close();
      workspaceChannel.current = null;
    };
  }, []);

  /**
   * A refresh moved the session because the membership it was in ended. Everything on screen belongs
   * to the workspace left, so the tab starts again from the top, and says why once it has.
   */
  const userRef = useRef(user);
  useLayoutEffect(() => {
    userRef.current = user;
  }, [user]);
  useEffect(() => {
    onWorkspaceMoved((moved) => {
      rememberWorkspaceMove(describeWorkspaceMove(userRef.current?.workspace ?? null, moved));
      forgetOpenAssistant();
      window.location.replace("/");
    });
  }, []);

  const switchWorkspace = useCallback(
    async (workspaceId: string) => {
      const session = await switchWorkspaceSession(workspaceId);
      startFreshSession();
      forgetOpenAssistant();
      setUser(session.user);
      // Through the listening channel itself: a channel never receives its own posts, but a second
      // one of the same name in this tab does — which reloaded the switching tab too, and threw away
      // the New workspace modal between its two stages.
      workspaceChannel.current?.postMessage({ workspaceId });
      return session.user;
    },
    [startFreshSession],
  );

  /** The user is set only once there is a session: set on a challenge, AnonymousOnly would leave the code step. */
  const adoptAnswer = useCallback(
    (answer: SignInAnswer): User | MfaChallenge => {
      if (isMfaChallenge(answer)) {
        return answer;
      }
      startFreshSession();
      setUser(answer.user);
      return answer.user;
    },
    [startFreshSession],
  );

  const signIn = useCallback(
    async (email: string, password: string) => adoptAnswer(await authApi.login({ email, password })),
    [adoptAnswer],
  );

  const completeSecondFactor = useCallback(
    async (code: string) => {
      const session = await authApi.verifySecondFactor(code);
      startFreshSession();
      setUser(session.user);
      return session.user;
    },
    [startFreshSession],
  );

  const signUp = useCallback(
    async (fullName: string, email: string, password: string) => {
      const session = await authApi.signup({
        fullName,
        email,
        password,
        // The form will not submit without the box ticked; sending it explicitly keeps the server the
        // one that decides, rather than trusting the button to have been disabled.
        termsAccepted: true,
      });
      startFreshSession();
      setUser(session.user);
      return session.user;
    },
    [startFreshSession],
  );

  const acceptInviteSignup = useCallback(
    async (token: string, fullName: string, password: string) => {
      const session = await authApi.acceptInvitationSignup(token, fullName, password);
      startFreshSession();
      setUser(session.user);
      return session.user;
    },
    [startFreshSession],
  );

  const verifyEmail = useCallback(
    async (token: string) => adoptAnswer(await authApi.verifyEmail(token)),
    [adoptAnswer],
  );

  const resetPassword = useCallback(
    async (token: string, password: string) => adoptAnswer(await authApi.resetPassword(token, password)),
    [adoptAnswer],
  );

  const forgetDeletedAccount = useCallback(() => {
    setAccessToken(null);
    startFreshSession();
    forgetScorecardDrafts();
    setUser(null);
  }, [startFreshSession]);

  const signOut = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      // Even if the call failed, this browser is done with the session. Clearing locally regardless
      // means a network blip cannot leave someone stuck looking at a page they meant to leave.
      setAccessToken(null);
      startFreshSession();
      forgetScorecardDrafts();
      setUser(null);
    }
  }, [startFreshSession]);

  /**
   * Re-reads the session — token first, then user. Tenant claims (`wsId`, `role`) are baked in at mint, so after a
   * membership change (a new workspace, an approved join) the held token is refused; `/auth/refresh` mints a true one.
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

  const acceptAndSwitch = useCallback(
    async (accept: () => Promise<User>) => {
      const joined = await accept();
      if (!joined.workspace) throw new Error("The invitation led to no workspace");
      try {
        return await switchWorkspace(joined.workspace.id);
      } catch (error) {
        await reload();
        throw error;
      }
    },
    [switchWorkspace, reload],
  );

  /**
   * Takes on a session established elsewhere (the OAuth callback, handed a bare token in the URL fragment), so it takes
   * the token and user separately rather than a fabricated {@code AuthResponse}.
   */
  const adopt = useCallback(
    (token: string, adopted: User) => {
      setAccessToken(token);
      startFreshSession();
      setUser(adopted);
    },
    [startFreshSession],
  );

  /**
   * Mints an access token from the refresh cookie, learns who it belongs to, and adopts both.
   *
   * Reuses `restoreSession` — the same exchange a cold page load performs — rather than a second way
   * of turning that cookie into a session, and hands off to `adopt` so the query cache is cleared
   * exactly as it is for every other way of signing in.
   */
  const adoptRestoredSession = useCallback(async () => {
    const token = await restoreSession();
    if (!token) {
      return null;
    }

    try {
      const adopted = await authApi.me();
      adopt(token, adopted);
      return adopted;
    } catch {
      setAccessToken(null);
      return null;
    }
  }, [adopt]);

  const value = useMemo(
    () => ({
      user,
      loading,
      signIn,
      completeSecondFactor,
      signUp,
      acceptInviteSignup,
      verifyEmail,
      resetPassword,
      signOut,
      forgetDeletedAccount,
      reload,
      switchWorkspace,
      acceptAndSwitch,
      adopt,
      adoptRestoredSession,
    }),
    [
      user,
      loading,
      signIn,
      completeSecondFactor,
      signUp,
      acceptInviteSignup,
      verifyEmail,
      resetPassword,
      signOut,
      forgetDeletedAccount,
      reload,
      switchWorkspace,
      acceptAndSwitch,
      adopt,
      adoptRestoredSession,
    ],
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
