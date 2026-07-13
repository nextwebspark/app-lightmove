import type { ReactNode } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { Logo } from "../components/ui";
import { useAuth } from "../features/auth/AuthProvider";
import { InviteStepPage } from "../features/auth/pages/InviteStepPage";
import { LoginPage } from "../features/auth/pages/LoginPage";
import { OAuthCallbackPage } from "../features/auth/pages/OAuthCallbackPage";
import { PendingApprovalPage } from "../features/auth/pages/PendingApprovalPage";
import { SignupPage } from "../features/auth/pages/SignupPage";
import { VerifyEmailPage } from "../features/auth/pages/VerifyEmailPage";
import { WorkspaceStepPage } from "../features/auth/pages/WorkspaceStepPage";
import { WorkspacePage } from "../features/workspace/pages/WorkspacePage";

/**
 * Routing follows the user's actual state, not a step counter.
 *
 * A signup wizard held in component state cannot survive a closed tab, and the account created at step
 * 1 is real and permanent. So each guard asks the server-derived user what is true right now — do they
 * exist, are they verified, do they have a workspace — and routes on the answer. Someone who signs up,
 * closes the browser, and signs back in a week later lands exactly where they left off, without the
 * app having to remember anything.
 */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<AnonymousOnly><LoginPage /></AnonymousOnly>} />
      <Route path="/signup" element={<AnonymousOnly><SignupPage /></AnonymousOnly>} />

      {/* Public: the link is clicked from an email, in a browser that may have no session at all. */}
      <Route path="/auth/verify" element={<VerifyEmailPage />} />
      <Route path="/auth/callback" element={<OAuthCallbackPage />} />

      {/* Signed in, but not yet in a workspace. */}
      <Route path="/signup/workspace" element={<RequireAuth><WorkspaceStepPage /></RequireAuth>} />
      <Route path="/signup/invite" element={<RequireWorkspace><InviteStepPage /></RequireWorkspace>} />
      <Route path="/signup/pending" element={<RequireAuth><PendingApprovalPage /></RequireAuth>} />

      <Route path="/" element={<RequireWorkspace><WorkspacePage /></RequireWorkspace>} />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

/** The initial session restore is in flight. Routing now would flash the login page at a signed-in user. */
function Booting() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4">
      <Logo />
      <p className="font-mono text-xs text-text3">Loading…</p>
    </div>
  );
}

function AnonymousOnly({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();

  if (loading) return <Booting />;
  if (user) return <Navigate to={user.workspace ? "/" : "/signup/workspace"} replace />;

  return <>{children}</>;
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();

  if (loading) return <Booting />;

  if (!user) {
    // Remember where they were headed, so signing in returns them to it rather than dumping them on
    // the home page.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <>{children}</>;
}

/**
 * The app proper. Requires an account *and* a workspace.
 *
 * A user with neither is mid-signup; one with a pending join request has an account and no workspace,
 * and belongs on the waiting screen rather than being bounced back into a wizard they have finished.
 */
function RequireWorkspace({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();

  if (loading) return <Booting />;
  if (!user) return <Navigate to="/login" replace />;
  if (!user.workspace) return <Navigate to="/signup/workspace" replace />;

  return <>{children}</>;
}
