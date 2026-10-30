import { lazy, Suspense, useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import { AuthLogo, Button, Card, FormError } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { RecoveryCodesModal } from "../../settings/components/RecoveryCodesModal";
import { ThemeToggle } from "../../theme/ThemeToggle";
import { useAuth } from "../AuthProvider";
import { landingAfterSignIn, safeReturnTo } from "../returnTo";

const EnableTwoFactorModal = lazy(() => import("../../settings/components/EnableTwoFactorModal"));

/**
 * Where a member lands when the session's workspace requires two-factor authentication and they have not set it up:
 * the server refuses every read of the workspace until they do, so this is the one thing on offer — that, another
 * workspace of theirs, or signing out. The recovery codes are shown once, before the app opens.
 */
export function TwoFactorEnrolmentPage() {
  const { user, reload, signOut, switchWorkspace } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const returnTo = safeReturnTo((location.state as { from?: unknown } | null)?.from);

  const [settingUp, setSettingUp] = useState(false);
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);
  const [switchError, setSwitchError] = useState<string | null>(null);

  if (!user) return null;
  if (!user.twoFactorEnrolmentRequired && !recoveryCodes) {
    return <Navigate to={landingAfterSignIn(user, returnTo)} replace />;
  }

  const otherWorkspaces = user.workspaces.filter((workspace) => workspace.id !== user.workspace?.id);

  const handleEnabled = (codes: string[]) => {
    setSettingUp(false);
    setRecoveryCodes(codes);
  };

  const handleCodesSaved = async () => {
    const fresh = await reload();
    setRecoveryCodes(null);
    if (fresh) navigate(landingAfterSignIn(fresh, returnTo), { replace: true });
  };

  const handleSwitch = async (workspaceId: string) => {
    setSwitchError(null);
    try {
      const moved = await switchWorkspace(workspaceId);
      navigate(landingAfterSignIn(moved, null), { replace: true });
    } catch (error) {
      setSwitchError(messageFor(error));
    }
  };

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 p-4 sm:p-6">
      <ThemeToggle className="fixed right-4 top-4 z-50" />
      <AuthLogo />

      <Card className="w-[440px] max-w-[94vw] [animation-delay:60ms]">
        <h1 className="type-title">Set up two-factor authentication</h1>
        <p className="mb-6 mt-1 text-note text-u-text2">
          {user.workspace?.name ?? "This workspace"} requires two-factor authentication. Set it up with an authenticator
          app to continue.
        </p>

        <Button type="button" className="w-full" onClick={() => setSettingUp(true)}>
          Set up two-factor authentication
        </Button>

        {otherWorkspaces.length > 0 && (
          <div className="mt-6 border-t border-u-border pt-4">
            <p className="mb-2 font-mono text-meta text-u-text3">Or open another workspace</p>
            <FormError message={switchError} />
            <ul className="flex flex-col gap-1">
              {otherWorkspaces.map((workspace) => (
                <li key={workspace.id}>
                  <button
                    type="button"
                    onClick={() => void handleSwitch(workspace.id)}
                    className="w-full rounded-u-control px-2 py-1.5 text-left text-note text-u-text hover:bg-u-raised"
                  >
                    {workspace.name}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}
      </Card>

      <p className="animate-fade-up text-note text-u-text2 [animation-delay:120ms]">
        Signed in as {user.email} ·{" "}
        <button type="button" onClick={() => void signOut()} className="text-u-accent hover:underline">
          Sign out
        </button>
      </p>

      {settingUp && (
        <Suspense fallback={null}>
          <EnableTwoFactorModal
            hasPassword={user.hasPassword}
            onEnabled={handleEnabled}
            onClose={() => setSettingUp(false)}
          />
        </Suspense>
      )}

      {recoveryCodes && <RecoveryCodesModal codes={recoveryCodes} onDone={() => void handleCodesSaved()} />}
    </div>
  );
}
