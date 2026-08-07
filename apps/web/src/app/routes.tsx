import type { ReactNode } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { ProjectLayout } from "../components/layout/ProjectLayout";
import { SettingsLayout } from "../components/layout/SettingsLayout";
import { WorkspaceLayout } from "../components/layout/WorkspaceLayout";
import { Logo } from "../components/ui";
import { useAuth } from "../features/auth/AuthProvider";
import { AcceptInvitePage } from "../features/auth/pages/AcceptInvitePage";
import { CheckInboxPage } from "../features/auth/pages/CheckInboxPage";
import { ForgotPasswordPage } from "../features/auth/pages/ForgotPasswordPage";
import { InviteStepPage } from "../features/auth/pages/InviteStepPage";
import { LoginPage } from "../features/auth/pages/LoginPage";
import { OAuthCallbackPage } from "../features/auth/pages/OAuthCallbackPage";
import { ResetPasswordPage } from "../features/auth/pages/ResetPasswordPage";
import { SignupPage } from "../features/auth/pages/SignupPage";
import { VerifyEmailPage } from "../features/auth/pages/VerifyEmailPage";
import { WorkspaceStepPage } from "../features/auth/pages/WorkspaceStepPage";
import { ClientsPage } from "../features/clients/pages/ClientsPage";
import { PositionPage } from "../features/position/pages/PositionPage";
import { ProjectPlaceholderPage } from "../features/position/pages/ProjectPlaceholderPage";
import { ProjectsPage } from "../features/projects/pages/ProjectsPage";
import { TeamAccessPage } from "../features/projects/pages/TeamAccessPage";
import { SettingsGeneralPage } from "../features/settings/pages/SettingsGeneralPage";
import { SettingsMembersPage } from "../features/settings/pages/SettingsMembersPage";
import { SourcingPage } from "../features/sourcing/pages/SourcingPage";
import { StrategyPage } from "../features/strategy/pages/StrategyPage";
import { TeamPage } from "../features/workspace/pages/TeamPage";

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
      <Route path="/forgot-password" element={<AnonymousOnly><ForgotPasswordPage /></AnonymousOnly>} />

      {/* Public: the link is clicked from an email, in a browser that may have no session at all. */}
      <Route path="/auth/verify" element={<VerifyEmailPage />} />
      {/* Public like /auth/verify, and deliberately not AnonymousOnly: a signed-in user following the
          link simply replaces their session with the fresh one the reset returns. */}
      <Route path="/auth/reset-password" element={<ResetPasswordPage />} />
      <Route path="/auth/callback" element={<OAuthCallbackPage />} />

      {/* Public, and unguarded on purpose: the invitee may have no account, an unverified one, or be
          signed in as somebody else entirely. The page reads its own state and says which. Guarding it
          with RequireAuth would bounce a first-time invitee to a login screen for an account they have
          not got, which is where the invitation used to die. */}
      <Route path="/auth/accept-invite" element={<AcceptInvitePage />} />

      {/* Signed in, but not yet in a workspace. */}
      <Route path="/signup/workspace" element={<RequireAuth><WorkspaceStepPage /></RequireAuth>} />
      {/* RequireAuth, not RequireWorkspace: an unverified user has no workspace yet — theirs is held
          until they confirm their address — and is still entitled to finish their own signup. Guarding
          this with RequireWorkspace bounced them straight back to step 2, in a loop. */}
      <Route path="/signup/invite" element={<RequireAuth><InviteStepPage /></RequireAuth>} />
      <Route path="/signup/verify" element={<RequireAuth><CheckInboxPage /></RequireAuth>} />

      {/* The app shell. Sidebar views are routes so every screen is deep-linkable. */}
      <Route element={<RequireWorkspace><WorkspaceLayout /></RequireWorkspace>}>
        <Route path="/" element={<ProjectsPage view="my" />} />
        <Route path="/all" element={<ProjectsPage view="all" />} />
        <Route path="/clients" element={<ClientsPage />} />
        <Route path="/team" element={<TeamPage />} />
      </Route>

      {/* The project workspace shell (Project.dc.html). Position is the mandate's landing tab; the
          other tabs are placeholders until their screens are built. */}
      <Route element={<RequireWorkspace><ProjectLayout /></RequireWorkspace>}>
        <Route path="/projects/:projectId" element={<PositionPage />} />
        <Route path="/projects/:projectId/strategy" element={<StrategyPage />} />
        <Route path="/projects/:projectId/sourcing" element={<SourcingPage />} />
        <Route path="/projects/:projectId/candidates" element={<ProjectPlaceholderPage title="Candidates" icon="candidates" />} />
        <Route path="/projects/:projectId/outreach" element={<ProjectPlaceholderPage title="Outreach" icon="outreach" />} />
        <Route path="/projects/:projectId/reports" element={<ProjectPlaceholderPage title="Reports" icon="reports" />} />
        <Route path="/projects/:projectId/team" element={<TeamAccessPage />} />
      </Route>

      {/* Admin-gated in the client for UX only; every settings endpoint re-checks in the service. */}
      <Route element={<RequireAdmin><SettingsLayout /></RequireAdmin>}>
        <Route path="/settings" element={<Navigate to="/settings/general" replace />} />
        <Route path="/settings/general" element={<SettingsGeneralPage />} />
        <Route path="/settings/members" element={<SettingsMembersPage />} />
      </Route>

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
  if (user) return <Navigate to={homeFor(user)} replace />;

  return <>{children}</>;
}

/**
 * Where a signed-in user actually belongs, given what is true of them right now.
 *
 * Three states share `workspace: null` and they are not the same place. Someone who *finished* the
 * wizard while unverified needs their inbox, not an empty form they have already filled in. Someone
 * holding an invitation belongs on "join {workspace}", never on create-your-own. Everyone else has
 * not started, and needs the wizard.
 *
 * Order matters: `onboardingHeld` outranks `pendingInvitation`. Verifying materialises a held wizard
 * into a workspace, so routing a held user to accept-invite would dead-end on ALREADY_IN_WORKSPACE
 * the moment they verify.
 */
function homeFor(user: {
  workspace: { roles: string[] } | null;
  onboardingHeld: boolean;
  pendingInvitation: unknown;
}) {
  // Everyone in a workspace lands on the projects list; the server scopes a pure client's to their seats.
  if (user.workspace) return "/";
  if (user.onboardingHeld) return "/signup/verify";
  if (user.pendingInvitation) return "/auth/accept-invite";
  return "/signup/workspace";
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
 * A user with neither is mid-signup; one holding an invitation has an account and no workspace, and
 * belongs on the join confirmation rather than being bounced into a wizard that isn't theirs.
 */
function RequireWorkspace({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();

  if (loading) return <Booting />;
  if (!user) return <Navigate to="/login" replace />;
  if (!user.workspace) return <Navigate to={homeFor(user)} replace />;

  return <>{children}</>;
}

/** Hides admin screens from non-admins. UX only — the server re-reads the roles on every call. */
function RequireAdmin({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();

  if (loading) return <Booting />;
  if (!user) return <Navigate to="/login" replace />;
  if (!user.workspace?.roles.includes("ADMIN")) return <Navigate to="/" replace />;

  return <>{children}</>;
}
